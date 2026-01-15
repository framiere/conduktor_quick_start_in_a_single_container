#!/bin/bash
#
# Deploy Conduktor Console + Gateway + Redpanda to Minikube
#
# This script sets up a complete Conduktor environment for CLI integration demo.
#
# Usage:
#   ./functional-tests/deploy-conduktor.sh
#
# Prerequisites:
#   - Minikube running (./functional-tests/setup-minikube.sh)
#   - Helm installed
#

set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

NAMESPACE="${NAMESPACE:-conduktor}"

# Check prerequisites
check_prerequisites() {
    log_info "Checking prerequisites..."

    if ! command -v minikube &> /dev/null; then
        log_error "minikube not found. Please install it first."
        exit 1
    fi

    if ! command -v helm &> /dev/null; then
        log_error "helm not found. Please install it first."
        exit 1
    fi

    if ! minikube status &> /dev/null; then
        log_error "Minikube is not running. Start it with: minikube start"
        exit 1
    fi

    log_info "Prerequisites OK"
}

# Add Helm repos
setup_helm_repos() {
    log_info "Setting up Helm repositories..."

    # Conduktor Helm repo
    helm repo add conduktor https://helm.conduktor.io 2>/dev/null || true

    # Redpanda Helm repo
    helm repo add redpanda https://charts.redpanda.com 2>/dev/null || true

    # Bitnami repo (for PostgreSQL)
    helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true

    helm repo update

    log_info "Helm repos configured"
}

# Create namespace
create_namespace() {
    log_info "Creating namespace ${NAMESPACE}..."
    kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
}

# Deploy PostgreSQL (for Console)
deploy_postgresql() {
    log_info "Deploying PostgreSQL..."

    cat <<EOF | helm upgrade --install postgresql bitnami/postgresql \
        --namespace "${NAMESPACE}" \
        -f - \
        --wait --timeout 5m
auth:
  postgresPassword: "conduktor"
  database: "conduktor"
primary:
  resources:
    requests:
      memory: "256Mi"
      cpu: "100m"
    limits:
      memory: "512Mi"
      cpu: "500m"
  persistence:
    size: "1Gi"
EOF

    log_info "PostgreSQL deployed"
}

# Deploy Redpanda (Kafka-compatible)
deploy_redpanda() {
    log_info "Deploying Redpanda..."

    cat <<EOF | helm upgrade --install redpanda redpanda/redpanda \
        --namespace "${NAMESPACE}" \
        -f - \
        --wait --timeout 5m
statefulset:
  replicas: 1
resources:
  cpu:
    cores: "1"
  memory:
    container:
      max: "1536Mi"
storage:
  persistentVolume:
    size: "2Gi"
external:
  enabled: false
auth:
  sasl:
    enabled: false
tls:
  enabled: false
EOF

    log_info "Redpanda deployed"
}

# Deploy Conduktor Console
deploy_console() {
    log_info "Deploying Conduktor Console..."

    # Create Console values
    cat <<EOF | helm upgrade --install console conduktor/console \
        --namespace "${NAMESPACE}" \
        -f - \
        --wait --timeout 5m
config:
  database:
    url: "postgresql://postgres:conduktor@postgresql.${NAMESPACE}.svc.cluster.local:5432/conduktor"
  organization:
    name: "Demo Organization"
  admin:
    email: "admin@demo.dev"
    password: "123_ABC_abc"

  clusters:
    - id: local
      name: "Local Redpanda"
      bootstrapServers: "redpanda.${NAMESPACE}.svc.cluster.local:9093"

resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1"

service:
  type: ClusterIP
  port: 8080
EOF

    log_info "Console deployed"
}

# Deploy Conduktor Gateway
deploy_gateway() {
    log_info "Deploying Conduktor Gateway..."

    cat <<EOF | helm upgrade --install gateway conduktor/gateway \
        --namespace "${NAMESPACE}" \
        -f - \
        --wait --timeout 5m
config:
  kafka:
    bootstrapServers: "redpanda.${NAMESPACE}.svc.cluster.local:9093"

  gateway:
    portRange:
      start: 9092
      end: 9099

  admin:
    port: 8888
    users:
      admin: "conduktor"

resources:
  requests:
    memory: "256Mi"
    cpu: "100m"
  limits:
    memory: "512Mi"
    cpu: "500m"

service:
  type: ClusterIP
  adminPort: 8888
  gatewayPort: 9092
EOF

    log_info "Gateway deployed"
}

# Wait for pods to be ready
wait_for_pods() {
    log_info "Waiting for all pods to be ready..."
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=redpanda -n "${NAMESPACE}" --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=console -n "${NAMESPACE}" --timeout=300s || true
    kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=gateway -n "${NAMESPACE}" --timeout=300s || true
}

# Show status
show_status() {
    echo ""
    log_info "=== Deployment Complete ==="
    echo ""
    kubectl get pods -n "${NAMESPACE}"
    echo ""
    log_info "Services:"
    kubectl get svc -n "${NAMESPACE}"
    echo ""
    log_info "To access Console:"
    echo "  kubectl port-forward svc/console 8080:80 -n ${NAMESPACE}"
    echo "  Open: http://localhost:8080"
    echo "  Login: admin@demo.dev / 123_ABC_abc"
    echo ""
    log_info "To access Gateway Admin API:"
    echo "  kubectl port-forward svc/gateway-conduktor-gateway-internal 8888:8888 -n ${NAMESPACE}"
    echo ""
    log_info "To run the bootstrap script:"
    echo "  CONSOLE_URL=http://localhost:8080 ./scripts/bootstrap-conduktor-token.sh"
}

# Main
main() {
    log_info "=== Conduktor Deployment for Minikube ==="

    check_prerequisites
    setup_helm_repos
    create_namespace
    deploy_postgresql
    deploy_redpanda
    deploy_console
    deploy_gateway
    wait_for_pods
    show_status
}

main "$@"
