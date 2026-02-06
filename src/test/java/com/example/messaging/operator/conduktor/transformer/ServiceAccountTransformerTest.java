package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.GatewayServiceAccount;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec.ServiceAccountType;
import com.example.messaging.operator.crd.AuthType;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.store.CRDKind;
import com.example.messaging.operator.store.CRDStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceAccountTransformer Unit Tests")
class ServiceAccountTransformerTest {

    private static final String NAMESPACE = "default";

    private CRDStore store;
    private ServiceAccountTransformer transformer;

    @BeforeEach
    void setUp() {
        store = new CRDStore();
        transformer = new ServiceAccountTransformer(store);

        // Default MTLS cluster for most tests
        store.create(CRDKind.APPLICATION_SERVICE, NAMESPACE,
                TestDataBuilder.applicationService().namespace(NAMESPACE).name("test-app").build());
        store.create(CRDKind.KAFKA_CLUSTER, NAMESPACE,
                TestDataBuilder.kafkaCluster()
                        .namespace(NAMESPACE).name("my-cluster").clusterId("my-cluster")
                        .applicationServiceRef("test-app").authType(AuthType.MTLS).build());
    }

    @Nested
    @DisplayName("MTLS with DN present (current behavior)")
    class MtlsWithDn {

        @Test
        @DisplayName("should transform ServiceAccount with correct API version")
        void shouldTransformWithCorrectApiVersion() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=my-user,OU=Users,O=Example").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getApiVersion()).isEqualTo("gateway/v2");
        }

        @Test
        @DisplayName("should transform ServiceAccount with correct kind")
        void shouldTransformWithCorrectKind() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=my-user").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getKind()).isEqualTo("GatewayServiceAccount");
        }

        @Test
        @DisplayName("should use ServiceAccount name as metadata name")
        void shouldUseServiceAccountNameAsMetadataName() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("production-sa").clusterRef("my-cluster")
                    .dn("CN=my-user").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getMetadata().getName()).isEqualTo("production-sa");
        }

        @Test
        @DisplayName("should use clusterRef as vCluster in metadata")
        void shouldUseClusterRefAsVCluster() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=my-user").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getMetadata().getVCluster()).isEqualTo("my-cluster");
        }

        @Test
        @DisplayName("should set type to EXTERNAL")
        void shouldSetTypeToExternal() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=my-user").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getType()).isEqualTo(ServiceAccountType.EXTERNAL);
        }

        @Test
        @DisplayName("should extract CN from single DN")
        void shouldExtractCnFromSingleDn() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=demo-admin,OU=Users,O=Example").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("demo-admin");
        }

        @Test
        @DisplayName("should extract CN from multiple DNs")
        void shouldExtractCnFromMultipleDns() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn(List.of(
                            "CN=user1,OU=Users,O=Example",
                            "CN=user2,OU=Admins,O=Example",
                            "CN=user3,OU=Service,O=Example"))
                    .build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("user1", "user2", "user3");
        }

        @Test
        @DisplayName("should return full DN when CN is not present")
        void shouldReturnFullDnWhenCnNotPresent() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("OU=Users,O=Example").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("OU=Users,O=Example");
        }

        @Test
        @DisplayName("should handle DN with CN at different positions")
        void shouldHandleCnAtDifferentPositions() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn(List.of(
                            "CN=first-user,OU=Users,O=Example",
                            "OU=Users,CN=middle-user,O=Example",
                            "OU=Users,O=Example,CN=last-user"))
                    .build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("first-user", "middle-user", "last-user");
        }

        @Test
        @DisplayName("should handle simple CN without other attributes")
        void shouldHandleSimpleCn() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("my-sa").clusterRef("my-cluster")
                    .dn("CN=simple-user").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("simple-user");
        }
    }

    @Nested
    @DisplayName("MTLS without DN")
    class MtlsWithoutDn {

        @Test
        @DisplayName("should use service account name when DN is empty")
        void shouldUseNameWhenDnIsEmpty() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("auto-sa").clusterRef("my-cluster").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("auto-sa");
        }
    }

    @Nested
    @DisplayName("SASL_SSL authentication")
    class SaslSsl {

        @BeforeEach
        void setUp() {
            store.create(CRDKind.APPLICATION_SERVICE, "sasl-ns",
                    TestDataBuilder.applicationService().namespace("sasl-ns").name("sasl-app").build());
            store.create(CRDKind.KAFKA_CLUSTER, "sasl-ns",
                    TestDataBuilder.kafkaCluster()
                            .namespace("sasl-ns").name("sasl-cluster").clusterId("sasl-cluster")
                            .applicationServiceRef("sasl-app").authType(AuthType.SASL_SSL).build());
        }

        @Test
        @DisplayName("should use service account name as externalNames")
        void shouldUseNameAsExternalNames() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace("sasl-ns").saName("sasl-user").clusterRef("sasl-cluster")
                    .applicationServiceRef("sasl-app").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("sasl-user");
            assertThat(result.getSpec().getType()).isEqualTo(ServiceAccountType.EXTERNAL);
        }

        @Test
        @DisplayName("should ignore DN even when specified")
        void shouldIgnoreDnWhenSaslSsl() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace("sasl-ns").saName("sasl-user").clusterRef("sasl-cluster")
                    .applicationServiceRef("sasl-app")
                    .dn("CN=should-be-ignored,OU=Test,O=Org").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("sasl-user");
        }

        @Test
        @DisplayName("should use service account name when DN is absent")
        void shouldUseNameWhenNoDn() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace("sasl-ns").saName("sasl-no-dn").clusterRef("sasl-cluster")
                    .applicationServiceRef("sasl-app").build();

            GatewayServiceAccount result = transformer.transform(source);

            assertThat(result.getSpec().getExternalNames())
                    .containsExactly("sasl-no-dn");
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should throw when KafkaCluster not found in store")
        void shouldThrowWhenClusterNotFound() {
            ServiceAccount source = TestDataBuilder.serviceAccount()
                    .namespace(NAMESPACE).saName("orphan-sa").clusterRef("nonexistent-cluster").build();

            assertThatThrownBy(() -> transformer.transform(source))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("KafkaCluster 'nonexistent-cluster' not found");
        }
    }
}
