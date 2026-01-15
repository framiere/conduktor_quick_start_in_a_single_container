#!/usr/bin/env bats
#
# Webhook Admission Tests
# Tests that the webhook correctly accepts valid resources and rejects invalid ones
#

load 'test_helper'

setup_file() {
    echo "# Setting up webhook admission tests" >&3
    wait_for_webhook 60
}

setup() {
    # Clean up before each test for isolation
    cleanup_test_resources
}

teardown_file() {
    cleanup_test_resources
}

# Valid resource tests

@test "webhook accepts valid ApplicationService" {
    run create_app_service "test-app"
    assert_success

    run kubectl get applicationservice test-app -n "$NAMESPACE"
    assert_success
}

@test "webhook accepts valid KafkaCluster with existing ApplicationService" {
    # Create prerequisite
    create_app_service "test-app"

    run create_virtual_cluster "test-vc" "test-app"
    assert_success

    run kubectl get kafkacluster test-vc -n "$NAMESPACE"
    assert_success
}

@test "webhook accepts valid ServiceAccount with prerequisites" {
    # Create prerequisites
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"

    run create_service_account "test-sa" "test-vc" "test-app"
    assert_success

    run kubectl get serviceaccount.messaging.example.com test-sa -n "$NAMESPACE"
    assert_success
}

@test "webhook accepts valid Topic with prerequisites" {
    # Create prerequisites
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"
    create_service_account "test-sa" "test-vc" "test-app"

    run create_topic "test-topic" "test-sa" "test-app"
    assert_success

    run kubectl get topic test-topic -n "$NAMESPACE"
    assert_success
}

@test "webhook accepts valid ACL with prerequisites" {
    # Create prerequisites
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"
    create_service_account "test-sa" "test-vc" "test-app"
    create_topic "test-topic" "test-sa" "test-app"

    run create_acl "test-acl" "test-sa" "test-app" "test-topic"
    assert_success

    run kubectl get acl test-acl -n "$NAMESPACE"
    assert_success
}

@test "webhook accepts valid ConsumerGroup with prerequisites" {
    # Create prerequisites
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"
    create_service_account "test-sa" "test-vc" "test-app"

    run create_consumer_group "test-cg" "test-sa" "test-app"
    assert_success

    run kubectl get consumergroup test-cg -n "$NAMESPACE"
    assert_success
}

# Invalid resource tests

@test "webhook rejects ApplicationService with missing name" {
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ApplicationService
metadata:
  name: invalid-app
spec: {}
EOF

    assert_failure
}

@test "webhook rejects KafkaCluster with non-existent ApplicationService ref" {
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: KafkaCluster
metadata:
  name: invalid-vc
spec:
  clusterId: invalid-vc
  applicationServiceRef: non-existent-app
EOF

    assert_failure
}

@test "webhook rejects ServiceAccount with non-existent KafkaCluster ref" {
    # Create ApplicationService but not KafkaCluster
    create_app_service "test-app"

    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: invalid-sa
spec:
  name: invalid-sa
  dn:
    - "CN=invalid-sa"
  clusterRef: non-existent-vc
  applicationServiceRef: test-app
EOF

    assert_failure
}

@test "webhook rejects Topic with non-existent ServiceAccount ref" {
    # Create prerequisites except ServiceAccount
    create_app_service "test-app"
    create_virtual_cluster "test-vc" "test-app"

    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: invalid-topic
spec:
  name: invalid-topic
  serviceRef: non-existent-sa
  applicationServiceRef: test-app
  partitions: 3
  replicationFactor: 1
EOF

    assert_failure
}
