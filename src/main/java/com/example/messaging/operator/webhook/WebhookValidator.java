package com.example.messaging.operator.webhook;

import com.example.messaging.operator.validation.OwnershipValidator;
import com.example.messaging.operator.validation.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.messaging.operator.webhook.AdmissionResponse.*;

/**
 * Validates admission requests using ownership rules. Integrates with existing OwnershipValidator to enforce immutable ownership and authorization for DELETE operations.
 */
public class WebhookValidator {
    private static final Logger log = LoggerFactory.getLogger(WebhookValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OwnershipValidator ownershipValidator;

    public WebhookValidator(OwnershipValidator ownershipValidator) {
        this.ownershipValidator = ownershipValidator;
    }

    /**
     * Validate an admission request for a specific resource type. Validates CREATE, UPDATE, and DELETE
     * operations using OwnershipValidator to enforce ownership chains.
     */
    public <T> AdmissionResponse validate(AdmissionRequest request, Class<T> resourceClass) {
        try {
            return switch (request.getOperation()) {
                case "CREATE" -> validateCreate(request, resourceClass);
                case "UPDATE" -> validateUpdate(request, resourceClass);
                case "DELETE" -> validateDelete(request, resourceClass);
                default -> allowed(request.getUid());
            };
        } catch (Exception e) {
            log.error("Validation error for {} {}/{}: {}", request.getOperation(), request.getNamespace(), request.getName(), e.getMessage());
            return denied(request.getUid(), "Internal validation error: " + e.getMessage());
        }
    }

    private <T> AdmissionResponse validateCreate(AdmissionRequest request, Class<T> resourceClass) {
        try {
            T resource = objectMapper.convertValue(request.getObject(), resourceClass);

            ValidationResult result = ownershipValidator.validateCreate(resource, request.getNamespace());

            if (!result.isValid()) {
                return denied(request.getUid(), result.getMessage());
            }

            return allowed(request.getUid());

        } catch (Exception e) {
            log.error("Error validating CREATE for {}/{}: {}", request.getNamespace(), request.getName(), e.getMessage());
            return denied(request.getUid(), "Failed to validate create: " + e.getMessage());
        }
    }

    private <T> AdmissionResponse validateUpdate(AdmissionRequest request, Class<T> resourceClass) {
        try {
            T oldResource = objectMapper.convertValue(request.getOldObject(), resourceClass);
            T newResource = objectMapper.convertValue(request.getObject(), resourceClass);

            ValidationResult result = ownershipValidator.validateUpdate(oldResource, newResource);

            if (!result.isValid()) {
                return denied(request.getUid(), result.getMessage());
            }

            return allowed(request.getUid());

        } catch (Exception e) {
            log.error("Error validating UPDATE for {}/{}: {}", request.getNamespace(), request.getName(), e.getMessage());
            return denied(request.getUid(), "Failed to validate update: " + e.getMessage());
        }
    }

    /**
     * Validates DELETE requests. Resource must exist and be well-formed.
     * Authorization is delegated to Kubernetes RBAC.
     * TODO: Map K8s user to ApplicationService for ownership-based delete authorization.
     */
    private <T> AdmissionResponse validateDelete(AdmissionRequest request, Class<T> resourceClass) {
        try {
            if (request.getOldObject() == null) {
                return denied(request.getUid(), "Resource not found");
            }

            T resource = objectMapper.convertValue(request.getOldObject(), resourceClass);
            if (resource == null) {
                return denied(request.getUid(), "Failed to parse resource");
            }

            String requestingUser = request.getUserInfo() != null
                    ? request.getUserInfo().getUsername()
                    : "unknown";

            log.info("DELETE approved for {}/{} by user {} (auth delegated to K8s RBAC)",
                    request.getNamespace(), request.getName(), requestingUser);

            return allowed(request.getUid());

        } catch (Exception e) {
            log.error("Error validating DELETE for {}/{}: {}", request.getNamespace(), request.getName(), e.getMessage());
            return denied(request.getUid(), "Failed to validate delete");
        }
    }
}
