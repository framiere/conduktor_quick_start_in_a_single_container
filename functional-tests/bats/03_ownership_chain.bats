#!/usr/bin/env bats
#
# Ownership Chain Tests
# Tests that ownership hierarchies are enforced correctly
#

load 'test_helper'

setup_file() {
    echo "# Setting up ownership chain tests" >&3
    wait_for_webhook 60
}

setup() {
    cleanup_test_resources
}

teardown_file() {
    cleanup_test_resources
}

@test "full ownership chain - valid hierarchy accepted" {
    # Create complete chain: App -> VC -> SA -> Topic
    run create_app_service "chain-app"
    assert_success

    run create_virtual_cluster "chain-vc" "chain-app"
    assert_success

    run create_service_account "chain-sa" "chain-vc" "chain-app"
    assert_success

    run create_topic "chain-topic" "chain-sa" "chain-app"
    assert_success

    # Verify all resources exist
    run kubectl get applicationservice chain-app -n "$NAMESPACE"
    assert_success

    run kubectl get kafkacluster chain-vc -n "$NAMESPACE"
    assert_success

    run kubectl get serviceaccount.messaging.example.com chain-sa -n "$NAMESPACE"
    assert_success

    run kubectl get topic chain-topic -n "$NAMESPACE"
    assert_success
}

@test "KafkaCluster requires valid ApplicationService reference" {
    # Try to create KafkaCluster without ApplicationService
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: orphan-vc
spec:
  clusterId: orphan-vc
  applicationServiceRef: non-existent-app
EOF

    assert_failure
}

@test "ServiceAccount requires valid KafkaCluster reference" {
    # Create ApplicationService only
    create_app_service "test-app"

    # Try to create ServiceAccount with non-existent KafkaCluster
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: orphan-sa
spec:
  name: orphan-sa
  dn:
    - "CN=orphan-sa"
  clusterRef: non-existent-vc
  applicationServiceRef: test-app
EOF

    assert_failure
}

@test "Topic requires valid ServiceAccount reference" {
    # Create partial chain
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"

    # Try to create Topic with non-existent ServiceAccount
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: orphan-topic
spec:
  name: orphan-topic
  serviceRef: non-existent-sa
  applicationServiceRef: test-app
  partitions: 3
  replicationFactor: 1
EOF

    assert_failure
}

@test "ACL requires valid ServiceAccount reference" {
    # Create partial chain
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"

    # Try to create ACL with non-existent ServiceAccount
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: orphan-acl
spec:
  serviceRef: non-existent-sa
  applicationServiceRef: test-app
  operations:
    - READ
  permission: ALLOW
EOF

    assert_failure
}

@test "ConsumerGroup requires valid ServiceAccount reference" {
    # Create partial chain
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"

    # Try to create ConsumerGroup with non-existent ServiceAccount
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ConsumerGroup
metadata:
  name: orphan-cg
spec:
  name: orphan-cg
  serviceRef: non-existent-sa
  applicationServiceRef: test-app
  patternType: LITERAL
EOF

    assert_failure
}

@test "ServiceAccount must belong to same ApplicationService as KafkaCluster" {
    # Create two separate ApplicationServices
    create_app_service "app-a"
    create_app_service "app-b"

    # Create KafkaCluster under app-a
    create_virtual_cluster "vc-a" "app-a"

    # Try to create ServiceAccount referencing vc-a but owned by app-b
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: cross-sa
spec:
  name: cross-sa
  dn:
    - "CN=cross-sa"
  clusterRef: vc-a
  applicationServiceRef: app-b
EOF

    assert_failure
}

@test "resources can be deleted in reverse dependency order" {
    # Create full chain
    create_app_service "delete-app"
    create_virtual_cluster "delete-vc" "delete-app"
    create_service_account "delete-sa" "delete-vc" "delete-app"
    create_topic "delete-topic" "delete-sa" "delete-app"

    # Delete in reverse order (should succeed)
    run kubectl delete topic delete-topic -n "$NAMESPACE"
    assert_success

    run kubectl delete serviceaccount.messaging.example.com delete-sa -n "$NAMESPACE"
    assert_success

    run kubectl delete kafkacluster delete-vc -n "$NAMESPACE"
    assert_success

    run kubectl delete applicationservice delete-app -n "$NAMESPACE"
    assert_success
}
