package com.example.messaging.operator.webhook;

public enum HttpStatus {
    OK(200),
    BAD_REQUEST(400),
    FORBIDDEN(403),
    METHOD_NOT_ALLOWED(405),
    INTERNAL_SERVER_ERROR(500);

    private final int code;

    HttpStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
