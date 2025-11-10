#!/usr/bin/env bash
#
# Purpose: Install kubeadm, kubelet, and kubectl on Debian-based systems.
# Usage: Called automatically by bootstrap or run manually.
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
    lib::header "Installing kubeadm, kubelet, and kubectl"

    if lib::pkg_installed kubeadm && lib::pkg_installed kubelet && lib::pkg_installed kubectl; then
        lib::success "kubeadm/kubelet/kubectl already installed, skipping..."
        return 0
    fi

    local ver_major_minor
    # Use K8S_VERSION env if set (e.g., 1.30.2), otherwise default to 1.30.x
    if [ -n "${K8S_VERSION:-}" ]; then
        ver_major_minor="v${K8S_VERSION%.*}"
    else
        ver_major_minor="v1.30"
    fi

    lib::log "Adding Kubernetes apt repository for ${ver_major_minor} (idempotent)..."
    lib::ensure_packages gnupg || true
    lib::ensure_apt_key_from_url "https://pkgs.k8s.io/core:/stable:/${ver_major_minor}/deb/Release.key" /etc/apt/keyrings/kubernetes-apt-keyring.gpg
    lib::ensure_apt_source_file /etc/apt/sources.list.d/kubernetes.list \
      "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${ver_major_minor}/deb/ /"

    lib::log "Installing kubeadm, kubelet, kubectl..."
    lib::ensure_apt_updated
    lib::ensure_packages kubeadm kubelet kubectl || true
    apt-mark hold kubelet kubeadm kubectl || true

    lib::success "kubeadm/kubelet/kubectl installed successfully!"
}

main "$@"
