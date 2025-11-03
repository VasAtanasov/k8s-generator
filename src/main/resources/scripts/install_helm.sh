#!/usr/bin/env bash
#
# Purpose: Install Helm on Debian-based systems.
# Usage: Called automatically by Vagrant provisioner or can be run manually.
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
    lib::header "Installing Helm"

    # Idempotency: helm exists → skip
    if lib::cmd_exists helm; then
        lib::success "Helm already installed, skipping..."
        return 0
    fi

    lib::log "Ensuring prerequisites..."
    lib::ensure_packages apt-transport-https ca-certificates curl gnupg || true

    lib::ensure_apt_key_from_url https://baltocdn.com/helm/signing.asc /usr/share/keyrings/helm.gpg

    lib::log "Adding the Helm repository to APT sources (idempotent)..."
    lib::ensure_apt_source_file /etc/apt/sources.list.d/helm-stable-debian.list \
      "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/helm.gpg] https://baltocdn.com/helm/stable/debian/ all main"

    lib::log "Installing Helm..."
    lib::apt_update_once
    lib::ensure_packages helm || true

    lib::log "Verifying Helm installation..."
    helm version

    lib::success "Helm installed successfully!"
}

main "$@"
