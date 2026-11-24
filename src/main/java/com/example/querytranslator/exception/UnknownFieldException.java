package com.example.querytranslator.exception;

public final class UnknownFieldException extends TranslationException {
    public UnknownFieldException(String field, String path) { super(ErrorCode.UNKNOWN_FIELD, "Field is not allowed", path); }
}
