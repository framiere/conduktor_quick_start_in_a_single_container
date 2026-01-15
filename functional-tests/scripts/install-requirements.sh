#!/usr/bin/env bash
set -euo pipefail

#
# Functional Tests Requirements Installer
# Installs: minikube, kubectl, helm, bats, bats-support, bats-assert
#
# Usage:
#   ./install-requirements.sh           # Install all missing tools
#   ./install-requirements.sh --check   # Check status only
#   ./install-requirements.sh --force   # Reinstall all tools
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Options
CHECK_ONLY=false
FORCE_INSTALL=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --check)
            CHECK_ONLY=true
            shift
            ;;
        --force)
            FORCE_INSTALL=true
            shift
            ;;
        -h|--help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --check   Check installation status only"
            echo "  --force   Force reinstall all tools"
            echo "  -h        Show this help"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Logging functions
info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[OK]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

# Detect OS
detect_os() {
    if [[ -f /etc/os-release ]]; then
        . /etc/os-release
        OS=$ID
        OS_VERSION=$VERSION_ID
    elif [[ "$(uname)" == "Darwin" ]]; then
        OS="macos"
        OS_VERSION=$(sw_vers -productVersion)
    else
        OS="unknown"
        OS_VERSION="unknown"
    fi
    echo "$OS"
}

# Check if command exists
command_exists() {
    command -v "$1" &>/dev/null
}

# Get installed version
get_version() {
    local cmd=$1
    case $cmd in
        minikube)
            minikube version --short 2>/dev/null | head -1 || echo "unknown"
            ;;
        kubectl)
            kubectl version --client -o yaml 2>/dev/null | grep gitVersion | awk '{print $2}' || echo "unknown"
            ;;
        helm)
            helm version --short 2>/dev/null || echo "unknown"
            ;;
        bats)
            bats --version 2>/dev/null || echo "unknown"
            ;;
        docker)
            docker --version 2>/dev/null | awk '{print $3}' | tr -d ',' || echo "unknown"
            ;;
    esac
}

# Print status table
print_status() {
    echo ""
    echo "========================================"
    echo "  Requirements Status"
    echo "========================================"
    echo ""

    local tools=("docker" "minikube" "kubectl" "helm" "bats")
    local all_installed=true

    printf "%-15s %-12s %-20s\n" "TOOL" "STATUS" "VERSION"
    printf "%-15s %-12s %-20s\n" "----" "------" "-------"

    for tool in "${tools[@]}"; do
        if command_exists "$tool"; then
            local version=$(get_version "$tool")
            printf "%-15s ${GREEN}%-12s${NC} %-20s\n" "$tool" "INSTALLED" "$version"
        else
            printf "%-15s ${RED}%-12s${NC} %-20s\n" "$tool" "MISSING" "-"
            all_installed=false
        fi
    done

    # Check bats libraries
    echo ""
    printf "%-15s %-12s %-20s\n" "LIBRARY" "STATUS" "PATH"
    printf "%-15s %-12s %-20s\n" "-------" "------" "----"

    if [[ -f /usr/lib/bats-support/load.bash ]]; then
        printf "%-15s ${GREEN}%-12s${NC} %-20s\n" "bats-support" "INSTALLED" "/usr/lib/bats-support"
    elif [[ -f "$HOME/.bats/bats-support/load.bash" ]]; then
        printf "%-15s ${GREEN}%-12s${NC} %-20s\n" "bats-support" "INSTALLED" "~/.bats/bats-support"
    else
        printf "%-15s ${RED}%-12s${NC} %-20s\n" "bats-support" "MISSING" "-"
        all_installed=false
    fi

    if [[ -f /usr/lib/bats-assert/load.bash ]]; then
        printf "%-15s ${GREEN}%-12s${NC} %-20s\n" "bats-assert" "INSTALLED" "/usr/lib/bats-assert"
    elif [[ -f "$HOME/.bats/bats-assert/load.bash" ]]; then
        printf "%-15s ${GREEN}%-12s${NC} %-20s\n" "bats-assert" "INSTALLED" "~/.bats/bats-assert"
    else
        printf "%-15s ${RED}%-12s${NC} %-20s\n" "bats-assert" "MISSING" "-"
        all_installed=false
    fi

    echo ""

    if $all_installed; then
        success "All requirements are installed!"
        return 0
    else
        warn "Some requirements are missing. Run without --check to install."
        return 1
    fi
}

