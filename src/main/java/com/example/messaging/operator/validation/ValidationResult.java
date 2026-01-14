package com.example.messaging.operator.validation;

/**
 * Result of a validation operation. Immutable value object representing success or failure with message.
 */
public class ValidationResult {
    private final boolean valid;
    private final String message;

    private ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult invalid(String message, Object... args) {
        return new ValidationResult(false, String.format(message == null ? "" : message, args));
    }

    public boolean isValid() {
        return valid;
    }

    public String getMessage() {
        return message;
    }
}
