#!/usr/bin/env bash
#
# Purpose: Bootstrap kubeadm master prerequisites for ${MODULE_NUM} (${MODULE_TYPE})
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

LOCK_FILE="${LOCK_FILE}"
if [ -f "$LOCK_FILE" ]; then
    lib::log "Prerequisites already installed, skipping..."
    exit 0
fi

write_env_files() {
    lib::log "Writing environment variables to /etc/k8s-env"
    cat > /etc/k8s-env <<ENVEOF
export CLUSTER_NAME="${CLUSTER_NAME:-clu-${MODULE_NUM}}"
export K8S_VERSION="${K8S_VERSION:-1.30.2}"
export K8S_POD_CIDR="${K8S_POD_CIDR:-10.244.0.0/16}"
export K8S_SVC_CIDR="${K8S_SVC_CIDR:-10.96.0.0/12}"
export CNI_TYPE="${CNI_TYPE:-calico}"
export NAMESPACE_DEFAULT="${NAMESPACE_DEFAULT:-ns-${MODULE_NUM}-labs}"
export NODE_ROLE="master"
ENVEOF
    chmod 644 /etc/k8s-env
}

install_tools() {
    lib::log "Installing kubeadm prerequisites (no cluster init)..."
${MANDATORY_INSTALLS_BLOCK}
${ADDITIONAL_INSTALLS_BLOCK}
}

next_steps() {
    lib::log ""
    lib::log "Manual steps (run on master):"
    lib::log "  sudo kubeadm config images pull --kubernetes-version \"$K8S_VERSION\""
    lib::log "  sudo kubeadm init --pod-network-cidr \"$K8S_POD_CIDR\" --service-cidr \"$K8S_SVC_CIDR\" \\"
    lib::log "       --apiserver-advertise-address \"$(hostname -I | awk '{print $2}')\" --kubernetes-version \"$K8S_VERSION\""
    lib::log "  mkdir -p ~/.kube && sudo cp /etc/kubernetes/admin.conf ~/.kube/config && sudo chown $(id -u):$(id -g) ~/.kube/config"
    lib::log "  # Save artifacts to /vagrant/.clusters/$CLUSTER_NAME/ for workers and bastion"
    lib::log "  sudo mkdir -p /vagrant/.clusters/\"$CLUSTER_NAME\" && sudo cp /etc/kubernetes/admin.conf /vagrant/.clusters/\"$CLUSTER_NAME\"/"
    lib::log "  sudo kubeadm token create --print-join-command | sudo tee /vagrant/.clusters/\"$CLUSTER_NAME\"/join.sh && sudo chmod +x /vagrant/.clusters/\"$CLUSTER_NAME\"/join.sh"
    lib::log "  # Install CNI according to CNI_TYPE"
}

main() {
    lib::header "Bootstrapping kubeadm master prerequisites for ${MODULE_NUM} (${MODULE_TYPE})"
    write_env_files
    lib::run_pre_hooks "${DOLLAR}SCRIPT_DIR"
    install_tools
    lib::run_post_hooks "${DOLLAR}SCRIPT_DIR"
    touch "$LOCK_FILE"
    lib::success "Kubeadm prerequisites installed."
    next_steps
}

main "$@"
