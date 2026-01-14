package io.conduktor.quickstart;

import org.junit.jupiter.api.Test;
import org.openapitools.client.model.AclPermissionTypeForAccessControlEntry;
import org.openapitools.client.model.AclResourceType;
import org.openapitools.client.model.ResourcePatternType;

import static org.assertj.core.api.Assertions.*;
import static org.openapitools.client.model.ResourcePatternType.*;

import java.util.List;
import java.util.Map;

class CrdParsingTest {

    private final SetupGateway setup = new SetupGateway();

    @Test
    void testParseValidCrdWithAllFields() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: orders-service
              namespace: orders
            spec:
              serviceName: orders-service
              virtualClusterId: prod-cluster
              topics:
                - name: orders.events
                  partitions: 12
                  replicationFactor: 3
                  config:
                    retention.ms: "604800000"
                    cleanup.policy: "delete"
                - name: orders.deadletter
                  partitions: 3
              acls:
                - type: TOPIC
                  name: orders.events
                  operations: [READ, WRITE]
                - type: TOPIC
                  name: orders.deadletter
                  operations: [READ, WRITE]
                - type: TOPIC
                  name: inventory.updates
                  operations: [READ]
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.apiVersion).isEqualTo("messaging.example.com/v1");
        assertThat(result.kind).isEqualTo("MessagingDeclaration");
        assertThat(result.metadata.name).isEqualTo("orders-service");
        assertThat(result.metadata.namespace).isEqualTo("orders");

        assertThat(result.spec.serviceName).isEqualTo("orders-service");
        assertThat(result.spec.virtualClusterId).isEqualTo("prod-cluster");

        assertThat(result.spec.topics).hasSize(2);
        assertThat(result.spec.topics.get(0).name).isEqualTo("orders.events");
        assertThat(result.spec.topics.get(0).partitions).isEqualTo(12);
        assertThat(result.spec.topics.get(0).replicationFactor).isEqualTo(3);
        assertThat(result.spec.topics.get(0).config).containsEntry("retention.ms", "604800000");
        assertThat(result.spec.topics.get(0).config).containsEntry("cleanup.policy", "delete");

        assertThat(result.spec.topics.get(1).name).isEqualTo("orders.deadletter");
        assertThat(result.spec.topics.get(1).partitions).isEqualTo(3);
        assertThat(result.spec.topics.get(1).replicationFactor).isEqualTo(3);
        assertThat(result.spec.topics.get(1).config).isEmpty();

        assertThat(result.spec.acls).hasSize(3);
        assertThat(result.spec.acls.get(0).name).isEqualTo("orders.events");
        assertThat(result.spec.acls.get(0).operations).containsExactly("READ", "WRITE");
        assertThat(result.spec.acls.get(1).name).isEqualTo("orders.deadletter");
        assertThat(result.spec.acls.get(1).operations).containsExactly("READ", "WRITE");
        assertThat(result.spec.acls.get(2).name).isEqualTo("inventory.updates");
        assertThat(result.spec.acls.get(2).operations).containsExactly("READ");
    }

    @Test
    void testParseCrdWithMinimalFields() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: minimal-service
            spec:
              serviceName: minimal-service
              virtualClusterId: test-cluster
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.serviceName).isEqualTo("minimal-service");
        assertThat(result.spec.virtualClusterId).isEqualTo("test-cluster");
        assertThat(result.spec.topics).isEmpty();
        assertThat(result.spec.acls).isEmpty();
    }

    @Test
    void testParseCrdWithTopicsButNoAcls() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: topic-only-service
            spec:
              serviceName: topic-only-service
              virtualClusterId: test-cluster
              topics:
                - name: test.topic
                  partitions: 6
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.serviceName).isEqualTo("topic-only-service");
        assertThat(result.spec.topics).hasSize(1);
        assertThat(result.spec.topics.get(0).name).isEqualTo("test.topic");
        assertThat(result.spec.topics.get(0).partitions).isEqualTo(6);
        assertThat(result.spec.acls).isEmpty();
    }

    @Test
    void testParseCrdWithAclsButNoTopics() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: acl-only-service
            spec:
              serviceName: acl-only-service
              virtualClusterId: test-cluster
              acls:
                - type: TOPIC
                  name: external.topic
                  operations: [READ]
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.serviceName).isEqualTo("acl-only-service");
        assertThat(result.spec.topics).isEmpty();
        assertThat(result.spec.acls).hasSize(1);
        assertThat(result.spec.acls.get(0).name).isEqualTo("external.topic");
        assertThat(result.spec.acls.get(0).operations).containsExactly("READ");
    }

    @Test
    void testParseCrdWithEmptyTopicsList() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: empty-topics-service
            spec:
              serviceName: empty-topics-service
              virtualClusterId: test-cluster
              topics: []
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.topics).isEmpty();
    }

    @Test
    void testParseCrdMissingSpec() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("spec must not be null");
    }

    @Test
    void testParseCrdMissingServiceName() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            spec:
              virtualClusterId: test-cluster
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName must not be empty");
    }

    @Test
    void testParseCrdEmptyServiceName() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            spec:
              serviceName: ""
              virtualClusterId: test-cluster
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("serviceName must not be empty");
    }

    @Test
    void testParseCrdMissingVirtualClusterId() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            spec:
              serviceName: invalid-service
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("virtualClusterId must not be empty");
    }

    @Test
    void testParseCrdEmptyVirtualClusterId() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            spec:
              serviceName: invalid-service
              virtualClusterId: ""
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("virtualClusterId must not be empty");
    }

    @Test
    void testParseCrdInvalidYaml() {
        String crd = """
            this is not: [valid yaml at all {
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(Exception.class);
    }

    @Test
    void testParseCrdWithComplexTopicConfig() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: config-service
            spec:
              serviceName: config-service
              virtualClusterId: test-cluster
              topics:
                - name: complex.topic
                  partitions: 24
                  replicationFactor: 5
                  config:
                    retention.ms: "86400000"
                    retention.bytes: "1073741824"
                    compression.type: "lz4"
                    cleanup.policy: "compact"
                    min.insync.replicas: "2"
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.topics).hasSize(1);
        SetupGateway.TopicDef topic = result.spec.topics.get(0);
        assertThat(topic.name).isEqualTo("complex.topic");
        assertThat(topic.partitions).isEqualTo(24);
        assertThat(topic.replicationFactor).isEqualTo(5);
        assertThat(topic.config).hasSize(5);
        assertThat(topic.config).containsEntry("retention.ms", "86400000");
        assertThat(topic.config).containsEntry("retention.bytes", "1073741824");
        assertThat(topic.config).containsEntry("compression.type", "lz4");
        assertThat(topic.config).containsEntry("cleanup.policy", "compact");
        assertThat(topic.config).containsEntry("min.insync.replicas", "2");
    }

    @Test
    void testParseCrdWithMultipleOperations() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: multi-op-service
            spec:
              serviceName: multi-op-service
              virtualClusterId: test-cluster
              acls:
                - type: TOPIC
                  name: full-access.topic
                  operations: [READ, WRITE, CREATE, DELETE, ALTER]
                - type: TOPIC
                  name: read-only.topic
                  operations: [READ]
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.acls).hasSize(2);
        assertThat(result.spec.acls.get(0).operations).containsExactly("READ", "WRITE", "CREATE", "DELETE", "ALTER");
        assertThat(result.spec.acls.get(1).operations).containsExactly("READ");
    }

    @Test
    void testParseCrdWithNoOperations() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: no-ops-service
            spec:
              serviceName: no-ops-service
              virtualClusterId: test-cluster
              acls:
                - type: TOPIC
                  name: all-access.topic
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.acls).hasSize(1);
        assertThat(result.spec.acls.get(0).name).isEqualTo("all-access.topic");
        assertThat(result.spec.acls.get(0).operations).containsExactly("READ", "WRITE", "DESCRIBE");
    }

    @Test
    void testParseCrdWithEmptyOperations() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: empty-ops-service
            spec:
              serviceName: empty-ops-service
              virtualClusterId: test-cluster
              acls:
                - type: TOPIC
                  name: all-access.topic
                  operations: []
            """;

        assertThatThrownBy(() -> setup.parseYaml(crd))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("operations list must have between 1 and 10 items");
    }

    @Test
    void testParseCrdWithPatternType() {
        String crd = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: pattern-service
            spec:
              serviceName: pattern-service
              virtualClusterId: test-cluster
              acls:
                - type: TOPIC
                  name: click
                  patternType: PREFIXED
                  operations: [READ]
                - type: TOPIC
                  name: exact.topic
                  patternType: LITERAL
                  operations: [WRITE]
                - type: TOPIC
                  name: default.topic
                  operations: [READ]
            """;

        SetupGateway.MessagingDeclaration result = setup.parseYaml(crd);

        assertThat(result).isNotNull();
        assertThat(result.spec.acls).hasSize(3);
        assertThat(result.spec.acls.get(0).name).isEqualTo("click");
        assertThat(result.spec.acls.get(0).patternType).isEqualTo(PREFIXED);
        assertThat(result.spec.acls.get(0).operations).containsExactly("READ");
        assertThat(result.spec.acls.get(1).name).isEqualTo("exact.topic");
        assertThat(result.spec.acls.get(1).patternType).isEqualTo(LITERAL);
        assertThat(result.spec.acls.get(1).operations).containsExactly("WRITE");
        assertThat(result.spec.acls.get(2).name).isEqualTo("default.topic");
        assertThat(result.spec.acls.get(2).patternType).isEqualTo(LITERAL);
        assertThat(result.spec.acls.get(2).operations).containsExactly("READ");
    }

    @Test
    void testParseAllCrdsMultiDocument() {
        String multiDocYaml = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: service-one
            spec:
              serviceName: service-one
              virtualClusterId: cluster-one
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: service-two
            spec:
              serviceName: service-two
              virtualClusterId: cluster-two
              topics:
                - name: test.topic
                  partitions: 3
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: service-three
            spec:
              serviceName: service-three
              virtualClusterId: cluster-three
              acls:
                - type: TOPIC
                  name: secure.topic
                  operations: [READ, WRITE]
            """;

        List<SetupGateway.MessagingDeclaration> crds = setup.parseAllCrds(multiDocYaml);

        assertThat(crds).hasSize(3);
        assertThat(crds.get(0).spec.serviceName).isEqualTo("service-one");
        assertThat(crds.get(1).spec.serviceName).isEqualTo("service-two");
        assertThat(crds.get(2).spec.serviceName).isEqualTo("service-three");
        assertThat(crds.get(1).spec.topics).hasSize(1);
        assertThat(crds.get(2).spec.acls).hasSize(1);
    }

    @Test
    void testParseAllCrdsEmptyYaml() {
        assertThatThrownBy(() -> setup.parseAllCrds(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No valid CRD documents found in YAML");
    }

    @Test
    void testParseAllCrdsInvalidDocumentInMultiDoc() {
        String multiDocYaml = """
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: valid-service
            spec:
              serviceName: valid-service
              virtualClusterId: test-cluster
            ---
            apiVersion: messaging.example.com/v1
            kind: MessagingDeclaration
            metadata:
              name: invalid-service
            spec:
              serviceName: ""
              virtualClusterId: test-cluster
            """;

        assertThatThrownBy(() -> setup.parseAllCrds(multiDocYaml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("document #2")
            .hasMessageContaining("invalid-service")
            .hasMessageContaining("serviceName must not be empty");
    }
}
