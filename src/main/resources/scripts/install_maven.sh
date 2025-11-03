#!/usr/bin/env bash
#
# Purpose: Installs Apache Maven.
# Usage: ./install_maven.sh
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
    lib::log "Starting Apache Maven installation..."

    # Idempotency: binary present → skip
    if lib::cmd_exists mvn; then
        lib::success "Maven already installed. Version: $(mvn -version 2>&1 | head -n 1)"
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    lib::apt_update_once
    lib::ensure_packages maven || true

    lib::log "Verifying installation..."
    if ! command -v mvn &>/dev/null; then
        lib::error "Maven installation failed. The 'mvn' command is not available."
        exit 1
    fi

    lib::log "Maven installation completed successfully."
    mvn -version
}

# --- Run ---
main
