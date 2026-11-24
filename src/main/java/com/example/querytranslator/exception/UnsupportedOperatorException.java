package com.example.querytranslator.exception;

public final class UnsupportedOperatorException extends TranslationException {
    public UnsupportedOperatorException(String operator, String path) {
        super(ErrorCode.UNSUPPORTED_OPERATOR, "Operator '" + operator + "' is not supported", path);
    }
}
