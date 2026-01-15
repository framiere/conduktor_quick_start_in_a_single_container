#!/usr/bin/env bats
#
# Multi-Tenant Isolation Tests
# Tests that tenant isolation is enforced correctly
#

load 'test_helper'

setup_file() {
    echo "# Setting up multi-tenant tests" >&3
    wait_for_webhook 60

    # Create tenant A infrastructure
    create_app_service "tenant-a-app"
    create_virtual_cluster "tenant-a-vc" "tenant-a-app"
    create_service_account "tenant-a-sa" "tenant-a-vc" "tenant-a-app"

    # Create tenant B infrastructure
    create_app_service "tenant-b-app"
    create_virtual_cluster "tenant-b-vc" "tenant-b-app"
    create_service_account "tenant-b-sa" "tenant-b-vc" "tenant-b-app"
}

teardown_file() {
    cleanup_test_resources
}

@test "tenant A can create resources under own ApplicationService" {
    run create_topic "tenant-a-topic" "tenant-a-sa" "tenant-a-app"
    assert_success

    run kubectl get topic tenant-a-topic -n "$NAMESPACE"
    assert_success
}

@test "tenant B can create resources under own ApplicationService" {
    run create_topic "tenant-b-topic" "tenant-b-sa" "tenant-b-app"
    assert_success

    run kubectl get topic tenant-b-topic -n "$NAMESPACE"
    assert_success
}

@test "tenant B cannot reference tenant A KafkaCluster" {
    # Try to create a ServiceAccount under tenant-b-app but referencing tenant-a-vc
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: cross-tenant-sa
spec:
  name: cross-tenant-sa
  dn:
    - "CN=cross-tenant-sa"
  clusterRef: tenant-a-vc
  applicationServiceRef: tenant-b-app
EOF

    assert_failure
}

@test "tenant B cannot reference tenant A ServiceAccount" {
    # Try to create a Topic under tenant-b-app but referencing tenant-a-sa
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: Topic
metadata:
  name: cross-tenant-topic
spec:
  name: cross-tenant-topic
  serviceRef: tenant-a-sa
  applicationServiceRef: tenant-b-app
  partitions: 3
  replicationFactor: 1
EOF

    assert_failure
}

@test "tenant A cannot reference tenant B KafkaCluster" {
    # Try to create a ServiceAccount under tenant-a-app but referencing tenant-b-vc
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ServiceAccount
metadata:
  name: cross-tenant-sa-2
spec:
  name: cross-tenant-sa-2
  dn:
    - "CN=cross-tenant-sa-2"
  clusterRef: tenant-b-vc
  applicationServiceRef: tenant-a-app
EOF

    assert_failure
}

@test "ACL cannot reference Topic from different tenant" {
    # Create topics for both tenants
    create_topic "acl-test-topic-a" "tenant-a-sa" "tenant-a-app"
    create_topic "acl-test-topic-b" "tenant-b-sa" "tenant-b-app"

    # Try to create ACL under tenant-b but referencing tenant-a's topic
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ACL
metadata:
  name: cross-tenant-acl
spec:
  serviceRef: tenant-b-sa
  applicationServiceRef: tenant-b-app
  topicRef: acl-test-topic-a
  operations:
    - READ
  permission: ALLOW
EOF

    assert_failure
}

@test "ConsumerGroup cannot be created under different tenant ServiceAccount" {
    # Try to create ConsumerGroup under tenant-b-app but referencing tenant-a-sa
    run kubectl apply -n "$NAMESPACE" -f - <<EOF
apiVersion: messaging.example.com/v1
kind: ConsumerGroup
metadata:
  name: cross-tenant-cg
spec:
  name: cross-tenant-cg
  serviceRef: tenant-a-sa
  applicationServiceRef: tenant-b-app
  patternType: LITERAL
EOF

    assert_failure
}

@test "tenants have isolated resource counts" {
    # Create additional resources for tenant A
    create_topic "count-topic-a1" "tenant-a-sa" "tenant-a-app"
    create_topic "count-topic-a2" "tenant-a-sa" "tenant-a-app"

    # Create additional resources for tenant B
    create_topic "count-topic-b1" "tenant-b-sa" "tenant-b-app"

    # Count topics per tenant (by applicationServiceRef)
    local tenant_a_topics
    tenant_a_topics=$(kubectl get topics -n "$NAMESPACE" -o json | \
        jq '[.items[] | select(.spec.applicationServiceRef == "tenant-a-app")] | length')

    local tenant_b_topics
    tenant_b_topics=$(kubectl get topics -n "$NAMESPACE" -o json | \
        jq '[.items[] | select(.spec.applicationServiceRef == "tenant-b-app")] | length')

    # Tenant A should have more topics than tenant B
    [[ "$tenant_a_topics" -ge 2 ]]
    [[ "$tenant_b_topics" -ge 1 ]]
}
