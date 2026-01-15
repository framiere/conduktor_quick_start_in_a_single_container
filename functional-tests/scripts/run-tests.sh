#!/usr/bin/env bash
set -euo pipefail

#
# Test Runner for Messaging Operator Functional Tests
# Orchestrates cluster setup, deployment, and test execution
#
# Usage:
#   ./run-tests.sh                         # Run all tests (reuse cluster)
#   ./run-tests.sh --fresh-cluster         # Fresh cluster for each run
#   ./run-tests.sh --skip-deploy           # Skip deployment (use existing)
#   ./run-tests.sh --skip-tests            # Deploy only, no tests
#   ./run-tests.sh --bats-only             # Run only Bats tests
#   ./run-tests.sh --java-only             # Run only Java E2E tests
#   ./run-tests.sh --test-filter "02_"     # Run tests matching pattern
#   ./run-tests.sh --cleanup               # Clean up after tests
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
FUNC_TESTS_DIR="$SCRIPT_DIR/.."
BATS_DIR="$FUNC_TESTS_DIR/bats"

# Default options
FRESH_CLUSTER=false
SKIP_DEPLOY=false
SKIP_TESTS=false
BATS_ONLY=false
JAVA_ONLY=false
TEST_FILTER=""
CLEANUP=false
VERBOSE=false

# Test results
BATS_PASSED=0
BATS_FAILED=0
JAVA_PASSED=0
JAVA_FAILED=0

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# Logging functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }
header() { echo -e "${CYAN}$1${NC}"; }

# Print usage
usage() {
    cat <<EOF
Usage: $(basename "$0") [OPTIONS]

Options:
  --fresh-cluster     Delete and recreate Minikube cluster
  --skip-deploy       Skip deployment (use existing)
  --skip-tests        Deploy only, don't run tests
  --bats-only         Run only Bats tests
  --java-only         Run only Java E2E tests
  --test-filter PAT   Run only tests matching pattern
  --cleanup           Clean up after tests
  --verbose           Verbose output
  -h, --help          Show this help

Examples:
  $(basename "$0")                           # Full test run, reuse cluster
  $(basename "$0") --fresh-cluster --cleanup # CI-style: fresh cluster, cleanup after
  $(basename "$0") --bats-only --test-filter "02_webhook"  # Run specific Bats tests
  $(basename "$0") --skip-deploy             # Run tests against existing deployment
EOF
}

# Parse arguments
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --fresh-cluster)
                FRESH_CLUSTER=true
                shift
                ;;
            --skip-deploy)
                SKIP_DEPLOY=true
                shift
                ;;
            --skip-tests)
                SKIP_TESTS=true
                shift
                ;;
            --bats-only)
                BATS_ONLY=true
                shift
                ;;
            --java-only)
                JAVA_ONLY=true
                shift
                ;;
            --test-filter)
                TEST_FILTER="$2"
                shift 2
                ;;
            --cleanup)
                CLEANUP=true
                shift
                ;;
            --verbose)
                VERBOSE=true
                shift
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                error "Unknown option: $1"
                usage
                exit 1
                ;;
        esac
    done
}

# Setup cluster
setup_cluster() {
    header "
==========================================
  Setting up Minikube Cluster
==========================================
"
    FRESH_CLUSTER="$FRESH_CLUSTER" "$SCRIPT_DIR/setup-minikube.sh"
}

# Deploy operator
deploy_operator() {
    if [[ "$SKIP_DEPLOY" == "true" ]]; then
        warn "Skipping deployment (--skip-deploy)"
        return
    fi

    header "
==========================================
  Deploying Messaging Operator
==========================================
"
    "$SCRIPT_DIR/deploy.sh"
}

