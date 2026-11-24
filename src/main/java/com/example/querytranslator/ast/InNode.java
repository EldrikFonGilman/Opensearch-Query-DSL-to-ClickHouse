package com.example.querytranslator.ast;

import java.util.List;

public record InNode(String field, List<Object> values) implements QueryNode {
    public InNode {
        values = List.copyOf(values);
        if (values.isEmpty()) throw new IllegalArgumentException("IN needs values");
    }
}
