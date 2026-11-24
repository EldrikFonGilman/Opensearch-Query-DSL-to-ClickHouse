package com.example.querytranslator.ast;

public record ComparisonNode(String field, Object value) implements QueryNode {}
