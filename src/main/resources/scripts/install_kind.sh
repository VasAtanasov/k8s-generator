#!/usr/bin/env bash
#
# Purpose: Installs kind (Kubernetes in Docker) binary.
# Usage: ./install_kind.sh
#

# --- Strict Mode & lib.sh sourcing (supports shell.inline and shell.path) ---
_ORIG_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${_ORIG_DIR}/lib.sh" ]; then
    BASE_DIR="${_ORIG_DIR}"
elif [ -f "/vagrant/scripts/lib.sh" ]; then
    BASE_DIR="/vagrant/scripts"
elif [ -f "/vagrant/lib.sh" ]; then
    BASE_DIR="/vagrant"
else
    echo "ERROR: Could not find the shared library 'lib.sh' (checked /vagrant/scripts/lib.sh, /vagrant/lib.sh, and this script's directory)." >&2
    exit 1
fi
SCRIPT_DIR="${BASE_DIR}"
# shellcheck disable=SC1091
source "${BASE_DIR}/lib.sh"
lib::strict
lib::setup_traps

main() {
    lib::header "Installing kind (Kubernetes in Docker)"

    # Idempotency
    if lib::cmd_exists kind; then
        lib::success "kind already installed: $(kind --version 2>/dev/null || echo present)"
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    # Ensure prerequisites
    if ! lib::cmd_exists docker; then
        lib::warn "Docker not found. Installing Docker Engine first."
        if [ -x "${SCRIPT_DIR}/install_docker.sh" ]; then
            bash "${SCRIPT_DIR}/install_docker.sh"
        else
            lib::error "install_docker.sh is not available. Please install Docker manually before installing kind."
            exit 1
        fi
    fi

    # Determine version (pin or default)
    local ver
    ver="${KIND_VERSION:-v0.23.0}"
    lib::log "Installing kind ${ver}..."

    local url arch=osx
    case "$(uname -s)" in
      Linux) arch=linux ;;
      Darwin) arch=darwin ;;
      *) lib::error "Unsupported OS: $(uname -s)"; exit 1 ;;
    esac

    local cpu=amd64
    case "$(uname -m)" in
      x86_64|amd64) cpu=amd64 ;;
      aarch64|arm64) cpu=arm64 ;;
    esac

    url="https://kind.sigs.k8s.io/dl/${ver}/kind-${arch}-${cpu}"

    lib::require_commands curl
    lib::install_binary "$url" kind

    # Verify
    if ! kind --version >/dev/null 2>&1; then
        lib::error "kind installation failed"
        exit 1
    fi

    lib::success "kind installed: $(kind --version)"
}

main "$@"
