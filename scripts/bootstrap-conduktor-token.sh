#!/bin/bash
#
# Bootstrap script for Conduktor CLI credentials
#
# This script authenticates to Conduktor Console, retrieves an access token,
# and stores it (along with Gateway credentials) in a Kubernetes Secret.
#
# Usage:
#   ./bootstrap-conduktor-token.sh
#
# Environment variables:
#   CONSOLE_URL       - Console URL (default: http://localhost:8080)
#   CONSOLE_USER      - Console username (default: admin@demo.dev)
#   CONSOLE_PASSWORD  - Console password (default: 123_ABC_abc)
#   GATEWAY_URL       - Gateway Admin API URL (default: http://localhost:8888)
#   GATEWAY_USER      - Gateway username (default: admin)
#   GATEWAY_PASSWORD  - Gateway password (default: conduktor)
#   SECRET_NAME       - Kubernetes Secret name (default: conduktor-cli-credentials)
#   SECRET_NAMESPACE  - Kubernetes namespace (default: default)
#   DRY_RUN           - If set, only print the Secret YAML without applying
#

set -euo pipefail

# Configuration with defaults
CONSOLE_URL="${CONSOLE_URL:-http://localhost:8080}"
CONSOLE_USER="${CONSOLE_USER:-admin@demo.dev}"
CONSOLE_PASSWORD="${CONSOLE_PASSWORD:-123_ABC_abc}"
GATEWAY_URL="${GATEWAY_URL:-http://localhost:8888}"
GATEWAY_USER="${GATEWAY_USER:-admin}"
GATEWAY_PASSWORD="${GATEWAY_PASSWORD:-conduktor}"
SECRET_NAME="${SECRET_NAME:-conduktor-cli-credentials}"
SECRET_NAMESPACE="${SECRET_NAMESPACE:-default}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Wait for Console to be ready
wait_for_console() {
    local max_attempts=60
    local attempt=1
    local health_url="${CONSOLE_URL}/platform/api/modules/resources/health/live"

    log_info "Waiting for Console at ${CONSOLE_URL}..."

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${health_url}" > /dev/null 2>&1; then
            log_info "Console is ready."
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo ""
    log_error "Console did not become ready after ${max_attempts} attempts"
    return 1
}

# Wait for Gateway to be ready
wait_for_gateway() {
    local max_attempts=60
    local attempt=1
    local health_url="${GATEWAY_URL}/health/ready"

    log_info "Waiting for Gateway at ${GATEWAY_URL}..."

    while [ $attempt -le $max_attempts ]; do
        if curl -sf "${health_url}" > /dev/null 2>&1; then
            log_info "Gateway is ready."
            return 0
        fi
        echo -n "."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo ""
    log_error "Gateway did not become ready after ${max_attempts} attempts"
    return 1
}

# Authenticate to Console and get access token
get_console_token() {
    log_info "Authenticating to Console as ${CONSOLE_USER}..."

    local response
    response=$(curl -sf -X POST "${CONSOLE_URL}/api/login" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"${CONSOLE_USER}\",\"password\":\"${CONSOLE_PASSWORD}\"}" \
        2>&1) || {
        log_error "Failed to authenticate to Console"
        log_error "Response: ${response}"
        return 1
    }

    # Extract access_token from JSON response
    local token
    token=$(echo "${response}" | jq -r '.access_token // empty')

    if [ -z "${token}" ]; then
        log_error "No access_token in response"
        log_error "Response: ${response}"
        return 1
    fi

    log_info "Successfully obtained Console access token"
    echo "${token}"
}

# Create or update the Kubernetes Secret
create_secret() {
    local token="$1"

    log_info "Creating/updating Secret '${SECRET_NAME}' in namespace '${SECRET_NAMESPACE}'..."

    local secret_yaml
    secret_yaml=$(cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${SECRET_NAME}
  namespace: ${SECRET_NAMESPACE}
  labels:
    app.kubernetes.io/name: conduktor-cli
    app.kubernetes.io/component: credentials
type: Opaque
stringData:
  console-url: "${CONSOLE_URL}"
  console-token: "${token}"
  gateway-url: "${GATEWAY_URL}"
  gateway-user: "${GATEWAY_USER}"
  gateway-password: "${GATEWAY_PASSWORD}"
EOF
)

    if [ -n "${DRY_RUN:-}" ]; then
        log_info "DRY_RUN mode - Secret YAML:"
        echo "${secret_yaml}"
        return 0
    fi

    echo "${secret_yaml}" | kubectl apply -f -

    log_info "Secret '${SECRET_NAME}' created/updated successfully"
}

# Verify the token works by making a test API call
verify_token() {
    local token="$1"

    log_info "Verifying token with Console API..."

    local response
    if curl -sf -X GET "${CONSOLE_URL}/api/public/v1/info" \
        -H "Authorization: Bearer ${token}" \
        > /dev/null 2>&1; then
        log_info "Token verification successful"
        return 0
    else
        log_warn "Token verification failed - token may still work for CLI"
        return 0  # Don't fail, just warn
    fi
}

# Main
main() {
    log_info "=== Conduktor CLI Bootstrap ==="
    log_info "Console URL: ${CONSOLE_URL}"
    log_info "Gateway URL: ${GATEWAY_URL}"
    log_info "Secret: ${SECRET_NAMESPACE}/${SECRET_NAME}"
    echo ""

    # Wait for services
    wait_for_console
    wait_for_gateway

    # Get token
    local token
    token=$(get_console_token)

    # Verify token (optional, non-blocking)
    verify_token "${token}"

    # Create secret
    create_secret "${token}"

    echo ""
    log_info "=== Bootstrap complete ==="
    log_info "The Conduktor CLI can now use credentials from Secret '${SECRET_NAME}'"
}

main "$@"
