#!/usr/bin/env bash
#
# Purpose: Installs SDKMAN! for managing JVM SDKs (Java, Maven, Gradle, etc.).
# Usage: ./install_sdkman.sh [--java VERSION] [--maven VERSION]
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

# --- Configuration ---
# If run with sudo from a non-root user (like 'vagrant'), install for that user.
if [ -n "${SUDO_USER:-}" ] && [ "$SUDO_USER" != "root" ]; then
    TARGET_USER="$SUDO_USER"
    # Safely get the home directory of the target user
    TARGET_HOME=$(eval echo "~$SUDO_USER")
else
    TARGET_USER="$USER"
    TARGET_HOME="$HOME"
fi

SDKMAN_DIR="${SDKMAN_DIR:-${TARGET_HOME}/.sdkman}"
JAVA_VERSION="${JAVA_VERSION:-}"
MAVEN_VERSION="${MAVEN_VERSION:-}"

# --- Parse Arguments ---
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --java)
                JAVA_VERSION="$2"
                shift 2
                ;;
            --maven)
                MAVEN_VERSION="$2"
                shift 2
                ;;
            -h|--help)
                cat <<EOF
Usage: $0 [OPTIONS]

Install SDKMAN! and optionally install Java and/or Maven versions.

Options:
  --java VERSION    Install specific Java version (e.g., 21.0.5-tem, 17.0.13-tem)
  --maven VERSION   Install specific Maven version (e.g., 3.9.9)
  -h, --help        Show this help message

Examples:
  $0                              # Install SDKMAN! only
  $0 --java 21.0.5-tem           # Install SDKMAN! + Java 21
  $0 --java 21.0.5-tem --maven 3.9.9  # Install SDKMAN! + Java 21 + Maven 3.9

Available Java versions: sdk list java
Available Maven versions: sdk list maven
EOF
                exit 0
                ;;
            *)
                lib::error "Unknown option: $1"
                exit 1
                ;;
        esac
    done
}

# --- Check if SDKMAN! is already installed ---
is_sdkman_installed() {
    [ -d "$SDKMAN_DIR" ] && [ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]
}

# --- Source SDKMAN! for use in this script ---
source_sdkman() {
    # Temporarily disable 'exit on unset variable' for sourcing the sdkman script,
    # as it may contain unbound variables that are not critical for its function
    # in a non-interactive shell.
    set +u
    # shellcheck disable=SC1091
    source "$SDKMAN_DIR/bin/sdkman-init.sh"
    set -u
}

# --- Install SDKMAN! ---
install_sdkman() {
    lib::log "Installing SDKMAN! for user '$TARGET_USER' in: $SDKMAN_DIR"

    # Install required dependencies
    export DEBIAN_FRONTEND=noninteractive
    lib::log "Installing dependencies (curl, zip, unzip)..."
    lib::ensure_packages curl zip unzip || true

    # Download and install SDKMAN! using official CI mode
    lib::log "Downloading SDKMAN! installer (CI mode)..."
    export SDKMAN_DIR
    
    # Run the installer as the target user to ensure correct ownership and home dir resolution
    sudo -u "$TARGET_USER" bash -c "curl -s 'https://get.sdkman.io?ci=true' | bash"

    # Verify installation
    if ! is_sdkman_installed; then
        lib::error "SDKMAN! installation failed. Directory not found: $SDKMAN_DIR"
        exit 1
    fi

    lib::success "SDKMAN! installed successfully at: $SDKMAN_DIR"
}

