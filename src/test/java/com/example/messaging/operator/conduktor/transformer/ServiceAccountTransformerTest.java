package com.example.messaging.operator.conduktor.transformer;

import static org.assertj.core.api.Assertions.*;

import com.example.messaging.operator.conduktor.model.GatewayServiceAccount;
import com.example.messaging.operator.conduktor.model.GatewayServiceAccountSpec.ServiceAccountType;
import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.it.base.TestDataBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ServiceAccountTransformer Unit Tests")
class ServiceAccountTransformerTest {

    private ServiceAccountTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new ServiceAccountTransformer();
    }

    @Test
    @DisplayName("should transform ServiceAccount with correct API version")
    void shouldTransformWithCorrectApiVersion() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("CN=my-user,OU=Users,O=Example")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getApiVersion()).isEqualTo("gateway/v2");
    }

    @Test
    @DisplayName("should transform ServiceAccount with correct kind")
    void shouldTransformWithCorrectKind() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("CN=my-user")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getKind()).isEqualTo("GatewayServiceAccount");
    }

    @Test
    @DisplayName("should use ServiceAccount name as metadata name")
    void shouldUseServiceAccountNameAsMetadataName() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("production-sa")
                .clusterRef("my-cluster")
                .dn("CN=my-user")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getMetadata().getName()).isEqualTo("production-sa");
    }

    @Test
    @DisplayName("should use clusterRef as vCluster in metadata")
    void shouldUseClusterRefAsVCluster() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("production-cluster")
                .dn("CN=my-user")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getMetadata().getVCluster()).isEqualTo("production-cluster");
    }

    @Test
    @DisplayName("should set type to EXTERNAL")
    void shouldSetTypeToExternal() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("CN=my-user")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getType()).isEqualTo(ServiceAccountType.EXTERNAL);
    }

    @Test
    @DisplayName("should extract CN from single DN")
    void shouldExtractCnFromSingleDn() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("CN=demo-admin,OU=Users,O=Example")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getExternalNames())
                .hasSize(1)
                .containsExactly("demo-admin");
    }

    @Test
    @DisplayName("should extract CN from multiple DNs")
    void shouldExtractCnFromMultipleDns() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn(List.of(
                        "CN=user1,OU=Users,O=Example",
                        "CN=user2,OU=Admins,O=Example",
                        "CN=user3,OU=Service,O=Example"))
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getExternalNames())
                .hasSize(3)
                .containsExactly("user1", "user2", "user3");
    }

    @Test
    @DisplayName("should return full DN when CN is not present")
    void shouldReturnFullDnWhenCnNotPresent() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("OU=Users,O=Example")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getExternalNames())
                .hasSize(1)
                .containsExactly("OU=Users,O=Example");
    }

    @Test
    @DisplayName("should handle DN with CN at different positions")
    void shouldHandleCnAtDifferentPositions() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn(List.of(
                        "CN=first-user,OU=Users,O=Example",
                        "OU=Users,CN=middle-user,O=Example",
                        "OU=Users,O=Example,CN=last-user"))
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getExternalNames())
                .hasSize(3)
                .containsExactly("first-user", "middle-user", "last-user");
    }

    @Test
    @DisplayName("should handle simple CN without other attributes")
    void shouldHandleSimpleCn() {
        ServiceAccount source = TestDataBuilder.serviceAccount()
                .saName("my-sa")
                .clusterRef("my-cluster")
                .dn("CN=simple-user")
                .build();

        GatewayServiceAccount result = transformer.transform(source);

        assertThat(result.getSpec().getExternalNames())
                .hasSize(1)
                .containsExactly("simple-user");
    }
}
