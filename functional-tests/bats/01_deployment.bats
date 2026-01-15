#!/usr/bin/env bats
#
# Deployment Tests
# Verifies that the messaging operator is correctly deployed
#

load 'test_helper'

setup_file() {
    echo "# Setting up deployment tests" >&3
    wait_for_webhook 60
}

@test "webhook deployment exists" {
    run kubectl get deployment "${RELEASE_NAME}-messaging-operator-webhook" -n "$NAMESPACE"
    assert_success
}

@test "webhook deployment has ready replicas" {
    local ready_replicas
    ready_replicas=$(get_webhook_ready_replicas)

    [[ "$ready_replicas" -ge 1 ]]
}

@test "webhook service exists" {
    run kubectl get service "${RELEASE_NAME}-messaging-operator-webhook" -n "$NAMESPACE"
    assert_success
}

@test "webhook service has endpoints" {
    run kubectl get endpoints "${RELEASE_NAME}-messaging-operator-webhook" -n "$NAMESPACE" \
        -o jsonpath='{.subsets[0].addresses[0].ip}'
    assert_success
    [[ -n "$output" ]]
}

@test "webhook pod is running" {
    local pod_phase
    pod_phase=$(kubectl get pods -n "$NAMESPACE" \
        -l "app.kubernetes.io/instance=$RELEASE_NAME,app.kubernetes.io/component=webhook" \
        -o jsonpath='{.items[0].status.phase}')

    [[ "$pod_phase" == "Running" ]]
}

@test "ApplicationService CRD is installed" {
    run kubectl get crd applicationservices.messaging.example.com
    assert_success
}

@test "KafkaCluster CRD is installed" {
    run kubectl get crd kafkaclusters.messaging.example.com
    assert_success
}

@test "ServiceAccount CRD is installed" {
    run kubectl get crd serviceaccounts.messaging.example.com
    assert_success
}

@test "Topic CRD is installed" {
    run kubectl get crd topics.messaging.example.com
    assert_success
}

@test "ACL CRD is installed" {
    run kubectl get crd acls.messaging.example.com
    assert_success
}

@test "ConsumerGroup CRD is installed" {
    run kubectl get crd consumergroups.messaging.example.com
    assert_success
}

@test "all 6 CRDs are installed" {
    local crd_count
    crd_count=$(kubectl get crd -o name | grep -c "messaging.example.com" || echo "0")

    [[ "$crd_count" -eq 6 ]]
}

@test "ValidatingWebhookConfiguration exists" {
    run kubectl get validatingwebhookconfiguration "${RELEASE_NAME}-messaging-operator-webhook"
    assert_success
}

@test "ValidatingWebhookConfiguration has 5 webhooks" {
    local webhook_count
    webhook_count=$(kubectl get validatingwebhookconfiguration "${RELEASE_NAME}-messaging-operator-webhook" \
        -o jsonpath='{.webhooks}' | grep -o '"name"' | wc -l)

    [[ "$webhook_count" -eq 5 ]]
}
