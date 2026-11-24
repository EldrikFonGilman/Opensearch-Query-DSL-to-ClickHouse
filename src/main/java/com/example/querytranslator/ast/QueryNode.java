package com.example.querytranslator.ast;

public sealed interface QueryNode permits AndNode, OrNode, NotNode, ComparisonNode, InNode, RangeNode, ExistsNode, WildcardNode {}
