#!/usr/bin/env bash
#
# Purpose: Bootstrap bastion management VM for ${MODULE_NUM} (${MODULE_TYPE})
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
    lib::log "Writing environment variables to /etc/k8s-env"
    cat > /etc/k8s-env <<ENVEOF
export CLUSTER_TYPE="${CLUSTER_TYPE:-bastion}"
export NAMESPACE_DEFAULT="${NAMESPACE_DEFAULT:-ns-${MODULE_NUM}-labs}"
ENVEOF
    chmod 644 /etc/k8s-env

    # Ensure shells source this file
    if ! grep -q '/etc/k8s-env' /etc/profile.d/k8s-env.sh 2>/dev/null; then
        echo 'source /etc/k8s-env' > /etc/profile.d/k8s-env.sh
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
    lib::header "Bootstrapping Bastion Management VM for ${MODULE_NUM} (${MODULE_TYPE})"
    lib::run_pre_hooks "${DOLLAR}SCRIPT_DIR"
    write_env_files
    install_tools

    lib::log ""
    lib::log "Bastion workstation ready!"
    lib::log ""
    lib::log "This is a management workstation with kubectl and other tools installed."
    lib::log "Use it to manage remote Kubernetes clusters or as a jump host."
    lib::log ""

    lib::run_post_hooks "${DOLLAR}SCRIPT_DIR"
    touch "$LOCK_FILE"
    lib::success "Bootstrap completed successfully for ${MODULE_NUM} (${MODULE_TYPE})"
}

# --- Run ---
main "$@"
