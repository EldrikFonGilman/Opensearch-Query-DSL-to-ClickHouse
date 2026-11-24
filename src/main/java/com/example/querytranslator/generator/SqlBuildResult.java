package com.example.querytranslator.generator;

import java.util.List;

public record SqlBuildResult(String sql, List<Object> parameters) {
    public SqlBuildResult { parameters = List.copyOf(parameters); }
}
