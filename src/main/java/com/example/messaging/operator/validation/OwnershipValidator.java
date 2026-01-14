package com.example.messaging.operator.validation;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;

/**
 * Validator for enforcing applicationService ownership rules.
 * Ensures that:
 * - Resources can only be modified by their owner applicationService
 * - Referenced resources exist and belong to the same owner
 * - Ownership chains are maintained (ApplicationService -> VirtualCluster -> ServiceAccount -> Topic/ACL)
 */
public class OwnershipValidator {
    private final CRDStore store;

    public OwnershipValidator(CRDStore store) {
        this.store = store;
    }

    /**
     * Validate resource creation
     * Ensures that all referenced resources exist and belong to the same applicationService
     */
    public ValidationResult validateCreate(Object resource, String namespace) {
        return switch (resource) {
            case VirtualCluster vc -> validateApplicationServiceExists(
                    vc.getSpec().getApplicationServiceRef(), namespace);
            case ServiceAccount sa -> {
                ValidationResult appServiceResult =
                        validateApplicationServiceExists(sa.getSpec().getApplicationServiceRef(), namespace);
                if (!appServiceResult.isValid()) yield appServiceResult;
                yield validateVirtualClusterExists(
                        sa.getSpec().getClusterRef(), namespace, sa.getSpec().getApplicationServiceRef());
            }
            case Topic topic -> validateServiceAccountExists(
                    topic.getSpec().getServiceRef(), namespace, topic.getSpec().getApplicationServiceRef());
            case ACL acl -> validateServiceAccountExists(
                    acl.getSpec().getServiceRef(), namespace, acl.getSpec().getApplicationServiceRef());
            default -> ValidationResult.valid();
        };
    }

    /**
     * Validate resource update
     * Ensures that applicationServiceRef cannot be changed (immutable ownership)
     */
    public ValidationResult validateUpdate(Object existingResource, Object newResource) {
        String existingOwner = getApplicationServiceRef(existingResource);
        String newOwner = getApplicationServiceRef(newResource);

        if (existingOwner == null || newOwner == null) {
            return ValidationResult.invalid("Resource must have applicationServiceRef");
        }

        if (!existingOwner.equals(newOwner)) {
            return ValidationResult.invalid(String.format(
                    "Cannot change applicationServiceRef from '%s' to '%s'. "
                            + "Only the original owner can modify this resource.",
                    existingOwner, newOwner));
        }

        return ValidationResult.valid();
    }

    /**
     * Validate resource deletion
     * Ensures that only the owner can delete the resource
     */
    public ValidationResult validateDelete(Object resource, String requestingOwner) {
        String resourceOwner = getApplicationServiceRef(resource);

        if (!resourceOwner.equals(requestingOwner)) {
            return ValidationResult.invalid(String.format(
                    "ApplicationService '%s' cannot delete resource owned by '%s'", requestingOwner, resourceOwner));
        }

        return ValidationResult.valid();
    }

    private ValidationResult validateApplicationServiceExists(String appServiceName, String namespace) {
        ApplicationService appService = store.get("ApplicationService", namespace, appServiceName);
        if (appService == null) {
            return ValidationResult.invalid("Referenced ApplicationService '" + appServiceName + "' does not exist");
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateVirtualClusterExists(
            String clusterRef, String namespace, String expectedAppService) {
        VirtualCluster vc = store.get("VirtualCluster", namespace, clusterRef);
        if (vc == null) {
            return ValidationResult.invalid("Referenced VirtualCluster '" + clusterRef + "' does not exist");
        }
        if (!vc.getSpec().getApplicationServiceRef().equals(expectedAppService)) {
            return ValidationResult.invalid(String.format(
                    "VirtualCluster '%s' is owned by '%s', not '%s'",
                    clusterRef, vc.getSpec().getApplicationServiceRef(), expectedAppService));
        }
        return ValidationResult.valid();
    }

    private ValidationResult validateServiceAccountExists(String saRef, String namespace, String expectedAppService) {
        ServiceAccount sa = store.get("ServiceAccount", namespace, saRef);
        if (sa == null) {
            return ValidationResult.invalid("Referenced ServiceAccount '" + saRef + "' does not exist");
        }
        if (!sa.getSpec().getApplicationServiceRef().equals(expectedAppService)) {
            return ValidationResult.invalid(String.format(
                    "ServiceAccount '%s' is owned by '%s', not '%s'",
                    saRef, sa.getSpec().getApplicationServiceRef(), expectedAppService));
        }
        return ValidationResult.valid();
    }

    private String getApplicationServiceRef(Object resource) {
        return switch (resource) {
            case VirtualCluster r -> r.getSpec().getApplicationServiceRef();
            case ServiceAccount sa -> sa.getSpec().getApplicationServiceRef();
            case Topic topic -> topic.getSpec().getApplicationServiceRef();
            case ConsumerGroup cg -> cg.getSpec().getApplicationServiceRef();
            case ACL acl -> acl.getSpec().getApplicationServiceRef();
            default -> null;
        };
    }
}
