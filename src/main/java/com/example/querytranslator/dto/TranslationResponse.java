package com.example.querytranslator.dto;

import java.util.List;

public record TranslationResponse(String sql, List<Object> parameters) {
    public TranslationResponse { parameters = List.copyOf(parameters); }
}
