#!/usr/bin/env bash
#
# Bats Test Helper Functions
# Provides common utilities for functional tests
#

# Script directories
BATS_TEST_DIRNAME="${BATS_TEST_DIRNAME:-$(dirname "${BASH_SOURCE[0]}")}"
FUNC_TESTS_DIR="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
PROJECT_ROOT="$(cd "$FUNC_TESTS_DIR/.." && pwd)"
FIXTURES_DIR="$FUNC_TESTS_DIR/fixtures"

# Load bats libraries with fallback paths
load_bats_libraries() {
    # Try system paths first
    if [[ -f /usr/lib/bats-support/load.bash ]]; then
        load /usr/lib/bats-support/load.bash
        load /usr/lib/bats-assert/load.bash
    elif [[ -f "$HOME/.bats/bats-support/load.bash" ]]; then
        load "$HOME/.bats/bats-support/load.bash"
        load "$HOME/.bats/bats-assert/load.bash"
    elif [[ -f /usr/local/lib/bats-support/load.bash ]]; then
        load /usr/local/lib/bats-support/load.bash
        load /usr/local/lib/bats-assert/load.bash
    else
        # Provide minimal fallback assertions
        echo "Warning: bats-support/bats-assert not found, using minimal fallbacks" >&2
        assert_success() {
            if [[ "$status" -ne 0 ]]; then
                echo "Expected success but got status $status" >&2
                echo "Output: $output" >&2
                return 1
            fi
        }
        assert_failure() {
            if [[ "$status" -eq 0 ]]; then
                echo "Expected failure but got success" >&2
                return 1
            fi
        }
        assert_output() {
            if [[ "$output" != *"$1"* ]]; then
                echo "Expected output to contain: $1" >&2
                echo "Actual output: $output" >&2
                return 1
            fi
        }
    fi
}

# Load libraries
load_bats_libraries

# Read namespace from file
get_namespace() {
    local ns_file="$FUNC_TESTS_DIR/.test-namespace"
    if [[ -f "$ns_file" ]]; then
        cat "$ns_file"
    else
        echo "operator-system"
    fi
}

# Set namespace variable
NAMESPACE="${NAMESPACE:-$(get_namespace)}"
RELEASE_NAME="${RELEASE_NAME:-messaging-operator}"

# Wait for condition with timeout
# Usage: wait_for <timeout_seconds> <command>
wait_for() {
    local timeout=$1
    shift
    local command="$*"
    local start_time
    start_time=$(date +%s)

    while true; do
        if eval "$command"; then
            return 0
        fi

        local current_time
        current_time=$(date +%s)
        local elapsed=$((current_time - start_time))

        if [[ $elapsed -ge $timeout ]]; then
            echo "Timeout waiting for: $command" >&2
            return 1
        fi

        sleep 2
    done
}

# Wait for webhook to be ready
wait_for_webhook() {
    local timeout="${1:-60}"
    echo "Waiting for webhook to be ready (timeout: ${timeout}s)..."

    wait_for "$timeout" "kubectl get endpoints ${RELEASE_NAME}-webhook -n $NAMESPACE -o jsonpath='{.subsets[0].addresses[0].ip}' 2>/dev/null | grep -q '.'"
}

# Check if resource exists
resource_exists() {
    local resource_type=$1
    local resource_name=$2
    local ns="${3:-$NAMESPACE}"

    kubectl get "$resource_type" "$resource_name" -n "$ns" &>/dev/null
}

# Get resource
get_resource() {
    local resource_type=$1
    local resource_name=$2
    local ns="${3:-$NAMESPACE}"

    kubectl get "$resource_type" "$resource_name" -n "$ns" -o yaml
}

# Apply fixture file
apply_fixture() {
    local fixture_path=$1
    local ns="${2:-$NAMESPACE}"

    if [[ ! -f "$fixture_path" ]]; then
        # Check in fixtures directory
        if [[ -f "$FIXTURES_DIR/$fixture_path" ]]; then
            fixture_path="$FIXTURES_DIR/$fixture_path"
        else
            echo "Fixture not found: $fixture_path" >&2
            return 1
        fi
    fi

    kubectl apply -f "$fixture_path" -n "$ns"
}

# Delete fixture file
delete_fixture() {
    local fixture_path=$1
    local ns="${2:-$NAMESPACE}"

    if [[ ! -f "$fixture_path" ]]; then
        if [[ -f "$FIXTURES_DIR/$fixture_path" ]]; then
            fixture_path="$FIXTURES_DIR/$fixture_path"
        else
            return 0  # Already deleted or doesn't exist
        fi
    fi

    kubectl delete -f "$fixture_path" -n "$ns" --ignore-not-found=true
}

