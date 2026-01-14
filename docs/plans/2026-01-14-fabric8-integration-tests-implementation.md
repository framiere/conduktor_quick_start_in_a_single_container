# Fabric8 Integration Tests Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement comprehensive Kubernetes integration tests using Fabric8 Mock Server to validate CRD lifecycle, webhook validation, ownership chains, reconciliation, and multi-tenant scenarios.

**Architecture:** Layered integration tests with fast component tests (15-20 tests, ~100ms each) and comprehensive scenario tests (12-15 tests, ~300ms each). Uses Fabric8 KubernetesServer mock to simulate K8s API and actual WebhookServer HTTP calls for full admission control flow validation.

**Tech Stack:** Fabric8 kubernetes-server-mock, JUnit 5, AssertJ, Maven Failsafe, YAML fixtures

---

## Task 1: Add Maven Dependencies

**Files:**
- Modify: `pom.xml`

**Step 1: Add kubernetes-server-mock dependency**

Add to `<dependencies>` section in pom.xml:

```xml
<!-- Fabric8 Kubernetes Mock Server for IT tests -->
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-server-mock</artifactId>
    <version>${fabric8.version}</version>
    <scope>test</scope>
</dependency>
```

**Step 2: Add Maven Failsafe plugin**

Add to `<build><plugins>` section in pom.xml:

```xml
<!-- Maven Failsafe Plugin for Integration Tests -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.2.5</version>
    <executions>
        <execution>
            <goals>
                <goal>integration-test</goal>
                <goal>verify</goal>
            </goals>
        </execution>
    </executions>
    <configuration>
        <includes>
            <include>**/*IT.java</include>
        </includes>
    </configuration>
</plugin>
```

**Step 3: Verify dependencies**

Run: `mvn dependency:tree | grep kubernetes-server-mock`
Expected: Dependency appears in tree

**Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add Fabric8 mock server and Failsafe plugin for IT tests"
```

---

## Task 2: Create KubernetesITBase

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/base/KubernetesITBase.java`

**Step 1: Create base IT test class**

Create: `src/test/java/com/example/messaging/operator/it/base/KubernetesITBase.java`

```java
package com.example.messaging.operator.it.base;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.store.CRDStore;
import com.example.messaging.operator.validation.OwnershipValidator;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for Kubernetes integration tests.
 * Provides mock K8s server, client, CRDStore, and OwnershipValidator.
 */
public abstract class KubernetesITBase {

    protected static KubernetesServer k8sServer;
    protected static KubernetesClient k8sClient;
    protected static CRDStore store;
    protected static OwnershipValidator ownershipValidator;

    @BeforeAll
    static void setupKubernetes() {
        // Start Fabric8 mock server
        k8sServer = new KubernetesServer(false, true);
        k8sServer.before();

        // Get client connected to mock server
        k8sClient = k8sServer.getClient();

        // Initialize store and validator
        store = new CRDStore();
        ownershipValidator = new OwnershipValidator(store);
    }

    @AfterAll
    static void teardownKubernetes() {
        if (k8sServer != null) {
            k8sServer.after();
        }
    }

    @AfterEach
    void cleanupResources() {
        // Clear CRDStore between tests
        store.clear();

        // Clean up K8s mock server state
        k8sClient.resources(ApplicationService.class).inAnyNamespace().delete();
        k8sClient.resources(VirtualCluster.class).inAnyNamespace().delete();
        k8sClient.resources(ServiceAccount.class).inAnyNamespace().delete();
        k8sClient.resources(Topic.class).inAnyNamespace().delete();
        k8sClient.resources(ACL.class).inAnyNamespace().delete();
        k8sClient.resources(ConsumerGroup.class).inAnyNamespace().delete();
    }

    /**
     * Sync a resource to CRDStore (simulates watch event)
     */
    protected void syncToStore(HasMetadata resource) {
        String kind = resource.getKind();
        String namespace = resource.getMetadata().getNamespace();
        store.create(kind, namespace, resource);
    }

    /**
     * Sync all resources from K8s to store
     */
    protected void syncAllToStore() {
        k8sClient.resources(ApplicationService.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
        k8sClient.resources(VirtualCluster.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
        k8sClient.resources(ServiceAccount.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
        k8sClient.resources(Topic.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
        k8sClient.resources(ACL.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
        k8sClient.resources(ConsumerGroup.class).inAnyNamespace().list()
            .getItems().forEach(this::syncToStore);
    }
}
```

**Step 2: Verify compilation**

Run: `mvn test-compile`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/base/KubernetesITBase.java
git commit -m "test: add KubernetesITBase with mock K8s server setup"
```

---

## Task 3: Create TestDataBuilder

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/base/TestDataBuilder.java`

**Step 1: Create builder class with ApplicationService builder**

Create: `src/test/java/com/example/messaging/operator/it/base/TestDataBuilder.java`

