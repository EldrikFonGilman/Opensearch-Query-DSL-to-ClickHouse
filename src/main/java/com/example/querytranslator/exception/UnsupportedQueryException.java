package com.example.querytranslator.exception;

public final class UnsupportedQueryException extends TranslationException {
    public UnsupportedQueryException(String message, String path) { super(ErrorCode.UNSUPPORTED_QUERY, message, path); }
}
