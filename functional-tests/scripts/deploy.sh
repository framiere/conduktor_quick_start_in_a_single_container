#!/usr/bin/env bash
set -euo pipefail

#
# Deploy Script for Messaging Operator
# Builds the application, Docker image, and deploys to Minikube
#
# Environment Variables:
#   NAMESPACE       - Kubernetes namespace (reads from .test-namespace if not set)
#   RELEASE_NAME    - Helm release name (default: messaging-operator)
#   CLUSTER_NAME    - Minikube profile name (default: messaging-operator-test)
#   SKIP_BUILD      - Skip Maven build if true (default: false)
#   SKIP_DOCKER     - Skip Docker build if true (default: false)
#   SKIP_CERTS      - Skip certificate generation if true (default: false)
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FUNC_TESTS_DIR="$SCRIPT_DIR/.."
HELM_CHART_DIR="$FUNC_TESTS_DIR/helm/messaging-operator"

# Configuration
NAMESPACE="${NAMESPACE:-}"
RELEASE_NAME="${RELEASE_NAME:-messaging-operator}"
CLUSTER_NAME="${CLUSTER_NAME:-messaging-operator-test}"
SKIP_BUILD="${SKIP_BUILD:-false}"
SKIP_DOCKER="${SKIP_DOCKER:-false}"
SKIP_CERTS="${SKIP_CERTS:-false}"
IMAGE_NAME="${IMAGE_NAME:-messaging-operator}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

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
            error "Namespace not set and .test-namespace file not found"
            error "Run setup-minikube.sh first or set NAMESPACE env var"
            exit 1
        fi
    fi
}

# Build Java application
build_java() {
    if [[ "$SKIP_BUILD" == "true" ]]; then
        warn "Skipping Maven build (SKIP_BUILD=true)"
        return
    fi

    info "Building Java application..."
    cd "$PROJECT_ROOT"
    mvn package -DskipTests -q
    success "Java build complete"
}

# Build Docker image in Minikube context
build_docker() {
    if [[ "$SKIP_DOCKER" == "true" ]]; then
        warn "Skipping Docker build (SKIP_DOCKER=true)"
        return
    fi

    info "Configuring Docker to use Minikube daemon..."
    eval $(minikube -p "$CLUSTER_NAME" docker-env)

    info "Building Docker image: ${IMAGE_NAME}:${IMAGE_TAG}..."
    cd "$PROJECT_ROOT"

    # Always use a webhook-specific Dockerfile (project may have other Dockerfiles)
    info "Creating Dockerfile for webhook..."
    cat > Dockerfile.webhook <<'EOF'
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8443
ENV WEBHOOK_PORT=8443
ENV TLS_CERT_PATH=/etc/webhook/certs/tls.crt
ENV TLS_KEY_PATH=/etc/webhook/certs/tls.key
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
    docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" -f Dockerfile.webhook .
    rm -f Dockerfile.webhook

    success "Docker image built: ${IMAGE_NAME}:${IMAGE_TAG}"
}

# Generate TLS certificates
generate_certs() {
    if [[ "$SKIP_CERTS" == "true" ]]; then
        warn "Skipping certificate generation (SKIP_CERTS=true)"
        return
    fi

    # Determine service name from Helm values
    # When release name contains chart name, Helm fullname = release name
    # So webhook service = release-name-webhook (not release-name-chart-name-webhook)
    local service_name
    service_name="${RELEASE_NAME}-webhook"

    info "Generating TLS certificates..."
    NAMESPACE="$NAMESPACE" SERVICE_NAME="$service_name" "$SCRIPT_DIR/generate-certs.sh"
    success "Certificates generated"
}

