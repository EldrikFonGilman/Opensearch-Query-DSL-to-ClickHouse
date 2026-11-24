package com.example.querytranslator.exception;

public final class InvalidRequestException extends TranslationException {
    public InvalidRequestException(String message, String path) { super(ErrorCode.INVALID_REQUEST, message, path); }
}
