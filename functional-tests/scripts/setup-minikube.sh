#!/usr/bin/env bash
set -euo pipefail

#
# Minikube Cluster Setup Script
# Sets up or reuses a Minikube cluster for functional tests
#
# Environment Variables:
#   CLUSTER_NAME    - Minikube profile name (default: messaging-operator-test)
#   FRESH_CLUSTER   - Delete existing cluster if true (default: false)
#   NAMESPACE       - Kubernetes namespace for tests (default: test-<timestamp>)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FUNC_TESTS_DIR="$SCRIPT_DIR/.."

# Configuration
CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"
FRESH_CLUSTER="${FRESH_CLUSTER:-false}"
NAMESPACE="${NAMESPACE:-test-$(date +%s)}"

# Minikube resource settings
MINIKUBE_CPUS="${MINIKUBE_CPUS:-2}"
MINIKUBE_MEMORY="${MINIKUBE_MEMORY:-4096}"
MINIKUBE_DRIVER="${MINIKUBE_DRIVER:-docker}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Logging functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Check if cluster exists
cluster_exists() {
    minikube status -p "$CLUSTER_NAME" &>/dev/null
}

# Check if cluster is running
cluster_running() {
    local status
    status=$(minikube status -p "$CLUSTER_NAME" --format='{{.Host}}' 2>/dev/null || echo "")
    [[ "$status" == "Running" ]]
}

# Delete existing cluster
delete_cluster() {
    info "Deleting existing cluster '$CLUSTER_NAME'..."
    minikube delete -p "$CLUSTER_NAME" || true
    success "Cluster deleted"
}

# Create new cluster
create_cluster() {
    info "Creating new Minikube cluster '$CLUSTER_NAME'..."
    info "  Driver: $MINIKUBE_DRIVER"
    info "  CPUs: $MINIKUBE_CPUS"
    info "  Memory: ${MINIKUBE_MEMORY}MB"

    minikube start \
        -p "$CLUSTER_NAME" \
        --driver="$MINIKUBE_DRIVER" \
        --cpus="$MINIKUBE_CPUS" \
        --memory="$MINIKUBE_MEMORY" \
        --wait=all

    success "Cluster created and ready"
}

# Start existing cluster
start_cluster() {
    info "Starting existing cluster '$CLUSTER_NAME'..."
    minikube start -p "$CLUSTER_NAME" --wait=all
    success "Cluster started"
}

# Create namespace
create_namespace() {
    info "Creating namespace '$NAMESPACE'..."

    # Delete namespace if it exists (for fresh start)
    kubectl delete namespace "$NAMESPACE" --ignore-not-found=true --wait=true 2>/dev/null || true

    # Create fresh namespace
    kubectl create namespace "$NAMESPACE"
    success "Namespace '$NAMESPACE' created"
}

# Write namespace to file for other scripts
write_namespace_file() {
    local ns_file="$FUNC_TESTS_DIR/.test-namespace"
    echo "$NAMESPACE" > "$ns_file"
    success "Namespace written to $ns_file"
}

# Set kubectl context
set_context() {
    info "Setting kubectl context to cluster '$CLUSTER_NAME'..."
    kubectl config use-context "$CLUSTER_NAME"
    success "Context set"
}

# Verify cluster health
verify_cluster() {
    info "Verifying cluster health..."

    # Check nodes
    local nodes
    nodes=$(kubectl get nodes -o name | wc -l)
    if [[ "$nodes" -lt 1 ]]; then
        error "No nodes found in cluster"
        return 1
    fi

    # Check node ready
    kubectl wait --for=condition=Ready node --all --timeout=60s

    # Check core components
    kubectl get pods -n kube-system --no-headers | head -5

    success "Cluster is healthy"
}

# Print cluster info
print_info() {
    echo ""
    echo "=========================================="
    echo "  Cluster Information"
    echo "=========================================="
    echo ""
    echo "  Cluster Name: $CLUSTER_NAME"
    echo "  Namespace:    $NAMESPACE"
    echo "  Context:      $(kubectl config current-context)"
    echo ""
    echo "  Minikube IP:  $(minikube ip -p "$CLUSTER_NAME")"
    echo ""
    echo "  To use this cluster:"
    echo "    export KUBECONFIG=\$(minikube -p $CLUSTER_NAME kubeconfig)"
    echo "    kubectl config use-context $CLUSTER_NAME"
    echo ""
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo "  Minikube Cluster Setup"
    echo "=========================================="
    echo ""

    # Check prerequisites
    if ! command -v minikube &>/dev/null; then
        error "minikube is not installed. Run install-requirements.sh first."
        exit 1
    fi

    if ! command -v kubectl &>/dev/null; then
        error "kubectl is not installed. Run install-requirements.sh first."
        exit 1
    fi

    # Handle fresh cluster request
    if [[ "$FRESH_CLUSTER" == "true" ]]; then
        if cluster_exists; then
            delete_cluster
        fi
        create_cluster
    else
        # Reuse existing cluster if possible
        if cluster_exists; then
            if cluster_running; then
                info "Reusing existing running cluster '$CLUSTER_NAME'"
            else
                start_cluster
            fi
        else
            create_cluster
        fi
    fi

    # Set context and create namespace
    set_context
    create_namespace
    write_namespace_file
    verify_cluster
    print_info

    success "Setup complete!"
}

main "$@"
