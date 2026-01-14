package com.example.messaging.operator.validation;

/**
 * Result of a validation operation.
 * Immutable value object representing success or failure with message.
 */
public class ValidationResult {
    private final boolean valid;
    private final String message;

    private ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    /**
     * Create a successful validation result
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, null);
    }

    /**
     * Create a failed validation result with error message
     */
    public static ValidationResult invalid(String message) {
        return new ValidationResult(false, message);
    }

    /**
     * Check if validation passed
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Get validation error message (null if valid)
     */
    public String getMessage() {
        return message;
    }
}
