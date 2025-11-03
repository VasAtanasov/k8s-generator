#!/usr/bin/env bash
#
# Purpose: Installs common base packages and utilities.
# Usage: ./install_base_packages.sh
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
    lib::header "Starting installation of base packages..."

    export DEBIAN_FRONTEND=noninteractive
    lib::apt_update_once

    # Let apt and our helpers handle idempotency
    lib::ensure_packages jq curl yq tree git vim tmux wget zip unzip w3m tar gpg bash-completion || true

    lib::success "Base packages installation completed (idempotent)."
}

# --- Run ---
main
