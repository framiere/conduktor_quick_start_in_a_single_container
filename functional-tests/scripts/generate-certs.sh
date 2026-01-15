#!/usr/bin/env bash
set -euo pipefail

#
# TLS Certificate Generator for Webhook
# Generates self-signed CA and server certificates for the validating webhook
#
# Environment Variables:
#   NAMESPACE       - Kubernetes namespace (reads from .test-namespace if not set)
#   SERVICE_NAME    - Webhook service name (default: messaging-operator-webhook)
#   CERT_DIR        - Directory to store certificates (default: .certs)
#   CERT_DAYS       - Certificate validity in days (default: 365)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FUNC_TESTS_DIR="$SCRIPT_DIR/.."

# Configuration
NAMESPACE="${NAMESPACE:-}"
SERVICE_NAME="${SERVICE_NAME:-messaging-operator-webhook}"
CERT_DIR="${CERT_DIR:-$FUNC_TESTS_DIR/.certs}"
CERT_DAYS="${CERT_DAYS:-365}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m'

# Logging functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Read namespace from file if not set
read_namespace() {
    if [[ -z "$NAMESPACE" ]]; then
        local ns_file="$FUNC_TESTS_DIR/.test-namespace"
        if [[ -f "$ns_file" ]]; then
            NAMESPACE=$(cat "$ns_file")
        else
            error "Namespace not set and .test-namespace file not found"
            error "Run setup-minikube.sh first or set NAMESPACE env var"
            exit 1
        fi
    fi
}

# Create certificate directory
setup_cert_dir() {
    info "Creating certificate directory: $CERT_DIR"
    rm -rf "$CERT_DIR"
    mkdir -p "$CERT_DIR"
}

# Generate CA certificate
generate_ca() {
    info "Generating CA private key..."
    openssl genrsa -out "$CERT_DIR/ca.key" 2048

    info "Generating CA certificate..."
    openssl req -x509 -new -nodes \
        -key "$CERT_DIR/ca.key" \
        -sha256 \
        -days "$CERT_DAYS" \
        -out "$CERT_DIR/ca.crt" \
        -subj "/CN=Messaging Operator CA/O=messaging-operator"

    success "CA certificate generated"
}

# Generate server certificate
generate_server_cert() {
    local service_fqdn="${SERVICE_NAME}.${NAMESPACE}.svc"
    local service_full_fqdn="${SERVICE_NAME}.${NAMESPACE}.svc.cluster.local"

    info "Generating server private key..."
    openssl genrsa -out "$CERT_DIR/server.key" 2048

    info "Creating certificate signing request..."
    # Create CSR config with SANs
    cat > "$CERT_DIR/server.conf" <<EOF
[req]
req_extensions = v3_req
distinguished_name = req_distinguished_name
prompt = no

[req_distinguished_name]
CN = ${service_fqdn}
O = messaging-operator

[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = ${SERVICE_NAME}
DNS.2 = ${SERVICE_NAME}.${NAMESPACE}
DNS.3 = ${service_fqdn}
DNS.4 = ${service_full_fqdn}
EOF

    openssl req -new \
        -key "$CERT_DIR/server.key" \
        -out "$CERT_DIR/server.csr" \
        -config "$CERT_DIR/server.conf"

    info "Signing server certificate with CA..."
    openssl x509 -req \
        -in "$CERT_DIR/server.csr" \
        -CA "$CERT_DIR/ca.crt" \
        -CAkey "$CERT_DIR/ca.key" \
        -CAcreateserial \
        -out "$CERT_DIR/server.crt" \
        -days "$CERT_DAYS" \
        -sha256 \
        -extensions v3_req \
        -extfile "$CERT_DIR/server.conf"

    success "Server certificate generated with SANs:"
    echo "  - ${SERVICE_NAME}"
    echo "  - ${SERVICE_NAME}.${NAMESPACE}"
    echo "  - ${service_fqdn}"
    echo "  - ${service_full_fqdn}"
}

# Export base64-encoded values
export_base64() {
    info "Exporting base64-encoded certificates..."

    # Export to environment-friendly format
    export TLS_CRT_B64=$(base64 -w0 < "$CERT_DIR/server.crt")
    export TLS_KEY_B64=$(base64 -w0 < "$CERT_DIR/server.key")
    export CA_CRT_B64=$(base64 -w0 < "$CERT_DIR/ca.crt")

    # Write values file for Helm
    cat > "$FUNC_TESTS_DIR/values-tls.yaml" <<EOF
# Generated TLS certificates - DO NOT COMMIT
# Generated at: $(date -Iseconds)
# Namespace: ${NAMESPACE}
# Service: ${SERVICE_NAME}

tls:
  generate: false
  cert: "${TLS_CRT_B64}"
  key: "${TLS_KEY_B64}"
  caCert: "${CA_CRT_B64}"
EOF

    success "TLS values written to values-tls.yaml"
}

# Print certificate info
print_info() {
    echo ""
    echo "=========================================="
    echo "  Certificate Information"
    echo "=========================================="
    echo ""
    echo "  CA Certificate:"
    openssl x509 -in "$CERT_DIR/ca.crt" -noout -subject -dates | sed 's/^/    /'
    echo ""
    echo "  Server Certificate:"
    openssl x509 -in "$CERT_DIR/server.crt" -noout -subject -dates | sed 's/^/    /'
    echo ""
    echo "  Files created:"
    ls -la "$CERT_DIR" | sed 's/^/    /'
    echo ""
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo "  TLS Certificate Generator"
    echo "=========================================="
    echo ""

    # Check prerequisites
    if ! command -v openssl &>/dev/null; then
        error "openssl is not installed"
        exit 1
    fi

    read_namespace
    info "Using namespace: $NAMESPACE"
    info "Service name: $SERVICE_NAME"

    setup_cert_dir
    generate_ca
    generate_server_cert
    export_base64
    print_info

    success "Certificate generation complete!"
}

main "$@"
