package com.example.messaging.operator.validation;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for ValidationResult value object. Tests immutability, factory methods, and accessor behavior.
 */
@DisplayName("ValidationResult Unit Tests")
class ValidationResultTest {

    @Test
    @DisplayName("should create valid result with null message")
    void testValidResult() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid())
                .isTrue();
        assertThat(result.getMessage())
                .isNull();
    }

    @Test
    @DisplayName("should create invalid result with error message")
    void testInvalidResult() {
        String errorMessage = "ApplicationService 'foo' does not exist";
        ValidationResult result = ValidationResult.invalid(errorMessage);

        assertThat(result.isValid())
                .isFalse();
        assertThat(result.getMessage())
                .isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("should accept null message for invalid result")
    void testInvalidResultWithNullMessage() {
        ValidationResult result = ValidationResult.invalid(null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage()).isNull();
    }

    @Test
    @DisplayName("should accept empty message for invalid result")
    void testInvalidResultWithEmptyMessage() {
        ValidationResult result = ValidationResult.invalid("");

        assertThat(result.isValid())
                .isFalse();
        assertThat(result.getMessage())
                .isEmpty();
    }

    @Test
    @DisplayName("should handle multiline error messages")
    void testMultilineErrorMessage() {
        String multilineMessage = """
                Validation failed:
                - ApplicationService 'foo' does not exist
                - VirtualCluster 'bar' is owned by different service
                """;
        ValidationResult result = ValidationResult.invalid(multilineMessage);

        assertThat(result.isValid())
                .isFalse();
        assertThat(result.getMessage())
                .isEqualTo(multilineMessage)
                .contains("ApplicationService")
                .contains("VirtualCluster");
    }

    @Test
    @DisplayName("should handle special characters in error message")
    void testSpecialCharactersInMessage() {
        String messageWithSpecialChars = "Cannot change applicationServiceRef from 'foo-service' to 'bar-service'";
        ValidationResult result = ValidationResult.invalid(messageWithSpecialChars);

        assertThat(result.isValid())
                .isFalse();
        assertThat(result.getMessage())
                .isEqualTo(messageWithSpecialChars);
    }

    @Test
    @DisplayName("valid and invalid results should have different states")
    void testValidVsInvalid() {
        ValidationResult valid = ValidationResult.valid();
        ValidationResult invalid = ValidationResult.invalid("error");

        assertThat(valid.isValid())
                .isTrue();
        assertThat(invalid.isValid())
                .isFalse();
        assertThat(valid.isValid())
                .isNotEqualTo(invalid.isValid());
    }

    @Test
    @DisplayName("should be immutable - getMessage returns same instance")
    void testImmutability() {
        ValidationResult result = ValidationResult.invalid("error message");

        String message1 = result.getMessage();
        String message2 = result.getMessage();

        assertThat(message1)
                .isSameAs(message2);
        assertThat(result.isValid())
                .isFalse();
    }

    @Test
    @DisplayName("factory methods should return new instances")
    void testFactoryMethodsReturnNewInstances() {
        ValidationResult valid1 = ValidationResult.valid();
        ValidationResult valid2 = ValidationResult.valid();
        ValidationResult invalid1 = ValidationResult.invalid("error1");
        ValidationResult invalid2 = ValidationResult.invalid("error2");

        // Each factory call creates a new instance
        assertThat(valid1)
                .isNotSameAs(valid2);
        assertThat(invalid1)
                .isNotSameAs(invalid2);
    }

    @Test
    @DisplayName("should handle very long error messages")
    void testVeryLongErrorMessage() {
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longMessage.append("Error ").append(i).append(". ");
        }
        String longErrorMessage = longMessage.toString();

        ValidationResult result = ValidationResult.invalid(longErrorMessage);

        assertThat(result.isValid())
                .isFalse();
        assertThat(result.getMessage())
                .isEqualTo(longErrorMessage)
                .hasSizeGreaterThan(5000);
    }
}
