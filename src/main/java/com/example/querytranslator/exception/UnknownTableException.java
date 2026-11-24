package com.example.querytranslator.exception;

public final class UnknownTableException extends TranslationException {
    public UnknownTableException(String table) { super(ErrorCode.UNKNOWN_TABLE, "Table is not allowed", "table"); }
}
