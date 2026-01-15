package com.example.messaging.operator.validation;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDKind;

/**
 * Interface for looking up CRD resources. Implementations can use either
 * an in-memory store (for testing) or Kubernetes API (for production).
 */
public interface ResourceLookup {
    ApplicationService getApplicationService(String namespace, String name);
    KafkaCluster getKafkaCluster(String namespace, String name);
    ServiceAccount getServiceAccount(String namespace, String name);
    Topic getTopic(String namespace, String name);
    ConsumerGroup getConsumerGroup(String namespace, String name);
    ACL getAcl(String namespace, String name);
}
