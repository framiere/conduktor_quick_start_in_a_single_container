package com.example.messaging.operator.it.base;

import com.example.messaging.operator.crd.*;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.*;

/**
 * Fluent builder for creating test data for all CRD types.
 * Provides a clean, readable API for test data setup.
 */
public class TestDataBuilder {

    // Static factory methods
    public static ApplicationServiceBuilder applicationService() {
        return new ApplicationServiceBuilder();
    }

    public static VirtualClusterBuilder virtualCluster() {
        return new VirtualClusterBuilder();
    }

    public static ServiceAccountBuilder serviceAccount() {
        return new ServiceAccountBuilder();
    }

    public static TopicBuilder topic() {
        return new TopicBuilder();
    }

    public static ACLBuilder acl() {
        return new ACLBuilder();
    }

    public static ConsumerGroupBuilder consumerGroup() {
        return new ConsumerGroupBuilder();
    }

    // ApplicationService Builder
    public static class ApplicationServiceBuilder {
        private String namespace = "default";
        private String name = "test-app";
        private String appName;
        private List<OwnerReference> owners = new ArrayList<>();

        public ApplicationServiceBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ApplicationServiceBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ApplicationServiceBuilder appName(String appName) {
            this.appName = appName;
            return this;
        }

        public ApplicationServiceBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ApplicationService build() {
            ApplicationService as = new ApplicationService();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            as.setMetadata(metadata);

            ApplicationServiceSpec spec = new ApplicationServiceSpec();
            spec.setName(appName != null ? appName : name);
            as.setSpec(spec);

            return as;
        }

        public ApplicationService createIn(KubernetesClient client) {
            ApplicationService as = build();
            return client.resource(as).create();
        }
    }

    // VirtualCluster Builder
    public static class VirtualClusterBuilder {
        private String namespace = "default";
        private String name = "test-cluster";
        private String clusterId;
        private String applicationServiceRef;
        private List<OwnerReference> owners = new ArrayList<>();

        public VirtualClusterBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public VirtualClusterBuilder name(String name) {
            this.name = name;
            return this;
        }

        public VirtualClusterBuilder clusterId(String clusterId) {
            this.clusterId = clusterId;
            return this;
        }

        public VirtualClusterBuilder applicationServiceRef(String applicationServiceRef) {
            this.applicationServiceRef = applicationServiceRef;
            return this;
        }

        public VirtualClusterBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public VirtualCluster build() {
            VirtualCluster vc = new VirtualCluster();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            vc.setMetadata(metadata);

            VirtualClusterSpec spec = new VirtualClusterSpec();
            spec.setClusterId(clusterId != null ? clusterId : "test-cluster-id");
            spec.setApplicationServiceRef(applicationServiceRef != null ? applicationServiceRef : "test-app");
            vc.setSpec(spec);

            return vc;
        }

        public VirtualCluster createIn(KubernetesClient client) {
            VirtualCluster vc = build();
            return client.resource(vc).create();
        }
    }

    // ServiceAccount Builder
    public static class ServiceAccountBuilder {
        private String namespace = "default";
        private String name = "test-sa";
        private String saName;
        private List<String> dn = new ArrayList<>();
        private String clusterRef;
        private String applicationServiceRef;
        private List<OwnerReference> owners = new ArrayList<>();

        public ServiceAccountBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ServiceAccountBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ServiceAccountBuilder saName(String saName) {
            this.saName = saName;
            return this;
        }

        public ServiceAccountBuilder dn(String... dn) {
            this.dn = new ArrayList<>(Arrays.asList(dn));
            return this;
        }

        public ServiceAccountBuilder dn(List<String> dn) {
            this.dn = new ArrayList<>(dn);
            return this;
        }

        public ServiceAccountBuilder clusterRef(String clusterRef) {
            this.clusterRef = clusterRef;
            return this;
        }

        public ServiceAccountBuilder applicationServiceRef(String applicationServiceRef) {
            this.applicationServiceRef = applicationServiceRef;
            return this;
        }