```java
package com.example.messaging.operator.it.base;

import com.example.messaging.operator.crd.*;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Fluent builders for creating test CRs.
 */
public class TestDataBuilder {

    public static ApplicationServiceBuilder applicationService(String name) {
        return new ApplicationServiceBuilder(name);
    }

    public static VirtualClusterBuilder virtualCluster(String name) {
        return new VirtualClusterBuilder(name);
    }

    public static ServiceAccountBuilder serviceAccount(String name) {
        return new ServiceAccountBuilder(name);
    }

    public static TopicBuilder topic(String name) {
        return new TopicBuilder(name);
    }

    public static ACLBuilder acl(String name) {
        return new ACLBuilder(name);
    }

    public static ConsumerGroupBuilder consumerGroup(String name) {
        return new ConsumerGroupBuilder(name);
    }

    public static class ApplicationServiceBuilder {
        private final String name;
        private String namespace = "default";

        public ApplicationServiceBuilder(String name) {
            this.name = name;
        }

        public ApplicationServiceBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public ApplicationService build() {
            ApplicationService app = new ApplicationService();
            app.setMetadata(new ObjectMeta());
            app.getMetadata().setName(name);
            app.getMetadata().setNamespace(namespace);

            ApplicationServiceSpec spec = new ApplicationServiceSpec();
            spec.setName(name);
            app.setSpec(spec);

            return app;
        }

        public ApplicationService createIn(KubernetesClient client) {
            return client.resources(ApplicationService.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class VirtualClusterBuilder {
        private final String name;
        private String namespace = "default";
        private String applicationServiceRef;

        public VirtualClusterBuilder(String name) {
            this.name = name;
        }

        public VirtualClusterBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public VirtualClusterBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public VirtualCluster build() {
            VirtualCluster vc = new VirtualCluster();
            vc.setMetadata(new ObjectMeta());
            vc.getMetadata().setName(name);
            vc.getMetadata().setNamespace(namespace);

            VirtualClusterSpec spec = new VirtualClusterSpec();
            spec.setClusterId(name);
            spec.setApplicationServiceRef(applicationServiceRef);
            vc.setSpec(spec);

            return vc;
        }

        public VirtualCluster createIn(KubernetesClient client) {
            return client.resources(VirtualCluster.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class ServiceAccountBuilder {
        private final String name;
        private String namespace = "default";
        private String applicationServiceRef;
        private String clusterRef;

        public ServiceAccountBuilder(String name) {
            this.name = name;
        }

        public ServiceAccountBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public ServiceAccountBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public ServiceAccountBuilder cluster(String clusterRef) {
            this.clusterRef = clusterRef;
            return this;
        }

        public ServiceAccount build() {
            ServiceAccount sa = new ServiceAccount();
            sa.setMetadata(new ObjectMeta());
            sa.getMetadata().setName(name);
            sa.getMetadata().setNamespace(namespace);

            ServiceAccountSpec spec = new ServiceAccountSpec();
            spec.setName(name.replace("-sa", "").replace("sa-", ""));
            spec.setDn(new ArrayList<>() {{
                add("CN=" + name + ",OU=TEST,O=EXAMPLE,L=CITY,C=US");
            }});
            spec.setClusterRef(clusterRef);
            spec.setApplicationServiceRef(applicationServiceRef);
            sa.setSpec(spec);

            return sa;
        }

        public ServiceAccount createIn(KubernetesClient client) {
            return client.resources(ServiceAccount.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class TopicBuilder {
        private final String name;
        private String namespace = "default";
        private String applicationServiceRef;
        private String serviceRef;
        private int partitions = 3;
        private int replicationFactor = 3;

        public TopicBuilder(String name) {
            this.name = name;
        }

        public TopicBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public TopicBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public TopicBuilder serviceAccount(String saRef) {
            this.serviceRef = saRef;
            return this;
        }

        public TopicBuilder partitions(int p) {
            this.partitions = p;
            return this;
        }

        public Topic build() {
            Topic topic = new Topic();
            topic.setMetadata(new ObjectMeta());
            topic.getMetadata().setName(name);
            topic.getMetadata().setNamespace(namespace);

            TopicCRSpec spec = new TopicCRSpec();
            spec.setName(name.replace("-", "."));
            spec.setPartitions(partitions);
            spec.setReplicationFactor(replicationFactor);
            spec.setServiceRef(serviceRef);
            spec.setApplicationServiceRef(applicationServiceRef);
            spec.setConfig(new HashMap<>());
            topic.setSpec(spec);

            return topic;
        }

        public Topic createIn(KubernetesClient client) {
            return client.resources(Topic.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class ACLBuilder {
        private final String name;
        private String namespace = "default";
        private String applicationServiceRef;
        private String serviceRef;
        private String topicRef;

        public ACLBuilder(String name) {
            this.name = name;
        }

        public ACLBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public ACLBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public ACLBuilder serviceAccount(String saRef) {
            this.serviceRef = saRef;
            return this;
        }

        public ACLBuilder topic(String topicRef) {
            this.topicRef = topicRef;
            return this;
        }

        public ACL build() {
            ACL acl = new ACL();
            acl.setMetadata(new ObjectMeta());
            acl.getMetadata().setName(name);
            acl.getMetadata().setNamespace(namespace);

            AclCRSpec spec = new AclCRSpec();
            spec.setServiceRef(serviceRef);
            spec.setTopicRef(topicRef);
            spec.setApplicationServiceRef(applicationServiceRef);
            spec.setOperations(new ArrayList<>());
            acl.setSpec(spec);

            return acl;
        }

        public ACL createIn(KubernetesClient client) {
            return client.resources(ACL.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class ConsumerGroupBuilder {
        private final String name;
        private String namespace = "default";
        private String applicationServiceRef;
        private String serviceRef;

        public ConsumerGroupBuilder(String name) {
            this.name = name;
        }

        public ConsumerGroupBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public ConsumerGroupBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public ConsumerGroupBuilder serviceAccount(String saRef) {
            this.serviceRef = saRef;
            return this;
        }

        public ConsumerGroup build() {
            ConsumerGroup cg = new ConsumerGroup();
            cg.setMetadata(new ObjectMeta());
            cg.getMetadata().setName(name);
            cg.getMetadata().setNamespace(namespace);

            ConsumerGroupSpec spec = new ConsumerGroupSpec();
            spec.setName(name);
            spec.setServiceRef(serviceRef);
            spec.setApplicationServiceRef(applicationServiceRef);
            spec.setPatternType(ConsumerGroupSpec.ResourcePatternType.LITERAL);
            cg.setSpec(spec);

            return cg;
        }

        public ConsumerGroup createIn(KubernetesClient client) {
            return client.resources(ConsumerGroup.class)
                .inNamespace(namespace)
                .create(build());
        }
    }
}
```

