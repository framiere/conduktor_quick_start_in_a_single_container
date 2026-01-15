package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.VirtualCluster;
import com.example.messaging.operator.crd.KafkaCluster;
import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KafkaClusterTransformer Unit Tests")
class KafkaClusterTransformerTest {

    private KafkaClusterTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new KafkaClusterTransformer();
    }

    @Test
    @DisplayName("should transform KafkaCluster to VirtualCluster with correct API version")
    void shouldTransformWithCorrectApiVersion() {
        KafkaCluster source = TestDataBuilder.kafkaCluster()
                .clusterId("my-cluster")
                .build();

        VirtualCluster result = transformer.transform(source);

        assertThat(result.getApiVersion()).isEqualTo("gateway/v2");
    }

    @Test
    @DisplayName("should transform KafkaCluster to VirtualCluster with correct kind")
    void shouldTransformWithCorrectKind() {
        KafkaCluster source = TestDataBuilder.kafkaCluster()
                .clusterId("my-cluster")
                .build();

        VirtualCluster result = transformer.transform(source);

        assertThat(result.getKind()).isEqualTo("VirtualCluster");
    }

    @Test
    @DisplayName("should use clusterId as metadata name")
    void shouldUseClusterIdAsMetadataName() {
        KafkaCluster source = TestDataBuilder.kafkaCluster()
                .clusterId("production-cluster")
                .build();

        VirtualCluster result = transformer.transform(source);

        assertThat(result.getMetadata().getName()).isEqualTo("production-cluster");
    }

    @Test
    @DisplayName("should set aclEnabled to true by default")
    void shouldSetAclEnabledToTrue() {
        KafkaCluster source = TestDataBuilder.kafkaCluster()
                .clusterId("my-cluster")
                .build();

        VirtualCluster result = transformer.transform(source);

        assertThat(result.getSpec().getAclEnabled()).isTrue();
    }

    @Test
    @DisplayName("should handle special characters in clusterId")
    void shouldHandleSpecialCharactersInClusterId() {
        KafkaCluster source = TestDataBuilder.kafkaCluster()
                .clusterId("my-cluster_with-special.chars")
                .build();

        VirtualCluster result = transformer.transform(source);

        assertThat(result.getMetadata().getName()).isEqualTo("my-cluster_with-special.chars");
    }
}