        public ServiceAccountBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ServiceAccountBuilder ownedBy(VirtualCluster owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ServiceAccount build() {
            ServiceAccount sa = new ServiceAccount();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            sa.setMetadata(metadata);

            ServiceAccountSpec spec = new ServiceAccountSpec();
            spec.setName(saName != null ? saName : name);
            spec.setDn(dn.isEmpty() ? List.of("CN=test") : dn);
            spec.setClusterRef(clusterRef != null ? clusterRef : "test-cluster");
            spec.setApplicationServiceRef(applicationServiceRef != null ? applicationServiceRef : "test-app");
            sa.setSpec(spec);

            return sa;
        }

        public ServiceAccount createIn(KubernetesClient client) {
            ServiceAccount sa = build();
            return client.resource(sa).create();
        }
    }

    // Topic Builder
    public static class TopicBuilder {
        private String namespace = "default";
        private String name = "test-topic";
        private String serviceRef;
        private String topicName;
        private int partitions = 6;
        private int replicationFactor = 3;
        private Map<String, String> config = new HashMap<>();
        private String applicationServiceRef;
        private List<OwnerReference> owners = new ArrayList<>();

        public TopicBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public TopicBuilder name(String name) {
            this.name = name;
            return this;
        }

        public TopicBuilder serviceRef(String serviceRef) {
            this.serviceRef = serviceRef;
            return this;
        }

        public TopicBuilder topicName(String topicName) {
            this.topicName = topicName;
            return this;
        }

        public TopicBuilder partitions(int partitions) {
            this.partitions = partitions;
            return this;
        }

        public TopicBuilder replicationFactor(int replicationFactor) {
            this.replicationFactor = replicationFactor;
            return this;
        }

        public TopicBuilder config(Map<String, String> config) {
            this.config = new HashMap<>(config);
            return this;
        }

        public TopicBuilder config(String key, String value) {
            this.config.put(key, value);
            return this;
        }

        public TopicBuilder applicationServiceRef(String applicationServiceRef) {
            this.applicationServiceRef = applicationServiceRef;
            return this;
        }

        public TopicBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public TopicBuilder ownedBy(ServiceAccount owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public Topic build() {
            Topic topic = new Topic();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            topic.setMetadata(metadata);

            TopicCRSpec spec = new TopicCRSpec();
            spec.setServiceRef(serviceRef != null ? serviceRef : "test-sa");
            spec.setName(topicName != null ? topicName : name);
            spec.setPartitions(partitions);
            spec.setReplicationFactor(replicationFactor);
            spec.setConfig(config);
            spec.setApplicationServiceRef(applicationServiceRef != null ? applicationServiceRef : "test-app");
            topic.setSpec(spec);

            return topic;
        }

        public Topic createIn(KubernetesClient client) {
            Topic topic = build();
            return client.resource(topic).create();
        }
    }

    // ACL Builder
    public static class ACLBuilder {
        private String namespace = "default";
        private String name = "test-acl";
        private String serviceRef;
        private String topicRef;
        private String consumerGroupRef;
        private List<AclCRSpec.Operation> operations = new ArrayList<>();
        private String host = "*";
        private AclCRSpec.AclPermissionTypeForAccessControlEntry permission = AclCRSpec.AclPermissionTypeForAccessControlEntry.ALLOW;
        private String applicationServiceRef;
        private List<OwnerReference> owners = new ArrayList<>();

        public ACLBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ACLBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ACLBuilder serviceRef(String serviceRef) {
            this.serviceRef = serviceRef;
            return this;
        }

        public ACLBuilder topicRef(String topicRef) {
            this.topicRef = topicRef;
            return this;
        }

        public ACLBuilder consumerGroupRef(String consumerGroupRef) {
            this.consumerGroupRef = consumerGroupRef;
            return this;
        }

        public ACLBuilder operations(AclCRSpec.Operation... operations) {
            this.operations = new ArrayList<>(Arrays.asList(operations));
            return this;
        }

        public ACLBuilder operations(List<AclCRSpec.Operation> operations) {
            this.operations = new ArrayList<>(operations);
            return this;
        }

        public ACLBuilder host(String host) {
            this.host = host;
            return this;
        }

        public ACLBuilder permission(AclCRSpec.AclPermissionTypeForAccessControlEntry permission) {
            this.permission = permission;
            return this;
        }

        public ACLBuilder applicationServiceRef(String applicationServiceRef) {
            this.applicationServiceRef = applicationServiceRef;
            return this;
        }

