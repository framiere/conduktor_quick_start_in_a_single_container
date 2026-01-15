package com.example.messaging.operator.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.messaging.operator.crd.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * E2E tests for multi-tenant isolation. Verifies that tenants cannot access or reference resources belonging to other tenants.
 */
@E2ETest
class MultiTenantE2ETest extends E2ETestBase {

    @BeforeEach
    void setup() {
        cleanupTestResources();

        // Create tenant A hierarchy
        createApplicationService("e2e-tenant-a");
        createVirtualCluster("e2e-vc-a", "e2e-tenant-a");
        createServiceAccount("e2e-sa-a", "e2e-vc-a", "e2e-tenant-a");

        // Create tenant B app
        createApplicationService("e2e-tenant-b");
    }

    @AfterEach
    void cleanup() {
        cleanupTestResources();
    }

    @Test
    @DisplayName("Tenant A can create resources under own ApplicationService")
    void tenantA_canCreateResourcesUnderOwnApplicationService() {
        Topic topic = createTopic("e2e-tenant-a-topic", "e2e-sa-a", "e2e-tenant-a");

        assertThat(resourceExists(Topic.class, "e2e-tenant-a-topic")).isTrue();
    }

    @Test
    @DisplayName("Tenant B cannot reference Tenant A's VirtualCluster")
    void tenantB_cannotReferenceTenantA_virtualCluster() {
        assertRejectedWith(() -> createServiceAccount("e2e-cross-sa", "e2e-vc-a", "e2e-tenant-b"), "does not belong");
    }

    @Test
    @DisplayName("Tenant B cannot reference Tenant A's ServiceAccount")
    void tenantB_cannotReferenceTenantA_serviceAccount() {
        assertRejectedWith(() -> createTopic("e2e-cross-topic", "e2e-sa-a", "e2e-tenant-b"), "does not belong");
    }

    @Test
    @DisplayName("Tenant B can create own isolated resources")
    void tenantB_canCreateOwnResources() {
        // Create tenant B's own hierarchy
        VirtualCluster vcB = createVirtualCluster("e2e-vc-b", "e2e-tenant-b");
        assertThat(resourceExists(VirtualCluster.class, "e2e-vc-b")).isTrue();

        ServiceAccount saB = createServiceAccount("e2e-sa-b", "e2e-vc-b", "e2e-tenant-b");
        assertThat(resourceExists(ServiceAccount.class, "e2e-sa-b")).isTrue();

        Topic topicB = createTopic("e2e-tenant-b-topic", "e2e-sa-b", "e2e-tenant-b");
        assertThat(resourceExists(Topic.class, "e2e-tenant-b-topic")).isTrue();
    }

    @Test
    @DisplayName("Tenants with same-named resources are isolated")
    void tenantsWithSameNamedResources_areIsolated() {
        // Tenant A already has sa-a
        // Create tenant B with similarly-named resources
        createVirtualCluster("e2e-vc-b", "e2e-tenant-b");
        createServiceAccount("e2e-sa-b", "e2e-vc-b", "e2e-tenant-b");

        // Create topics with same logical purpose in each tenant
        createTopic("orders-topic", "e2e-sa-a", "e2e-tenant-a");
        createTopic("orders-topic-b", "e2e-sa-b", "e2e-tenant-b");

        // Both should exist independently
        assertThat(resourceExists(Topic.class, "orders-topic")).isTrue();
        assertThat(resourceExists(Topic.class, "orders-topic-b")).isTrue();
    }

    @Test
    @DisplayName("Cross-tenant ACL reference is rejected")
    void crossTenantAclReference_isRejected() {
        // Create tenant A topic
        createTopic("e2e-acl-topic", "e2e-sa-a", "e2e-tenant-a");

        // Create tenant B hierarchy
        createVirtualCluster("e2e-acl-vc-b", "e2e-tenant-b");
        createServiceAccount("e2e-acl-sa-b", "e2e-acl-vc-b", "e2e-tenant-b");

        // Tenant B cannot create ACL referencing tenant A's ServiceAccount
        assertRejectedWith(() -> {
            ACL acl = new ACL();
            acl.getMetadata().setNamespace(namespace);
            acl.getMetadata().setName("cross-acl");
            acl.getSpec().setServiceRef("e2e-sa-a"); // Tenant A's SA
            acl.getSpec().setApplicationServiceRef("e2e-tenant-b"); // Tenant B
            acl.getSpec().setTopicRef("e2e-acl-topic");
            acl.getSpec().setHost("*");
            acl.getSpec().setPermission(AclCRSpec.AclPermissionTypeForAccessControlEntry.ALLOW);
            k8sClient.resource(acl).create();
        }, "does not belong");
    }
}
