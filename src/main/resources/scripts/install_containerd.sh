#!/usr/bin/env bash
#
# Purpose: Install containerd on Debian-based systems.
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
    lib::header "Installing containerd"

    # Idempotency: package + service active
    if lib::pkg_installed containerd.io || lib::pkg_installed containerd; then
        if lib::systemd_active containerd; then
            lib::success "containerd already installed and active, skipping..."
            return 0
        fi
    fi

    lib::log "Ensuring prerequisites..."
    lib::ensure_packages apt-transport-https ca-certificates curl gnupg || true

    lib::ensure_apt_key_from_url https://download.docker.com/linux/debian/gpg /etc/apt/keyrings/docker.gpg

    lib::log "Adding Docker's APT repository (idempotent)..."
    lib::ensure_apt_source_file /etc/apt/sources.list.d/docker.list \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo \"$VERSION_CODENAME\") stable"
    lib::ensure_apt_updated

    lib::log "Installing containerd.io..."
    lib::ensure_packages containerd.io || true

    lib::log "Configuring containerd to use systemd cgroup driver..."
    if [ ! -f /etc/containerd/config.toml ]; then
        containerd config default | tee /etc/containerd/config.toml > /dev/null
    fi
    if grep -q '^\s*SystemdCgroup = false' /etc/containerd/config.toml; then
        sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    fi

    lib::log "Enabling and (re)starting containerd service..."
    lib::ensure_service containerd || systemctl restart containerd

    lib::log "Verifying containerd installation..."
    systemctl is-active containerd >/dev/null 2>&1 || { lib::error "containerd not active"; exit 1; }

    lib::success "containerd installed successfully!"
}

main "$@"
