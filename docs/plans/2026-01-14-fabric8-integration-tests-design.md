# Fabric8 Integration Tests Design

**Date:** 2026-01-14
**Goal:** Implement comprehensive Kubernetes integration tests using Fabric8 Mock Server to test CRD lifecycle, webhook validation, ownership chains, reconciliation, and multi-tenant scenarios.

---

## Overview

Add a layered integration test suite that validates the operator's behavior in a Kubernetes environment:
- **Component tests** - Fast tests for individual components with K8s backing (~50-100ms each)
- **Scenario tests** - Complete end-to-end flows simulating real usage (~200-400ms each)

**Scope:** Comprehensive coverage of ownership validation, CR lifecycle, and error scenarios.

---

## Architecture

### Test Structure

```
src/test/java/com/example/messaging/operator/it/
├── base/
│   ├── KubernetesITBase.java           # Base class with K8s mock server setup
│   ├── ComponentITBase.java            # Shared webhook server (@BeforeAll)
│   ├── ScenarioITBase.java             # Fresh webhook server (@BeforeEach)
│   └── TestDataBuilder.java            # Fluent builders for CRs
├── component/                           # Fast component tests (~100ms each)
│   ├── CRDStoreIT.java                 # CRDStore CRUD operations
│   ├── OwnershipValidatorIT.java       # Ownership validation rules
│   └── WebhookValidatorIT.java         # Webhook validation logic
├── scenario/                            # Realistic end-to-end tests (~300ms each)
│   ├── CRDLifecycleIT.java             # Full CR lifecycle flows
│   ├── OwnershipChainIT.java           # Multi-resource ownership
│   ├── MultiTenantIsolationIT.java     # Cross-tenant protection
│   └── ReconciliationIT.java           # Reconciliation events
└── fixtures/                            # YAML test data
    ├── app-service-basic.yaml
    ├── ownership-chain-valid.yaml
    └── multi-tenant-scenario.yaml
```

src/test/resources/fixtures/ contains YAML fixtures for complex scenarios.

### Key Dependencies

- `kubernetes-server-mock` (Fabric8 mock server) - new dependency to add
- `kubernetes-client` (already present)
- `okhttp3` (already present)
- `jackson` (already present)

---

## Base Classes

### KubernetesITBase.java

Foundation for all IT tests:

```java
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

        // Register all CRDs with mock server
        registerCRDs();

        // Initialize store and validator
        store = new CRDStore();
        ownershipValidator = new OwnershipValidator(store);
    }

    @AfterAll
    static void teardownKubernetes() {
        k8sServer.after();
    }

    @BeforeEach
    void cleanupResources() {
        // Clear CRDStore between tests
        store.clear();

        // Clean up K8s mock server state
        k8sClient.resources(Topic.class).inAnyNamespace().delete();
        // ... delete all CRD types
    }
}
```

### ComponentITBase

Extends `KubernetesITBase`. Starts one `WebhookServer` in `@BeforeAll`, configures K8s mock to call webhook on UPDATE/DELETE. Used for fast component tests.

### ScenarioITBase

Extends `KubernetesITBase`. Starts `WebhookServer` in `@BeforeEach` for complete isolation between scenarios. Used for end-to-end tests.

---

## Test Data Management

### TestDataBuilder.java - Fluent Builders