# Run Bats tests
run_bats_tests() {
    if [[ "$JAVA_ONLY" == "true" ]]; then
        warn "Skipping Bats tests (--java-only)"
        return
    fi

    header "
==========================================
  Running Bats Tests
==========================================
"

    # Check if bats is installed
    if ! command -v bats &>/dev/null; then
        error "Bats is not installed. Run install-requirements.sh first."
        return 1
    fi

    # Check if there are test files
    local test_files
    if [[ -n "$TEST_FILTER" ]]; then
        test_files=$(find "$BATS_DIR" -name "*.bats" -type f | grep "$TEST_FILTER" | sort || true)
    else
        test_files=$(find "$BATS_DIR" -name "*.bats" -type f | sort || true)
    fi

    if [[ -z "$test_files" ]]; then
        warn "No Bats test files found matching pattern: $TEST_FILTER"
        return
    fi

    info "Running Bats test files:"
    echo "$test_files" | while read -r f; do echo "  - $(basename "$f")"; done

    # Run bats
    local bats_args=()
    if [[ "$VERBOSE" == "true" ]]; then
        bats_args+=("--verbose-run")
    fi
    bats_args+=("--timing")

    set +e
    local output
    output=$(bats "${bats_args[@]}" $test_files 2>&1)
    local exit_code=$?
    set -e

    echo "$output"

    # Parse results
    BATS_PASSED=$(echo "$output" | grep -oP '\d+(?= test)' | head -1 || echo "0")
    BATS_FAILED=$(echo "$output" | grep -oP '\d+(?= failure)' | head -1 || echo "0")

    if [[ $exit_code -eq 0 ]]; then
        success "Bats tests passed"
    else
        error "Bats tests failed"
    fi

    return $exit_code
}

# Run Java E2E tests
run_java_tests() {
    if [[ "$BATS_ONLY" == "true" ]]; then
        warn "Skipping Java tests (--bats-only)"
        return
    fi

    header "
==========================================
  Running Java E2E Tests
==========================================
"

    cd "$PROJECT_ROOT"

    # Read namespace
    local namespace
    if [[ -f "$FUNC_TESTS_DIR/.test-namespace" ]]; then
        namespace=$(cat "$FUNC_TESTS_DIR/.test-namespace")
    else
        namespace="operator-system"
    fi

    info "Running Java E2E tests with namespace: $namespace"

    local mvn_args=("-Pe2e" "-DTEST_NAMESPACE=$namespace")
    if [[ -n "$TEST_FILTER" ]]; then
        mvn_args+=("-Dtest=$TEST_FILTER")
    fi

    set +e
    mvn verify "${mvn_args[@]}"
    local exit_code=$?
    set -e

    if [[ $exit_code -eq 0 ]]; then
        success "Java E2E tests passed"
        JAVA_PASSED=1
    else
        error "Java E2E tests failed"
        JAVA_FAILED=1
    fi

    return $exit_code
}

# Cleanup
cleanup() {
    if [[ "$CLEANUP" != "true" ]]; then
        return
    fi

    header "
==========================================
  Cleaning Up
==========================================
"
    DELETE_NAMESPACE=true "$SCRIPT_DIR/teardown.sh"
}

# Print summary
print_summary() {
    header "
==========================================
  Test Summary
==========================================
"
    echo ""
    echo "  Bats Tests:"
    echo "    Passed: $BATS_PASSED"
    echo "    Failed: $BATS_FAILED"
    echo ""
    echo "  Java E2E Tests:"
    echo "    Passed: $JAVA_PASSED"
    echo "    Failed: $JAVA_FAILED"
    echo ""

    local total_failed=$((BATS_FAILED + JAVA_FAILED))
    if [[ $total_failed -eq 0 ]]; then
        success "All tests passed!"
        return 0
    else
        error "Some tests failed!"
        return 1
    fi
}

# Main
main() {
    parse_args "$@"

    local start_time
    start_time=$(date +%s)

    header "
==========================================
  Messaging Operator Functional Tests
==========================================
"
    info "Options:"
    info "  Fresh cluster: $FRESH_CLUSTER"
    info "  Skip deploy:   $SKIP_DEPLOY"
    info "  Skip tests:    $SKIP_TESTS"
    info "  Bats only:     $BATS_ONLY"
    info "  Java only:     $JAVA_ONLY"
    info "  Test filter:   ${TEST_FILTER:-<none>}"
    info "  Cleanup:       $CLEANUP"

    # Check prerequisites
    for cmd in kubectl helm minikube; do
        if ! command -v "$cmd" &>/dev/null; then
            error "$cmd is not installed. Run install-requirements.sh first."
            exit 1
        fi
    done

    # Setup and deploy
    setup_cluster
    deploy_operator

    # Run tests
    local test_exit_code=0
    if [[ "$SKIP_TESTS" != "true" ]]; then
        set +e
        run_bats_tests
        local bats_exit=$?
        run_java_tests
        local java_exit=$?
        set -e

        if [[ $bats_exit -ne 0 ]] || [[ $java_exit -ne 0 ]]; then
            test_exit_code=1
        fi
    fi

    # Cleanup
    cleanup

    # Summary
    local end_time
    end_time=$(date +%s)
    local duration=$((end_time - start_time))

    print_summary
    echo ""
    info "Total duration: ${duration}s"

    exit $test_exit_code
}

main "$@"
