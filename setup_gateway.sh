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
