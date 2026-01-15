#!/usr/bin/env bash
set -euo pipefail

#
# Teardown Script for Messaging Operator
# Cleans up Helm release, CRD instances, and optionally the cluster
#
# Environment Variables:
#   NAMESPACE        - Kubernetes namespace (reads from .test-namespace if not set)
#   RELEASE_NAME     - Helm release name (default: messaging-operator)
#   DELETE_NAMESPACE - Delete namespace if true (default: false)
#   DELETE_CLUSTER   - Delete Minikube cluster if true (default: false)
#   CLUSTER_NAME     - Minikube profile name (default: messaging-operator-test)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FUNC_TESTS_DIR="$SCRIPT_DIR/.."

# Configuration
NAMESPACE="${NAMESPACE:-}"
RELEASE_NAME="${RELEASE_NAME:-messaging-operator}"
DELETE_NAMESPACE="${DELETE_NAMESPACE:-false}"
DELETE_CLUSTER="${DELETE_CLUSTER:-false}"
CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"

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

# Read namespace from file if not set
read_namespace() {
    if [[ -z "$NAMESPACE" ]]; then
        local ns_file="$FUNC_TESTS_DIR/.test-namespace"
        if [[ -f "$ns_file" ]]; then
            NAMESPACE=$(cat "$ns_file")
        else
            warn "Namespace not set and .test-namespace file not found"
            NAMESPACE="operator-system"
        fi
    fi
}

# Uninstall Helm release
uninstall_helm() {
    info "Uninstalling Helm release '$RELEASE_NAME'..."

    if helm status "$RELEASE_NAME" -n "$NAMESPACE" &>/dev/null; then
        helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" --wait
        success "Helm release uninstalled"
    else
        warn "Helm release '$RELEASE_NAME' not found in namespace '$NAMESPACE'"
    fi
}

# Delete CRD instances
delete_crd_instances() {
    info "Deleting CRD instances in namespace '$NAMESPACE'..."

    local crds=(
        "topics.messaging.example.com"
        "acls.messaging.example.com"
        "consumergroups.messaging.example.com"
        "serviceaccounts.messaging.example.com"
        "virtualclusters.messaging.example.com"
        "applicationservices.messaging.example.com"
    )

    for crd in "${crds[@]}"; do
        local resource_type="${crd%%.*}"
        if kubectl get "$resource_type" -n "$NAMESPACE" &>/dev/null; then
            kubectl delete "$resource_type" --all -n "$NAMESPACE" --ignore-not-found=true || true
            info "  Deleted all $resource_type"
        fi
    done

    success "CRD instances deleted"
}

# Delete namespace
delete_namespace() {
    if [[ "$DELETE_NAMESPACE" != "true" ]]; then
        return
    fi

    info "Deleting namespace '$NAMESPACE'..."
    kubectl delete namespace "$NAMESPACE" --ignore-not-found=true --wait=true
    success "Namespace deleted"
}

# Delete Minikube cluster
delete_cluster() {
    if [[ "$DELETE_CLUSTER" != "true" ]]; then
        return
    fi

    info "Deleting Minikube cluster '$CLUSTER_NAME'..."
    minikube delete -p "$CLUSTER_NAME" || true
    success "Cluster deleted"
}

# Cleanup local files
cleanup_files() {
    info "Cleaning up local files..."

    # Remove namespace file
    rm -f "$FUNC_TESTS_DIR/.test-namespace"

    # Remove certificates directory
    rm -rf "$FUNC_TESTS_DIR/.certs"

    # Remove generated TLS values
    rm -f "$FUNC_TESTS_DIR/values-tls.yaml"

    success "Local files cleaned up"
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo "  Messaging Operator Teardown"
    echo "=========================================="
    echo ""

    read_namespace
    info "Using namespace: $NAMESPACE"
    info "Release name: $RELEASE_NAME"

    # Perform cleanup in order
    delete_crd_instances
    uninstall_helm

    if [[ "$DELETE_NAMESPACE" == "true" ]]; then
        delete_namespace
    fi

    if [[ "$DELETE_CLUSTER" == "true" ]]; then
        delete_cluster
    fi

    cleanup_files

    echo ""
    success "Teardown complete!"
}

main "$@"
