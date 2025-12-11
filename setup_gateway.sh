#!/bin/bash
set -euo pipefail

# Configuration
CDK_BASE_URL="${CDK_BASE_URL:-http://localhost:8080}"
CDK_USER="${CDK_USER:-admin@demo.dev}"
CDK_PASSWORD="${CDK_PASSWORD:-123_ABC_abc}"
CDK_GATEWAY_BASE_URL="${CDK_GATEWAY_BASE_URL:-http://localhost:8888}"
CDK_GATEWAY_USER="${CDK_GATEWAY_USER:-admin}"
CDK_GATEWAY_PASSWORD="${CDK_GATEWAY_PASSWORD:-conduktor}"

CERT_DIR="${CERT_DIR:-certs}"
VCLUSTER="${VCLUSTER:-demo}"
VCLUSTER_ACL="${VCLUSTER}-acl"
VCLUSTER_ADMIN="${VCLUSTER}-admin"
VCLUSTER_ACL_ADMIN="${VCLUSTER_ACL}-admin"
VCLUSTER_ACL_USER="${VCLUSTER_ACL}-user"

# Helper: wait for service to be ready
wait_for_service() {
    local name="$1" url="$2"
    if curl -sf "$url" >/dev/null 2>&1; then
        echo "$name is ready."
        return
    fi
    printf "Waiting for %s to be ready" "$name"
    while ! curl -sf "$url" >/dev/null 2>&1; do sleep 1; printf "."; done
    echo
    echo "$name is ready."
}

# Wait for Console and Gateway to be up
wait_for_service "Conduktor Console" "$CDK_BASE_URL/platform/api/modules/resources/health/live"
wait_for_service "Gateway Admin API" "$CDK_GATEWAY_BASE_URL/health/ready"

# Get Console Bearer token
echo "Authenticating with Console..."
CDK_TOKEN=$(curl -sf "$CDK_BASE_URL/api/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"'"$CDK_USER"'","password":"'"$CDK_PASSWORD"'"}' | jq -r '.access_token')

if [ -z "$CDK_TOKEN" ] || [ "$CDK_TOKEN" = "null" ]; then
    echo "ERROR: Failed to authenticate with Console"
    exit 1
fi
echo "Authenticated."

# Helper: Gateway API call (Basic auth)
gateway_api() {
    local method="$1" endpoint="$2"
    shift 2
    curl -sf -X "$method" "$CDK_GATEWAY_BASE_URL$endpoint" \
        -u "$CDK_GATEWAY_USER:$CDK_GATEWAY_PASSWORD" \
        -H "Content-Type: application/json" \
        "$@" | jq
}

# Helper: Console API call (Bearer token)
console_api() {
    local method="$1" endpoint="$2"
    shift 2
    curl -sf -X "$method" "$CDK_BASE_URL$endpoint" \
        -H "Authorization: Bearer $CDK_TOKEN" \
        -H "Content-Type: application/json" \
        "$@" | jq
}

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
    echo "Created: $props_file"
}

echo
echo "Setting up vClusters with mTLS: $VCLUSTER, $VCLUSTER_ACL"

# =============================================================================
# vCluster 1: demo (ACL disabled)
# =============================================================================

echo
echo "Creating vCluster: $VCLUSTER..."
gateway_api PUT "/gateway/v2/virtual-cluster" -d @- <<EOF
{
    "kind": "VirtualCluster",
    "apiVersion": "gateway/v2",
    "metadata": {
        "name": "$VCLUSTER"
    },
    "spec": {
        "aclMode": "KAFKA_API",
        "superUsers": [
            "$VCLUSTER_ADMIN"
        ]
    }
}
EOF
echo "VirtualCluster/$VCLUSTER created"

echo "Creating service account: $VCLUSTER_ADMIN..."
gateway_api PUT "/gateway/v2/service-account" -d @- <<EOF
{
    "kind": "GatewayServiceAccount",
    "apiVersion": "gateway/v2",
    "metadata": {
        "vCluster": "$VCLUSTER",
        "name": "$VCLUSTER_ADMIN"
    },
    "spec": {
        "type": "EXTERNAL",
        "externalNames": [
            "CN=$VCLUSTER_ADMIN,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"
        ]
    }
}
EOF
echo "GatewayServiceAccount/$VCLUSTER_ADMIN created"

generate_ssl_properties "$VCLUSTER_ADMIN"

echo
echo "Adding vCluster $VCLUSTER to Console..."
console_api PUT "/api/public/console/v2/kafka-cluster" -d @- <<EOF
{
    "apiVersion": "v2",
    "kind": "KafkaCluster",
    "metadata": {
        "name": "$VCLUSTER"
    },
    "spec": {
        "displayName": "$VCLUSTER (mTLS)",
        "bootstrapServers": "localhost:6969",
        "properties": {
            "security.protocol": "SSL",
            "ssl.truststore.location": "/var/lib/conduktor/certs/$VCLUSTER_ADMIN.truststore.jks",
            "ssl.truststore.password": "conduktor",
            "ssl.keystore.location": "/var/lib/conduktor/certs/$VCLUSTER_ADMIN.keystore.jks",
            "ssl.keystore.password": "conduktor",
            "ssl.key.password": "conduktor"
        },
        "kafkaFlavor": {
            "type": "Gateway",
            "url": "$CDK_GATEWAY_BASE_URL",
            "user": "$CDK_GATEWAY_USER",
            "password": "$CDK_GATEWAY_PASSWORD",
            "virtualCluster": "$VCLUSTER"
        }
    }
}
EOF
echo "KafkaCluster/$VCLUSTER created in Console"

