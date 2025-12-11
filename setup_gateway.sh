#!/bin/bash
set -euo pipefail

# Configuration
export CDK_BASE_URL="${CDK_BASE_URL:-http://localhost:8080}"
export CDK_USER="${CDK_USER:-admin@demo.dev}"
export CDK_PASSWORD="${CDK_PASSWORD:-123_ABC_abc}"
export CDK_GATEWAY_BASE_URL="${CDK_GATEWAY_BASE_URL:-http://localhost:8888}"
export CDK_GATEWAY_USER="${CDK_GATEWAY_USER:-admin}"
export CDK_GATEWAY_PASSWORD="${CDK_GATEWAY_PASSWORD:-conduktor}"

CERT_DIR="${CERT_DIR:-certs}"
VCLUSTER="${VCLUSTER:-demo}"
VCLUSTER_ACL="${VCLUSTER}-acl"
VCLUSTER_ADMIN="${VCLUSTER}-admin"
 
VCLUSTER_ACL_ADMIN="${VCLUSTER_ACL}-admin"
VCLUSTER_ACL_USER="${VCLUSTER_ACL}-user"

# Helper: wait for service to be ready
wait_for_service() {
    local name="$1" url="$2" max_attempts="${3:-90}"
    for i in $(seq 1 $max_attempts); do
        if curl -sf -o /dev/null "$url"; then
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
        [ -n "$EXTRACTED" ] && [ "$EXTRACTED" != "null" ] && VCLUSTER="$EXTRACTED"
    else
        VCLUSTER="$1"
    fi
fi

# Helper: generate SSL properties file for a user
generate_ssl_properties() {
    local user="$1"
    local props_file="$user.properties"

    cat > "$props_file" <<EOF
security.protocol=SSL
ssl.truststore.location=$CERT_DIR/$user.truststore.jks
ssl.truststore.password=conduktor
ssl.keystore.location=$CERT_DIR/$user.keystore.jks
ssl.keystore.password=conduktor
ssl.key.password=conduktor
EOF

    echo "Created mTLS properties file: $props_file"
    echo "$props_file"
}

i=0
# Helper: apply yaml via temp file
apply_yaml() {
    local tmpfile=$(mktemp)
    cat > "$tmpfile"
    mkdir -p .temp 
    cp $tmpfile .temp/$i.yaml
    i=$((i+1))
    conduktor apply -f "$tmpfile"
    local rc=$?
    rm -f "$tmpfile"
    return $rc
}

echo
echo "Setting up vClusters with mTLS: $VCLUSTER, $VCLUSTER_ACL"

# =============================================================================
# vCluster 1: demo (ACL disabled)
# =============================================================================

echo
echo "Creating vCluster: $VCLUSTER..."
apply_yaml <<EOF
---
kind: VirtualCluster
apiVersion: gateway/v2
metadata:
  name: $VCLUSTER
spec:
  aclMode: KAFKA_API
  superUsers:
  - $VCLUSTER_ADMIN
---
kind: GatewayServiceAccount
apiVersion: gateway/v2
metadata:
  vCluster: $VCLUSTER
  name: $VCLUSTER_ADMIN
spec:
  type: EXTERNAL
  externalNames:
    - CN=$VCLUSTER_ADMIN,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK

EOF

generate_ssl_properties "$VCLUSTER_ADMIN"

echo
echo "Adding vCluster: $VCLUSTER... to Console"

apply_yaml <<EOF
---
apiVersion: v2
kind: KafkaCluster
metadata:
  name: $VCLUSTER
spec:
  displayName: "$VCLUSTER (mTLS)"
  bootstrapServers: localhost:6969
  properties:
    security.protocol: SSL
    ssl.truststore.location: /var/lib/conduktor/certs/$VCLUSTER_ADMIN.truststore.jks
    ssl.truststore.password: conduktor
    ssl.keystore.location: /var/lib/conduktor/certs/$VCLUSTER_ADMIN.keystore.jks
    ssl.keystore.password: conduktor
    ssl.key.password: conduktor
  kafkaFlavor:
    type: Gateway
    url: $CDK_GATEWAY_BASE_URL
    user: $CDK_GATEWAY_USER
    password: $CDK_GATEWAY_PASSWORD
    virtualCluster: $VCLUSTER
EOF

# Apply interceptor file if provided
[ $# -ge 1 ] && [ -f "$1" ] && conduktor apply -f "$1"

# =============================================================================
# vCluster 2: demo-acl (ACL enabled)
# =============================================================================

echo
echo "Creating vCluster: $VCLUSTER_ACL (ACL enabled)..."
apply_yaml <<EOF
---
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
  name: $VCLUSTER_ACL
spec:
  aclEnabled: true
  superUsers:
  - $VCLUSTER_ACL_ADMIN
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  vCluster: $VCLUSTER_ACL
  name: $VCLUSTER_ACL_ADMIN
spec:
  type: EXTERNAL
  externalNames:
    - CN=$VCLUSTER_ACL_ADMIN,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK
---
apiVersion: gateway/v2
kind: GatewayServiceAccount
metadata:
  vCluster: $VCLUSTER_ACL
  name: $VCLUSTER_ACL_USER
spec:
  type: EXTERNAL
  externalNames:
    - CN=$VCLUSTER_ACL_USER,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK
EOF

generate_ssl_properties "$VCLUSTER_ACL_ADMIN"
generate_ssl_properties "$VCLUSTER_ACL_USER"

echo
echo "Adding vCluster: $VCLUSTER_ACL to console"

apply_yaml <<EOF
---
apiVersion: v2
kind: KafkaCluster
metadata:
  name: $VCLUSTER_ACL
spec:
  displayName: "$VCLUSTER_ACL (mTLS + ACL)"
  bootstrapServers: localhost:6969
  properties:
    security.protocol: SSL
    ssl.truststore.location: /var/lib/conduktor/certs/$VCLUSTER_ACL_ADMIN.truststore.jks
    ssl.truststore.password: conduktor
    ssl.keystore.location: /var/lib/conduktor/certs/$VCLUSTER_ACL_ADMIN.keystore.jks
    ssl.keystore.password: conduktor
    ssl.key.password: conduktor
  kafkaFlavor:
    type: Gateway
    url: $CDK_GATEWAY_BASE_URL
    user: $CDK_GATEWAY_USER
    password: $CDK_GATEWAY_PASSWORD
    virtualCluster: $VCLUSTER_ACL
---
apiVersion: v1
kind: ServiceAccount
metadata:
  cluster: $VCLUSTER_ACL
  name: $VCLUSTER_ACL_USER
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
# ACL Demo with mTLS
# =============================================================================

echo ""
echo "=== Virtual ACL Demo (mTLS) ==="

echo
echo "$VCLUSTER_ACL_ADMIN can create the topic"
kafka-topics \
  --bootstrap-server localhost:6969 \
  --command-config $VCLUSTER_ACL_ADMIN.properties \
  --create --if-not-exists \
  --topic click-stream \
  --partitions 3 \
  --replication-factor 1

echo
echo "$VCLUSTER_ACL_ADMIN can write in the topic"
echo '{"event":"admin-test"}'  | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config $VCLUSTER_ACL_ADMIN.properties \
  --topic click-stream \

echo
echo "$VCLUSTER_ACL_ADMIN can read from the topic"
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config $VCLUSTER_ACL_ADMIN.properties \
  --topic click-stream \
  --from-beginning \
  --max-messages 1

echo
echo "$VCLUSTER_ACL_USER cannot write to the topic"
echo '{"event":"user-test"}'  | kafka-console-producer \
  --bootstrap-server localhost:6969 \
  --producer.config $VCLUSTER_ACL_USER.properties \
  --topic click-stream

echo
echo "$VCLUSTER_ACL_USER can read from the topic, as kafka-console-consumer is using a consumer group, we specify its name to match the ACL"
kafka-console-consumer \
  --bootstrap-server localhost:6969 \
  --consumer.config $VCLUSTER_ACL_USER.properties \
  --topic click-stream \
  --group myconsumer-demo \
  --from-beginning \
  --max-messages 1

echo "Demo complete"
