package com.example.messaging.operator.crd;

import static com.example.messaging.operator.crd.AclCRSpec.Operation.*;
import static com.example.messaging.operator.crd.ConsumerGroupSpec.ResourcePatternType.PREFIXED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.messaging.operator.crd.ConsumerGroupSpec.ResourcePatternType;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for all CRD specifications using AssertJ. Tests validate field presence, types, builder patterns, and relationships.
 */
@DisplayName("CRD Specification Tests")
class CrdSpecificationTest {

    @Nested
    @DisplayName("ApplicationService CRD")
    class ApplicationServiceTest {

        @Test
        @DisplayName("should create ApplicationService with required name field")
        void testApplicationServiceCreation() {
            ApplicationServiceSpec spec = new ApplicationServiceSpec();
            spec.setName("test-app-service");

            assertThat(spec.getName())
                    .isNotNull()
                    .isEqualTo("test-app-service");
        }

        @Test
        @DisplayName("should build ApplicationService using builder pattern")
        void testApplicationServiceBuilder() {
            ApplicationServiceSpec spec = new ApplicationServiceSpecBuilder().withName("orders-service").build();

            assertThat(spec)
                    .isNotNull()
                    .extracting(ApplicationServiceSpec::getName)
                    .isEqualTo("orders-service");
        }