# Install kubectl
install_kubectl() {
    if command_exists kubectl && [[ "$FORCE_INSTALL" != "true" ]]; then
        success "kubectl already installed: $(get_version kubectl)"
        return 0
    fi

    info "Installing kubectl..."

    local os=$(detect_os)
    case $os in
        ubuntu|debian)
            # Official Kubernetes apt repository
            sudo apt-get update -qq
            sudo apt-get install -y -qq apt-transport-https ca-certificates curl gnupg

            # Add Kubernetes signing key
            curl -fsSL https://pkgs.k8s.io/core:/stable:/v1.29/deb/Release.key | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg --yes

            # Add Kubernetes apt repository
            echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v1.29/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list

            sudo apt-get update -qq
            sudo apt-get install -y -qq kubectl
            ;;
        fedora|rhel|centos)
            cat <<EOF | sudo tee /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://pkgs.k8s.io/core:/stable:/v1.29/rpm/
enabled=1
gpgcheck=1
gpgkey=https://pkgs.k8s.io/core:/stable:/v1.29/rpm/repodata/repomd.xml.key
EOF
            sudo yum install -y kubectl
            ;;
        macos)
            brew install kubectl
            ;;
        *)
            # Fallback: download binary directly
            local arch=$(uname -m)
            [[ "$arch" == "x86_64" ]] && arch="amd64"
            [[ "$arch" == "aarch64" ]] && arch="arm64"

            curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/${arch}/kubectl"
            chmod +x kubectl
            sudo mv kubectl /usr/local/bin/
            ;;
    esac

    success "kubectl installed: $(get_version kubectl)"
}

# Install Helm
install_helm() {
    if command_exists helm && [[ "$FORCE_INSTALL" != "true" ]]; then
        success "helm already installed: $(get_version helm)"
        return 0
    fi

    info "Installing helm..."

    local os=$(detect_os)
    case $os in
        ubuntu|debian)
            curl https://baltocdn.com/helm/signing.asc | gpg --dearmor | sudo tee /usr/share/keyrings/helm.gpg > /dev/null
            sudo apt-get install -y -qq apt-transport-https
            echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main" | sudo tee /etc/apt/sources.list.d/helm-stable-debian.list
            sudo apt-get update -qq
            sudo apt-get install -y -qq helm
            ;;
        fedora|rhel|centos)
            sudo dnf install -y helm || {
                curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
                chmod 700 get_helm.sh
                ./get_helm.sh
                rm get_helm.sh
            }
            ;;
        macos)
            brew install helm
            ;;
        *)
            # Fallback: official install script
            curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
            chmod 700 get_helm.sh
            ./get_helm.sh
            rm get_helm.sh
            ;;
    esac

    success "helm installed: $(get_version helm)"
}

# Install Minikube
install_minikube() {
    if command_exists minikube && [[ "$FORCE_INSTALL" != "true" ]]; then
        success "minikube already installed: $(get_version minikube)"
        return 0
    fi

    info "Installing minikube..."

    local os=$(detect_os)
    local arch=$(uname -m)
    [[ "$arch" == "x86_64" ]] && arch="amd64"
    [[ "$arch" == "aarch64" ]] && arch="arm64"

    case $os in
        ubuntu|debian)
            curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube_latest_${arch}.deb
            sudo dpkg -i minikube_latest_${arch}.deb
            rm minikube_latest_${arch}.deb
            ;;
        fedora|rhel|centos)
            curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-latest.x86_64.rpm
            sudo rpm -Uvh minikube-latest.x86_64.rpm
            rm minikube-latest.x86_64.rpm
            ;;
        macos)
            brew install minikube
            ;;
        *)
            curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-${arch}
            chmod +x minikube-linux-${arch}
            sudo mv minikube-linux-${arch} /usr/local/bin/minikube
            ;;
    esac

    success "minikube installed: $(get_version minikube)"
}

# Install Bats
install_bats() {
    if command_exists bats && [[ "$FORCE_INSTALL" != "true" ]]; then
        success "bats already installed: $(get_version bats)"
        return 0
    fi

    info "Installing bats-core..."

    local os=$(detect_os)
    case $os in
        ubuntu|debian)
            sudo apt-get update -qq
            sudo apt-get install -y -qq bats
            ;;
        fedora|rhel|centos)
            sudo dnf install -y bats || {
                # Install from source
                git clone https://github.com/bats-core/bats-core.git /tmp/bats-core
                sudo /tmp/bats-core/install.sh /usr/local
                rm -rf /tmp/bats-core
            }
            ;;
        macos)
            brew install bats-core
            ;;
        *)
            git clone https://github.com/bats-core/bats-core.git /tmp/bats-core
            sudo /tmp/bats-core/install.sh /usr/local
            rm -rf /tmp/bats-core
            ;;
    esac

    success "bats installed: $(get_version bats)"
}