# --- Configure shell integration ---
configure_shell_integration() {
    lib::log "Configuring shell integration..."

    # This targets the user who ran sudo, which is what we want.
    local target_bashrc="${TARGET_HOME}/.bashrc"

    if [ -f "$target_bashrc" ]; then
        # Check if already configured
        if grep -q "sdkman-init.sh" "$target_bashrc" 2>/dev/null; then
            lib::debug "SDKMAN! already configured in: $target_bashrc"
        else
        lib::log "Adding SDKMAN! to: $target_bashrc"
            cat >> "$target_bashrc" <<EOF

# SDKMAN! - Software Development Kit Manager
export SDKMAN_DIR="${SDKMAN_DIR}"
[[ -s "\$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "\$SDKMAN_DIR/bin/sdkman-init.sh"
EOF
            chown "$TARGET_USER:$TARGET_USER" "$target_bashrc"
        fi
    fi

    # Configure for non-interactive shells (system-wide) for root
    if [ ! -f /etc/profile.d/sdkman.sh ]; then
        lib::log "Creating system-wide SDKMAN! profile..."
        cat > /etc/profile.d/sdkman.sh <<'EOF'
# SDKMAN! - Software Development Kit Manager
if [ -d "/home/vagrant/.sdkman" ]; then
    export SDKMAN_DIR="/home/vagrant/.sdkman"
    [[ -s "$SDKMAN_DIR/bin/sdkman-init.sh" ]] && source "$SDKMAN_DIR/bin/sdkman-init.sh"
fi
EOF
        chmod 644 /etc/profile.d/sdkman.sh
    fi

    lib::success "Shell integration configured"
}

# --- Install Java via SDKMAN! ---
install_java_sdk() {
    local version=$1
    lib::log "Installing Java version: $version"

    source_sdkman

    # Check if already installed
    if sdk list java 2>/dev/null | grep -q "installed.*$version"; then
        lib::log "Java $version is already installed"
        return 0
    fi

    # Install Java in non-interactive mode
    # SDKMAN_ROSETTA2_COMPATIBLE=false prevents prompts on ARM Macs
    # Redirect stdin from /dev/null to prevent any interactive prompts
    # Temporarily disable 'exit on unset variable' for SDK commands,
    # as SDKMAN's internal scripts may reference unbound variables
    lib::log "Downloading and installing Java $version..."
    set +u
    SDKMAN_ROSETTA2_COMPATIBLE=false sdk install java "$version" < /dev/null || {
        lib::warn "First attempt failed, retrying..."
        sleep 2
        SDKMAN_ROSETTA2_COMPATIBLE=false sdk install java "$version" < /dev/null
    }

    # Set as default
    sdk default java "$version" < /dev/null
    set -u

    lib::success "Java $version installed and set as default"
}

# --- Install Maven via SDKMAN! ---
install_maven_sdk() {
    local version=$1
    lib::log "Installing Maven version: $version"

    source_sdkman

    # Check if already installed
    if sdk list maven 2>/dev/null | grep -q "installed.*$version"; then
        lib::log "Maven $version is already installed"
        return 0
    fi

    # Install Maven in non-interactive mode
    # Temporarily disable 'exit on unset variable' for SDK commands,
    # as SDKMAN's internal scripts may reference unbound variables
    lib::log "Downloading and installing Maven $version..."
    set +u
    sdk install maven "$version" < /dev/null || {
        lib::warn "First attempt failed, retrying..."
        sleep 2
        sdk install maven "$version" < /dev/null
    }

    # Set as default
    sdk default maven "$version" < /dev/null
    set -u

    lib::success "Maven $version installed and set as default"
}

# --- Show installed SDKs ---
show_installed_sdks() {
    lib::log "Currently installed SDKs:"
    source_sdkman

    if command -v java &>/dev/null; then
        lib::kv "Java" "$(java -version 2>&1 | head -n 1)"
    fi

    if command -v mvn &>/dev/null; then
        lib::kv "Maven" "$(mvn -version 2>&1 | head -n 1)"
    fi
}

# --- Main Logic ---
main() {
    lib::header "SDKMAN! Installation"

    parse_args "$@"

    # Idempotency: state only
    if is_sdkman_installed; then
        lib::success "SDKMAN! already installed at: $SDKMAN_DIR"
        lib::log "Version: $(source_sdkman && sdk version)"
    else
        install_sdkman
        configure_shell_integration
    fi

    # Install Java if requested
    if [ -n "$JAVA_VERSION" ]; then
        install_java_sdk "$JAVA_VERSION"
    fi

    # Install Maven if requested
    if [ -n "$MAVEN_VERSION" ]; then
        install_maven_sdk "$MAVEN_VERSION"
    fi

    lib::hr
    lib::success "SDKMAN! setup completed successfully"
    show_installed_sdks

    lib::hr
    lib::log "Usage:"
    lib::log "  sdk list java          # List available Java versions"
    lib::log "  sdk list maven         # List available Maven versions"
    lib::log "  sdk install java VERSION  # Install specific Java version"
    lib::log "  sdk default java VERSION  # Set default Java version"
    lib::log "  sdk current            # Show current versions"
    lib::log ""
    lib::log "For the current shell, source SDKMAN!:"
    lib::log "  source ~/.sdkman/bin/sdkman-init.sh"
}

# --- Run ---
main "$@"
