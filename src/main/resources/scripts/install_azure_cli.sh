#!/usr/bin/env bash
#
# Purpose: Installs the Azure CLI with GPG verification.
# Usage: ./install_azure_cli.sh
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
    lib::header "Starting Azure CLI installation..."

    if lib::cmd_exists az; then
        lib::success "Azure CLI already installed. Version: $(az version --output tsv --query '"'"'azure-cli'"'"' 2>/dev/null || echo unknown)"
        return 0
    fi

    export DEBIAN_FRONTEND=noninteractive

    lib::log "Ensuring prerequisites are present..."
    lib::ensure_packages ca-certificates curl apt-transport-https lsb-release gnupg || true

    lib::ensure_apt_key_from_url https://packages.microsoft.com/keys/microsoft.asc /etc/apt/keyrings/microsoft.gpg

    lib::log "Adding Azure CLI repository (idempotent)..."
    local AZ_REPO
    AZ_REPO=$(lsb_release -cs)
    lib::ensure_apt_source_file /etc/apt/sources.list.d/azure-cli.list \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/microsoft.gpg] https://packages.microsoft.com/repos/azure-cli/ $AZ_REPO main"

    lib::log "Installing Azure CLI package..."
    lib::ensure_apt_updated
    lib::ensure_packages azure-cli || true

    lib::log "Verifying installation..."
    if ! command -v az &>/dev/null; then
        lib::error "Azure CLI installation failed. The 'az' command is not available."
        exit 1
    fi

    lib::log "Azure CLI installation completed successfully."
    az version
}

main
