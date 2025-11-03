#!/usr/bin/env bash
#
# Purpose: Bootstrap Vagrant VM for ${MODULE_NUM} (${MODULE_TYPE}) with kind (Kubernetes in Docker)
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

# --- Optional Azure environment ---
${AZURE_ENV_BLOCK}
if [ -f /etc/azure-env ]; then
    if ! grep -q '/etc/azure-env' /etc/profile.d/azure-env.sh 2>/dev/null; then
        echo 'source /etc/azure-env' > /etc/profile.d/azure-env.sh
    fi
fi

# --- Installation ---
install_tools() {
    lib::log "Installing required tools for kind..."
${INSTALL_COMMANDS_BLOCK}
}

# --- Main execution ---
main() {
    lib::header "Bootstrapping VM for ${MODULE_NUM} (${MODULE_TYPE}) with kind"
    lib::run_pre_hooks "${DOLLAR}SCRIPT_DIR"
    install_tools

    lib::log ""
    lib::log "kind installed. To create a cluster:"
    lib::log "  kind create cluster --name ${CLUSTER_NAME:-dev}"
    lib::log ""
    lib::log "Example multi-node config (save as kind-config.yaml):"
    lib::log "  kind: Cluster"
    lib::log "  apiVersion: kind.x-k8s.io/v1alpha4"
    lib::log "  nodes:"
    lib::log "    - role: control-plane"
    lib::log "      extraPortMappings:"
    lib::log "        - containerPort: 30080"
    lib::log "          hostPort: 30080"
    lib::log "        - containerPort: 30443"
    lib::log "          hostPort: 30443"
    lib::log "    - role: worker"
    lib::log "    - role: worker"
    lib::log "Create with:"
    lib::log "  kind create cluster --name ${CLUSTER_NAME:-dev} --config kind-config.yaml"

    lib::run_post_hooks "${DOLLAR}SCRIPT_DIR"
    touch "$LOCK_FILE"
    lib::success "Bootstrap completed successfully for ${MODULE_NUM} (${MODULE_TYPE})"
}

# --- Run ---
main "$@"
