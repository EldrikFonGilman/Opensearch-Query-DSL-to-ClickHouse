package com.example.querytranslator.exception;

public class TranslationException extends RuntimeException {
    private final ErrorCode error;
    private final String path;
    public TranslationException(ErrorCode error, String message, String path) {
        super(message);
        this.error = error;
        this.path = path;
    }
    public ErrorCode getError() { return error; }
    public String getPath() { return path; }
}