```java
public class TestDataBuilder {

    public static ApplicationServiceBuilder applicationService(String name) {
        return new ApplicationServiceBuilder(name);
    }

    public static TopicBuilder topic(String name) {
        return new TopicBuilder(name);
    }

    // Similar for VirtualCluster, ServiceAccount, ACL, ConsumerGroup

    public static class ApplicationServiceBuilder {
        private String name;
        private String namespace = "default";

        public ApplicationServiceBuilder namespace(String ns) {
            this.namespace = ns;
            return this;
        }

        public ApplicationService build() {
            ApplicationService app = new ApplicationService();
            app.setMetadata(new ObjectMeta());
            app.getMetadata().setName(name);
            app.getMetadata().setNamespace(namespace);
            // ... set spec
            return app;
        }

        public ApplicationService createIn(KubernetesClient client) {
            return client.resources(ApplicationService.class)
                .inNamespace(namespace)
                .create(build());
        }
    }

    public static class TopicBuilder {
        private String name;
        private String namespace = "default";
        private String applicationServiceRef;
        private String serviceRef;
        private int partitions = 3;

        public TopicBuilder ownedBy(String appServiceRef) {
            this.applicationServiceRef = appServiceRef;
            return this;
        }

        public TopicBuilder serviceAccount(String saRef) {
            this.serviceRef = saRef;
            return this;
        }

        // build() and createIn() methods
    }
}
```

### YAML Fixtures

**ownership-chain-valid.yaml** - Complete valid chain (ApplicationService → VirtualCluster → ServiceAccount → Topic → ACL)

**multi-tenant-scenario.yaml** - Two ApplicationServices (app1, app2) with completely isolated resources

**Fixture Loader:**

```java
public class FixtureLoader {
    public static <T> T load(String path, Class<T> type) {
        return k8sClient.load(FixtureLoader.class.getResourceAsStream(path))
            .get().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .findFirst()
            .orElseThrow();
    }

    public static List<HasMetadata> loadAll(String path) {
        return k8sClient.load(FixtureLoader.class.getResourceAsStream(path)).get();
    }
}
```

---

## Component Tests (Fast Layer)

### CRDStoreIT.java

Tests CRDStore with real K8s API:
- K8s create syncs to CRDStore
- Concurrent updates from K8s
- List/get operations with K8s backing
- Watch events trigger store updates

### OwnershipValidatorIT.java

Tests validation with K8s-backed store:
- Validate complete ownership chain from K8s
- Reject creation when references missing in K8s
- Validate cross-resource ownership (VirtualCluster owned by app1, ServiceAccount tries to claim app2)
- Update validation with K8s state

### WebhookValidatorIT.java

Tests webhook with K8s mock calling it:
- Deny UPDATE when K8s sends ownership change
- Allow UPDATE when same owner
- Validate DELETE authorization
- Test all CRD types (Topic, ACL, ServiceAccount, VirtualCluster, ConsumerGroup)

**Expected:** 15-20 component tests, each running in 50-100ms.

---

## Scenario Tests (Comprehensive Layer)

### CRDLifecycleIT.java

Complete CR lifecycle flows:
- **Scenario 1:** Create complete ownership chain (app → vc → sa → topic → acl), update resources (denied: ownership change, allowed: partition increase), delete in reverse order
- **Scenario 2:** Create with invalid references, verify failures
- **Scenario 3:** Update lifecycle with status transitions

### OwnershipChainIT.java

Multi-resource ownership validation:
- **Scenario 1:** Reject Topic when ServiceAccount references wrong VirtualCluster owner (app2's SA tries to use app1's VC)
- **Scenario 2:** Create resources from YAML fixture - valid chain succeeds
- **Scenario 3:** Create resources from YAML fixture - invalid chain fails at correct point
- **Scenario 4:** Complex chain with multiple Topics and ACLs

### MultiTenantIsolationIT.java

Cross-tenant protection:
- **Scenario 1:** App1 cannot modify App2's resources via UPDATE (webhook blocks)
- **Scenario 2:** App1 cannot reference App2's ServiceAccount (validation fails)
- **Scenario 3:** App1 cannot delete App2's resources (webhook blocks)
- **Scenario 4:** Two tenants operate independently without interference

### ReconciliationIT.java

Reconciliation event flow:
- **Scenario 1:** CREATE triggers reconciliation events (START → END)
- **Scenario 2:** Failed validation triggers FAILED reconciliation event
- **Scenario 3:** UPDATE triggers reconciliation with old/new state
- **Scenario 4:** Event listeners receive all events in order

**Expected:** 12-15 scenario tests, each running in 200-400ms.