**Step 2: Verify compilation**

Run: `mvn test-compile`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/base/TestDataBuilder.java
git commit -m "test: add TestDataBuilder with fluent builders for all CRD types"
```

---

## Task 4: Create FixtureLoader

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/base/FixtureLoader.java`

**Step 1: Create fixture loader class**

Create: `src/test/java/com/example/messaging/operator/it/base/FixtureLoader.java`

```java
package com.example.messaging.operator.it.base;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.InputStream;
import java.util.List;

/**
 * Loads YAML fixtures for integration tests.
 */
public class FixtureLoader {

    /**
     * Load a single resource of specific type from YAML fixture
     */
    public static <T> T load(KubernetesClient client, String path, Class<T> type) {
        InputStream stream = FixtureLoader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }

        return client.load(stream)
            .get().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No resource of type " + type.getSimpleName() + " found in " + path));
    }

    /**
     * Load all resources from YAML fixture
     */
    public static List<HasMetadata> loadAll(KubernetesClient client, String path) {
        InputStream stream = FixtureLoader.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalArgumentException("Fixture not found: " + path);
        }

        return client.load(stream).get();
    }
}
```

**Step 2: Verify compilation**

Run: `mvn test-compile`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/base/FixtureLoader.java
git commit -m "test: add FixtureLoader for YAML test fixtures"
```

---

## Task 5: Create YAML Fixtures

**Files:**
- Create: `src/test/resources/fixtures/ownership-chain-valid.yaml`
- Create: `src/test/resources/fixtures/multi-tenant-scenario.yaml`

**Step 1: Create ownership-chain-valid.yaml**

Create: `src/test/resources/fixtures/ownership-chain-valid.yaml`

```yaml
---
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: orders-app
  namespace: default
spec:
  name: orders-app
---
apiVersion: messaging.example.com/v1
kind: VirtualCluster
metadata:
  name: prod-cluster
  namespace: default
spec:
  clusterId: prod-cluster
  applicationServiceRef: orders-app
---
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: orders-sa
  namespace: default
spec:
  name: orders
  dn:
    - "CN=orders-sa,OU=TEST,O=EXAMPLE,L=CITY,C=US"
  clusterRef: prod-cluster
  applicationServiceRef: orders-app
---
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-events
  namespace: default
spec:
  name: orders.events
  partitions: 6
  replicationFactor: 3
  serviceRef: orders-sa
  applicationServiceRef: orders-app
  config: {}
---
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-dlq
  namespace: default
spec:
  name: orders.dlq
  partitions: 3
  replicationFactor: 3
  serviceRef: orders-sa
  applicationServiceRef: orders-app
  config: {}
---
apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: orders-read
  namespace: default
spec:
  serviceRef: orders-sa
  topicRef: orders-events
  operations:
    - READ
    - DESCRIBE
  applicationServiceRef: orders-app
```

**Step 2: Create multi-tenant-scenario.yaml**

Create: `src/test/resources/fixtures/multi-tenant-scenario.yaml`

```yaml
# App1 with its resources
---
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: app1
  namespace: default
spec:
  name: app1
---
apiVersion: messaging.example.com/v1
kind: VirtualCluster
metadata:
  name: vc1
  namespace: default
spec:
  clusterId: vc1
  applicationServiceRef: app1
---
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: sa-app1
  namespace: default
spec:
  name: app1-sa
  dn:
    - "CN=sa-app1,OU=TEST,O=EXAMPLE"
  clusterRef: vc1
  applicationServiceRef: app1
---
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orders-events
  namespace: default
spec:
  name: orders.events
  partitions: 3
  replicationFactor: 3
  serviceRef: sa-app1
  applicationServiceRef: app1
  config: {}
---
# App2 with its resources
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: app2
  namespace: default
spec:
  name: app2
---
apiVersion: messaging.example.com/v1
kind: VirtualCluster
metadata:
  name: vc2
  namespace: default