# Install bats-support library
install_bats_support() {
    if [[ -f /usr/lib/bats-support/load.bash ]] || [[ -f "$HOME/.bats/bats-support/load.bash" ]]; then
        if [[ "$FORCE_INSTALL" != "true" ]]; then
            success "bats-support already installed"
            return 0
        fi
    fi

    info "Installing bats-support..."

    local os=$(detect_os)
    case $os in
        ubuntu|debian)
            sudo apt-get update -qq
            sudo apt-get install -y -qq bats-support 2>/dev/null || {
                # Manual install if package not available
                sudo mkdir -p /usr/lib/bats-support
                sudo git clone https://github.com/bats-core/bats-support.git /usr/lib/bats-support
            }
            ;;
        macos)
            brew install bats-support
            ;;
        *)
            mkdir -p "$HOME/.bats"
            git clone https://github.com/bats-core/bats-support.git "$HOME/.bats/bats-support"
            ;;
    esac

    success "bats-support installed"
}

# Install bats-assert library
install_bats_assert() {
    if [[ -f /usr/lib/bats-assert/load.bash ]] || [[ -f "$HOME/.bats/bats-assert/load.bash" ]]; then
        if [[ "$FORCE_INSTALL" != "true" ]]; then
            success "bats-assert already installed"
            return 0
        fi
    fi

    info "Installing bats-assert..."

    local os=$(detect_os)
    case $os in
        ubuntu|debian)
            sudo apt-get update -qq
            sudo apt-get install -y -qq bats-assert 2>/dev/null || {
                # Manual install if package not available
                sudo mkdir -p /usr/lib/bats-assert
                sudo git clone https://github.com/bats-core/bats-assert.git /usr/lib/bats-assert
            }
            ;;
        macos)
            brew install bats-assert
            ;;
        *)
            mkdir -p "$HOME/.bats"
            git clone https://github.com/bats-core/bats-assert.git "$HOME/.bats/bats-assert"
            ;;
    esac

    success "bats-assert installed"
}

# Add Awaitility to pom.xml if not present
add_awaitility_dependency() {
    local pom_file="$PROJECT_ROOT/pom.xml"

    if grep -q "awaitility" "$pom_file" 2>/dev/null; then
        success "Awaitility dependency already in pom.xml"
        return 0
    fi

    info "Adding Awaitility dependency to pom.xml..."

    # Check if we can modify pom.xml
    if [[ ! -w "$pom_file" ]]; then
        warn "Cannot modify pom.xml - add Awaitility manually"
        echo "Add this to <dependencies>:"
        echo '        <dependency>'
        echo '            <groupId>org.awaitility</groupId>'
        echo '            <artifactId>awaitility</artifactId>'
        echo '            <version>4.2.0</version>'
        echo '            <scope>test</scope>'
        echo '        </dependency>'
        return 1
    fi

    # Add before </dependencies> tag
    sed -i '/<\/dependencies>/i \
        <!-- Awaitility for async assertions in E2E tests -->\
        <dependency>\
            <groupId>org.awaitility</groupId>\
            <artifactId>awaitility</artifactId>\
            <version>4.2.0</version>\
            <scope>test</scope>\
        </dependency>' "$pom_file"

    success "Awaitility dependency added to pom.xml"
}

# Main
main() {
    echo ""
    echo "========================================"
    echo "  Functional Tests Requirements Installer"
    echo "========================================"
    echo ""

    local os=$(detect_os)
    info "Detected OS: $os"

    if $CHECK_ONLY; then
        print_status
        exit $?
    fi

    # Check for sudo access
    if ! sudo -v 2>/dev/null; then
        error "This script requires sudo access to install system packages"
        exit 1
    fi

    echo ""
    info "Installing requirements..."
    echo ""

    # Install tools
    install_minikube
    install_kubectl
    install_helm
    install_bats
    install_bats_support
    install_bats_assert
    add_awaitility_dependency

    echo ""
    print_status
}

main "$@"
