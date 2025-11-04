#!/usr/bin/env bash
#
# Purpose: Installs Skopeo for container registry operations.
# Usage: ./install_skopeo.sh
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
    lib::log "Starting Skopeo installation..."

    # Idempotency: binary present → skip
    if lib::cmd_exists skopeo; then
        lib::success "Skopeo already installed. Version: $(skopeo --version 2>&1 | head -n 1)"
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    lib::log "Ensuring prerequisites..."
    lib::ensure_packages ca-certificates curl apt-transport-https gnupg || true

    lib::log "Installing Skopeo..."
    lib::ensure_apt_updated
    lib::ensure_packages skopeo || true

    lib::log "Verifying installation..."
    if ! command -v skopeo &>/dev/null; then
        lib::error "Skopeo installation failed. The 'skopeo' command is not available."
        exit 1
    fi

    lib::log "Skopeo installation completed successfully."
    skopeo --version
}

main "$@"