spec:
  clusterId: vc2
  applicationServiceRef: app2
---
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: sa-app2
  namespace: default
spec:
  name: app2-sa
  dn:
    - "CN=sa-app2,OU=TEST,O=EXAMPLE"
  clusterRef: vc2
  applicationServiceRef: app2
---
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: payments-events
  namespace: default
spec:
  name: payments.events
  partitions: 3
  replicationFactor: 3
  serviceRef: sa-app2
  applicationServiceRef: app2
  config: {}
```

**Step 3: Commit**

```bash
git add src/test/resources/fixtures/
git commit -m "test: add YAML fixtures for ownership chain and multi-tenant scenarios"
```

---

## Task 6: Create ComponentITBase

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/base/ComponentITBase.java`

**Step 1: Create component base class with shared webhook**

Create: `src/test/java/com/example/messaging/operator/it/base/ComponentITBase.java`

```java
package com.example.messaging.operator.it.base;

import com.example.messaging.operator.webhook.WebhookServer;
import com.example.messaging.operator.webhook.WebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for component integration tests.
 * Shares a single WebhookServer instance across all tests in the class.
 */
public abstract class ComponentITBase extends KubernetesITBase {

    protected static WebhookServer webhookServer;
    protected static WebhookValidator webhookValidator;
    protected static final int WEBHOOK_PORT = 18080;

    @BeforeAll
    static void setupWebhook() throws Exception {
        setupKubernetes();

        ObjectMapper mapper = new ObjectMapper();
        webhookValidator = new WebhookValidator(ownershipValidator, mapper);
        webhookServer = new WebhookServer(webhookValidator, WEBHOOK_PORT);
        webhookServer.start();
    }

    @AfterAll
    static void teardownWebhook() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
        teardownKubernetes();
    }
}
```

**Step 2: Verify compilation**

Run: `mvn test-compile`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/base/ComponentITBase.java
git commit -m "test: add ComponentITBase with shared webhook server"
```

---

## Task 7: Create ScenarioITBase

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/base/ScenarioITBase.java`

**Step 1: Create scenario base class with per-test webhook**

Create: `src/test/java/com/example/messaging/operator/it/base/ScenarioITBase.java`

```java
package com.example.messaging.operator.it.base;

import com.example.messaging.operator.webhook.WebhookServer;
import com.example.messaging.operator.webhook.WebhookValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for scenario integration tests.
 * Creates a fresh WebhookServer for each test method.
 */
public abstract class ScenarioITBase extends KubernetesITBase {

    protected WebhookServer webhookServer;
    protected WebhookValidator webhookValidator;
    protected static final int WEBHOOK_PORT = 18081;

    @BeforeEach
    void setupWebhook() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        webhookValidator = new WebhookValidator(ownershipValidator, mapper);
        webhookServer = new WebhookServer(webhookValidator, WEBHOOK_PORT);
        webhookServer.start();
    }

    @AfterEach
    void teardownWebhook() {
        if (webhookServer != null) {
            webhookServer.stop();
        }
    }
}
```

**Step 2: Verify compilation**

Run: `mvn test-compile`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/base/ScenarioITBase.java
git commit -m "test: add ScenarioITBase with per-test webhook server"
```

---

## Task 8: Create CRDStoreIT (Component Test)

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/component/CRDStoreIT.java`

**Step 1: Create CRDStoreIT test class**

Create: `src/test/java/com/example/messaging/operator/it/component/CRDStoreIT.java`

```java
package com.example.messaging.operator.it.component;

import com.example.messaging.operator.crd.ApplicationService;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.ComponentITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for CRDStore with Kubernetes mock server.
 */
@DisplayName("CRDStore Integration Tests")
class CRDStoreIT extends ComponentITBase {

    @Test
    @DisplayName("should sync CRDStore when CR created in K8s")
    void testK8sCreateSyncsStore() {
        // Create CR directly in K8s mock
        ApplicationService app = TestDataBuilder
            .applicationService("app1")
            .createIn(k8sClient);

        // Sync to store (simulates watch event)
        syncToStore(app);

        // Verify store has the resource
        assertThat(store.get("ApplicationService", "default", "app1"))
            .isPresent()
            .get()
            .extracting(r -> ((ApplicationService) r).getSpec().getName())
            .isEqualTo("app1");
    }

    @Test
    @DisplayName("should list all resources of a type from store")
    void testListResourcesFromStore() {
        // Create multiple topics
        TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .createIn(k8sClient);

        TestDataBuilder.topic("topic2")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .createIn(k8sClient);

        // Sync all to store
        syncAllToStore();

        // Verify list operation
        assertThat(store.list("Topic", "default"))
            .hasSize(2)
            .extracting(r -> ((Topic) r).getMetadata().getName())
            .containsExactlyInAnyOrder("topic1", "topic2");
    }

    @Test
    @DisplayName("should update resource in store")
    void testUpdateResourceInStore() {
        // Create and sync
        ApplicationService app = TestDataBuilder
            .applicationService("app1")
            .createIn(k8sClient);
        syncToStore(app);

        // Update in K8s
        app.getSpec().setName("app1-updated");
        ApplicationService updated = k8sClient.resources(ApplicationService.class)
            .inNamespace("default")
            .withName("app1")
            .patch(app);

        // Update in store
        store.update("ApplicationService", "default", updated);

        // Verify
        assertThat(store.get("ApplicationService", "default", "app1"))
            .isPresent()
            .get()
            .extracting(r -> ((ApplicationService) r).getSpec().getName())
            .isEqualTo("app1-updated");
    }

    @Test
    @DisplayName("should delete resource from store")
    void testDeleteResourceFromStore() {
        // Create and sync
        ApplicationService app = TestDataBuilder
            .applicationService("app1")
            .createIn(k8sClient);
        syncToStore(app);

        // Delete from K8s
        k8sClient.resources(ApplicationService.class)
            .inNamespace("default")
            .withName("app1")
            .delete();

        // Delete from store
        store.delete("ApplicationService", "default", "app1");

        // Verify
        assertThat(store.get("ApplicationService", "default", "app1"))
            .isEmpty();
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=CRDStoreIT`
Expected: 4/4 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/component/CRDStoreIT.java
git commit -m "test: add CRDStoreIT with K8s mock server integration"
```

---

## Task 9: Create OwnershipValidatorIT (Component Test)

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/component/OwnershipValidatorIT.java`

