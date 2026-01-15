#!/bin/bash
# Demo recording script - clean output without typing simulation

set -e

clear
echo "=== Conduktor CLI Integration Demo ==="
echo ""
echo "We'll bootstrap auth tokens for Console and Gateway,"
echo "and store them in a Kubernetes Secret."
sleep 2

echo ""
echo "--- Step 1: Check if services are ready ---"
echo ""
echo '$ curl -sf http://localhost:8090/api/login -X POST -H "Content-Type: application/json" -d "{...}" | jq ".username, .token_type"'
curl -sf http://localhost:8090/api/login -X POST -H "Content-Type: application/json" -d '{"username":"admin@demo.dev","password":"123_ABC_abc"}' | jq '.username, .token_type'
sleep 1

echo ""
echo "$ curl -sf http://localhost:8888/health/ready"
curl -sf http://localhost:8888/health/ready
echo ""
sleep 1

echo ""
echo "--- Step 2: Look at the bootstrap script ---"
echo ""
echo "$ head -35 scripts/bootstrap-conduktor-token.sh"
head -35 scripts/bootstrap-conduktor-token.sh
sleep 2

echo ""
echo "--- Step 3: Run the bootstrap script ---"
echo ""

# Delete existing secret first
kubectl delete secret conduktor-cli-credentials -n conduktor 2>/dev/null || true

echo '$ CONSOLE_URL=http://localhost:8090 GATEWAY_URL=http://localhost:8888 SECRET_NAMESPACE=conduktor ./scripts/bootstrap-conduktor-token.sh'
CONSOLE_URL=http://localhost:8090 GATEWAY_URL=http://localhost:8888 SECRET_NAMESPACE=conduktor ./scripts/bootstrap-conduktor-token.sh
sleep 1

echo ""
echo "--- Step 4: Verify the Secret ---"
echo ""
echo "$ kubectl get secret conduktor-cli-credentials -n conduktor"
kubectl get secret conduktor-cli-credentials -n conduktor
sleep 1

echo ""
echo "$ kubectl get secret conduktor-cli-credentials -n conduktor -o jsonpath='{.data}' | jq 'keys'"
kubectl get secret conduktor-cli-credentials -n conduktor -o jsonpath='{.data}' | jq 'keys'
sleep 1

echo ""
echo "--- Step 5: Decode the token (first 50 chars) ---"
echo ""
echo "$ kubectl get secret ... -o jsonpath='{.data.console-token}' | base64 -d | cut -c1-50"
kubectl get secret conduktor-cli-credentials -n conduktor -o jsonpath='{.data.console-token}' | base64 -d | cut -c1-50
echo "..."
sleep 1

echo ""
echo "=== Demo Complete! ==="
echo "The operator can now use these credentials to apply"
echo "resources to Console and Gateway via the CLI."
echo ""
