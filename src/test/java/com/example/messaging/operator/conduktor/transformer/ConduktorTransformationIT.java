package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.ConduktorInterceptor;
import com.example.messaging.operator.conduktor.model.ConduktorResource;
import com.example.messaging.operator.conduktor.model.ConduktorTopic;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccount;
import com.example.messaging.operator.conduktor.model.PolicyType;
import com.example.messaging.operator.conduktor.model.VirtualCluster;
import com.example.messaging.operator.conduktor.yaml.ConduktorYamlWriter;
import com.example.messaging.operator.crd.AuthType;
import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.GatewayPolicy;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.Scope;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Functional tests for Conduktor CRD transformation.
 * These tests validate end-to-end YAML transformation from internal CRDs to Conduktor format.
 */
@DisplayName("Conduktor Transformation Functional Tests")
class ConduktorTransformationIT {

    private static final String FIXTURES_PATH = "/fixtures/conduktor/";

    private ObjectMapper yamlMapper;
    private ConduktorYamlWriter conduktorWriter;

    @BeforeEach
    void setUp() {
        yamlMapper = new ObjectMapper(new YAMLFactory());
        conduktorWriter = new ConduktorYamlWriter();
    }

    @Nested
    @DisplayName("KafkaCluster → VirtualCluster Transformation")
    class KafkaClusterTransformationTests {

        private KafkaClusterTransformer transformer;

        @BeforeEach
        void setUp() {
            transformer = new KafkaClusterTransformer();
        }