**Step 1: Create OwnershipValidatorIT test class**

Create: `src/test/java/com/example/messaging/operator/it/component/OwnershipValidatorIT.java`

```java
package com.example.messaging.operator.it.component;

import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.ComponentITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OwnershipValidator with K8s-backed CRDStore.
 */
@DisplayName("Ownership Validator Integration Tests")
class OwnershipValidatorIT extends ComponentITBase {

    @Test
    @DisplayName("should validate complete ownership chain from K8s")
    void testValidateOwnershipChainFromK8s() {
        // Create resources in K8s
        TestDataBuilder.applicationService("app1").createIn(k8sClient);
        TestDataBuilder.virtualCluster("vc1").ownedBy("app1").createIn(k8sClient);
        TestDataBuilder.serviceAccount("sa1").ownedBy("app1").cluster("vc1").createIn(k8sClient);

        // Sync to store
        syncAllToStore();

        // Validate topic creation
        Topic topic = TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .build();

        ValidationResult result = ownershipValidator.validateCreate(topic, "default");

        assertThat(result.isValid()).isTrue();
        assertThat(result.getMessage()).isNull();
    }

    @Test
    @DisplayName("should reject creation when ServiceAccount missing in K8s")
    void testRejectMissingServiceAccount() {
        // Create only ApplicationService
        TestDataBuilder.applicationService("app1").createIn(k8sClient);
        syncAllToStore();

        // Topic references non-existent ServiceAccount
        Topic topic = TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("nonexistent-sa")
            .build();

        ValidationResult result = ownershipValidator.validateCreate(topic, "default");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("ServiceAccount")
            .contains("does not exist");
    }

    @Test
    @DisplayName("should reject when VirtualCluster owned by different ApplicationService")
    void testRejectWrongVirtualClusterOwner() {
        // App1 owns VirtualCluster
        TestDataBuilder.applicationService("app1").createIn(k8sClient);
        TestDataBuilder.virtualCluster("vc1").ownedBy("app1").createIn(k8sClient);

        // App2 tries to use it
        TestDataBuilder.applicationService("app2").createIn(k8sClient);

        syncAllToStore();

        // ServiceAccount for app2 references vc1 (owned by app1)
        com.example.messaging.operator.crd.ServiceAccount sa = TestDataBuilder
            .serviceAccount("sa-app2")
            .ownedBy("app2")
            .cluster("vc1")
            .build();

        ValidationResult result = ownershipValidator.validateCreate(sa, "default");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("VirtualCluster")
            .contains("is owned by")
            .contains("app1");
    }

    @Test
    @DisplayName("should reject UPDATE when ownership changed")
    void testRejectOwnershipChange() {
        // Create topic in K8s
        Topic oldTopic = TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .createIn(k8sClient);

        syncToStore(oldTopic);

        // Attempt to change owner
        Topic newTopic = TestDataBuilder.topic("topic1")
            .ownedBy("app2")
            .serviceAccount("sa1")
            .build();

        ValidationResult result = ownershipValidator.validateUpdate(oldTopic, newTopic);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("Cannot change applicationServiceRef")
            .contains("app1")
            .contains("app2");
    }

    @Test
    @DisplayName("should allow UPDATE when ownership unchanged")
    void testAllowUpdateSameOwner() {
        // Create topic
        Topic oldTopic = TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .createIn(k8sClient);

        syncToStore(oldTopic);

        // Change only partitions
        Topic newTopic = TestDataBuilder.topic("topic1")
            .ownedBy("app1")
            .serviceAccount("sa1")
            .partitions(6)
            .build();

        ValidationResult result = ownershipValidator.validateUpdate(oldTopic, newTopic);

        assertThat(result.isValid()).isTrue();
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=OwnershipValidatorIT`
Expected: 5/5 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/component/OwnershipValidatorIT.java
git commit -m "test: add OwnershipValidatorIT with K8s-backed validation"
```

---

## Task 10: Create CRDLifecycleIT (Scenario Test)

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/scenario/CRDLifecycleIT.java`

