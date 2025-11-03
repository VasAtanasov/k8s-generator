#!/usr/bin/env bash
#
# Purpose: Bootstrap Azure management VM for ${MODULE_NUM} (${MODULE_TYPE})
# Usage: Called automatically by Vagrant provisioner
${TIMESTAMP_LINE}
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

# --- Source environment overrides ---
lib::source_scoped_envs "${DOLLAR}SCRIPT_DIR"

# --- Idempotency lock ---
LOCK_FILE="${LOCK_FILE}"
if [ -f "$LOCK_FILE" ]; then
    lib::log "Bootstrap already completed, skipping..."
    exit 0
fi

# --- Persist environment variables ---
write_env_files() {
    lib::log "Writing Azure environment variables to /etc/azure-env"
    cat > /etc/azure-env <<ENVEOF
export AZ_LOCATION="${AZ_LOCATION:-westeurope}"
export AZ_RESOURCE_GROUP="${AZ_RESOURCE_GROUP:-rg-k8s-${MODULE_NUM}}"
export AKS_NAME="${AKS_NAME:-aks-${MODULE_NUM}-core}"
export ACR_NAME="${ACR_NAME:-acr${MODULE_NUM}example}"
ENVEOF
    chmod 644 /etc/azure-env

    # Ensure shells source this file
    if ! grep -q '/etc/azure-env' /etc/profile.d/azure-env.sh 2>/dev/null; then
        echo 'source /etc/azure-env' > /etc/profile.d/azure-env.sh
    fi
}

# --- Installation ---
install_tools() {
    lib::log "Installing required tools..."
${MANDATORY_INSTALLS_BLOCK}
${ADDITIONAL_INSTALLS_BLOCK}
}

# --- Main execution ---
main() {
    lib::header "Bootstrapping Azure Management VM for ${MODULE_NUM} (${MODULE_TYPE})"
    lib::run_pre_hooks "${DOLLAR}SCRIPT_DIR"
    write_env_files
    install_tools

    lib::log ""
    lib::log "Azure management workstation ready!"
    lib::log ""
    lib::log "Next steps:"
    lib::log "  1. Login to Azure:"
    lib::log "     az login --use-device-code"
    lib::log ""
    lib::log "  2. Create AKS cluster:"
    lib::log "     az aks create -g \"$AZ_RESOURCE_GROUP\" -n \"$AKS_NAME\" --location \"$AZ_LOCATION\""
    lib::log ""
    lib::log "  3. Get credentials:"
    lib::log "     az aks get-credentials -g \"$AZ_RESOURCE_GROUP\" -n \"$AKS_NAME\""
    lib::log ""

    lib::run_post_hooks "${DOLLAR}SCRIPT_DIR"
    touch "$LOCK_FILE"
    lib::success "Bootstrap completed successfully for ${MODULE_NUM} (${MODULE_TYPE})"
}

# --- Run ---
main "$@"
