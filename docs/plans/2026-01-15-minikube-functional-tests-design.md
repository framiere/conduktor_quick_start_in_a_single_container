# Minikube Functional Tests Design

## Overview

End-to-end functional testing strategy for the Messaging Operator using Helm, kubectl, and Minikube. Tests validate webhook admission flow, ownership chain enforcement, multi-tenant isolation, and HA failover behavior.

## Test Runners

Three complementary testing layers:

| Layer | Purpose | Speed |
|-------|---------|-------|
| Bash scripts | Deployment validation, smoke tests | Fast |
| Bats | Structured kubectl/helm tests with assertions | Medium |
| Java/Fabric8 | Complex scenarios reusing TestDataBuilder | Thorough |

## Directory Structure

```
functional-tests/
├── helm/
│   └── messaging-operator/
│       ├── Chart.yaml
│       ├── values.yaml
│       ├── values-minikube.yaml
│       ├── templates/
│       │   ├── _helpers.tpl
│       │   ├── namespace.yaml
│       │   ├── crds/
│       │   ├── webhook-deployment.yaml
│       │   ├── webhook-service.yaml
│       │   ├── webhook-config.yaml
│       │   └── tls-secret.yaml
│       └── tests/
│           └── test-webhook-health.yaml
├── scripts/
│   ├── setup-minikube.sh
│   ├── deploy.sh
│   ├── teardown.sh
│   └── run-tests.sh
├── bats/
│   ├── test_helper.bash
│   ├── 01_deployment.bats
│   ├── 02_webhook_admission.bats
│   ├── 03_ownership_chain.bats
│   ├── 04_multi_tenant.bats
│   └── 05_ha_failover.bats
├── fixtures/
│   ├── valid/
│   ├── invalid/
│   ├── tenant-a/
│   ├── tenant-b/
│   ├── ownership-chain/
│   └── ha-test/
└── java/
    └── src/test/java/
        └── com/example/messaging/operator/e2e/
```

## Helm Chart Configuration

### values.yaml (production defaults)

```yaml
namespace: operator-system

webhook:
  replicaCount: 2
  image:
    repository: messaging-operator
    tag: latest
    pullPolicy: IfNotPresent
  port: 8443
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
  healthCheck:
    path: /health
    initialDelaySeconds: 10
    periodSeconds: 5

tls:
  secretName: webhook-tls
  generate: false

failurePolicy: Fail
timeoutSeconds: 10
```

### values-minikube.yaml (local testing overrides)

```yaml
webhook:
  replicaCount: 1
  image:
    pullPolicy: Never
  resources:
    requests:
      memory: "128Mi"
      cpu: "50m"
    limits:
      memory: "256Mi"
      cpu: "250m"

tls:
  generate: true
```

## Cluster Lifecycle Management

### setup-minikube.sh

```bash
#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"
FRESH_CLUSTER="${FRESH_CLUSTER:-false}"
NAMESPACE="${NAMESPACE:-operator-test-$(date +%s)}"

if minikube status -p "$CLUSTER_NAME" &>/dev/null; then
    if [[ "$FRESH_CLUSTER" == "true" ]]; then
        echo "Deleting existing cluster..."
        minikube delete -p "$CLUSTER_NAME"
    else
        echo "Reusing existing cluster: $CLUSTER_NAME"
        minikube profile "$CLUSTER_NAME"
        kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
        echo "$NAMESPACE" > .test-namespace
        exit 0
    fi
fi

echo "Creating Minikube cluster: $CLUSTER_NAME"
minikube start -p "$CLUSTER_NAME" \
    --driver=docker \
    --cpus=2 \
    --memory=4096 \
    --kubernetes-version=v1.29.0

kubectl create namespace "$NAMESPACE"
echo "$NAMESPACE" > .test-namespace
```

### run-tests.sh (main entry point)

```bash
#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat <<EOF
Usage: $0 [OPTIONS]

Options:
    --fresh-cluster    Create new Minikube cluster (default: reuse)
    --skip-deploy      Skip Helm deployment (use existing)
    --test-filter      Run only matching bats files (e.g., "02_webhook")
    --java-only        Run only Java integration tests
    --bats-only        Run only Bats tests
    --skip-tests       Deploy only, no tests
    --cleanup          Delete namespace after tests
    -h, --help         Show this help
EOF
}
```

## Bats Test Structure

### test_helper.bash

