package com.example.querytranslator.generator;

import com.example.querytranslator.ast.*;
import com.example.querytranslator.schema.TableDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClickHouseSqlGenerator {
    public SqlBuildResult generate(TableDefinition table, QueryNode query) {
        List<Object> parameters = new ArrayList<>();
        String predicate = render(table, query, parameters);
        return new SqlBuildResult("SELECT * FROM " + table.name() + " WHERE " + predicate, parameters);
    }

    private String render(TableDefinition table, QueryNode node, List<Object> parameters) {
        if (node instanceof ComparisonNode comparison) {
            parameters.add(comparison.value());
            return requireField(table, comparison.field()) + " = ?";
        }
        if (node instanceof InNode in) {
            parameters.addAll(in.values());
            return requireField(table, in.field()) + " IN (" + String.join(", ", java.util.Collections.nCopies(in.values().size(), "?")) + ")";
        }
        if (node instanceof RangeNode range) {
            List<String> bounds = new ArrayList<>(range.bounds().size());
            String field = requireField(table, range.field());
            for (RangeBound bound : range.bounds()) {
                parameters.add(bound.value());
                String operator = switch (bound.operator()) {
                    case GT -> ">";
                    case GTE -> ">=";
                    case LT -> "<";
                    case LTE -> "<=";
                };
                bounds.add(field + " " + operator + " ?");
            }
            return join(" AND ", bounds);
        }
        if (node instanceof ExistsNode exists) return requireField(table, exists.field()) + " IS NOT NULL";
        if (node instanceof WildcardNode wildcard) {
            parameters.add(toLikePattern(wildcard.pattern()));
            return requireField(table, wildcard.field()) + " LIKE ? ESCAPE '\\\\'";
        }
        if (node instanceof AndNode and) return parenthesized(table, " AND ", and.children(), parameters);
        if (node instanceof OrNode or) return parenthesized(table, " OR ", or.children(), parameters);
        if (node instanceof NotNode not) return "NOT (" + render(table, not.child(), parameters) + ")";
        throw new IllegalArgumentException("Unknown AST node: " + node.getClass().getName());
    }

    private String parenthesized(TableDefinition table, String delimiter, List<QueryNode> nodes, List<Object> parameters) {
        List<String> parts = new ArrayList<>(nodes.size());
        for (QueryNode node : nodes) parts.add(render(table, node, parameters));
        return "(" + join(delimiter, parts) + ")";
    }

    private static String requireField(TableDefinition table, String field) {
        var definition = table.fields().get(field);
        if (definition == null) throw new IllegalArgumentException("AST references a field outside the table allowlist");
        return definition.name();
    }

    private static String join(String delimiter, List<String> parts) {
        return String.join(delimiter, parts);
    }

    private static String toLikePattern(String pattern) {
        StringBuilder result = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char current = pattern.charAt(i);
            if (current == '*') {
                result.append('%');
            } else if (current == '?') {
                result.append('_');
            } else {
                appendLikeLiteral(result, current);
            }
        }
        return result.toString();
    }

    private static void appendLikeLiteral(StringBuilder result, char value) {
        if (value == '\\' || value == '%' || value == '_') result.append('\\');
        result.append(value);
    }
}