# Deploy with Helm
deploy_helm() {
    info "Deploying with Helm..."

    # Ensure namespace exists
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

    # Build values files list
    local values_files=("-f" "$HELM_CHART_DIR/values.yaml")
    values_files+=("-f" "$HELM_CHART_DIR/values-minikube.yaml")

    # Add TLS values if generated
    if [[ -f "$FUNC_TESTS_DIR/values-tls.yaml" ]]; then
        values_files+=("-f" "$FUNC_TESTS_DIR/values-tls.yaml")
    fi

    # Deploy
    helm upgrade --install "$RELEASE_NAME" "$HELM_CHART_DIR" \
        "${values_files[@]}" \
        --namespace "$NAMESPACE" \
        --set namespace="$NAMESPACE" \
        --set webhook.image.repository="$IMAGE_NAME" \
        --set webhook.image.tag="$IMAGE_TAG" \
        --wait \
        --timeout 120s

    success "Helm deployment complete"
}

# Wait for deployment to be ready
wait_for_ready() {
    info "Waiting for webhook deployment to be ready..."

    kubectl wait --for=condition=Available \
        deployment/${RELEASE_NAME}-webhook \
        -n "$NAMESPACE" \
        --timeout=120s

    success "Webhook deployment is ready"
}

# Verify deployment
verify_deployment() {
    info "Verifying deployment..."

    # Check deployment
    local ready_replicas
    ready_replicas=$(kubectl get deployment "${RELEASE_NAME}-webhook" \
        -n "$NAMESPACE" \
        -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")

    if [[ "$ready_replicas" -lt 1 ]]; then
        error "No ready replicas found"
        kubectl describe deployment "${RELEASE_NAME}-webhook" -n "$NAMESPACE"
        return 1
    fi

    # Check endpoints
    local endpoints
    endpoints=$(kubectl get endpoints "${RELEASE_NAME}-webhook" \
        -n "$NAMESPACE" \
        -o jsonpath='{.subsets[0].addresses[0].ip}' 2>/dev/null || echo "")

    if [[ -z "$endpoints" ]]; then
        warn "No endpoints found yet, waiting..."
        sleep 5
        endpoints=$(kubectl get endpoints "${RELEASE_NAME}-webhook" \
            -n "$NAMESPACE" \
            -o jsonpath='{.subsets[0].addresses[0].ip}' 2>/dev/null || echo "")
    fi

    # Check CRDs
    info "Checking CRDs..."
    local crd_count
    crd_count=$(kubectl get crd -o name | grep -c "messaging.example.com" || echo "0")

    if [[ "$crd_count" -lt 6 ]]; then
        warn "Expected 6 CRDs, found $crd_count"
    else
        success "All 6 CRDs installed"
    fi

    # Check ValidatingWebhookConfiguration
    info "Checking ValidatingWebhookConfiguration..."
    kubectl get validatingwebhookconfiguration "${RELEASE_NAME}-webhook" -o name

    success "Deployment verification complete"
}

# Print deployment info
print_info() {
    echo ""
    echo "=========================================="
    echo "  Deployment Information"
    echo "=========================================="
    echo ""
    echo "  Release:    $RELEASE_NAME"
    echo "  Namespace:  $NAMESPACE"
    echo "  Image:      ${IMAGE_NAME}:${IMAGE_TAG}"
    echo ""
    echo "  Pods:"
    kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/instance="$RELEASE_NAME" --no-headers | sed 's/^/    /'
    echo ""
    echo "  Services:"
    kubectl get svc -n "$NAMESPACE" -l app.kubernetes.io/instance="$RELEASE_NAME" --no-headers | sed 's/^/    /'
    echo ""
}

# Main
main() {
    echo ""
    echo "=========================================="
    echo "  Messaging Operator Deployment"
    echo "=========================================="
    echo ""

    # Check prerequisites
    for cmd in kubectl helm minikube mvn docker; do
        if ! command -v "$cmd" &>/dev/null; then
            error "$cmd is not installed"
            exit 1
        fi
    done

    read_namespace
    info "Using namespace: $NAMESPACE"

    build_java
    build_docker
    generate_certs
    deploy_helm
    wait_for_ready
    verify_deployment
    print_info

    success "Deployment complete!"
}

main "$@"
