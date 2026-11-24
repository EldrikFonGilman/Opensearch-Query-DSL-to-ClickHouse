package com.example.querytranslator.dto;

import tools.jackson.databind.JsonNode;

public record TranslationRequest(String table, JsonNode query) {}