        public ACLBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ACLBuilder ownedBy(ServiceAccount owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ACL build() {
            ACL acl = new ACL();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            acl.setMetadata(metadata);

            AclCRSpec spec = new AclCRSpec();
            spec.setServiceRef(serviceRef != null ? serviceRef : "test-sa");
            spec.setTopicRef(topicRef);
            spec.setConsumerGroupRef(consumerGroupRef);
            spec.setOperations(operations.isEmpty() ? List.of(AclCRSpec.Operation.READ) : operations);
            spec.setHost(host);
            spec.setPermission(permission);
            spec.setApplicationServiceRef(applicationServiceRef != null ? applicationServiceRef : "test-app");
            acl.setSpec(spec);

            return acl;
        }

        public ACL createIn(KubernetesClient client) {
            ACL acl = build();
            return client.resource(acl).create();
        }
    }

    // ConsumerGroup Builder
    public static class ConsumerGroupBuilder {
        private String namespace = "default";
        private String name = "test-cg";
        private String serviceRef;
        private String cgName;
        private ConsumerGroupSpec.ResourcePatternType patternType = ConsumerGroupSpec.ResourcePatternType.LITERAL;
        private String applicationServiceRef;
        private List<OwnerReference> owners = new ArrayList<>();

        public ConsumerGroupBuilder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public ConsumerGroupBuilder name(String name) {
            this.name = name;
            return this;
        }

        public ConsumerGroupBuilder serviceRef(String serviceRef) {
            this.serviceRef = serviceRef;
            return this;
        }

        public ConsumerGroupBuilder cgName(String cgName) {
            this.cgName = cgName;
            return this;
        }

        public ConsumerGroupBuilder patternType(ConsumerGroupSpec.ResourcePatternType patternType) {
            this.patternType = patternType;
            return this;
        }

        public ConsumerGroupBuilder applicationServiceRef(String applicationServiceRef) {
            this.applicationServiceRef = applicationServiceRef;
            return this;
        }

        public ConsumerGroupBuilder ownedBy(ApplicationService owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ConsumerGroupBuilder ownedBy(ServiceAccount owner) {
            this.owners.add(createOwnerReference(owner));
            return this;
        }

        public ConsumerGroup build() {
            ConsumerGroup cg = new ConsumerGroup();
            ObjectMeta metadata = new ObjectMeta();
            metadata.setNamespace(namespace);
            metadata.setName(name);
            if (!owners.isEmpty()) {
                metadata.setOwnerReferences(owners);
            }
            cg.setMetadata(metadata);

            ConsumerGroupSpec spec = new ConsumerGroupSpec();
            spec.setServiceRef(serviceRef != null ? serviceRef : "test-sa");
            spec.setName(cgName != null ? cgName : name);
            spec.setPatternType(patternType);
            spec.setApplicationServiceRef(applicationServiceRef != null ? applicationServiceRef : "test-app");
            cg.setSpec(spec);

            return cg;
        }

        public ConsumerGroup createIn(KubernetesClient client) {
            ConsumerGroup cg = build();
            return client.resource(cg).create();
        }
    }

    // Helper method to create OwnerReference
    private static OwnerReference createOwnerReference(ApplicationService owner) {
        OwnerReference ref = new OwnerReference();
        ref.setApiVersion(owner.getApiVersion());
        ref.setKind(owner.getKind());
        ref.setName(owner.getMetadata().getName());
        ref.setUid(owner.getMetadata().getUid());
        ref.setController(true);
        ref.setBlockOwnerDeletion(true);
        return ref;
    }

    private static OwnerReference createOwnerReference(VirtualCluster owner) {
        OwnerReference ref = new OwnerReference();
        ref.setApiVersion(owner.getApiVersion());
        ref.setKind(owner.getKind());
        ref.setName(owner.getMetadata().getName());
        ref.setUid(owner.getMetadata().getUid());
        ref.setController(true);
        ref.setBlockOwnerDeletion(true);
        return ref;
    }

    private static OwnerReference createOwnerReference(ServiceAccount owner) {
        OwnerReference ref = new OwnerReference();
        ref.setApiVersion(owner.getApiVersion());
        ref.setKind(owner.getKind());
        ref.setName(owner.getMetadata().getName());
        ref.setUid(owner.getMetadata().getUid());
        ref.setController(true);
        ref.setBlockOwnerDeletion(true);
        return ref;
    }
}
