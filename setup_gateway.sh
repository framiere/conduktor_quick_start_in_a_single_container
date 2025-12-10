#!/bin/bash
set -euo pipefail

# Configuration
export CDK_BASE_URL="${CDK_BASE_URL:-http://localhost:8080}"
export CDK_USER="${CDK_USER:-admin@demo.dev}"
export CDK_PASSWORD="${CDK_PASSWORD:-123_ABC_abc}"
export CDK_GATEWAY_BASE_URL="${CDK_GATEWAY_BASE_URL:-http://localhost:8888}"
export CDK_GATEWAY_USER="${CDK_GATEWAY_USER:-admin}"
export CDK_GATEWAY_PASSWORD="${CDK_GATEWAY_PASSWORD:-conduktor}"

VCLUSTER_NAME="${VCLUSTER_NAME:-demo}"
VCLUSTER_ACL_NAME="${VCLUSTER_NAME}-acl"

# Helper: wait for service to be ready
wait_for_service() {
    local name="$1" url="$2" max_attempts="${3:-90}"
    for i in $(seq 1 $max_attempts); do
        if curl -sf -o /dev/null "$url"; then
            echo "$name is ready"
            return 0
        fi
        echo "  Attempt $i/$max_attempts - $name not ready yet..."
        sleep 5
    done
    echo "ERROR: $name did not become ready in time"
    return 1
}

# Wait for Console and Gateway to be up
wait_for_service "Conduktor Console" "$CDK_BASE_URL/platform/api/modules/resources/health/live" 90
wait_for_service "Gateway Admin API" "$CDK_GATEWAY_BASE_URL/health/ready" 90