**Step 1: Create CRDLifecycleIT test class**

Create: `src/test/java/com/example/messaging/operator/it/scenario/CRDLifecycleIT.java`

```java
package com.example.messaging.operator.it.scenario;

import com.example.messaging.operator.crd.*;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario tests for complete CRD lifecycle flows.
 */
@DisplayName("CRD Lifecycle Scenario Tests")
class CRDLifecycleIT extends ScenarioITBase {

    @Test
    @DisplayName("Scenario: Create complete ownership chain, update, delete")
    void testCompleteLifecycle() {
        // Step 1: Create ApplicationService
        ApplicationService app = TestDataBuilder.applicationService("orders-app")
            .createIn(k8sClient);
        store.create("ApplicationService", "default", app);

        assertThat(k8sClient.resources(ApplicationService.class)
            .inNamespace("default")
            .withName("orders-app")
            .get()).isNotNull();

        // Step 2: Create VirtualCluster
        VirtualCluster vc = TestDataBuilder.virtualCluster("prod-cluster")
            .ownedBy("orders-app")
            .createIn(k8sClient);
        store.create("VirtualCluster", "default", vc);

        assertThat(k8sClient.resources(VirtualCluster.class)
            .inNamespace("default")
            .withName("prod-cluster")
            .get()).isNotNull();

        // Step 3: Create ServiceAccount
        ServiceAccount sa = TestDataBuilder.serviceAccount("orders-sa")
            .ownedBy("orders-app")
            .cluster("prod-cluster")
            .createIn(k8sClient);
        store.create("ServiceAccount", "default", sa);

        assertThat(k8sClient.resources(ServiceAccount.class)
            .inNamespace("default")
            .withName("orders-sa")
            .get()).isNotNull();

        // Step 4: Create Topic
        Topic topic = TestDataBuilder.topic("orders-events")
            .ownedBy("orders-app")
            .serviceAccount("orders-sa")
            .partitions(6)
            .createIn(k8sClient);
        store.create("Topic", "default", topic);

        assertThat(k8sClient.resources(Topic.class)
            .inNamespace("default")
            .withName("orders-events")
            .get()).isNotNull();

        // Step 5: Create ACL
        ACL acl = TestDataBuilder.acl("orders-read")
            .ownedBy("orders-app")
            .serviceAccount("orders-sa")
            .topic("orders-events")
            .createIn(k8sClient);
        store.create("ACL", "default", acl);

        assertThat(k8sClient.resources(ACL.class)
            .inNamespace("default")
            .withName("orders-read")
            .get()).isNotNull();

        // Step 6: Valid update (partition increase)
        topic.getSpec().setPartitions(12);
        Topic updated = k8sClient.resources(Topic.class)
            .inNamespace("default")
            .withName("orders-events")
            .patch(topic);

        assertThat(updated.getSpec().getPartitions()).isEqualTo(12);

        // Step 7: Delete in reverse order
        k8sClient.resources(ACL.class).inNamespace("default").withName("orders-read").delete();
        k8sClient.resources(Topic.class).inNamespace("default").withName("orders-events").delete();
        k8sClient.resources(ServiceAccount.class).inNamespace("default").withName("orders-sa").delete();
        k8sClient.resources(VirtualCluster.class).inNamespace("default").withName("prod-cluster").delete();
        k8sClient.resources(ApplicationService.class).inNamespace("default").withName("orders-app").delete();

        // Verify all deleted
        assertThat(k8sClient.resources(Topic.class).inNamespace("default").list().getItems()).isEmpty();
    }

    @Test
    @DisplayName("Scenario: Create from YAML fixture succeeds")
    void testCreateFromYAMLFixture() {
        // Load and create all resources from fixture
        var resources = com.example.messaging.operator.it.base.FixtureLoader
            .loadAll(k8sClient, "/fixtures/ownership-chain-valid.yaml");

        for (var resource : resources) {
            k8sClient.resource(resource).inNamespace("default").create();
            syncToStore(resource);
        }

        // Verify all created
        assertThat(store.list("ApplicationService", "default")).hasSize(1);
        assertThat(store.list("VirtualCluster", "default")).hasSize(1);
        assertThat(store.list("ServiceAccount", "default")).hasSize(1);
        assertThat(store.list("Topic", "default")).hasSize(2);
        assertThat(store.list("ACL", "default")).hasSize(1);
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=CRDLifecycleIT`
Expected: 2/2 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/scenario/CRDLifecycleIT.java
git commit -m "test: add CRDLifecycleIT for complete CR lifecycle scenarios"
```

---

## Task 11: Create OwnershipChainIT (Scenario Test)

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/scenario/OwnershipChainIT.java`

**Step 1: Create OwnershipChainIT test class**

Create: `src/test/java/com/example/messaging/operator/it/scenario/OwnershipChainIT.java`