```bash
load '/usr/lib/bats-support/load.bash'
load '/usr/lib/bats-assert/load.bash'

NAMESPACE=$(cat .test-namespace 2>/dev/null || echo "operator-system")
TIMEOUT=60

wait_for() {
    local cmd="$1" timeout="${2:-$TIMEOUT}"
    for ((i=0; i<timeout; i++)); do
        if eval "$cmd" &>/dev/null; then return 0; fi
        sleep 1
    done
    return 1
}

apply_fixture() {
    local fixture="$1"
    kubectl apply -f "fixtures/$fixture" -n "$NAMESPACE" 2>&1
}

expect_rejection() {
    local fixture="$1" expected_msg="$2"
    run kubectl apply -f "fixtures/$fixture" -n "$NAMESPACE"
    assert_failure
    assert_output --partial "$expected_msg"
}
```

### 02_webhook_admission.bats

```bash
#!/usr/bin/env bats
load 'test_helper'

setup_file() {
    wait_for "kubectl get endpoints webhook-service -n $NAMESPACE -o jsonpath='{.subsets[0].addresses}' | grep -q '.'"
}

@test "webhook accepts valid ApplicationService" {
    run apply_fixture "valid/application-service.yaml"
    assert_success
}

@test "webhook rejects ApplicationService with missing required field" {
    run apply_fixture "invalid/missing-appname.yaml"
    assert_failure
    assert_output --partial "appName is required"
}

@test "webhook rejects UPDATE changing applicationServiceRef" {
    apply_fixture "valid/topic-for-update-test.yaml"
    expect_rejection "invalid/topic-changed-owner.yaml" "applicationServiceRef is immutable"
}
```

### 04_multi_tenant.bats

```bash
#!/usr/bin/env bats
load 'test_helper'

setup_file() {
    apply_fixture "tenant-a/application-service.yaml"
    apply_fixture "tenant-b/application-service.yaml"
    apply_fixture "tenant-a/virtual-cluster.yaml"
}

@test "tenant B cannot reference tenant A's VirtualCluster" {
    expect_rejection "tenant-b/topic-referencing-tenant-a.yaml" \
        "does not belong to ApplicationService"
}

@test "tenant A can reference own VirtualCluster" {
    run apply_fixture "tenant-a/topic-valid.yaml"
    assert_success
}
```

### 05_ha_failover.bats

```bash
#!/usr/bin/env bats
load 'test_helper'

setup_file() {
    kubectl scale deployment webhook -n "$NAMESPACE" --replicas=2
    wait_for "kubectl get deployment webhook -n $NAMESPACE -o jsonpath='{.status.readyReplicas}' | grep -q '2'"
}

@test "webhook has 2 ready replicas" {
    run kubectl get deployment webhook -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}'
    assert_output "2"
}

@test "webhook survives single pod failure" {
    local pod=$(kubectl get pods -n "$NAMESPACE" -l app=webhook -o jsonpath='{.items[0].metadata.name}')
    kubectl delete pod "$pod" -n "$NAMESPACE" --wait=false
    sleep 2
    run apply_fixture "ha-test/application-service.yaml"
    assert_success
}

@test "webhook recovers after pod restart" {
    wait_for "kubectl get deployment webhook -n $NAMESPACE -o jsonpath='{.status.readyReplicas}' | grep -q '2'" 120
    run apply_fixture "ha-test/virtual-cluster.yaml"
    assert_success
}

@test "rolling restart maintains availability" {
    kubectl rollout restart deployment webhook -n "$NAMESPACE"
    for i in {1..10}; do
        run apply_fixture "ha-test/topic-rolling-$i.yaml"
        assert_success
        sleep 2
    done
    kubectl rollout status deployment webhook -n "$NAMESPACE" --timeout=120s
}
```

## Java E2E Tests

### E2ETestBase.java

```java
package com.example.messaging.operator.e2e;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class E2ETestBase {

    protected static KubernetesClient k8sClient;
    protected static String namespace;

    @BeforeAll
    void setupCluster() {
        Config config = Config.autoConfigure(null);
        k8sClient = new KubernetesClientBuilder()
            .withConfig(config)
            .build();

        namespace = System.getenv().getOrDefault(
            "TEST_NAMESPACE",
            readTestNamespace()
        );

        waitForWebhookReady();
    }

    private void waitForWebhookReady() {
        await().atMost(Duration.ofSeconds(60))
            .until(() -> webhookHasEndpoints());
    }

    protected void assertRejectedWith(Executable action, String expectedMessage) {
        var exception = assertThrows(KubernetesClientException.class, action);
        assertThat(exception.getMessage()).contains(expectedMessage);
    }
}
```

### OwnershipChainE2ETest.java

