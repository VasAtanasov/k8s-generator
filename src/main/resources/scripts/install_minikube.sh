#!/usr/bin/env bash
#
# Purpose: Installs minikube, the local Kubernetes development tool.
# Usage: ./install_minikube.sh
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
    lib::header "Starting minikube installation..."

    # Idempotency: binary present → skip
    if lib::cmd_exists minikube; then
        lib::success "minikube already installed, skipping..."
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    # Version pinning support: Use MINIKUBE_VERSION from environment or default to latest
    local release_path
    if [ -n "${MINIKUBE_VERSION:-}" ]; then
        lib::log "Using pinned minikube version: $MINIKUBE_VERSION"
        release_path="$MINIKUBE_VERSION"
    else
        lib::log "Using latest stable minikube version..."
        release_path="latest"
    fi

    lib::log "Downloading minikube from releases/$release_path with verification..."
    local MINIKUBE_BINARY="minikube-linux-amd64"
    local bin_url="https://storage.googleapis.com/minikube/releases/${release_path}/${MINIKUBE_BINARY}"
    local sum_url="https://storage.googleapis.com/minikube/releases/${release_path}/${MINIKUBE_BINARY}.sha256"
    local expected tmp
    expected=$(curl -fsSL "$sum_url" | awk '{print $1}')
    if [ -z "$expected" ]; then
        lib::error "Failed to fetch checksum for minikube ${release_path}"
        exit 1
    fi
    tmp=$(mktemp)
    lib::ensure_downloaded "$bin_url" "$tmp" "$expected"

    lib::log "Installing minikube to /usr/local/bin..."
    install -m 0755 "$tmp" /usr/local/bin/minikube
    rm -f "$tmp"

    lib::log "Enabling bash completion for minikube..."
    minikube completion bash > /etc/bash_completion.d/minikube

    lib::log "Verifying installation..."
    if ! command -v minikube &>/dev/null; then
        lib::error "minikube installation failed. The 'minikube' command is not available."
        exit 1
    fi

    lib::success "minikube installation completed successfully."
    minikube version
}

# --- Run ---
main "$@"