# =============================================================================
# vCluster 2: demo-acl (ACL enabled)
# =============================================================================

echo
echo "Creating vCluster: $VCLUSTER_ACL (ACL enabled)..."
gateway_api PUT "/gateway/v2/virtual-cluster" -d @- <<EOF
{
    "kind": "VirtualCluster",
    "apiVersion": "gateway/v2",
    "metadata": {
        "name": "$VCLUSTER_ACL"
    },
    "spec": {
        "aclEnabled": true,
        "superUsers": [
            "$VCLUSTER_ACL_ADMIN"
        ]
    }
}
EOF
echo "VirtualCluster/$VCLUSTER_ACL created"

echo "Creating service account: $VCLUSTER_ACL_ADMIN..."
gateway_api PUT "/gateway/v2/service-account" -d @- <<EOF
{
    "kind": "GatewayServiceAccount",
    "apiVersion": "gateway/v2",
    "metadata": {
        "vCluster": "$VCLUSTER_ACL",
        "name": "$VCLUSTER_ACL_ADMIN"
    },
    "spec": {
        "type": "EXTERNAL",
        "externalNames": [
            "CN=$VCLUSTER_ACL_ADMIN,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"
        ]
    }
}
EOF
echo "GatewayServiceAccount/$VCLUSTER_ACL_ADMIN created"

echo "Creating service account: $VCLUSTER_ACL_USER..."
gateway_api PUT "/gateway/v2/service-account" -d @- <<EOF
{
    "kind": "GatewayServiceAccount",
    "apiVersion": "gateway/v2",
    "metadata": {
        "vCluster": "$VCLUSTER_ACL",
        "name": "$VCLUSTER_ACL_USER"
    },
    "spec": {
        "type": "EXTERNAL",
        "externalNames": [
            "CN=$VCLUSTER_ACL_USER,OU=TEST,O=CONDUKTOR,L=LONDON,C=UK"
        ]
    }
}
EOF
echo "GatewayServiceAccount/$VCLUSTER_ACL_USER created"

generate_ssl_properties "$VCLUSTER_ACL_ADMIN"
generate_ssl_properties "$VCLUSTER_ACL_USER"

echo
echo "Adding vCluster $VCLUSTER_ACL to Console..."
console_api PUT "/api/public/console/v2/kafka-cluster" -d @- <<EOF
{
    "apiVersion": "v2",
    "kind": "KafkaCluster",
    "metadata": {
        "name": "$VCLUSTER_ACL"
    },
    "spec": {
        "displayName": "$VCLUSTER_ACL (mTLS + ACL)",
        "bootstrapServers": "localhost:6969",
        "properties": {
            "security.protocol": "SSL",
            "ssl.truststore.location": "/var/lib/conduktor/certs/$VCLUSTER_ACL_ADMIN.truststore.jks",
            "ssl.truststore.password": "conduktor",
            "ssl.keystore.location": "/var/lib/conduktor/certs/$VCLUSTER_ACL_ADMIN.keystore.jks",
            "ssl.keystore.password": "conduktor",
            "ssl.key.password": "conduktor"
        },
        "kafkaFlavor": {
            "type": "Gateway",
            "url": "$CDK_GATEWAY_BASE_URL",
            "user": "$CDK_GATEWAY_USER",
            "password": "$CDK_GATEWAY_PASSWORD",
            "virtualCluster": "$VCLUSTER_ACL"
        }
    }
}
EOF
echo "KafkaCluster/$VCLUSTER_ACL created in Console"

echo
echo "Creating Console ServiceAccount with ACLs for $VCLUSTER_ACL_USER..."
console_api PUT "/api/public/self-serve/v1/cluster/$VCLUSTER_ACL/service-account" -d @- <<EOF
{
    "apiVersion": "v1",
    "kind": "ServiceAccount",
    "metadata": {
        "cluster": "$VCLUSTER_ACL",
        "name": "$VCLUSTER_ACL_USER"
    },
    "spec": {
        "authorization": {
            "type": "KAFKA_ACL",
            "acls": [
                {
                    "type": "TOPIC",
                    "name": "click",
                    "patternType": "PREFIXED",
                    "operations": [
                        "read"
                    ],
                    "host": "*",
                    "permission": "Allow"
                },
                {
                    "type": "CONSUMER_GROUP",
                    "name": "myconsumer-",
                    "patternType": "PREFIXED",
                    "operations": [
                        "read"
                    ],
                    "host": "*",
                    "permission": "Allow"
                }
            ]
        }
    }
}
EOF
echo "ServiceAccount/$VCLUSTER_ACL_USER created in Console"

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
