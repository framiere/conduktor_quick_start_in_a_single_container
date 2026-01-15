#!/usr/bin/env bats
#
# High Availability Failover Tests
# Tests that the webhook survives pod failures
#

load 'test_helper'

setup_file() {
    echo "# Setting up HA failover tests" >&3
    wait_for_webhook 60

    # Scale to 2 replicas for HA tests
    echo "# Scaling webhook to 2 replicas" >&3
    scale_webhook 2

    # Wait for both replicas to be ready
    wait_for 60 "[[ \$(get_webhook_ready_replicas) -ge 2 ]]"

    # Create base resources for testing
    create_app_service "ha-test-app"
    create_virtual_cluster "ha-test-vc" "ha-test-app"
    create_service_account "ha-test-sa" "ha-test-vc" "ha-test-app"
}

teardown_file() {
    # Scale back to 1 replica
    scale_webhook 1 || true
    cleanup_test_resources
}

@test "webhook has 2 ready replicas" {
    local ready_replicas
    ready_replicas=$(get_webhook_ready_replicas)

    [[ "$ready_replicas" -ge 2 ]]
}

@test "webhook service has multiple endpoints" {
    local endpoint_count
    endpoint_count=$(kubectl get endpoints "${RELEASE_NAME}-messaging-operator-webhook" \
        -n "$NAMESPACE" \
        -o jsonpath='{.subsets[0].addresses}' | jq '. | length')

    [[ "$endpoint_count" -ge 2 ]]
}

@test "webhook survives single pod failure" {
    # Get pod names
    local pods
    pods=$(get_webhook_pods)
    local first_pod
    first_pod=$(echo "$pods" | awk '{print $1}')

    echo "# Deleting pod: $first_pod" >&3

    # Delete the first pod (non-blocking)
    kubectl delete pod "$first_pod" -n "$NAMESPACE" --wait=false

    # Wait a moment for deletion to start
    sleep 2

    # Operations should still work (other pod handles requests)
    run create_topic "ha-topic-1" "ha-test-sa" "ha-test-app"
    assert_success

    # Verify topic was created
    run kubectl get topic ha-topic-1 -n "$NAMESPACE"
    assert_success

    # Wait for pod to be recreated
    wait_for 60 "[[ \$(get_webhook_ready_replicas) -ge 2 ]]"
}

@test "webhook recovers after pod restart" {
    # Verify we're back to 2 replicas
    local ready_replicas
    ready_replicas=$(get_webhook_ready_replicas)

    [[ "$ready_replicas" -ge 2 ]]

    # Verify operations still work
    run create_topic "ha-topic-2" "ha-test-sa" "ha-test-app"
    assert_success
}

@test "rolling restart maintains availability" {
    echo "# Starting rolling restart" >&3

    # Initiate rolling restart
    kubectl rollout restart deployment/"${RELEASE_NAME}-messaging-operator-webhook" -n "$NAMESPACE"

    # Create resources during rollout - should succeed
    local success_count=0
    local total_attempts=5

    for i in $(seq 1 $total_attempts); do
        sleep 2
        if create_topic "rolling-topic-$i" "ha-test-sa" "ha-test-app" 2>/dev/null; then
            ((success_count++))
        fi
    done

    echo "# Created $success_count/$total_attempts topics during rollout" >&3

    # Wait for rollout to complete
    kubectl rollout status deployment/"${RELEASE_NAME}-messaging-operator-webhook" \
        -n "$NAMESPACE" \
        --timeout=120s

    # Most operations should have succeeded
    [[ "$success_count" -ge 3 ]]
}

@test "webhook handles concurrent requests during failover" {
    # Get a pod to delete
    local pods
    pods=$(get_webhook_pods)
    local first_pod
    first_pod=$(echo "$pods" | awk '{print $1}')

    # Start pod deletion in background
    kubectl delete pod "$first_pod" -n "$NAMESPACE" --wait=false &

    # Immediately create multiple resources
    local success_count=0
    for i in $(seq 1 3); do
        if create_topic "concurrent-topic-$i" "ha-test-sa" "ha-test-app" 2>/dev/null; then
            ((success_count++))
        fi
        sleep 1
    done

    # Wait for background deletion to complete
    wait

    # At least some operations should succeed
    [[ "$success_count" -ge 1 ]]

    # Wait for recovery
    wait_for 60 "[[ \$(get_webhook_ready_replicas) -ge 2 ]]"
}

@test "all created topics exist after HA tests" {
    # Wait for cluster to stabilize
    sleep 5

    # Check that topics created during HA tests exist
    local topic_count
    topic_count=$(kubectl get topics -n "$NAMESPACE" -o name | grep -c "ha-topic\|rolling-topic\|concurrent-topic" || echo "0")

    echo "# Found $topic_count HA test topics" >&3
    [[ "$topic_count" -ge 3 ]]
}
