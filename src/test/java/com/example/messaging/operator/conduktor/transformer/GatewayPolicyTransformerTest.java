package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.ConduktorInterceptor;
import com.example.messaging.operator.conduktor.model.PolicyType;
import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.GatewayPolicy;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("GatewayPolicyTransformer Unit Tests")
class GatewayPolicyTransformerTest {

    private static final String NAMESPACE = "payments-team";
    private static final String APP_SERVICE = "payments-service";

    private CRDStore store;
    private GatewayPolicyTransformer transformer;

    @BeforeEach
    void setUp() {
        store = new CRDStore();
        transformer = new GatewayPolicyTransformer(store);
        createApplicationService();
    }

    private void createApplicationService() {
        ApplicationService appService = TestDataBuilder.applicationService()
                .namespace(NAMESPACE)
                .name(APP_SERVICE)
                .appName(APP_SERVICE)
                .build();
        store.create(CRDKind.APPLICATION_SERVICE, NAMESPACE, appService);
    }

    @Nested
    @DisplayName("Basic Transformation")
    class BasicTransformationTests {

        @Test
        @DisplayName("should transform with correct API version")
        void shouldTransformWithCorrectApiVersion() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .name("test-policy")
                    .clusterRef("my-cluster")
                    .policyType(PolicyType.CREATE_TOPIC_POLICY)
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getApiVersion()).isEqualTo("gateway/v2");
        }

        @Test
        @DisplayName("should transform with correct kind")
        void shouldTransformWithCorrectKind() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getKind()).isEqualTo("Interceptor");
        }

        @Test
        @DisplayName("should build namespaced interceptor name")
        void shouldBuildNamespacedInterceptorName() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .name("enforce-partitions")
                    .clusterRef("my-cluster")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getMetadata().getName()).isEqualTo("payments-team--enforce-partitions");
        }

        @Test
        @DisplayName("should map policyType to correct pluginClass")
        void shouldMapPolicyTypeToPluginClass() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .policyType(PolicyType.PRODUCE_POLICY)
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getSpec().getPluginClass())
                    .isEqualTo("io.conduktor.gateway.interceptor.safeguard.ProducePolicyPlugin");
        }

        @Test
        @DisplayName("should copy priority from source")
        void shouldCopyPriority() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .priority(50)
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getSpec().getPriority()).isEqualTo(50);
        }

        @Test
        @DisplayName("should pass through config as-is")
        void shouldPassThroughConfig() {
            createKafkaCluster("my-cluster", "my-vcluster-id");
            Map<String, Object> config = Map.of(
                    "topic", "payments-.*",
                    "numPartition", Map.of("min", 3, "max", 12, "action", "BLOCK"));
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .config(config)
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getSpec().getConfig())
                    .containsEntry("topic", "payments-.*")
                    .containsKey("numPartition");
        }
    }

    @Nested
    @DisplayName("Scope Resolution")
    class ScopeResolutionTests {

        @Test
        @DisplayName("should resolve clusterRef to vCluster scope")
        void shouldResolveClusterRefToVClusterScope() {
            createKafkaCluster("payments-cluster", "payments-prod-vcluster");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("payments-cluster")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getMetadata().getScope()).isNotNull();
            assertThat(result.getMetadata().getScope().getVCluster()).isEqualTo("payments-prod-vcluster");
        }

        @Test
        @DisplayName("should resolve serviceAccountRef to username scope")
        void shouldResolveServiceAccountRefToUsernameScope() {
            createKafkaCluster("my-cluster", "my-vcluster");
            createServiceAccount("payments-admin-sa", "payments-admin", "my-cluster");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .serviceAccountRef("payments-admin-sa")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getMetadata().getScope().getUsername()).isEqualTo("payments-admin");
        }

        @Test
        @DisplayName("should pass through groupRef to group scope")
        void shouldPassThroughGroupRef() {
            createKafkaCluster("my-cluster", "my-vcluster");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .groupRef("admin-group")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getMetadata().getScope().getGroup()).isEqualTo("admin-group");
        }

        @Test
        @DisplayName("should combine multiple scope fields")
        void shouldCombineMultipleScopeFields() {
            createKafkaCluster("my-cluster", "my-vcluster");
            createServiceAccount("my-sa", "sa-name", "my-cluster");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .serviceAccountRef("my-sa")
                    .groupRef("my-group")
                    .build();

            ConduktorInterceptor result = transformer.transform(source);

            assertThat(result.getMetadata().getScope().getVCluster()).isEqualTo("my-vcluster");
            assertThat(result.getMetadata().getScope().getUsername()).isEqualTo("sa-name");
            assertThat(result.getMetadata().getScope().getGroup()).isEqualTo("my-group");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("should throw when clusterRef not found")
        void shouldThrowWhenClusterRefNotFound() {
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("non-existent-cluster")
                    .build();

            assertThatThrownBy(() -> transformer.transform(source))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("KafkaCluster 'non-existent-cluster' not found");
        }

        @Test
        @DisplayName("should throw when serviceAccountRef not found")
        void shouldThrowWhenServiceAccountRefNotFound() {
            createKafkaCluster("my-cluster", "my-vcluster");
            GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                    .namespace(NAMESPACE)
                    .clusterRef("my-cluster")
                    .serviceAccountRef("non-existent-sa")
                    .build();

            assertThatThrownBy(() -> transformer.transform(source))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ServiceAccount 'non-existent-sa' not found");
        }

        @Test
        @DisplayName("should throw when transformer constructed with null store")
        void shouldThrowWhenStoreIsNull() {
            assertThatThrownBy(() -> new GatewayPolicyTransformer(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("store must not be null");
        }
    }

    @Nested
    @DisplayName("PolicyType Mapping")
    class PolicyTypeMappingTests {

        @Test
        @DisplayName("should map all traffic control policy types")
        void shouldMapTrafficControlPolicies() {
            createKafkaCluster("my-cluster", "my-vcluster");

            for (PolicyType type : new PolicyType[] {
                PolicyType.CREATE_TOPIC_POLICY,
                PolicyType.ALTER_TOPIC_POLICY,
                PolicyType.PRODUCE_POLICY,
                PolicyType.FETCH_POLICY,
                PolicyType.CONSUMER_GROUP_POLICY,
                PolicyType.CLIENT_ID_POLICY,
                PolicyType.PRODUCER_RATE_LIMITING,
                PolicyType.LIMIT_CONNECTION,
                PolicyType.LIMIT_JOIN_GROUP
            }) {
                GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                        .namespace(NAMESPACE)
                        .clusterRef("my-cluster")
                        .policyType(type)
                        .build();

                ConduktorInterceptor result = transformer.transform(source);

                assertThat(result.getSpec().getPluginClass())
                        .as("PolicyType %s should have a valid pluginClass", type)
                        .startsWith("io.conduktor.gateway.interceptor");
            }
        }

        @Test
        @DisplayName("should map data security policy types")
        void shouldMapDataSecurityPolicies() {
            createKafkaCluster("my-cluster", "my-vcluster");

            for (PolicyType type : new PolicyType[] {
                PolicyType.FIELD_ENCRYPTION,
                PolicyType.FIELD_DECRYPTION,
                PolicyType.DATA_MASKING,
                PolicyType.AUDIT,
                PolicyType.HEADER_INJECTION,
                PolicyType.HEADER_REMOVAL
            }) {
                GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                        .namespace(NAMESPACE)
                        .clusterRef("my-cluster")
                        .policyType(type)
                        .build();

                ConduktorInterceptor result = transformer.transform(source);

                assertThat(result.getSpec().getPluginClass())
                        .as("PolicyType %s should have a valid pluginClass", type)
                        .startsWith("io.conduktor.gateway.interceptor");
            }
        }

        @Test
        @DisplayName("should map chaos testing policy types")
        void shouldMapChaosPolicies() {
            createKafkaCluster("my-cluster", "my-vcluster");

            for (PolicyType type : new PolicyType[] {
                PolicyType.CHAOS_LATENCY,
                PolicyType.CHAOS_SLOW_BROKER,
                PolicyType.CHAOS_SLOW_PRODUCERS_CONSUMERS,
                PolicyType.CHAOS_BROKEN_BROKER,
                PolicyType.CHAOS_LEADER_ELECTION,
                PolicyType.CHAOS_MESSAGE_CORRUPTION,
                PolicyType.CHAOS_DUPLICATE_MESSAGES
            }) {
                GatewayPolicy source = TestDataBuilder.gatewayPolicy()
                        .namespace(NAMESPACE)
                        .clusterRef("my-cluster")
                        .policyType(type)
                        .build();

                ConduktorInterceptor result = transformer.transform(source);

                assertThat(result.getSpec().getPluginClass())
                        .as("PolicyType %s should have chaos pluginClass", type)
                        .contains("chaos");
            }
        }
    }

    private void createKafkaCluster(String name, String clusterId) {
        KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                .namespace(NAMESPACE)
                .name(name)
                .clusterId(clusterId)
                .applicationServiceRef(APP_SERVICE)
                .build();
        store.create(CRDKind.KAFKA_CLUSTER, NAMESPACE, cluster);
    }

    private void createServiceAccount(String name, String saName, String clusterRef) {
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .namespace(NAMESPACE)
                .name(name)
                .saName(saName)
                .clusterRef(clusterRef)
                .applicationServiceRef(APP_SERVICE)
                .dn("CN=test")
                .build();
        store.create(CRDKind.SERVICE_ACCOUNT, NAMESPACE, sa);
    }
}