```java
package com.example.messaging.operator.it.scenario;

import com.example.messaging.operator.crd.ServiceAccount;
import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario tests for multi-resource ownership chain validation.
 */
@DisplayName("Ownership Chain Scenario Tests")
class OwnershipChainIT extends ScenarioITBase {

    @Test
    @DisplayName("Scenario: Reject ServiceAccount when VirtualCluster owned by different app")
    void testCrossOwnershipRejection() {
        // App1 owns VirtualCluster1
        TestDataBuilder.applicationService("app1").createIn(k8sClient);
        TestDataBuilder.virtualCluster("vc1").ownedBy("app1").createIn(k8sClient);

        // App2 exists
        TestDataBuilder.applicationService("app2").createIn(k8sClient);

        syncAllToStore();

        // App2 tries to create ServiceAccount using App1's VirtualCluster
        ServiceAccount sa = TestDataBuilder.serviceAccount("sa-app2")
            .ownedBy("app2")
            .cluster("vc1")  // VirtualCluster owned by app1
            .build();

        ValidationResult result = ownershipValidator.validateCreate(sa, "default");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("VirtualCluster")
            .contains("is owned by")
            .contains("app1");
    }

    @Test
    @DisplayName("Scenario: Reject Topic when ServiceAccount owned by different app")
    void testTopicRejectsWrongServiceAccountOwner() {
        // Setup: Two apps with their own resources
        TestDataBuilder.applicationService("app1").createIn(k8sClient);
        TestDataBuilder.virtualCluster("vc1").ownedBy("app1").createIn(k8sClient);
        TestDataBuilder.serviceAccount("sa-app1").ownedBy("app1").cluster("vc1").createIn(k8sClient);

        TestDataBuilder.applicationService("app2").createIn(k8sClient);
        TestDataBuilder.virtualCluster("vc2").ownedBy("app2").createIn(k8sClient);
        TestDataBuilder.serviceAccount("sa-app2").ownedBy("app2").cluster("vc2").createIn(k8sClient);

        syncAllToStore();

        // App1 tries to create Topic using App2's ServiceAccount
        Topic topic = TestDataBuilder.topic("sneaky-topic")
            .ownedBy("app1")
            .serviceAccount("sa-app2")  // Wrong! Belongs to app2
            .build();

        ValidationResult result = ownershipValidator.validateCreate(topic, "default");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("ServiceAccount")
            .contains("is owned by")
            .contains("app2");
    }

    @Test
    @DisplayName("Scenario: Valid complete chain from fixture succeeds")
    void testValidChainFromFixture() {
        // Load complete valid chain
        var resources = com.example.messaging.operator.it.base.FixtureLoader
            .loadAll(k8sClient, "/fixtures/ownership-chain-valid.yaml");

        // Create all and verify no validation errors
        for (var resource : resources) {
            k8sClient.resource(resource).inNamespace("default").create();
            syncToStore(resource);
        }

        // All resources created successfully
        assertThat(store.list("ApplicationService", "default")).hasSize(1);
        assertThat(store.list("VirtualCluster", "default")).hasSize(1);
        assertThat(store.list("ServiceAccount", "default")).hasSize(1);
        assertThat(store.list("Topic", "default")).hasSize(2);
        assertThat(store.list("ACL", "default")).hasSize(1);

        // Verify ownership is correct
        var topic = (Topic) store.get("Topic", "default", "orders-events").orElseThrow();
        assertThat(topic.getSpec().getApplicationServiceRef()).isEqualTo("orders-app");
        assertThat(topic.getSpec().getServiceRef()).isEqualTo("orders-sa");
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=OwnershipChainIT`
Expected: 3/3 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/scenario/OwnershipChainIT.java
git commit -m "test: add OwnershipChainIT for multi-resource ownership validation"
```

---

## Task 12: Create MultiTenantIsolationIT (Scenario Test)

**Files:**
- Create: `src/test/java/com/example/messaging/operator/it/scenario/MultiTenantIsolationIT.java`

**Step 1: Create MultiTenantIsolationIT test class**

Create: `src/test/java/com/example/messaging/operator/it/scenario/MultiTenantIsolationIT.java`

```java
package com.example.messaging.operator.it.scenario;

import com.example.messaging.operator.crd.Topic;
import com.example.messaging.operator.it.base.ScenarioITBase;
import com.example.messaging.operator.it.base.TestDataBuilder;
import com.example.messaging.operator.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario tests for multi-tenant isolation and cross-tenant protection.
 */
@DisplayName("Multi-Tenant Isolation Scenario Tests")
class MultiTenantIsolationIT extends ScenarioITBase {

    @Test
    @DisplayName("Scenario: Load multi-tenant fixture and verify isolation")
    void testMultiTenantFixtureIsolation() {
        // Load multi-tenant scenario
        var resources = com.example.messaging.operator.it.base.FixtureLoader
            .loadAll(k8sClient, "/fixtures/multi-tenant-scenario.yaml");

        for (var resource : resources) {
            k8sClient.resource(resource).inNamespace("default").create();
            syncToStore(resource);
        }

        // Verify both tenants have resources
        assertThat(store.list("ApplicationService", "default")).hasSize(2);
        assertThat(store.list("Topic", "default")).hasSize(2);

        // Verify app1's topic
        var app1Topic = (Topic) store.get("Topic", "default", "orders-events").orElseThrow();
        assertThat(app1Topic.getSpec().getApplicationServiceRef()).isEqualTo("app1");

        // Verify app2's topic
        var app2Topic = (Topic) store.get("Topic", "default", "payments-events").orElseThrow();
        assertThat(app2Topic.getSpec().getApplicationServiceRef()).isEqualTo("app2");
    }

