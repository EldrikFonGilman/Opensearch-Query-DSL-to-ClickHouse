package com.example.querytranslator.ast;

import java.util.List;

public record RangeNode(String field, List<RangeBound> bounds) implements QueryNode {
    public RangeNode { bounds = List.copyOf(bounds); if (bounds.isEmpty()) throw new IllegalArgumentException("Range needs bounds"); }
}
