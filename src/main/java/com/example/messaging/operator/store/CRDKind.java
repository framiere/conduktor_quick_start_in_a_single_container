package com.example.messaging.operator.store;

import com.example.messaging.operator.crd.*;
import lombok.Getter;

/**
 * Enum representing all Custom Resource Definition (CRD) kinds supported by the operator. Provides type-safe access to CRD kind identifiers and mapping between resource
 * classes and kind names.
 */
@Getter
public enum CRDKind {
    APPLICATION_SERVICE("ApplicationService", ApplicationService.class), VIRTUAL_CLUSTER("VirtualCluster", VirtualCluster.class), SERVICE_ACCOUNT("ServiceAccount",
            ServiceAccount.class), TOPIC("Topic", Topic.class), ACL("ACL", ACL.class), CONSUMER_GROUP("ConsumerGroup", ConsumerGroup.class);

    private final String value;
    private final Class<?> resourceClass;

    CRDKind(String value, Class<?> resourceClass) {
        this.value = value;
        this.resourceClass = resourceClass;
    }

    /**
     * Get CRDKind from resource class
     */
    public static CRDKind fromClass(Class<?> clazz) {
        for (CRDKind kind : values()) {
            if (kind.resourceClass.equals(clazz)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown resource class: " + clazz.getName());
    }

    /**
     * Get CRDKind from string value
     */
    public static CRDKind fromValue(String value) {
        for (CRDKind kind : values()) {
            if (kind.value.equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown CRD kind: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
