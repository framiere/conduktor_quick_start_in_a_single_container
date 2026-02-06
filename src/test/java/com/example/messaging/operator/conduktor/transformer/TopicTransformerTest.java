package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.ConduktorTopic;
import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TopicTransformer Unit Tests")
class TopicTransformerTest {

    private static final String NAMESPACE = "default";
    private static final String APP_SERVICE = "test-app";

    private CRDStore store;
    private TopicTransformer transformer;

    private static final String DEFAULT_CLUSTER_NAME = "my-cluster";
    private static final String DEFAULT_CLUSTER_ID = "my-vcluster";

    @BeforeEach
    void setUp() {
        store = new CRDStore();
        transformer = new TopicTransformer(store);
        createApplicationService();
        createKafkaCluster(DEFAULT_CLUSTER_NAME, DEFAULT_CLUSTER_ID);
    }

    private void createApplicationService() {
        ApplicationService appService = TestDataBuilder.applicationService()
                .namespace(NAMESPACE)
                .name(APP_SERVICE)
                .appName(APP_SERVICE)
                .build();
        store.create(CRDKind.APPLICATION_SERVICE, NAMESPACE, appService);
    }

    @Test
    @DisplayName("should transform Topic with correct API version")
    void shouldTransformWithCorrectApiVersion() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getApiVersion()).isEqualTo("kafka/v2");
    }

    @Test
    @DisplayName("should transform Topic with correct kind")
    void shouldTransformWithCorrectKind() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getKind()).isEqualTo("Topic");
    }

    @Test
    @DisplayName("should use topic name as metadata name")
    void shouldUseTopicNameAsMetadataName() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("production-topic")
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getMetadata().getName()).isEqualTo("production-topic");
    }

    @Test
    @DisplayName("should resolve cluster using clusterId (not clusterRef metadata.name)")
    void shouldResolveClusterUsingClusterId() {
        createKafkaCluster("production-cluster", "production-vcluster");
        createAndStoreServiceAccount("my-sa", "production-cluster");
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getMetadata().getCluster()).isEqualTo("production-vcluster");
    }

    @Test
    @DisplayName("should copy partitions from source Topic")
    void shouldCopyPartitions() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .partitions(12)
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getSpec().getPartitions()).isEqualTo(12);
    }

    @Test
    @DisplayName("should copy replication factor from source Topic")
    void shouldCopyReplicationFactor() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .replicationFactor(5)
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getSpec().getReplicationFactor()).isEqualTo(5);
    }

    @Test
    @DisplayName("should copy configs from source Topic")
    void shouldCopyConfigs() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .config(Map.of(
                        "retention.ms", "86400000",
                        "cleanup.policy", "delete"))
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getSpec().getConfigs())
                .containsEntry("retention.ms", "86400000")
                .containsEntry("cleanup.policy", "delete");
    }

    @Test
    @DisplayName("should set configs to null when source configs are empty")
    void shouldSetConfigsToNullWhenEmpty() {
        createAndStoreServiceAccount("my-sa", DEFAULT_CLUSTER_NAME);
        Topic source = TestDataBuilder.topic()
                .serviceRef("my-sa")
                .topicName("my-topic")
                .config(Map.of())
                .build();

        ConduktorTopic result = transformer.transform(source);

        assertThat(result.getSpec().getConfigs()).isNull();
    }

    @Test
    @DisplayName("should throw when ServiceAccount not found")
    void shouldThrowWhenServiceAccountNotFound() {
        Topic source = TestDataBuilder.topic()
                .serviceRef("non-existent-sa")
                .topicName("my-topic")
                .build();

        assertThatThrownBy(() -> transformer.transform(source))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ServiceAccount 'non-existent-sa' not found");
    }

    @Test
    @DisplayName("should throw when transformer constructed with null store")
    void shouldThrowWhenStoreIsNull() {
        assertThatThrownBy(() -> new TopicTransformer(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("store must not be null");
    }

    private ServiceAccount createAndStoreServiceAccount(String name, String clusterName) {
        ServiceAccount sa = TestDataBuilder.serviceAccount()
                .name(name)
                .saName(name)
                .clusterRef(clusterName)
                .applicationServiceRef(APP_SERVICE)
                .dn("CN=test")
                .build();
        store.create(CRDKind.SERVICE_ACCOUNT, NAMESPACE, sa);
        return sa;
    }

    private void createKafkaCluster(String clusterName, String clusterId) {
        KafkaCluster cluster = TestDataBuilder.kafkaCluster()
                .namespace(NAMESPACE)
                .name(clusterName)
                .clusterId(clusterId)
                .applicationServiceRef(APP_SERVICE)
                .build();
        store.create(CRDKind.KAFKA_CLUSTER, NAMESPACE, cluster);
    }
}
