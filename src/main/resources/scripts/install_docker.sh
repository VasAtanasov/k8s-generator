#!/usr/bin/env bash
#
# Purpose: Installs Docker Engine with buildx and compose plugins.
# Usage: ./install_docker.sh
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
    lib::header "Starting Docker installation..."

    # Idempotency: state (binary + daemon active)
    if lib::cmd_exists docker && systemctl is-active --quiet docker; then
        lib::success "Docker already installed and active. Version: $(docker --version)"
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    lib::log "Ensuring prerequisites..."
    lib::ensure_packages ca-certificates curl gnupg lsb-release || true

    lib::ensure_apt_key_from_url https://download.docker.com/linux/debian/gpg /etc/apt/keyrings/docker.gpg

    lib::log "Setting up Docker repository (idempotent)..."
    lib::ensure_apt_source_file /etc/apt/sources.list.d/docker.list \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable"

    lib::log "Installing Docker Engine..."
    lib::apt_update_once
    lib::ensure_packages docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin || true

    lib::log "Configuring Docker for non-root users..."
    # Add vagrant user to docker group if it exists
    if id -u vagrant &>/dev/null; then
        lib::ensure_user_in_group vagrant docker
    fi

    # Add current user to docker group if not root
    if [ "$EUID" -ne 0 ] && [ -n "${SUDO_USER:-}" ]; then
        lib::ensure_user_in_group "$SUDO_USER" docker
    fi

    lib::log "Starting Docker service..."
    if ! lib::ensure_service docker; then
        lib::warn "Docker service not managed yet; attempting direct enable/start"
        systemctl daemon-reload || true
        systemctl enable --now docker.service >/dev/null 2>&1 || systemctl start docker.service >/dev/null 2>&1 || true
    fi

    lib::log "Verifying Docker installation..."
    if ! command -v docker &>/dev/null; then
        lib::error "Docker installation failed. The 'docker' command is not available."
        exit 1
    fi

    # Test Docker is working
    if ! docker info &>/dev/null; then
        lib::error "Docker daemon is not running or not accessible."
        exit 1
    fi

    lib::success "Docker installation completed successfully."
    lib::log "Docker version: $(docker --version)"
    lib::log "Docker Compose version: $(docker compose version)"
    lib::log "Docker Buildx version: $(docker buildx version)"

    # Reminder for group membership
    if [ -n "${SUDO_USER:-}" ]; then
        lib::warn "Note: User '$SUDO_USER' needs to log out and back in for docker group membership to take effect."
    fi
}

# --- Run ---
main "$@"
