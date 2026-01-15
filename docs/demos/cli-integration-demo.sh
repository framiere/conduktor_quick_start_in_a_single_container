#!/bin/bash
# =============================================================================
# CLI Integration Demo - Recording Script for Minikube
# =============================================================================
#
# BEFORE RECORDING:
#   1. Deploy Conduktor: ./functional-tests/deploy-conduktor.sh
#   2. Start port-forwards in separate terminals:
#      kubectl port-forward svc/console 8080:8080 -n conduktor
#      kubectl port-forward svc/gateway 8888:8888 -n conduktor
#
# TO RECORD:
#   asciinema rec docs/demos/cli-integration.cast \
#     --title "Conduktor CLI Integration" \
#     --idle-time-limit 2
#
# TIPS FOR HUMAN FEEL:
#   - Type commands manually (don't paste)
#   - Pause briefly between commands
#   - React to output ("Looks good!", "Let's check...")
#   - Make a small typo and fix it (adds authenticity)
#
# =============================================================================

# --- Step 1: Show what we're doing ---
# (Type this, don't run)
echo "=== Conduktor CLI Integration Demo ==="
echo ""
echo "We'll bootstrap authentication tokens for Console and Gateway,"
echo "and store them in a Kubernetes Secret."
echo ""

# --- Step 2: Check services are accessible ---
echo "First, let's check if Console is ready..."
curl -sf http://localhost:8080/platform/api/modules/resources/health/live | jq .
# Expected: {"status":"UP"}

echo ""
echo "And Gateway..."
curl -sf http://localhost:8888/health/ready
# Expected: OK or similar

# --- Step 3: Show the bootstrap script ---
echo ""
echo "Here's our bootstrap script that handles authentication:"
head -50 scripts/bootstrap-conduktor-token.sh

# --- Step 4: Run the bootstrap script ---
echo ""
echo "Let's run it to get tokens and create the Secret..."
./scripts/bootstrap-conduktor-token.sh

# --- Step 5: Verify the secret ---
echo ""
echo "The Secret should now be created. Let's check:"
kubectl get secret conduktor-cli-credentials
kubectl get secret conduktor-cli-credentials -o jsonpath='{.data}' | jq 'keys'

# --- Step 6: Show how the operator uses it ---
echo ""
echo "The operator loads these credentials in Java like this:"
cat <<'CODE'
ConduktorCliCredentials credentials = ConduktorCliCredentials.load();
// Reads from env vars or /var/run/secrets/conduktor/*

ConduktorCli cli = new ConduktorCli(credentials);
cli.apply(virtualCluster);  // Executes: conduktor apply -f <yaml>
CODE

# --- Step 7: Test with dry-run (optional) ---
echo ""
echo "Let's test the CLI with a simple dry-run..."
cat <<'EOF' > /tmp/test-vcluster.yaml
apiVersion: gateway/v2
kind: VirtualCluster
metadata:
  name: demo-cluster
spec:
  prefix: demo_
  bootstrap:
    bootstrapServers:
      - redpanda.conduktor.svc.cluster.local:9093
EOF

echo "Resource to apply:"
cat /tmp/test-vcluster.yaml

# If conduktor CLI is installed:
# export CDK_BASE_URL=http://localhost:8080
# export CDK_TOKEN=$(kubectl get secret conduktor-cli-credentials -o jsonpath='{.data.console-token}' | base64 -d)
# conduktor apply -f /tmp/test-vcluster.yaml --dry-run

# --- Done ---
echo ""
echo "=== Demo Complete ==="
echo "The operator is now configured to apply resources to Console and Gateway!"
