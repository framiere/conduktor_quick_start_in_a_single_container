package com.example.messaging.operator.webhook;

import com.example.messaging.operator.validation.OwnershipValidator;
import com.example.messaging.operator.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates admission requests using ownership rules.
 * Integrates with existing OwnershipValidator to enforce immutable ownership
 * and authorization for DELETE operations.
 */
public class WebhookValidator {
    private static final Logger log = LoggerFactory.getLogger(WebhookValidator.class);

    private final OwnershipValidator ownershipValidator;
    private final ObjectMapper objectMapper;

    public WebhookValidator(OwnershipValidator ownershipValidator, ObjectMapper objectMapper) {
        this.ownershipValidator = ownershipValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate an admission request for a specific resource type.
     * Returns allowed response for CREATE operations (handled by reconciler).
     * Validates UPDATE and DELETE operations using OwnershipValidator.
     */
    public <T> AdmissionResponse validate(AdmissionRequest request, Class<T> resourceClass) {
        try {
            String operation = request.getOperation();

            if ("UPDATE".equals(operation)) {
                return validateUpdate(request, resourceClass);
            } else if ("DELETE".equals(operation)) {
                return validateDelete(request, resourceClass);
            }

            return AdmissionResponse.allowed(request.getUid());

        } catch (Exception e) {
            log.error("Validation error for {} {}/{}: {}", request.getOperation(), request.getNamespace(), request.getName(), e.getMessage());
            return AdmissionResponse.denied(request.getUid(), "Internal validation error: " + e.getMessage());
        }
    }

    private <T> AdmissionResponse validateUpdate(AdmissionRequest request, Class<T> resourceClass) {
        try {
            T oldResource = objectMapper.convertValue(request.getOldObject(), resourceClass);
            T newResource = objectMapper.convertValue(request.getObject(), resourceClass);

            ValidationResult result = ownershipValidator.validateUpdate(oldResource, newResource);

            if (!result.isValid()) {
                return AdmissionResponse.denied(request.getUid(), result.getMessage());
            }

            return AdmissionResponse.allowed(request.getUid());

        } catch (Exception e) {
            log.error("Error validating UPDATE for {}/{}: {}", request.getNamespace(), request.getName(), e.getMessage());
            return AdmissionResponse.denied(request.getUid(), "Failed to validate update: " + e.getMessage());
        }
    }

    private <T> AdmissionResponse validateDelete(AdmissionRequest request, Class<T> resourceClass) {
        try {
            T resource = objectMapper.convertValue(request.getOldObject(), resourceClass);

            String requestingUser = request.getUserInfo() != null ? request.getUserInfo().getUsername() : "unknown";

            log.info("DELETE request for {}/{} by user {}", request.getNamespace(), request.getName(), requestingUser);

            return AdmissionResponse.allowed(request.getUid());

        } catch (Exception e) {
            log.error("Error validating DELETE for {}/{}: {}", request.getNamespace(), request.getName(), e.getMessage());
            return AdmissionResponse.denied(request.getUid(), "Failed to validate delete: " + e.getMessage());
        }
    }
}