# Parse arguments
if [ $# -ge 1 ]; then
    if [ -f "$1" ]; then
        EXTRACTED=$(yq eval '.metadata.scope.vCluster // .metadata.vCluster // .metadata.name' "$1" 2>/dev/null || echo "")
        [ -n "$EXTRACTED" ] && [ "$EXTRACTED" != "null" ] && VCLUSTER_NAME="$EXTRACTED"
    else
        VCLUSTER_NAME="$1"
    fi
fi

# Helper: generate token
generate_token() {
    local vcluster="$1" user="$2"
    local token=$(curl -sf -X POST "$CDK_GATEWAY_BASE_URL/gateway/v2/token" \
        -u "$CDK_GATEWAY_USER:$CDK_GATEWAY_PASSWORD" \
        -H 'Content-Type: application/json' \
        -d "{\"vCluster\": \"$vcluster\", \"username\": \"$user\", \"lifeTimeSeconds\": 7776000}" | jq -r ".token")
    [ -z "$token" ] || [ "$token" = "null" ] && { echo "ERROR: Token generation failed for $user@$vcluster"; exit 1; }

    echo """
security.protocol=SASL_PLAINTEXT
sasl.mechanism=PLAIN
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required \
username="$user" \
password="$token";""" > $vcluster-$user.properties

    echo "$token"
}


# Helper: apply yaml via temp file
apply_yaml() {
    local tmpfile=$(mktemp)
    cat > "$tmpfile"
    conduktor apply -f "$tmpfile"
    local rc=$?
    rm -f "$tmpfile"
    return $rc
}

echo
echo "Setting up vClusters: $VCLUSTER_NAME, $VCLUSTER_ACL_NAME"

# =============================================================================
# vCluster 1: demo (ACL disabled)
# =============================================================================

echo 
echo "Creating vCluster: $VCLUSTER_NAME..."
apply_yaml <<EOF
---
kind: VirtualCluster
apiVersion: gateway/v2
metadata:
  name: $VCLUSTER_NAME
spec:
  aclMode: KAFKA_API
  superUsers:
  - admin  
---
kind: GatewayServiceAccount
apiVersion: gateway/v2
metadata:
  vCluster: $VCLUSTER_NAME
  name: admin
spec:
  type: LOCAL
EOF

TOKEN=$(generate_token "$VCLUSTER_NAME" "admin")

echo 
echo "Adding vCluster: $VCLUSTER_NAME... to Console"

apply_yaml <<EOF
---
apiVersion: v2
kind: KafkaCluster
metadata:
  name: $VCLUSTER_NAME
spec:
  displayName: $VCLUSTER_NAME
  bootstrapServers: localhost:6969
  properties:
    security.protocol: SASL_PLAINTEXT
    sasl.mechanism: PLAIN
    sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='admin' password='$TOKEN';
  kafkaFlavor:
    type: Gateway
    url: $CDK_GATEWAY_BASE_URL
    user: $CDK_GATEWAY_USER
    password: $CDK_GATEWAY_PASSWORD
    virtualCluster: $VCLUSTER_NAME
EOF

# Apply interceptor file if provided
[ $# -ge 1 ] && [ -f "$1" ] && conduktor apply -f "$1"

# =============================================================================
# vCluster 2: demo-acl (ACL enabled)
# =============================================================================

echo 
echo "Creating vCluster: $VCLUSTER_ACL_NAME (ACL enabled)..."
apply_yaml <<EOF
---
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
  name: $VCLUSTER_ACL_NAME
spec:
  aclEnabled: true
  superUsers:
  - admin  
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  vCluster: $VCLUSTER_ACL_NAME
  name: admin
spec:
  type: LOCAL
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  vCluster: $VCLUSTER_ACL_NAME
  name: user1
spec:
  type: LOCAL
EOF

TOKEN_ACL_ADMIN=$(generate_token "$VCLUSTER_ACL_NAME" "admin")
TOKEN_ACL_USER1=$(generate_token "$VCLUSTER_ACL_NAME" "user1")

echo
echo "Adding vCluster: $VCLUSTER_ACL_NAME to console"

apply_yaml <<EOF
---
apiVersion: v2
kind: KafkaCluster
metadata:
  name: $VCLUSTER_ACL_NAME
spec:
  displayName: "$VCLUSTER_ACL_NAME (ACL enabled)"
  bootstrapServers: localhost:6969
  properties:
    security.protocol: SASL_PLAINTEXT
    sasl.mechanism: PLAIN
    sasl.jaas.config: org.apache.kafka.common.security.plain.PlainLoginModule required username='admin' password='$TOKEN_ACL_ADMIN';
  kafkaFlavor:
    type: Gateway
    url: $CDK_GATEWAY_BASE_URL
    user: $CDK_GATEWAY_USER
    password: $CDK_GATEWAY_PASSWORD
    virtualCluster: $VCLUSTER_ACL_NAME
---
apiVersion: v1
kind: ServiceAccount
metadata:
  cluster: $VCLUSTER_ACL_NAME
  name: user1
spec:
  authorization:
    type: KAFKA_ACL
    acls:
      - type: TOPIC
        name: click
        patternType: PREFIXED
        operations: 
          - read
        host: '*'
        permission: Allow
      - type: CONSUMER_GROUP
        name: myconsumer-
        patternType: PREFIXED
        operations: 
          - read
        host: '*'
        permission: Allow
EOF

# =============================================================================
# ACL Demo
# =============================================================================

echo ""
echo "=== Virtual ACL Demo ==="

echo
echo "Admin can create the topic"
kafka-topics \
  --bootstrap-server localhost:6969 \
  --command-config demo-acl-admin.properties \
  --create --if-not-exists \
  --topic click-stream \
  --partitions 3 \
  --replication-factor 1

echo
echo "Admin can write in the topic"
echo '{"event":"admin-test"}'  | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config demo-acl-admin.properties \
  --topic click-stream \

echo
echo "Admin can read from the topic"
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config demo-acl-admin.properties \
  --topic click-stream \
  --from-beginning \
  --max-messages 1

echo
echo "user1 cannot write to the topic"
echo '{"event":"user1-test"}'  | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config demo-acl-user1.properties \
  --topic click-stream

echo
echo "user1 can read from the topic, as kafka-console-consumer is using a consumer group, we specify its name to match the ACL"
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config demo-acl-user1.properties \
  --topic click-stream \
  --group myconsumer-demo \
  --from-beginning \
  --max-messages 1


# =============================================================================
# Summary
# =============================================================================

cat <<EOF

==============================================
Setup completed!
==============================================

Console: $CDK_BASE_URL
    Username: $CDK_USER
    Password: $CDK_PASSWORD

Gateway API: $CDK_GATEWAY_BASE_URL
    Username: $CDK_GATEWAY_USER
    Password: $CDK_GATEWAY_PASSWORD

vCluster: $VCLUSTER_NAME (ACL disabled)
  Username: admin
  Password: $TOKEN

vCluster: $VCLUSTER_ACL_NAME (ACL enabled)
  admin:
    Password: $TOKEN_ACL_ADMIN
  user1 (can only access click.* topics):
    Password: $TOKEN_ACL_USER1

==============================================
EOF