# Expect kubectl command to be rejected
# Usage: expect_rejection <kubectl_command> [expected_message]
expect_rejection() {
    local cmd=$1
    local expected_message="${2:-}"

    set +e
    local output
    output=$(eval "$cmd" 2>&1)
    local exit_code=$?
    set -e

    if [[ $exit_code -eq 0 ]]; then
        echo "Expected rejection but command succeeded" >&2
        echo "Output: $output" >&2
        return 1
    fi

    if [[ -n "$expected_message" ]]; then
        if [[ "$output" != *"$expected_message"* ]]; then
            echo "Expected message '$expected_message' not found in output" >&2
            echo "Actual output: $output" >&2
            return 1
        fi
    fi

    return 0
}

# Cleanup all test resources in namespace
cleanup_test_resources() {
    local ns="${1:-$NAMESPACE}"

    echo "Cleaning up test resources in namespace: $ns"

    # Delete in reverse dependency order
    kubectl delete topics --all -n "$ns" --ignore-not-found=true 2>/dev/null || true
    kubectl delete acls --all -n "$ns" --ignore-not-found=true 2>/dev/null || true
    kubectl delete consumergroups --all -n "$ns" --ignore-not-found=true 2>/dev/null || true
    kubectl delete serviceaccounts.messaging.example.com --all -n "$ns" --ignore-not-found=true 2>/dev/null || true
    kubectl delete kafkaclusters --all -n "$ns" --ignore-not-found=true 2>/dev/null || true
    kubectl delete applicationservices --all -n "$ns" --ignore-not-found=true 2>/dev/null || true

    # Wait for resources to be deleted
    sleep 2
}

# Get webhook pod names
get_webhook_pods() {
    kubectl get pods -n "$NAMESPACE" \
        -l "app.kubernetes.io/instance=$RELEASE_NAME,app.kubernetes.io/component=webhook" \
        -o jsonpath='{.items[*].metadata.name}'
}

# Scale webhook deployment
scale_webhook() {
    local replicas=$1
    kubectl scale deployment "${RELEASE_NAME}-webhook" \
        -n "$NAMESPACE" \
        --replicas="$replicas"

    # Wait for scaling
    kubectl rollout status deployment/"${RELEASE_NAME}-webhook" \
        -n "$NAMESPACE" \
        --timeout=120s
}

# Get webhook ready replicas
get_webhook_ready_replicas() {
    kubectl get deployment "${RELEASE_NAME}-webhook" \
        -n "$NAMESPACE" \
        -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0"
}

# Create ApplicationService
create_app_service() {
    local name=$1
    local ns="${2:-$NAMESPACE}"

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: $name
spec:
  name: $name
EOF
}

# Create KafkaCluster
create_virtual_cluster() {
    local name=$1
    local app_service_ref=$2
    local ns="${3:-$NAMESPACE}"

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: $name
spec:
  clusterId: $name
  applicationServiceRef: $app_service_ref
EOF
}

# Create ServiceAccount
create_service_account() {
    local name=$1
    local cluster_ref=$2
    local app_service_ref=$3
    local ns="${4:-$NAMESPACE}"

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: $name
spec:
  name: $name
  dn:
    - "CN=$name"
  clusterRef: $cluster_ref
  applicationServiceRef: $app_service_ref
EOF
}

# Create Topic
create_topic() {
    local name=$1
    local service_ref=$2
    local app_service_ref=$3
    local ns="${4:-$NAMESPACE}"

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: $name
spec:
  name: $name
  serviceRef: $service_ref
  applicationServiceRef: $app_service_ref
  partitions: 3
  replicationFactor: 1
EOF
}

# Create ACL
create_acl() {
    local name=$1
    local service_ref=$2
    local app_service_ref=$3
    local topic_ref="${4:-}"
    local ns="${5:-$NAMESPACE}"

    local topic_line=""
    if [[ -n "$topic_ref" ]]; then
        topic_line="topicRef: $topic_ref"
    fi

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: $name
spec:
  serviceRef: $service_ref
  applicationServiceRef: $app_service_ref
  $topic_line
  operations:
    - READ
    - WRITE
  permission: ALLOW
EOF
}

# Create ConsumerGroup
create_consumer_group() {
    local name=$1
    local service_ref=$2
    local app_service_ref=$3
    local ns="${4:-$NAMESPACE}"

    kubectl apply -n "$ns" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ConsumerGroup
metadata:
  name: $name
spec:
  name: $name
  serviceRef: $service_ref
  applicationServiceRef: $app_service_ref
  patternType: LITERAL
EOF
}
