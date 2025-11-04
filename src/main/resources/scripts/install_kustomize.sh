#!/usr/bin/env bash
#
# Purpose: Install Kustomize on Debian-based systems.
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
    lib::header "Installing Kustomize"

    # Idempotency: kustomize exists → skip
    if lib::cmd_exists kustomize; then
        lib::success "Kustomize already installed, skipping..."
        return 0
    fi

    lib::ensure_apt_updated
    lib::ensure_packages kustomize || true

    lib::log "Verifying Kustomize installation..."
    kustomize version

    lib::success "Kustomize installed successfully!"
}

main "$@"
