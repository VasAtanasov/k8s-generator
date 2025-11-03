#!/usr/bin/env bash
#
# Purpose: Installs kubectl with checksum verification.
# Usage: ./install_kubectl.sh
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

# --- Idempotency: state + lock ---

# --- Main Logic ---
main() {
    lib::log "Starting kubectl installation..."

    # Idempotency: binary present → skip
    if lib::cmd_exists kubectl; then
        lib::success "kubectl already installed, skipping..."
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    lib::log "Ensuring prerequisites (curl, ca-certificates)..."
    lib::ensure_packages curl ca-certificates || true

    # Version pinning support: Use KUBECTL_VERSION from environment or fetch latest
    if [ -n "${KUBECTL_VERSION:-}" ]; then
        lib::log "Using pinned kubectl version: $KUBECTL_VERSION"
    else
        lib::log "Fetching latest stable kubectl version..."
        KUBECTL_VERSION=$(curl -sL https://dl.k8s.io/release/stable.txt)
        lib::log "Latest version: $KUBECTL_VERSION"
    fi

    # Download with checksum verification (idempotent)
    local bin_url="https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
    local sum_url="https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl.sha256"
    lib::log "Fetching expected checksum..."
    local expected
    expected=$(curl -fsSL "$sum_url" | awk '{print $1}')
    if [ -z "$expected" ]; then
        lib::error "Failed to fetch checksum for kubectl ${KUBECTL_VERSION}"
        exit 1
    fi
    lib::log "Downloading kubectl ${KUBECTL_VERSION} with verification..."
    local tmp
    tmp=$(mktemp)
    lib::ensure_downloaded "$bin_url" "$tmp" "$expected"

    lib::log "Installing kubectl to /usr/local/bin..."
    install -o root -g root -m 0755 "$tmp" /usr/local/bin/kubectl
    rm -f "$tmp"

    lib::log "Enabling bash completion for kubectl..."
    kubectl completion bash > /etc/bash_completion.d/kubectl

    lib::log "Verifying installation..."
    if ! command -v kubectl &>/dev/null; then
        lib::error "kubectl installation failed. The 'kubectl' command is not available."
        exit 1
    fi

    lib::log "kubectl installation completed successfully."
    kubectl version --client
}

# --- Run ---
main