```java
@E2ETest
class OwnershipChainE2ETest extends E2ETestBase {

    @Test
    void fullOwnershipChain_validHierarchy_accepted() {
        var app = TestDataBuilder.applicationService()
            .namespace(namespace)
            .name("e2e-app")
            .appName("e2e-app")
            .createIn(k8sClient);

        var vc = TestDataBuilder.virtualCluster()
            .namespace(namespace)
            .name("e2e-vc")
            .applicationServiceRef(app.getMetadata().getName())
            .createIn(k8sClient);

        var topic = TestDataBuilder.topic()
            .namespace(namespace)
            .name("e2e-topic")
            .virtualClusterRef(vc.getMetadata().getName())
            .createIn(k8sClient);

        assertThat(k8sClient.resources(Topic.class)
            .inNamespace(namespace)
            .withName("e2e-topic")
            .get()).isNotNull();
    }

    @Test
    void topic_withNonExistentVirtualCluster_rejected() {
        assertRejectedWith(
            () -> TestDataBuilder.topic()
                .namespace(namespace)
                .name("orphan-topic")
                .virtualClusterRef("does-not-exist")
                .createIn(k8sClient),
            "VirtualCluster 'does-not-exist' not found"
        );
    }
}
```

### HAFailoverE2ETest.java

```java
@E2ETest
class HAFailoverE2ETest extends E2ETestBase {

    @Test
    void webhookRemainsAvailable_duringSinglePodFailure() {
        scaleWebhook(2);

        var pods = getWebhookPods();
        assertThat(pods).hasSize(2);

        k8sClient.pods()
            .inNamespace(namespace)
            .withName(pods.get(0))
            .delete();

        for (int i = 0; i < 5; i++) {
            var app = TestDataBuilder.applicationService()
                .namespace(namespace)
                .name("ha-test-" + i)
                .appName("ha-test-" + i)
                .createIn(k8sClient);

            assertThat(app).isNotNull();
        }
    }
}
```

### Maven Profile

```xml
<profile>
    <id>e2e</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/*E2ETest.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

## Test Fixtures

```
fixtures/
├── valid/
│   ├── application-service.yaml
│   ├── virtual-cluster.yaml
│   ├── service-account.yaml
│   ├── topic.yaml
│   ├── acl.yaml
│   └── consumer-group.yaml
├── invalid/
│   ├── missing-appname.yaml
│   ├── missing-owner-ref.yaml
│   └── nonexistent-parent-ref.yaml
├── tenant-a/
│   ├── application-service.yaml
│   ├── virtual-cluster.yaml
│   └── topic-valid.yaml
├── tenant-b/
│   ├── application-service.yaml
│   └── topic-referencing-tenant-a.yaml
├── ownership-chain/
│   └── full-hierarchy.yaml
└── ha-test/
    └── *.yaml
```

## Execution

```bash
# Full fresh run (CI-style)
./scripts/run-tests.sh --fresh-cluster --cleanup

# Quick local iteration
./scripts/run-tests.sh

# Run specific test suites
./scripts/run-tests.sh --bats-only --test-filter "02_webhook"
./scripts/run-tests.sh --java-only

# Deploy only (manual exploration)
./scripts/run-tests.sh --skip-tests
```

## Expected Output

```
=== Messaging Operator E2E Tests ===
Cluster: messaging-operator-test (reusing)
Namespace: operator-test-1705312345

[1/4] Building operator image... ✓
[2/4] Deploying Helm chart... ✓
[3/4] Running Bats tests...
  ✓ 01_deployment.bats (5 tests)
  ✓ 02_webhook_admission.bats (8 tests)
  ✓ 03_ownership_chain.bats (6 tests)
  ✓ 04_multi_tenant.bats (4 tests)
  ✓ 05_ha_failover.bats (4 tests)
[4/4] Running Java E2E tests...
  ✓ OwnershipChainE2ETest (3 tests)
  ✓ MultiTenantE2ETest (2 tests)
  ✓ HAFailoverE2ETest (1 test)

=== Results ===
Bats:  27/27 passed
Java:   6/6 passed
Total: 33/33 passed ✓
```

## Test Scenarios Summary

| Category | Tests | Coverage |
|----------|-------|----------|
| Deployment | 5 | Pod lifecycle, service discovery, health probes |
| Webhook Admission | 8 | Valid/invalid CRDs, immutability enforcement |
| Ownership Chain | 6 | Full hierarchy, missing refs, cross-refs |
| Multi-Tenant | 4 | Isolation, cross-tenant rejection |
| HA Failover | 4 | Pod failure, recovery, rolling restart |
| **Total** | **27** | Bats tests |
| Java E2E | 6 | Complex scenarios with TestDataBuilder |
