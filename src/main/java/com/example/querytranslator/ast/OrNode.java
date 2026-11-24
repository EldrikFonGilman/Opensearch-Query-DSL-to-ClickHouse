package com.example.querytranslator.ast;

import java.util.List;

public record OrNode(List<QueryNode> children) implements QueryNode {
    public OrNode { children = List.copyOf(children); if (children.isEmpty()) throw new IllegalArgumentException("OR needs children"); }
}
