package com.example.querytranslator.ast;

public record WildcardNode(String field, String pattern) implements QueryNode {}
