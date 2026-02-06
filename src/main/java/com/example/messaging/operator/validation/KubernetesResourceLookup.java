package com.example.messaging.operator.validation;

import com.example.messaging.operator.crd.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ResourceLookup implementation backed by Kubernetes API.
 * Used for production deployment where resources are stored in etcd.
 */
@RequiredArgsConstructor
public class KubernetesResourceLookup implements ResourceLookup {
    private static final Logger log = LoggerFactory.getLogger(KubernetesResourceLookup.class);
    private final KubernetesClient client;

    @Override
    public ApplicationService getApplicationService(String namespace, String name) {
        try {
            return client.resources(ApplicationService.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get ApplicationService {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public KafkaCluster getKafkaCluster(String namespace, String name) {
        try {
            return client.resources(KafkaCluster.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get KafkaCluster {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public ServiceAccount getServiceAccount(String namespace, String name) {
        try {
            return client.resources(ServiceAccount.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get ServiceAccount {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public Topic getTopic(String namespace, String name) {
        try {
            return client.resources(Topic.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get Topic {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public ConsumerGroup getConsumerGroup(String namespace, String name) {
        try {
            return client.resources(ConsumerGroup.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get ConsumerGroup {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public ACL getAcl(String namespace, String name) {
        try {
            return client.resources(ACL.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get ACL {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }

    @Override
    public Scope getScope(String namespace, String name) {
        try {
            return client.resources(Scope.class)
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (KubernetesClientException e) {
            log.debug("Failed to get Scope {}/{}: {}", namespace, name, e.getMessage());
            return null;
        }
    }
}