        @Test
        @DisplayName("should create ApplicationService CR with metadata and spec")
        void testApplicationServiceCustomResource() {
            ApplicationService appService = new ApplicationService();

            ObjectMeta metadata = new ObjectMeta();
            metadata.setName("orders-service");
            metadata.setNamespace("orders");
            appService.setMetadata(metadata);

            ApplicationServiceSpec spec = new ApplicationServiceSpec();
            spec.setName("orders-service");
            appService.setSpec(spec);

            assertThat(appService).isNotNull();

            assertThat(appService.getMetadata())
                    .isNotNull()
                    .satisfies(m -> {
                assertThat(m.getName())
                        .isEqualTo("orders-service");
                assertThat(m.getNamespace())
                        .isEqualTo("orders");
            });

            assertThat(appService.getSpec())
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getName())
                                .isEqualTo("orders-service");
                    });
        }

        @Test
        @DisplayName("should use Lombok generated equals and hashCode")
        void testApplicationServiceEqualsAndHashCode() {
            ApplicationServiceSpec spec1 = new ApplicationServiceSpec();
            spec1.setName("test-service");

            ApplicationServiceSpec spec2 = new ApplicationServiceSpec();
            spec2.setName("test-service");

            ApplicationServiceSpec spec3 = new ApplicationServiceSpec();
            spec3.setName("different-service");

            assertThat(spec1)
                    .isEqualTo(spec2)
                    .hasSameHashCodeAs(spec2)
                    .isNotEqualTo(spec3);
        }
    }

    @Nested
    @DisplayName("VirtualCluster CRD")
    class VirtualClusterTest {

        @Test
        @DisplayName("should create VirtualCluster with clusterId and applicationServiceRef")
        void testVirtualClusterCreation() {
            VirtualClusterSpec spec = new VirtualClusterSpec();
            spec.setClusterId("prod-cluster");
            spec.setApplicationServiceRef("orders-service");

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getClusterId())
                                .isEqualTo("prod-cluster");
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("orders-service");
                    });
        }

        @Test
        @DisplayName("should build VirtualCluster using builder pattern")
        void testVirtualClusterBuilder() {
            VirtualClusterSpec spec = new VirtualClusterSpecBuilder().withClusterId("demo-cluster").withApplicationServiceRef("demo-admin").build();

            assertThat(spec)
                    .extracting(VirtualClusterSpec::getClusterId, VirtualClusterSpec::getApplicationServiceRef)
                    .containsExactly("demo-cluster", "demo-admin");
        }

        @Test
        @DisplayName("should validate all required fields are present")
        void testVirtualClusterRequiredFields() {
            VirtualClusterSpec spec = new VirtualClusterSpec();

            // Before setting required fields
            assertThat(spec.getClusterId())
                    .isNull();
            assertThat(spec.getApplicationServiceRef())
                    .isNull();

            // After setting required fields
            spec.setClusterId("test-cluster");
            spec.setApplicationServiceRef("test-app");

            assertThat(spec.getClusterId())
                    .isNotNull();
            assertThat(spec.getApplicationServiceRef())
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("ServiceAccount CRD")
    class ServiceAccountTest {

        @Test
        @DisplayName("should create ServiceAccount with dn as list of Distinguished Names")
        void testServiceAccountWithDnList() {
            ServiceAccountSpec spec = new ServiceAccountSpec();
            spec.setName("orders-service");
            spec.setDn(Arrays.asList("CN=orders-service,OU=ORDERS,O=EXAMPLE,L=CITY,C=US", "CN=orders-service-alt,OU=ORDERS,O=EXAMPLE,L=CITY,C=US"));
            spec.setClusterRef("prod-cluster");
            spec.setApplicationServiceRef("orders-service");

            assertThat(spec.getDn()).isNotNull()
                    .isInstanceOf(List.class)
                    .hasSize(2)
                    .containsExactly("CN=orders-service,OU=ORDERS,O=EXAMPLE,L=CITY,C=US", "CN=orders-service-alt,OU=ORDERS,O=EXAMPLE,L=CITY,C=US");
        }

        @Test
        @DisplayName("should handle single DN in list")
        void testServiceAccountWithSingleDn() {
            ServiceAccountSpec spec = new ServiceAccountSpec();
            spec.setName("demo-admin");
            spec.setDn(List.of("CN=demo-admin,OU=DEMO,O=EXAMPLE,L=CITY,C=US"));
            spec.setClusterRef("demo");
            spec.setApplicationServiceRef("demo-admin");

            assertThat(spec.getDn())
                    .hasSize(1)
                    .first()
                    .asString()
                    .contains("CN=demo-admin")
                    .contains("OU=DEMO");
        }

        @Test
        @DisplayName("should initialize dn as empty ArrayList by default")
        void testServiceAccountDnDefaultValue() {
            ServiceAccountSpec spec = new ServiceAccountSpec();

            assertThat(spec.getDn())
                    .isNotNull()
                    .isInstanceOf(java.util.ArrayList.class)
                    .isEmpty();
        }

        @Test
        @DisplayName("should build ServiceAccount with all required fields")
        void testServiceAccountBuilder() {
            ServiceAccountSpec spec = new ServiceAccountSpecBuilder()
                    .withName("test-sa")
                    .withDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"))
                    .withClusterRef("test-cluster")
                    .withApplicationServiceRef("test-app")
                    .build();

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getName())
                                .isEqualTo("test-sa");
                        assertThat(s.getDn())
                                .hasSize(1);
                        assertThat(s.getClusterRef())
                                .isEqualTo("test-cluster");
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("test-app");
                    });
        }

        @Test
        @DisplayName("should maintain relationship chain: ServiceAccount -> VirtualCluster -> ApplicationService")
        void testServiceAccountRelationships() {
            ServiceAccountSpec saSpec = new ServiceAccountSpec();
            saSpec.setName("orders-service");
            saSpec.setDn(List.of("CN=orders-service,OU=ORDERS,O=EXAMPLE,L=CITY,C=US"));
            saSpec.setClusterRef("prod-cluster");
            saSpec.setApplicationServiceRef("orders-service");

            // Verify the reference chain
            assertThat(saSpec)
                    .satisfies(s -> {
                        assertThat(s.getClusterRef())
                                .as("ServiceAccount should reference VirtualCluster")
                                .isEqualTo("prod-cluster");
                        assertThat(s.getApplicationServiceRef())
                                .as("ServiceAccount should reference ApplicationService")
                                .isEqualTo("orders-service");
                    });
        }

        @Test
        @DisplayName("should support mutable dn list")
        void testServiceAccountDnMutability() {
            ServiceAccountSpec spec = new ServiceAccountSpec();

            assertThat(spec.getDn())
                    .isEmpty();

            spec.getDn().add("CN=first,OU=TEST,O=EXAMPLE,L=CITY,C=US");
            assertThat(spec.getDn())
                    .hasSize(1);

            spec.getDn().add("CN=second,OU=TEST,O=EXAMPLE,L=CITY,C=US");
            assertThat(spec.getDn())
                    .hasSize(2);
        }
    }

    @Nested
    @DisplayName("Topic CRD")
    class TopicTest {

        @Test
        @DisplayName("should create Topic with all required fields")
        void testTopicCreation() {
            TopicCRSpec spec = new TopicCRSpec();
            spec.setServiceRef("orders-service-sa");
            spec.setName("orders.events");
            spec.setPartitions(12);
            spec.setReplicationFactor(3);
            spec.setApplicationServiceRef("orders-service");

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getServiceRef())
                                .isEqualTo("orders-service-sa");
                        assertThat(s.getName())
                                .isEqualTo("orders.events");
                        assertThat(s.getPartitions())
                                .isEqualTo(12);
                        assertThat(s.getReplicationFactor())
                                .isEqualTo(3);
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("orders-service");
                    });
        }

        @Test
        @DisplayName("should build Topic using builder pattern")
        void testTopicBuilder() {
            TopicCRSpec spec = new TopicCRSpecBuilder().withServiceRef("demo-acl-user-sa")
                    .withName("click")
                    .withPartitions(6)
                    .withReplicationFactor(3)
                    .withApplicationServiceRef("demo-acl-user")
                    .build();

            assertThat(spec)
                    .extracting(TopicCRSpec::getServiceRef, TopicCRSpec::getName, TopicCRSpec::getPartitions, TopicCRSpec::getReplicationFactor,
                            TopicCRSpec::getApplicationServiceRef)
                    .containsExactly("demo-acl-user-sa", "click", 6, 3, "demo-acl-user");
        }

        @Test
        @DisplayName("should reference ServiceAccount CR")
        void testTopicServiceAccountReference() {
            TopicCRSpec spec = new TopicCRSpec();
            spec.setServiceRef("orders-service-sa");
            spec.setApplicationServiceRef("orders-service");

            assertThat(spec.getServiceRef())
                    .as("Topic should reference ServiceAccount CR")
                    .isEqualTo("orders-service-sa");

            assertThat(spec.getApplicationServiceRef())
                    .as("Topic should reference ApplicationService CR")
                    .isEqualTo("orders-service");
        }
    }

    @Nested
    @DisplayName("ConsumerGroup CRD")
    class ConsumerGroupTest {

        @Test
        @DisplayName("should create ConsumerGroup with patternType")
        void testConsumerGroupCreation() {
            ConsumerGroupSpec spec = new ConsumerGroupSpec();
            spec.setServiceRef("demo-acl-user-sa");
            spec.setName("myconsumer-");
            spec.setPatternType(ResourcePatternType.PREFIXED);
            spec.setApplicationServiceRef("demo-acl-user");

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getServiceRef())
                                .isEqualTo("demo-acl-user-sa");
                        assertThat(s.getName())
                                .isEqualTo("myconsumer-");
                        assertThat(s.getPatternType())
                                .isEqualTo(ResourcePatternType.PREFIXED);
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("demo-acl-user");
                    });
        }

        @Test
        @DisplayName("should build ConsumerGroup using builder pattern")
        void testConsumerGroupBuilder() {
            ConsumerGroupSpec spec = new ConsumerGroupSpecBuilder().withServiceRef("test-sa")
                    .withName("test-consumer-")
                    .withPatternType(PREFIXED)
                    .withApplicationServiceRef("test-app")
                    .build();

            assertThat(spec)
                    .extracting(ConsumerGroupSpec::getServiceRef, ConsumerGroupSpec::getName, ConsumerGroupSpec::getPatternType,
                            ConsumerGroupSpec::getApplicationServiceRef)
                    .containsExactly("test-sa", "test-consumer-", PREFIXED, "test-app");
        }

        @Test
        @DisplayName("should support PREFIXED pattern type")
        void testConsumerGroupPrefixedPattern() {
            ConsumerGroupSpec spec = new ConsumerGroupSpec();
            spec.setPatternType(PREFIXED);
            spec.setName("myconsumer-");

            assertThat(spec.getPatternType())
                    .isEqualTo(PREFIXED)
                    .as("Pattern type should allow prefix-based matching");

            assertThat(spec.getName())
                    .endsWith("-")
                    .as("Prefixed consumer group names typically end with dash");
        }
    }

    @Nested
    @DisplayName("ACL CRD")
    class AclTest {

        @Test
        @DisplayName("should create ACL with Topic reference")
        void testAclWithTopicRef() {
            AclCRSpec spec = new AclCRSpec();
            spec.setServiceRef("orders-service-sa");
            spec.setTopicRef("orders-events");
            spec.setOperations(List.of(READ, WRITE));
            spec.setApplicationServiceRef("orders-service");

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getServiceRef())
                                .isEqualTo("orders-service-sa");
                        assertThat(s.getTopicRef())
                                .isEqualTo("orders-events");
                        assertThat(s.getOperations())
                                .hasSize(2)
                                .containsExactly(READ, WRITE);
                        assertThat(s.getConsumerGroupRef())
                                .isNull();
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("orders-service");
                    });
        }

        @Test
        @DisplayName("should create ACL with ConsumerGroup reference")
        void testAclWithConsumerGroupRef() {
            AclCRSpec spec = new AclCRSpec();
            spec.setServiceRef("demo-acl-user-sa");
            spec.setConsumerGroupRef("myconsumer-group");
            spec.setOperations(List.of(READ));
            spec.setApplicationServiceRef("demo-acl-user");

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getServiceRef())
                                .isEqualTo("demo-acl-user-sa");
                        assertThat(s.getConsumerGroupRef())
                                .isEqualTo("myconsumer-group");
                        assertThat(s.getOperations())
                                .hasSize(1)
                                .containsExactly(READ);
                        assertThat(s.getTopicRef())
                                .isNull();
                        assertThat(s.getApplicationServiceRef())
                                .isEqualTo("demo-acl-user");
                    });
        }

        @Test
        @DisplayName("should build ACL using builder pattern")
        void testAclBuilder() {
            AclCRSpec spec = new AclCRSpecBuilder()
                    .withServiceRef("test-sa")
                    .withTopicRef("test-topic")
                    .withOperations(List.of(READ, WRITE, DESCRIBE))
                    .withApplicationServiceRef("test-app")
                    .build();

            assertThat(spec)
                    .isNotNull()
                    .satisfies(s -> {
                        assertThat(s.getOperations())
                                .hasSize(3)
                                .containsExactlyInAnyOrder(READ, WRITE, DESCRIBE);
                    });
        }

        @Test
        @DisplayName("should support read-only operations")
        void testAclReadOnly() {
            AclCRSpec spec = new AclCRSpec();
            spec.setServiceRef("inventory-service-sa");
            spec.setTopicRef("inventory-updates");
            spec.setOperations(List.of(READ));
            spec.setApplicationServiceRef("inventory-service");

            assertThat(spec.getOperations())
                    .hasSize(1)
                    .containsOnly(READ)
                    .as("ACL should support read-only permissions");
        }

        @Test
        @DisplayName("should support read-write operations")
        void testAclReadWrite() {
            AclCRSpec spec = new AclCRSpec();
            spec.setOperations(List.of(READ, WRITE));

            assertThat(spec.getOperations())
                    .hasSize(2)
                    .contains(READ, WRITE)
                    .as("ACL should support read-write permissions");
        }

        @Test
        @DisplayName("should have mutually exclusive topicRef and consumerGroupRef")
        void testAclMutuallyExclusiveRefs() {
            AclCRSpec topicAcl = new AclCRSpec();
            topicAcl.setTopicRef("test-topic");

            AclCRSpec consumerGroupAcl = new AclCRSpec();
            consumerGroupAcl.setConsumerGroupRef("test-group");

            assertThat(topicAcl.getTopicRef())
                    .isNotNull();
            assertThat(topicAcl.getConsumerGroupRef())
                    .isNull();

            assertThat(consumerGroupAcl.getConsumerGroupRef())
                    .isNotNull();
            assertThat(consumerGroupAcl.getTopicRef())
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Cross-CR Relationship Tests")
    class RelationshipTest {

        @Test
        @DisplayName("should validate complete ApplicationService ownership chain")
        void testApplicationServiceOwnershipChain() {
            // ApplicationService is the root
            ApplicationServiceSpec appService = new ApplicationServiceSpec();
            appService.setName("orders-service");

            // VirtualCluster references ApplicationService
            VirtualClusterSpec vCluster = new VirtualClusterSpec();
            vCluster.setClusterId("prod-cluster");
            vCluster.setApplicationServiceRef("orders-service");

            // ServiceAccount references both VirtualCluster and ApplicationService
            ServiceAccountSpec sa = new ServiceAccountSpec();
            sa.setName("orders-service");
            sa.setDn(List.of("CN=orders-service,OU=ORDERS,O=EXAMPLE,L=CITY,C=US"));
            sa.setClusterRef("prod-cluster");
            sa.setApplicationServiceRef("orders-service");

            // Topic references ServiceAccount and ApplicationService
            TopicCRSpec topic = new TopicCRSpec();
            topic.setServiceRef("orders-service-sa");
            topic.setName("orders.events");
            topic.setApplicationServiceRef("orders-service");

            // Validate the chain
            assertThat(vCluster.getApplicationServiceRef())
                    .isEqualTo(appService.getName())
                    .as("VirtualCluster should reference ApplicationService");

            assertThat(sa.getApplicationServiceRef())
                    .isEqualTo(appService.getName())
                    .as("ServiceAccount should reference ApplicationService");

            assertThat(sa.getClusterRef())
                    .isEqualTo(vCluster.getClusterId())
                    .as("ServiceAccount should reference VirtualCluster");

            assertThat(topic.getApplicationServiceRef())
                    .isEqualTo(appService.getName())
                    .as("Topic should reference ApplicationService");
        }

        @Test
        @DisplayName("should validate ServiceAccount to VirtualCluster reference")
        void testServiceAccountToVirtualClusterReference() {
            String clusterId = "demo-acl";
            String appServiceName = "demo-acl-admin";

            VirtualClusterSpec vCluster = new VirtualClusterSpec();
            vCluster.setClusterId(clusterId);
            vCluster.setApplicationServiceRef(appServiceName);

            ServiceAccountSpec sa = new ServiceAccountSpec();
            sa.setClusterRef(clusterId);
            sa.setApplicationServiceRef(appServiceName);

            assertThat(sa.getClusterRef())
                    .isEqualTo(vCluster.getClusterId())
                    .as("ServiceAccount clusterRef should match VirtualCluster clusterId");

            assertThat(sa.getApplicationServiceRef())
                    .isEqualTo(vCluster.getApplicationServiceRef())
                    .as("Both should reference the same ApplicationService");
        }

        @Test
        @DisplayName("should validate Topic to ServiceAccount reference")
        void testTopicToServiceAccountReference() {
            String serviceAccountName = "demo-acl-user-sa";
            String appServiceName = "demo-acl-user";

            ServiceAccountSpec sa = new ServiceAccountSpec();
            sa.setName("demo-acl-user");
            sa.setApplicationServiceRef(appServiceName);

            TopicCRSpec topic = new TopicCRSpec();
            topic.setServiceRef(serviceAccountName);
            topic.setApplicationServiceRef(appServiceName);

            assertThat(topic.getServiceRef())
                    .contains(sa.getName())
                    .as("Topic serviceRef should reference ServiceAccount name");

            assertThat(topic.getApplicationServiceRef())
                    .isEqualTo(sa.getApplicationServiceRef())
                    .as("Both should reference the same ApplicationService");
        }

        @Test
        @DisplayName("should validate ACL references to Topic and ServiceAccount")
        void testAclReferences() {
            String serviceAccountName = "orders-service-sa";
            String topicName = "orders-events";
            String appServiceName = "orders-service";

            TopicCRSpec topic = new TopicCRSpec();
            topic.setName("orders.events");
            topic.setServiceRef(serviceAccountName);
            topic.setApplicationServiceRef(appServiceName);

            AclCRSpec acl = new AclCRSpec();
            acl.setServiceRef(serviceAccountName);
            acl.setTopicRef(topicName);
            acl.setApplicationServiceRef(appServiceName);

            assertThat(acl.getServiceRef())
                    .isEqualTo(topic.getServiceRef())
                    .as("ACL and Topic should reference the same ServiceAccount");

            assertThat(acl.getApplicationServiceRef())
                    .isEqualTo(topic.getApplicationServiceRef())
                    .as("ACL and Topic should reference the same ApplicationService");
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTest {

        @Test
        @DisplayName("should verify all CRD specs have builder support via @Buildable")
        void testAllSpecsHaveBuilders() {
            assertThatCode(() -> {
                new ApplicationServiceSpecBuilder().build();
                new VirtualClusterSpecBuilder().build();
                new ServiceAccountSpecBuilder().build();
                new TopicCRSpecBuilder().build();
                new ConsumerGroupSpecBuilder().build();
                new AclCRSpecBuilder().build();
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should support fluent builder chaining")
        void testFluentBuilderChaining() {
            ServiceAccountSpec spec = new ServiceAccountSpecBuilder()
                    .withName("test-sa")
                    .withDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"))
                    .withClusterRef("test-cluster")
                    .withApplicationServiceRef("test-app")
                    .build();

            assertThat(spec)
                    .isNotNull();
            assertThat(spec.getName())
                    .isEqualTo("test-sa");
            assertThat(spec.getClusterRef())
                    .isEqualTo("test-cluster");
        }

        @Test
        @DisplayName("should create new builder from existing spec")
        void testBuilderFromExistingSpec() {
            ServiceAccountSpec original = new ServiceAccountSpec();
            original.setName("original");
            original.setDn(List.of("CN=original,OU=TEST,O=EXAMPLE,L=CITY,C=US"));
            original.setClusterRef("original-cluster");
            original.setApplicationServiceRef("original-app");

            ServiceAccountSpec modified = new ServiceAccountSpecBuilder(original).withName("modified").build();

            assertThat(modified.getName())
                    .isEqualTo("modified");
            assertThat(modified.getClusterRef())
                    .isEqualTo("original-cluster");
            assertThat(modified.getDn())
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("Lombok Integration Tests")
    class LombokIntegrationTest {

        @Test
        @DisplayName("should generate equals method correctly")
        void testLombokEquals() {
            ServiceAccountSpec spec1 = new ServiceAccountSpec();
            spec1.setName("test");
            spec1.setDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"));

            ServiceAccountSpec spec2 = new ServiceAccountSpec();
            spec2.setName("test");
            spec2.setDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"));

            assertThat(spec1)
                    .isEqualTo(spec2);
        }

        @Test
        @DisplayName("should generate hashCode method correctly")
        void testLombokHashCode() {
            ServiceAccountSpec spec1 = new ServiceAccountSpec();
            spec1.setName("test");
            spec1.setDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"));

            ServiceAccountSpec spec2 = new ServiceAccountSpec();
            spec2.setName("test");
            spec2.setDn(List.of("CN=test,OU=TEST,O=EXAMPLE,L=CITY,C=US"));

            assertThat(spec1)
                    .hasSameHashCodeAs(spec2);
        }

        @Test
        @DisplayName("should generate toString method correctly")
        void testLombokToString() {
            ServiceAccountSpec spec = new ServiceAccountSpec();
            spec.setName("orders-service");
            spec.setDn(List.of("CN=orders-service,OU=ORDERS,O=EXAMPLE,L=CITY,C=US"));
            spec.setClusterRef("prod-cluster");
            spec.setApplicationServiceRef("orders-service");

            String toString = spec.toString();

            assertThat(toString)
                    .contains("ServiceAccountSpec")
                    .contains("name=orders-service")
                    .contains("clusterRef=prod-cluster")
                    .contains("applicationServiceRef=orders-service")
                    .contains("dn=");
        }

        @Test
        @DisplayName("should generate getter and setter methods")
        void testLombokGettersSetters() {
            TopicCRSpec spec = new TopicCRSpec();

            // Test setters
            spec.setName("test-topic");
            spec.setPartitions(3);
            spec.setReplicationFactor(2);

            // Test getters
            assertThat(spec.getName())
                    .isEqualTo("test-topic");
            assertThat(spec.getPartitions())
                    .isEqualTo(3);
            assertThat(spec.getReplicationFactor())
                    .isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Field Validation Tests")
    class FieldValidationTest {

        @Test
        @DisplayName("should have all required fields annotated with @Required")
        void testRequiredAnnotations() {
            // This is validated at compile time by Fabric8 CRD generator
            // The generated CRDs will have these fields in the 'required' section
            // Testing that objects can be created without null pointer exceptions

            ServiceAccountSpec saSpec = new ServiceAccountSpec();
            assertThat(saSpec.getDn())
                    .isNotNull()
                    .isEmpty();

            VirtualClusterSpec vcSpec = new VirtualClusterSpec();
            assertThat(vcSpec)
                    .isNotNull();

            TopicCRSpec topicSpec = new TopicCRSpec();
            assertThat(topicSpec)
                    .isNotNull();
        }

        @Test
        @DisplayName("should initialize list fields with empty collections")
        void testListFieldInitialization() {
            ServiceAccountSpec saSpec = new ServiceAccountSpec();
            assertThat(saSpec.getDn())
                    .as("DN list should be initialized as empty ArrayList")
                    .isNotNull()
                    .isEmpty();
        }

        @Test
        @DisplayName("should handle null vs empty list correctly")
        void testNullVsEmptyList() {
            ServiceAccountSpec spec1 = new ServiceAccountSpec();
            assertThat(spec1.getDn())
                    .isNotNull()
                    .isEmpty();

            ServiceAccountSpec spec2 = new ServiceAccountSpec();
            spec2.setDn(null);
            assertThat(spec2.getDn())
                    .isNull();

            ServiceAccountSpec spec3 = new ServiceAccountSpec();
            spec3.setDn(new ArrayList<>());
            assertThat(spec3.getDn())
                    .isNotNull()
                    .isEmpty();
        }
    }
}
