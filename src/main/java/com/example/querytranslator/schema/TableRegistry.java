package com.example.querytranslator.schema;

import com.example.querytranslator.config.QueryTranslatorProperties;
import com.example.querytranslator.exception.UnknownFieldException;
import com.example.querytranslator.exception.UnknownTableException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class TableRegistry {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private final Map<String, TableDefinition> tables;

    public TableRegistry(QueryTranslatorProperties properties) {
        Map<String, TableDefinition> loaded = new LinkedHashMap<>();
        if (properties.getTables() == null || properties.getTables().isEmpty()) {
            throw new IllegalStateException("At least one query-translator table must be configured");
        }
        properties.getTables().forEach((tableName, tableProperties) -> {
            requireSafeIdentifier(tableName, "table");
            if (tableProperties == null || tableProperties.getFields() == null || tableProperties.getFields().isEmpty()) {
                throw new IllegalStateException("Every configured table must contain fields");
            }
            Map<String, FieldDefinition> fields = new LinkedHashMap<>();
            tableProperties.getFields().forEach((fieldName, fieldType) -> {
                requireSafeIdentifier(fieldName, "field");
                if (fieldType == null) throw new IllegalStateException("Configured field type cannot be null");
                fields.put(fieldName, new FieldDefinition(fieldName, fieldType));
            });
            loaded.put(tableName, new TableDefinition(tableName, fields));
        });
        tables = Map.copyOf(loaded);
    }

    public TableDefinition requireTable(String name) {
        TableDefinition table = tables.get(name);
        if (table == null) throw new UnknownTableException(name);
        return table;
    }

    public FieldDefinition requireField(TableDefinition table, String name, String path) {
        FieldDefinition field = table.fields().get(name);
        if (field == null) throw new UnknownFieldException(name, path);
        return field;
    }

    private static void requireSafeIdentifier(String value, String kind) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException("Configured " + kind + " name is not a supported SQL identifier");
        }
    }
}
