#!/bin/bash
# Demo recording script with natural timing
# This script simulates human typing for asciinema recording

# Typing simulation - prints chars with delay
type_cmd() {
    local cmd="$1"
    echo -n "$ "
    for ((i=0; i<${#cmd}; i++)); do
        echo -n "${cmd:$i:1}"
        sleep 0.03
    done
    echo ""
    sleep 0.3
}

# Run command after typing it
run() {
    type_cmd "$1"
    eval "$1"
    sleep 1
}

# Just show text with pause
say() {
    echo -e "\n$1"
    sleep 1.5
}

clear
say "=== Conduktor CLI Integration Demo ==="
say "We'll bootstrap auth tokens for Console and Gateway,"
say "and store them in a Kubernetes Secret."

sleep 1

say "\n--- Step 1: Check if services are ready ---"
run "curl -sf http://localhost:8090/api/login -X POST -H 'Content-Type: application/json' -d '{\"username\":\"admin@demo.dev\",\"password\":\"123_ABC_abc\"}' | jq '.username, .token_type'"

sleep 0.5
say "Console is ready and authentication works!"

run "curl -sf http://localhost:8888/health/ready"
say "Gateway is ready!"

say "\n--- Step 2: Look at the bootstrap script ---"
run "head -35 scripts/bootstrap-conduktor-token.sh"

say "\n--- Step 3: Run the bootstrap script ---"
say "This will authenticate, get a token, and create a K8s Secret..."
sleep 1

# Delete existing secret first
kubectl delete secret conduktor-cli-credentials -n conduktor 2>/dev/null || true
sleep 0.5

run "CONSOLE_URL=http://localhost:8090 GATEWAY_URL=http://localhost:8888 SECRET_NAMESPACE=conduktor ./scripts/bootstrap-conduktor-token.sh"

say "\n--- Step 4: Verify the Secret ---"
run "kubectl get secret conduktor-cli-credentials -n conduktor"

say "Let's see what keys are stored:"
run "kubectl get secret conduktor-cli-credentials -n conduktor -o jsonpath='{.data}' | jq 'keys'"

say "\n--- Step 5: Decode and verify the token ---"
run "kubectl get secret conduktor-cli-credentials -n conduktor -o jsonpath='{.data.console-token}' | base64 -d | cut -c1-50"
say "...token continues (truncated for display)"

say "\n=== Demo Complete! ==="
say "The operator can now use these credentials to apply"
say "resources to Console and Gateway via the CLI."
echo ""