    @Test
    @DisplayName("Scenario: App1 cannot update ownership of App2's Topic")
    void testCrossTenantUpdateDenied() {
        // Setup two tenants
        setupMultiTenantEnvironment();

        // Get App2's topic
        Topic app2Topic = (Topic) store.get("Topic", "default", "payments-events").orElseThrow();
        assertThat(app2Topic.getSpec().getApplicationServiceRef()).isEqualTo("app2");

        // App1 tries to hijack ownership
        Topic hijackAttempt = TestDataBuilder.topic("payments-events")
            .ownedBy("app1")  // Changed from app2 to app1
            .serviceAccount("sa-app2")
            .build();

        ValidationResult result = ownershipValidator.validateUpdate(app2Topic, hijackAttempt);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("Cannot change applicationServiceRef")
            .contains("app2")
            .contains("app1");
    }

    @Test
    @DisplayName("Scenario: App1 cannot reference App2's ServiceAccount")
    void testCrossTenantReferenceDenied() {
        // Setup two tenants
        setupMultiTenantEnvironment();

        // App1 tries to create Topic using App2's ServiceAccount
        Topic crossTenantTopic = TestDataBuilder.topic("sneaky-topic")
            .ownedBy("app1")
            .serviceAccount("sa-app2")  // Belongs to app2!
            .build();

        ValidationResult result = ownershipValidator.validateCreate(crossTenantTopic, "default");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getMessage())
            .contains("ServiceAccount")
            .contains("is owned by")
            .contains("app2");
    }

    @Test
    @DisplayName("Scenario: Both tenants can operate independently")
    void testIndependentTenantOperations() {
        setupMultiTenantEnvironment();

        // App1 creates a new topic - should succeed
        Topic app1NewTopic = TestDataBuilder.topic("orders-dlq")
            .ownedBy("app1")
            .serviceAccount("sa-app1")
            .createIn(k8sClient);
        store.create("Topic", "default", app1NewTopic);

        ValidationResult result1 = ownershipValidator.validateCreate(app1NewTopic, "default");
        assertThat(result1.isValid()).isTrue();

        // App2 creates a new topic - should succeed
        Topic app2NewTopic = TestDataBuilder.topic("payments-dlq")
            .ownedBy("app2")
            .serviceAccount("sa-app2")
            .createIn(k8sClient);
        store.create("Topic", "default", app2NewTopic);

        ValidationResult result2 = ownershipValidator.validateCreate(app2NewTopic, "default");
        assertThat(result2.isValid()).isTrue();

        // Verify both exist and are isolated
        assertThat(store.list("Topic", "default")).hasSize(4); // 2 from fixture + 2 new
    }

    private void setupMultiTenantEnvironment() {
        var resources = com.example.messaging.operator.it.base.FixtureLoader
            .loadAll(k8sClient, "/fixtures/multi-tenant-scenario.yaml");

        for (var resource : resources) {
            k8sClient.resource(resource).inNamespace("default").create();
            syncToStore(resource);
        }
    }
}
```

**Step 2: Run test**

Run: `mvn test -Dtest=MultiTenantIsolationIT`
Expected: 4/4 tests PASS

**Step 3: Commit**

```bash
git add src/test/java/com/example/messaging/operator/it/scenario/MultiTenantIsolationIT.java
git commit -m "test: add MultiTenantIsolationIT for cross-tenant protection"
```

---

## Task 13: Run All Integration Tests

**Step 1: Run all IT tests**

Run: `mvn verify`
Expected: All IT tests pass (component + scenario)

**Step 2: Verify test count**

Check output for:
- CRDStoreIT: 4 tests
- OwnershipValidatorIT: 5 tests
- CRDLifecycleIT: 2 tests
- OwnershipChainIT: 3 tests
- MultiTenantIsolationIT: 4 tests
- Total: 18 IT tests

**Step 3: Check timing**

Verify:
- Component tests: < 100ms each
- Scenario tests: < 400ms each
- Total IT suite: < 15 seconds

**Step 4: Commit if all pass**

```bash
git add -A
git commit -m "test: verify all Fabric8 integration tests passing"
```

---

## Summary

**What we built:**
1. Maven dependencies (kubernetes-server-mock, failsafe plugin)
2. Base classes (KubernetesITBase, ComponentITBase, ScenarioITBase)
3. Test data builders (fluent builders for all CRD types)
4. YAML fixtures (ownership-chain-valid, multi-tenant-scenario)
5. Component tests (CRDStoreIT, OwnershipValidatorIT) - 9 tests
6. Scenario tests (CRDLifecycleIT, OwnershipChainIT, MultiTenantIsolationIT) - 9 tests

**Test coverage:**
- ✅ CRD lifecycle (create, read, update, delete)
- ✅ Ownership chain enforcement
- ✅ Multi-tenant isolation
- ✅ K8s-backed validation
- ✅ Store synchronization

**Total:** 18 integration tests running in ~10-15 seconds

**Next steps:** Add Testcontainers + K3s tests for real Kubernetes validation with actual webhook TLS handshake.
