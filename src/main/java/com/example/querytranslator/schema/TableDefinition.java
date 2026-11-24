package com.example.querytranslator.schema;

import java.util.Map;

public record TableDefinition(String name, Map<String, FieldDefinition> fields) {
    public TableDefinition { fields = Map.copyOf(fields); }
}