        @Test
        @DisplayName("should transform multiple KafkaClusters to VirtualClusters with correct YAML output")
        void shouldTransformKafkaClustersToVirtualClusters() throws IOException {
            List<Map<String, Object>> inputDocs = loadYamlDocuments("input-kafka-cluster.yaml");
            List<Map<String, Object>> expectedDocs = loadYamlDocuments("expected-virtual-cluster.yaml");

            assertThat(inputDocs).hasSameSizeAs(expectedDocs);

            for (int i = 0; i < inputDocs.size(); i++) {
                KafkaCluster input = parseKafkaCluster(inputDocs.get(i));
                VirtualCluster result = transformer.transform(input);
                String actualYaml = conduktorWriter.toYaml(result);
                Map<String, Object> actualDoc = parseYaml(actualYaml);

                assertThat(actualDoc)
                        .as("Transformation %d: %s", i, input.getSpec().getClusterId())
                        .containsEntry("apiVersion", expectedDocs.get(i).get("apiVersion"))
                        .containsEntry("kind", expectedDocs.get(i).get("kind"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedMetadata = (Map<String, Object>) expectedDocs.get(i).get("metadata");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualMetadata = (Map<String, Object>) actualDoc.get("metadata");

                assertThat(actualMetadata.get("name"))
                        .as("Metadata name for transformation %d", i)
                        .isEqualTo(expectedMetadata.get("name"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedSpec = (Map<String, Object>) expectedDocs.get(i).get("spec");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualSpec = (Map<String, Object>) actualDoc.get("spec");

                assertThat(actualSpec.get("aclEnabled"))
                        .as("aclEnabled for transformation %d", i)
                        .isEqualTo(expectedSpec.get("aclEnabled"));
            }
        }

        @Test
        @DisplayName("should produce valid YAML for payments-prod-vcluster")
        void shouldProduceValidYamlForPaymentsProdCluster() throws IOException {
            KafkaCluster input = TestDataBuilder.kafkaCluster()
                    .name("production-cluster")
                    .namespace("payments-team")
                    .clusterId("payments-prod-vcluster")
                    .applicationServiceRef("payments-service")
                    .build();

            VirtualCluster result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("apiVersion: gateway/v2")
                    .contains("kind: VirtualCluster")
                    .contains("name: payments-prod-vcluster")
                    .contains("aclEnabled: true");
        }
    }

    @Nested
    @DisplayName("ServiceAccount → GatewayServiceAccount Transformation")
    class ServiceAccountTransformationTests {

        private CRDStore store;
        private ServiceAccountTransformer transformer;

        @BeforeEach
        void setUp() {
            store = new CRDStore();
            transformer = new ServiceAccountTransformer(store);
            setupKafkaClusters();
        }

        private void setupKafkaClusters() {
            createAppAndCluster("payments-service", "payments-team", "production-cluster", AuthType.MTLS);
            createAppAndCluster("orders-service", "orders-team", "orders-cluster", AuthType.MTLS);
            createAppAndCluster("legacy-service", "legacy-team", "legacy-cluster", AuthType.MTLS);
            createAppAndCluster("sasl-service", "sasl-team", "sasl-cluster", AuthType.SASL_SSL);
        }

        private void createAppAndCluster(String appService, String namespace, String clusterName, AuthType authType) {
            ApplicationService app = TestDataBuilder.applicationService()
                    .namespace(namespace).name(appService).appName(appService).build();
            store.create(CRDKind.APPLICATION_SERVICE, namespace, app);

            KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                    .namespace(namespace).name(clusterName).clusterId(clusterName)
                    .applicationServiceRef(appService).authType(authType).build();
            store.create(CRDKind.KAFKA_CLUSTER, namespace, cluster);
        }

        @Test
        @DisplayName("should transform multiple ServiceAccounts to GatewayServiceAccounts with correct DN extraction")
        void shouldTransformServiceAccountsWithDnExtraction() throws IOException {
            List<Map<String, Object>> inputDocs = loadYamlDocuments("input-service-account.yaml");
            List<Map<String, Object>> expectedDocs = loadYamlDocuments("expected-gateway-service-account.yaml");

            assertThat(inputDocs).hasSameSizeAs(expectedDocs);

            for (int i = 0; i < inputDocs.size(); i++) {
                ServiceAccount input = parseServiceAccount(inputDocs.get(i));
                GatewayServiceAccount result = transformer.transform(input);
                String actualYaml = conduktorWriter.toYaml(result);
                Map<String, Object> actualDoc = parseYaml(actualYaml);

                assertThat(actualDoc)
                        .as("Transformation %d: %s", i, input.getSpec().getName())
                        .containsEntry("apiVersion", expectedDocs.get(i).get("apiVersion"))
                        .containsEntry("kind", expectedDocs.get(i).get("kind"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedMetadata = (Map<String, Object>) expectedDocs.get(i).get("metadata");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualMetadata = (Map<String, Object>) actualDoc.get("metadata");

                assertThat(actualMetadata)
                        .as("Metadata for transformation %d", i)
                        .containsEntry("name", expectedMetadata.get("name"))
                        .containsEntry("vCluster", expectedMetadata.get("vCluster"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedSpec = (Map<String, Object>) expectedDocs.get(i).get("spec");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualSpec = (Map<String, Object>) actualDoc.get("spec");

                assertThat(actualSpec.get("type"))
                        .as("Type for transformation %d", i)
                        .isEqualTo(expectedSpec.get("type"));

                assertThat(actualSpec.get("externalNames"))
                        .as("External names for transformation %d", i)
                        .isEqualTo(expectedSpec.get("externalNames"));
            }
        }

        @Test
        @DisplayName("should correctly extract CN from complex DN patterns")
        void shouldExtractCnFromComplexDnPatterns() {
            ServiceAccount input = TestDataBuilder.serviceAccount()
                    .namespace("payments-team").saName("payments-admin")
                    .clusterRef("production-cluster").applicationServiceRef("payments-service")
                    .dn(List.of(
                            "CN=payments-admin,OU=ServiceAccounts,O=PaymentsTeam,C=FR",
                            "CN=payments-backup-admin,OU=ServiceAccounts,O=PaymentsTeam,C=FR"))
                    .build();

            GatewayServiceAccount result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("apiVersion: gateway/v2")
                    .contains("kind: GatewayServiceAccount")
                    .contains("name: payments-admin")
                    .containsIgnoringCase("vcluster: production-cluster")
                    .contains("type: EXTERNAL")
                    .contains("- payments-admin")
                    .contains("- payments-backup-admin");
        }

        @Test
        @DisplayName("should preserve full DN when CN is not present")
        void shouldPreserveFullDnWhenCnNotPresent() {
            ServiceAccount input = TestDataBuilder.serviceAccount()
                    .namespace("legacy-team").saName("legacy-app")
                    .clusterRef("legacy-cluster").applicationServiceRef("legacy-service")
                    .dn("OU=LegacyApps,O=Company")
                    .build();

            GatewayServiceAccount result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .containsPattern("- [\"']?OU=LegacyApps,O=Company[\"']?");
        }
    }

    @Nested
    @DisplayName("Topic → ConduktorTopic Transformation")
    class TopicTransformationTests {

        private CRDStore store;
        private TopicTransformer transformer;

        @BeforeEach
        void setUp() {
            store = new CRDStore();
            transformer = new TopicTransformer(store);
            setupTestData();
        }

        private void setupTestData() {
            createAppServiceAndCluster("payments-service", "payments-team", "payments-admin", "payments-prod-vcluster");
            createAppServiceAndCluster("orders-service", "orders-team", "orders-processor", "orders-prod-vcluster");
            createAppServiceAndCluster("simple-service", "simple-team", "simple-sa", "simple-cluster");
        }

        private void createAppServiceAndCluster(String appService, String namespace, String saName, String clusterRef) {
            ApplicationService app = TestDataBuilder.applicationService()
                    .namespace(namespace)
                    .name(appService)
                    .appName(appService)
                    .build();
            store.create(CRDKind.APPLICATION_SERVICE, namespace, app);

            KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                    .namespace(namespace)
                    .name(clusterRef)
                    .clusterId(clusterRef)
                    .applicationServiceRef(appService)
                    .build();
            store.create(CRDKind.KAFKA_CLUSTER, namespace, cluster);

            ServiceAccount sa = TestDataBuilder.serviceAccount()
                    .namespace(namespace)
                    .name(saName)
                    .saName(saName)
                    .clusterRef(clusterRef)
                    .applicationServiceRef(appService)
                    .dn("CN=" + saName)
                    .build();
            store.create(CRDKind.SERVICE_ACCOUNT, namespace, sa);
        }

        @Test
        @DisplayName("should transform topics with full config preservation")
        void shouldTransformTopicsWithConfigs() throws IOException {
            List<Map<String, Object>> inputDocs = loadYamlDocuments("input-topic.yaml");
            List<Map<String, Object>> expectedDocs = loadYamlDocuments("expected-topic.yaml");

            assertThat(inputDocs).hasSameSizeAs(expectedDocs);

            for (int i = 0; i < inputDocs.size(); i++) {
                Topic input = parseTopic(inputDocs.get(i));
                ConduktorTopic result = transformer.transform(input);
                String actualYaml = conduktorWriter.toYaml(result);
                Map<String, Object> actualDoc = parseYaml(actualYaml);

                assertThat(actualDoc)
                        .as("Transformation %d: %s", i, input.getSpec().getName())
                        .containsEntry("apiVersion", expectedDocs.get(i).get("apiVersion"))
                        .containsEntry("kind", expectedDocs.get(i).get("kind"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedMetadata = (Map<String, Object>) expectedDocs.get(i).get("metadata");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualMetadata = (Map<String, Object>) actualDoc.get("metadata");

                assertThat(actualMetadata)
                        .as("Metadata for transformation %d", i)
                        .containsEntry("name", expectedMetadata.get("name"))
                        .containsEntry("cluster", expectedMetadata.get("cluster"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedSpec = (Map<String, Object>) expectedDocs.get(i).get("spec");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualSpec = (Map<String, Object>) actualDoc.get("spec");

                assertThat(actualSpec.get("partitions"))
                        .as("Partitions for transformation %d", i)
                        .isEqualTo(expectedSpec.get("partitions"));

                assertThat(actualSpec.get("replicationFactor"))
                        .as("Replication factor for transformation %d", i)
                        .isEqualTo(expectedSpec.get("replicationFactor"));

                if (expectedSpec.get("configs") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> expectedConfigs = (Map<String, Object>) expectedSpec.get("configs");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> actualConfigs = (Map<String, Object>) actualSpec.get("configs");

                    assertThat(actualConfigs.keySet())
                            .as("Config keys for transformation %d", i)
                            .containsExactlyInAnyOrderElementsOf(expectedConfigs.keySet());

                    for (String key : expectedConfigs.keySet()) {
                        assertThat(String.valueOf(actualConfigs.get(key)))
                                .as("Config %s for transformation %d", key, i)
                                .isEqualTo(String.valueOf(expectedConfigs.get(key)));
                    }
                }
            }
        }

        @Test
        @DisplayName("should produce valid YAML for topic with retention config")
        void shouldProduceValidYamlForTopicWithRetention() {
            Topic input = TestDataBuilder.topic()
                    .namespace("payments-team")
                    .name("payments-events-topic")
                    .topicName("payments.events.v1")
                    .serviceRef("payments-admin")
                    .applicationServiceRef("payments-service")
                    .partitions(12)
                    .replicationFactor(3)
                    .config(Map.of(
                            "retention.ms", "604800000",
                            "cleanup.policy", "delete",
                            "min.insync.replicas", "2"))
                    .build();

            ConduktorTopic result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("apiVersion: kafka/v2")
                    .contains("kind: Topic")
                    .contains("name: payments.events.v1")
                    .contains("cluster: payments-prod-vcluster")
                    .contains("partitions: 12")
                    .contains("replicationFactor: 3")
                    .contains("retention.ms:")
                    .contains("cleanup.policy: delete");
        }

        @Test
        @DisplayName("should handle topic without configs")
        void shouldHandleTopicWithoutConfigs() {
            Topic input = TestDataBuilder.topic()
                    .namespace("simple-team")
                    .name("simple-topic")
                    .topicName("simple.events")
                    .serviceRef("simple-sa")
                    .applicationServiceRef("simple-service")
                    .partitions(3)
                    .replicationFactor(1)
                    .config(Map.of())
                    .build();

            ConduktorTopic result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("apiVersion: kafka/v2")
                    .contains("kind: Topic")
                    .contains("name: simple.events")
                    .contains("cluster: simple-cluster")
                    .contains("partitions: 3")
                    .contains("replicationFactor: 1")
                    .doesNotContain("configs:");
        }
    }

    @Nested
    @DisplayName("GatewayPolicy → Interceptor Transformation")
    class GatewayPolicyTransformationTests {

        private CRDStore store;
        private GatewayPolicyTransformer transformer;

        @BeforeEach
        void setUp() {
            store = new CRDStore();
            transformer = new GatewayPolicyTransformer(store);
            setupTestData();
        }

        private void setupTestData() {
            createAppServiceAndCluster("payments-service", "payments-team", "payments-admin", "payments-cluster", "payments-prod-vcluster");
            createAppServiceAndCluster("orders-service", "orders-team", "orders-processor", "orders-cluster", "orders-prod-vcluster");
            createAppServiceAndCluster("test-service", "staging", "test-sa", "staging-cluster", "staging-vcluster");

            createScope("payments-cluster-scope", "payments-team", "payments-service", "payments-cluster", null, null);
            createScope("payments-admin-scope", "payments-team", "payments-service", "payments-cluster", "payments-admin", null);
            createScope("orders-cluster-scope", "orders-team", "orders-service", "orders-cluster", null, null);
            createScope("staging-cluster-scope", "staging", "test-service", "staging-cluster", null, null);
        }

        private void createAppServiceAndCluster(String appService, String namespace, String saName, String clusterName, String clusterId) {
            ApplicationService app = TestDataBuilder.applicationService()
                    .namespace(namespace)
                    .name(appService)
                    .appName(appService)
                    .build();
            store.create(CRDKind.APPLICATION_SERVICE, namespace, app);

            KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                    .namespace(namespace)
                    .name(clusterName)
                    .clusterId(clusterId)
                    .applicationServiceRef(appService)
                    .build();
            store.create(CRDKind.KAFKA_CLUSTER, namespace, cluster);

            ServiceAccount sa = TestDataBuilder.serviceAccount()
                    .namespace(namespace)
                    .name(saName)
                    .saName(saName)
                    .clusterRef(clusterName)
                    .applicationServiceRef(appService)
                    .dn("CN=" + saName)
                    .build();
            store.create(CRDKind.SERVICE_ACCOUNT, namespace, sa);
        }

        private void createScope(String name, String namespace, String appService, String clusterRef, String serviceAccountRef, String groupRef) {
            Scope scope = TestDataBuilder.scope()
                    .namespace(namespace)
                    .name(name)
                    .applicationServiceRef(appService)
                    .clusterRef(clusterRef)
                    .serviceAccountRef(serviceAccountRef)
                    .groupRef(groupRef)
                    .build();
            store.create(CRDKind.SCOPE, namespace, scope);
        }

        @Test
        @DisplayName("should transform GatewayPolicy to Interceptor with correct structure")
        void shouldTransformGatewayPolicyToInterceptor() {
            GatewayPolicy input = TestDataBuilder.gatewayPolicy()
                    .namespace("payments-team")
                    .name("enforce-partitions")
                    .scopeRef("payments-cluster-scope")
                    .policyType(PolicyType.CREATE_TOPIC_POLICY)
                    .priority(100)
                    .config(Map.of(
                            "topic", "payments-.*",
                            "numPartition", Map.of("min", 3, "max", 12, "action", "BLOCK")))
                    .build();

            ConduktorInterceptor result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("apiVersion: gateway/v2")
                    .contains("kind: Interceptor")
                    .contains("name: payments-team--enforce-partitions")
                    .contains("vCluster: payments-prod-vcluster")
                    .contains("pluginClass: io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin")
                    .contains("priority: 100")
                    .contains("topic: payments-.*");
        }

        @Test
        @DisplayName("should transform DataMasking policy with complex config")
        void shouldTransformDataMaskingPolicy() {
            GatewayPolicy input = TestDataBuilder.gatewayPolicy()
                    .namespace("payments-team")
                    .name("mask-pii")
                    .scopeRef("payments-cluster-scope")
                    .policyType(PolicyType.DATA_MASKING)
                    .priority(200)
                    .config(Map.of(
                            "topic", "payments.customers.*",
                            "fields", List.of(
                                    Map.of("fieldName", "email", "rule", "MASK_ALL"),
                                    Map.of("fieldName", "phone", "rule", "MASK_LAST_N", "nChars", 4))))
                    .build();

            ConduktorInterceptor result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("pluginClass: io.conduktor.gateway.interceptor.FieldLevelDataMaskingPlugin")
                    .contains("fieldName: email")
                    .contains("rule: MASK_ALL")
                    .contains("fieldName: phone")
                    .contains("rule: MASK_LAST_N");
        }

        @Test
        @DisplayName("should transform Audit policy")
        void shouldTransformAuditPolicy() {
            GatewayPolicy input = TestDataBuilder.gatewayPolicy()
                    .namespace("orders-team")
                    .name("audit-requests")
                    .scopeRef("orders-cluster-scope")
                    .policyType(PolicyType.AUDIT)
                    .priority(1)
                    .config(Map.of(
                            "topic", ".*",
                            "apiKeys", List.of("PRODUCE", "FETCH")))
                    .build();

            ConduktorInterceptor result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("name: orders-team--audit-requests")
                    .contains("vCluster: orders-prod-vcluster")
                    .contains("pluginClass: io.conduktor.gateway.interceptor.AuditPlugin")
                    .contains("priority: 1");
        }

        @Test
        @DisplayName("should transform Chaos policy for testing")
        void shouldTransformChaosPolicy() {
            GatewayPolicy input = TestDataBuilder.gatewayPolicy()
                    .namespace("staging")
                    .name("chaos-latency")
                    .scopeRef("staging-cluster-scope")
                    .policyType(PolicyType.CHAOS_LATENCY)
                    .priority(100)
                    .config(Map.of(
                            "rateInPercent", 10,
                            "minLatencyMs", 100,
                            "maxLatencyMs", 500))
                    .build();

            ConduktorInterceptor result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("name: staging--chaos-latency")
                    .contains("vCluster: staging-vcluster")
                    .contains("pluginClass: io.conduktor.gateway.interceptor.chaos.SimulateLatencyPlugin")
                    .contains("rateInPercent: 10")
                    .contains("minLatencyMs: 100")
                    .contains("maxLatencyMs: 500");
        }

        @Test
        @DisplayName("should transform all fixture GatewayPolicies via Scope indirection to expected Interceptors")
        void shouldTransformFixtureGatewayPoliciesToExpectedInterceptors() throws IOException {
            List<Map<String, Object>> inputDocs = loadYamlDocuments("input-gateway-policy.yaml");
            List<Map<String, Object>> expectedDocs = loadYamlDocuments("expected-interceptor.yaml");

            assertThat(inputDocs).hasSameSizeAs(expectedDocs);

            for (int i = 0; i < inputDocs.size(); i++) {
                GatewayPolicy input = parseGatewayPolicy(inputDocs.get(i));
                ConduktorInterceptor result = transformer.transform(input);
                String actualYaml = conduktorWriter.toYaml(result);
                Map<String, Object> actualDoc = parseYaml(actualYaml);

                assertThat(actualDoc)
                        .as("Transformation %d: %s", i, input.getMetadata().getName())
                        .containsEntry("apiVersion", expectedDocs.get(i).get("apiVersion"))
                        .containsEntry("kind", expectedDocs.get(i).get("kind"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedMetadata = (Map<String, Object>) expectedDocs.get(i).get("metadata");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualMetadata = (Map<String, Object>) actualDoc.get("metadata");

                assertThat(actualMetadata.get("name"))
                        .as("Interceptor name for transformation %d", i)
                        .isEqualTo(expectedMetadata.get("name"));

                assertThat(actualMetadata.get("scope"))
                        .as("Scope for transformation %d", i)
                        .isEqualTo(expectedMetadata.get("scope"));

                @SuppressWarnings("unchecked")
                Map<String, Object> expectedSpec = (Map<String, Object>) expectedDocs.get(i).get("spec");
                @SuppressWarnings("unchecked")
                Map<String, Object> actualSpec = (Map<String, Object>) actualDoc.get("spec");

                assertThat(actualSpec.get("pluginClass"))
                        .as("pluginClass for transformation %d", i)
                        .isEqualTo(expectedSpec.get("pluginClass"));

                assertThat(actualSpec.get("priority"))
                        .as("priority for transformation %d", i)
                        .isEqualTo(expectedSpec.get("priority"));
            }
        }

        @Test
        @DisplayName("should include username scope when serviceAccountRef is set in Scope")
        void shouldIncludeUsernameScopeWhenServiceAccountRefSet() {
            GatewayPolicy input = TestDataBuilder.gatewayPolicy()
                    .namespace("payments-team")
                    .name("user-scoped-policy")
                    .scopeRef("payments-admin-scope")
                    .policyType(PolicyType.PRODUCE_POLICY)
                    .priority(50)
                    .build();

            ConduktorInterceptor result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            assertThat(yaml)
                    .contains("vCluster: payments-prod-vcluster")
                    .contains("username: payments-admin");
        }
    }

    @Nested
    @DisplayName("Full Pipeline YAML Comparison")
    class FullPipelineTests {

        @Test
        @DisplayName("should produce identical YAML structure for KafkaCluster transformation")
        void shouldProduceIdenticalYamlForKafkaCluster() {
            KafkaClusterTransformer transformer = new KafkaClusterTransformer();

            KafkaCluster input = TestDataBuilder.kafkaCluster()
                    .clusterId("my-test-cluster")
                    .build();

            VirtualCluster result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            String expectedYaml = """
                    apiVersion: gateway/v2
                    kind: VirtualCluster
                    metadata:
                      name: my-test-cluster
                    spec:
                      aclEnabled: true
                    """;

            assertThat(normalizeYaml(yaml)).isEqualTo(normalizeYaml(expectedYaml));
        }

        @Test
        @DisplayName("should produce identical YAML structure for ServiceAccount transformation")
        void shouldProduceIdenticalYamlForServiceAccount() {
            CRDStore saStore = new CRDStore();

            ApplicationService app = TestDataBuilder.applicationService()
                    .namespace("default").name("test-app").appName("test-app").build();
            saStore.create(CRDKind.APPLICATION_SERVICE, "default", app);

            KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                    .namespace("default").name("my-cluster").clusterId("my-cluster")
                    .applicationServiceRef("test-app").authType(AuthType.MTLS).build();
            saStore.create(CRDKind.KAFKA_CLUSTER, "default", cluster);

            ServiceAccountTransformer transformer = new ServiceAccountTransformer(saStore);

            ServiceAccount input = TestDataBuilder.serviceAccount()
                    .namespace("default").saName("my-service-account")
                    .clusterRef("my-cluster")
                    .dn(List.of("CN=user1,O=Org", "CN=user2,O=Org"))
                    .build();

            GatewayServiceAccount result = transformer.transform(input);
            String yaml = conduktorWriter.toYaml(result);

            String expectedYaml = """
                    apiVersion: gateway/v2
                    kind: GatewayServiceAccount
                    metadata:
                      name: my-service-account
                      vCluster: my-cluster
                    spec:
                      type: EXTERNAL
                      externalNames:
                        - user1
                        - user2
                    """;

            assertThat(normalizeYaml(yaml)).isEqualTo(normalizeYaml(expectedYaml));
        }

        @Test
        @DisplayName("should produce identical YAML structure for GatewayPolicy → Interceptor via Scope")
        void shouldProduceIdenticalYamlForGatewayPolicyViaScope() {
            CRDStore store = new CRDStore();
            GatewayPolicyTransformer policyTransformer = new GatewayPolicyTransformer(store);

            ApplicationService app = TestDataBuilder.applicationService()
                    .namespace("test-ns").name("test-app").appName("test-app").build();
            store.create(CRDKind.APPLICATION_SERVICE, "test-ns", app);

            KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                    .namespace("test-ns").name("my-cluster").clusterId("my-vcluster")
                    .applicationServiceRef("test-app").build();
            store.create(CRDKind.KAFKA_CLUSTER, "test-ns", cluster);

            ServiceAccount sa = TestDataBuilder.serviceAccount()
                    .namespace("test-ns").name("my-sa").saName("my-username")
                    .clusterRef("my-cluster").applicationServiceRef("test-app").dn("CN=test").build();
            store.create(CRDKind.SERVICE_ACCOUNT, "test-ns", sa);

            Scope scope = TestDataBuilder.scope()
                    .namespace("test-ns").name("my-scope")
                    .applicationServiceRef("test-app").clusterRef("my-cluster")
                    .serviceAccountRef("my-sa").build();
            store.create(CRDKind.SCOPE, "test-ns", scope);

            GatewayPolicy policy = TestDataBuilder.gatewayPolicy()
                    .namespace("test-ns").name("enforce-partitions")
                    .scopeRef("my-scope")
                    .policyType(PolicyType.CREATE_TOPIC_POLICY)
                    .priority(100)
                    .config(Map.of("topic", "payments-.*"))
                    .build();

            ConduktorInterceptor result = policyTransformer.transform(policy);
            String yaml = conduktorWriter.toYaml(result);

            String expectedYaml = """
                    apiVersion: gateway/v2
                    kind: Interceptor
                    metadata:
                      name: test-ns--enforce-partitions
                      scope:
                        username: my-username
                        vCluster: my-vcluster
                    spec:
                      pluginClass: io.conduktor.gateway.interceptor.safeguard.CreateTopicPolicyPlugin
                      priority: 100
                      config:
                        topic: payments-.*
                    """;

            assertThat(normalizeYaml(yaml)).isEqualTo(normalizeYaml(expectedYaml));
        }
    }

    private List<Map<String, Object>> loadYamlDocuments(String filename) throws IOException {
        String content = loadResource(FIXTURES_PATH + filename);
        List<Map<String, Object>> documents = new ArrayList<>();

        for (String doc : content.split("---")) {
            String trimmed = doc.trim();
            if (!trimmed.isEmpty()) {
                documents.add(parseYaml(trimmed));
            }
        }

        return documents;
    }

    private String loadResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yaml) throws IOException {
        return yamlMapper.readValue(yaml, Map.class);
    }

    @SuppressWarnings("unchecked")
    private KafkaCluster parseKafkaCluster(Map<String, Object> doc) {
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");

        TestDataBuilder.KafkaClusterBuilder builder = TestDataBuilder.kafkaCluster()
                .name((String) metadata.get("name"))
                .namespace((String) metadata.get("namespace"))
                .clusterId((String) spec.get("clusterId"))
                .applicationServiceRef((String) spec.get("applicationServiceRef"));

        String authTypeStr = (String) spec.get("authType");
        if (authTypeStr != null) {
            builder.authType(AuthType.valueOf(authTypeStr));
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private ServiceAccount parseServiceAccount(Map<String, Object> doc) {
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");

        TestDataBuilder.ServiceAccountBuilder builder = TestDataBuilder.serviceAccount()
                .name((String) metadata.get("name"))
                .namespace((String) metadata.get("namespace"))
                .saName((String) spec.get("name"))
                .clusterRef((String) spec.get("clusterRef"))
                .applicationServiceRef((String) spec.get("applicationServiceRef"));

        List<String> dn = (List<String>) spec.get("dn");
        if (dn != null) {
            builder.dn(dn);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private Topic parseTopic(Map<String, Object> doc) {
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");

        TestDataBuilder.TopicBuilder builder = TestDataBuilder.topic()
                .name((String) metadata.get("name"))
                .namespace((String) metadata.get("namespace"))
                .topicName((String) spec.get("name"))
                .serviceRef((String) spec.get("serviceRef"))
                .applicationServiceRef((String) spec.get("applicationServiceRef"))
                .partitions((Integer) spec.get("partitions"))
                .replicationFactor((Integer) spec.get("replicationFactor"));

        Map<String, String> config = (Map<String, String>) spec.get("config");
        if (config != null) {
            builder.config(config);
        }

        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private GatewayPolicy parseGatewayPolicy(Map<String, Object> doc) {
        Map<String, Object> metadata = (Map<String, Object>) doc.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");

        TestDataBuilder.GatewayPolicyBuilder builder = TestDataBuilder.gatewayPolicy()
                .name((String) metadata.get("name"))
                .namespace((String) metadata.get("namespace"))
                .scopeRef((String) spec.get("scopeRef"))
                .policyType(PolicyType.valueOf((String) spec.get("policyType")))
                .priority((Integer) spec.get("priority"));

        Map<String, Object> config = (Map<String, Object>) spec.get("config");
        if (config != null) {
            builder.config(config);
        }

        return builder.build();
    }

    private String normalizeYaml(String yaml) {
        return yaml.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n+", "\n")
                .replaceAll("(?m)^[ \\t]+-", "    -");
    }
}
