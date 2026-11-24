package com.example.querytranslator.parser;

import com.example.querytranslator.ast.*;
import com.example.querytranslator.config.QueryTranslatorProperties;
import com.example.querytranslator.exception.InvalidRequestException;
import com.example.querytranslator.exception.UnsupportedOperatorException;
import com.example.querytranslator.exception.UnsupportedQueryException;
import com.example.querytranslator.schema.FieldDefinition;
import com.example.querytranslator.schema.FieldType;
import com.example.querytranslator.schema.TableDefinition;
import com.example.querytranslator.schema.TableRegistry;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class OpenSearchQueryParser {
    private static final Set<String> BOOL_KEYS = Set.of("must", "filter", "should", "must_not");
    private final TableRegistry tableRegistry;
    private final int maxDepth;
    private final int maxTermsValues;
    private final int maxWildcardLength;

    public OpenSearchQueryParser(TableRegistry tableRegistry, QueryTranslatorProperties properties) {
        this.tableRegistry = tableRegistry;
        this.maxDepth = properties.getLimits().getNestingDepth();
        this.maxTermsValues = properties.getLimits().getTermsValues();
        this.maxWildcardLength = properties.getLimits().getWildcardLength();
        if (maxDepth < 1 || maxTermsValues < 1 || maxWildcardLength < 1) {
            throw new IllegalStateException("Query limits must be positive");
        }
    }

    public QueryNode parse(String tableName, JsonNode query) {
        return parse(tableRegistry.requireTable(tableName), query);
    }

    public QueryNode parse(TableDefinition table, JsonNode query) {
        if (query == null || !query.isObject()) {
            throw new InvalidRequestException("query must be an object", "query");
        }
        return parseNode(query, table, "query", 1);
    }

    private QueryNode parseNode(JsonNode node, TableDefinition table, String path, int depth) {
        checkDepth(depth, path);
        if (!node.isObject() || node.size() != 1) {
            throw new UnsupportedQueryException("Each query object must contain exactly one operator", path);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        Map.Entry<String, JsonNode> entry = fields.next();
        String operator = entry.getKey();
        JsonNode body = entry.getValue();
        String operatorPath = path + "." + operator;
        return switch (operator) {
            case "term" -> parseTerm(body, table, operatorPath);
            case "terms" -> parseTerms(body, table, operatorPath);
            case "range" -> parseRange(body, table, operatorPath);
            case "exists" -> parseExists(body, table, operatorPath);
            case "bool" -> parseBool(body, table, operatorPath, depth);
            case "wildcard" -> parseWildcard(body, table, operatorPath);
            default -> throw new UnsupportedOperatorException(operator, operatorPath);
        };
    }

    private QueryNode parseTerm(JsonNode body, TableDefinition table, String path) {
        soleFieldBody(body, path);
        String fieldName = body.propertyNames().iterator().next();
        FieldDefinition field = requireField(table, fieldName, path + "." + fieldName);
        JsonNode value = body.get(fieldName);
        return new ComparisonNode(field.name(), parseValue(value, field.type(), path + "." + fieldName));
    }

    private QueryNode parseTerms(JsonNode body, TableDefinition table, String path) {
        soleFieldBody(body, path);
        String fieldName = body.propertyNames().iterator().next();
        String fieldPath = path + "." + fieldName;
        FieldDefinition field = requireField(table, fieldName, fieldPath);
        JsonNode values = body.get(fieldName);
        if (!values.isArray() || values.isEmpty()) {
            throw new UnsupportedQueryException("terms requires a non-empty array", fieldPath);
        }
        if (values.size() > maxTermsValues) {
            throw new UnsupportedQueryException("terms exceeds the configured value limit", fieldPath);
        }
        List<Object> parsed = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            parsed.add(parseValue(values.get(i), field.type(), fieldPath + "[" + i + "]"));
        }
        return new InNode(field.name(), parsed);
    }

    private QueryNode parseRange(JsonNode body, TableDefinition table, String path) {
        soleFieldBody(body, path);
        String fieldName = body.propertyNames().iterator().next();
        String fieldPath = path + "." + fieldName;
        FieldDefinition field = requireField(table, fieldName, fieldPath);
        if (field.type() != FieldType.NUMBER && field.type() != FieldType.TIMESTAMP) {
            throw new UnsupportedQueryException("range is supported only for NUMBER and TIMESTAMP fields", fieldPath);
        }
        JsonNode bounds = body.get(fieldName);
        if (!bounds.isObject() || bounds.isEmpty()) {
            throw new UnsupportedQueryException("range requires at least one boundary", fieldPath);
        }
        for (String name : bounds.propertyNames()) {
            if (!Set.of("gt", "gte", "lt", "lte").contains(name)) {
                throw new UnsupportedQueryException("range option '" + name + "' is not supported", fieldPath + "." + name);
            }
        }
        if (hasProperty(bounds, "gt") && hasProperty(bounds, "gte")) {
            throw new UnsupportedQueryException("Use either gt or gte, not both", fieldPath);
        }
        if (hasProperty(bounds, "lt") && hasProperty(bounds, "lte")) {
            throw new UnsupportedQueryException("Use either lt or lte, not both", fieldPath);
        }
        List<RangeBound> parsed = new ArrayList<>(2);
        for (String name : List.of("gt", "gte", "lt", "lte")) {
            if (hasProperty(bounds, name)) {
                RangeOperator op = RangeOperator.valueOf(name.toUpperCase());
                parsed.add(new RangeBound(op, parseValue(bounds.get(name), field.type(), fieldPath + "." + name)));
            }
        }
        return new RangeNode(field.name(), parsed);
    }

    private QueryNode parseExists(JsonNode body, TableDefinition table, String path) {
        if (!body.isObject() || body.size() != 1 || !hasProperty(body, "field") || !body.get("field").isString()) {
            throw new UnsupportedQueryException("exists requires exactly one string property named field", path);
        }
        String fieldName = body.get("field").stringValue();
        FieldDefinition field = requireField(table, fieldName, path + ".field");
        return new ExistsNode(field.name());
    }

    private QueryNode parseBool(JsonNode body, TableDefinition table, String path, int depth) {
        if (!body.isObject() || body.isEmpty()) throw new UnsupportedQueryException("bool requires at least one clause", path);
        List<QueryNode> groups = new ArrayList<>();
        for (String name : body.propertyNames()) {
            if (!BOOL_KEYS.contains(name)) {
                String detail = name.equals("minimum_should_match")
                        ? "minimum_should_match is not supported"
                        : "bool property '" + name + "' is not supported";
                throw new UnsupportedQueryException(detail, path + "." + name);
            }
        }
        for (String clause : List.of("must", "filter", "should", "must_not")) {
            if (!hasProperty(body, clause)) continue;
            JsonNode items = body.get(clause);
            String clausePath = path + "." + clause;
            if (!items.isArray() || items.isEmpty()) {
                throw new UnsupportedQueryException("bool clause must be a non-empty array", clausePath);
            }
            List<QueryNode> children = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                children.add(parseNode(items.get(i), table, clausePath + "[" + i + "]", depth + 1));
            }
            if (clause.equals("should")) {
                groups.add(children.size() == 1 ? children.getFirst() : new OrNode(children));
            } else if (clause.equals("must") || clause.equals("filter")) {
                groups.add(children.size() == 1 ? children.getFirst() : new AndNode(children));
            } else {
                for (QueryNode child : children) groups.add(new NotNode(child));
            }
        }
        if (groups.isEmpty()) throw new UnsupportedQueryException("bool requires at least one non-empty clause", path);
        return groups.size() == 1 ? groups.getFirst() : new AndNode(groups);
    }

    private QueryNode parseWildcard(JsonNode body, TableDefinition table, String path) {
        soleFieldBody(body, path);
        String fieldName = body.propertyNames().iterator().next();
        String fieldPath = path + "." + fieldName;
        FieldDefinition field = requireField(table, fieldName, fieldPath);
        if (field.type() != FieldType.STRING) {
            throw new UnsupportedQueryException("wildcard is supported only for STRING fields", fieldPath);
        }
        JsonNode value = body.get(fieldName);
        if (value.isObject()) {
            for (String option : value.propertyNames()) {
                if (option.equals("case_insensitive")) {
                    throw new UnsupportedQueryException("case_insensitive is not supported", fieldPath + ".case_insensitive");
                }
                if (!option.equals("value")) {
                    throw new UnsupportedQueryException("wildcard option '" + option + "' is not supported", fieldPath + "." + option);
                }
            }
            if (value.size() != 1 || !hasProperty(value, "value")) {
                throw new UnsupportedQueryException("wildcard object requires exactly one value property", fieldPath);
            }
            value = value.get("value");
        }
        if (!value.isString()) throw new UnsupportedQueryException("wildcard value must be a string", fieldPath);
        String pattern = value.stringValue();
        if (pattern.codePointCount(0, pattern.length()) > maxWildcardLength) {
            throw new UnsupportedQueryException("wildcard exceeds the configured length limit", fieldPath);
        }
        return new WildcardNode(field.name(), pattern);
    }

    private Object parseValue(JsonNode value, FieldType type, String path) {
        if (value == null || value.isNull()) throw new UnsupportedQueryException("null values are not supported", path);
        return switch (type) {
            case STRING -> {
                if (!value.isString()) throw typeError(type, path);
                yield value.stringValue();
            }
            case NUMBER -> {
                if (!value.isNumber()) throw typeError(type, path);
                yield value.numberValue();
            }
            case BOOLEAN -> {
                if (!value.isBoolean()) throw typeError(type, path);
                yield value.booleanValue();
            }
            case TIMESTAMP -> {
                if (!value.isString()) throw typeError(type, path);
                try {
                    yield OffsetDateTime.parse(value.stringValue()).toInstant().toString();
                } catch (DateTimeParseException ex) {
                    throw new UnsupportedQueryException("timestamp must be ISO 8601 with an explicit timezone", path);
                }
            }
        };
    }

    private FieldDefinition requireField(TableDefinition table, String name, String path) {
        return tableRegistry.requireField(table, name, path);
    }

    private static JsonNode soleFieldBody(JsonNode body, String path) {
        if (!body.isObject() || body.size() != 1) {
            throw new UnsupportedQueryException("operator requires exactly one field", path);
        }
        return body;
    }

    private static UnsupportedQueryException typeError(FieldType type, String path) {
        return new UnsupportedQueryException("Value does not match field type " + type, path);
    }

    private static boolean hasProperty(JsonNode object, String name) { return object.get(name) != null; }

    private void checkDepth(int depth, String path) {
        if (depth > maxDepth) throw new UnsupportedQueryException("query exceeds the configured nesting depth", path);
    }

}
