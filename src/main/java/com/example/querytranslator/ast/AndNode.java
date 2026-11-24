package com.example.querytranslator.ast;

import java.util.List;

public record AndNode(List<QueryNode> children) implements QueryNode {
    public AndNode { children = List.copyOf(children); if (children.isEmpty()) throw new IllegalArgumentException("AND needs children"); }
}