---

## Webhook Integration with Mock Server

K8s mock server intercepts UPDATE/DELETE operations and calls the real WebhookServer:

```java
protected void configureWebhookInterception() {
    // Configure K8s mock to intercept UPDATE and call webhook
    k8sServer.expect()
        .patch()
        .withPath("/apis/messaging.example.com/v1/namespaces/*/topics/*")
        .andReturn(HttpURLConnection.HTTP_OK, (req) -> {
            // Extract CR from request
            Topic oldTopic = getCurrentTopic(req);
            Topic newTopic = parseBody(req);

            // Call webhook
            AdmissionReview review = buildAdmissionReview("UPDATE", oldTopic, newTopic);
            AdmissionReview response = callWebhook("/validate/topic", review);

            // If denied, return 403
            if (!response.getResponse().isAllowed()) {
                return new StatusBuilder()
                    .withCode(403)
                    .withMessage("admission webhook denied: " +
                        response.getResponse().getStatus().getMessage())
                    .build();
            }

            // If allowed, return updated resource
            return newTopic;
        })
        .always();

    // Similar for DELETE, ACL, ServiceAccount, VirtualCluster, ConsumerGroup
}
```

This simulates the full Kubernetes admission webhook flow: API server → webhook → allow/deny response.

---

## Maven Configuration

### Dependencies

Add to pom.xml:

```xml
<dependency>
    <groupId>io.fabric8</groupId>
    <artifactId>kubernetes-server-mock</artifactId>
    <version>${fabric8.version}</version>
    <scope>test</scope>
</dependency>
```

### Maven Failsafe Plugin

```xml
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

---

## Test Execution

### Commands

- Unit tests only: `mvn test` (155 tests, ~16s)
- Integration tests only: `mvn verify -DskipTests` (~10-15s)
- All tests: `mvn clean verify` (~30s total)

### Expected Coverage

- **Component layer:** 15-20 tests covering individual components with K8s backing
- **Scenario layer:** 12-15 tests covering complete end-to-end flows
- **Total IT tests:** ~30-35
- **Combined with unit tests:** 185-190 total tests

---

## YAML Fixtures

### ownership-chain-valid.yaml

Complete valid ownership chain with all resources properly linked.

Resources:
- 1 ApplicationService (orders-app)
- 1 VirtualCluster (prod-cluster) → owned by orders-app
- 1 ServiceAccount (orders-sa) → owned by orders-app, references prod-cluster
- 2 Topics (orders-events, orders-dlq) → owned by orders-app, reference orders-sa
- 1 ACL (orders-read) → owned by orders-app, references orders-sa and orders-events

### multi-tenant-scenario.yaml

Two completely isolated tenants with their own resource hierarchies.

App1 resources:
- ApplicationService: app1
- VirtualCluster: vc1 → owned by app1
- ServiceAccount: sa-app1 → owned by app1, references vc1
- Topic: orders-events → owned by app1, references sa-app1

App2 resources:
- ApplicationService: app2
- VirtualCluster: vc2 → owned by app2
- ServiceAccount: sa-app2 → owned by app2, references vc2
- Topic: payments-events → owned by app2, references sa-app2

Used to test cross-tenant isolation and protection.

---

## Success Criteria

1. All 30-35 IT tests pass
2. Component tests run in < 100ms each
3. Scenario tests run in < 400ms each
4. Total IT suite completes in < 15 seconds
5. Tests validate:
   - ✅ CRD lifecycle (create, read, update, delete)
   - ✅ Webhook validation with real HTTP calls
   - ✅ Ownership chain enforcement
   - ✅ Multi-tenant isolation
   - ✅ Reconciliation events
   - ✅ Error scenarios and validation failures

---

## Next Steps

After Fabric8 integration tests are complete, add Testcontainers + K3s tests for:
- Real Kubernetes cluster testing
- Actual TLS webhook handshake validation
- Network/service discovery testing
- Production-like environment validation
