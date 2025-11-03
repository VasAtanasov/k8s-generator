#!/usr/bin/env bash
#
# Purpose: Generate a self-contained bootstrap script package for a new module.
# Usage: ./generate-bootstrap.sh --module m1 --type pt --cluster minikube
#

# --- Strict Mode & lib sourcing ---
# This script must always source the lib-generators.sh from its own directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${SCRIPT_DIR}/lib-generators.sh" ]; then
    # shellcheck disable=SC1091
    source "${SCRIPT_DIR}/lib-generators.sh"
else
    echo "ERROR: Could not find the shared library 'lib-generators.sh' in the script's own directory." >&2
    exit 1
fi
lib::strict
lib::setup_traps

# --- Configuration ---
MODULE_NUM=""
MODULE_TYPE=""
CLUSTER_TYPE="kubeadm"
ROLE=""
OUTPUT_DIR=""
MODULE_DIR=""
BOOTSTRAP_FILENAME="bootstrap.sh"

# Defaults
K8S_VERSION="1.30.2"
K8S_POD_CIDR="10.244.0.0/16"
K8S_SVC_CIDR="10.96.0.0/12"
CNI_TYPE="calico"
AZURE_ENABLED="false"
INCLUDE_TIMESTAMP="${INCLUDE_TIMESTAMP:-0}"
INSTALL_TOOLS=""

# --- Argument Parsing & Validation ---
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --module|-m) MODULE_NUM="$2"; shift 2 ;;
            --type|-t) MODULE_TYPE="$2"; shift 2 ;;
            --cluster|-c) CLUSTER_TYPE="$2"; shift 2 ;;
            --output-dir|-o) OUTPUT_DIR="$2"; shift 2 ;;
            --k8s-version) K8S_VERSION="$2"; shift 2 ;;
            --cni) CNI_TYPE="$2"; shift 2 ;;
            --azure) AZURE_ENABLED="true"; shift ;;
            --with-timestamp) INCLUDE_TIMESTAMP="1"; shift ;;
            --filename) BOOTSTRAP_FILENAME="$2"; shift 2 ;;
            --tools) INSTALL_TOOLS="$2"; shift 2 ;;
            --role) ROLE="$2"; shift 2 ;;
            -h|--help) show_usage; exit 0 ;;
            *) lib::error "Unknown option: $1"; show_usage; exit 1 ;;
        esac
    done
}

show_usage() {
    cat <<EOF
Usage: $0 --module NUM --type TYPE --cluster TYPE [OPTIONS]

Generates a self-contained bootstrap package for a course module.

Required:
  --module, -m NUM      Module number (e.g., m1, m7)
  --type, -t TYPE       Module type: hw (homework) or pt (practice)
  --cluster, -c TYPE    Cluster type: kubeadm, minikube, kind, aks, or bastion

Optional:
  --output-dir, -o DIR  Manually specify the output directory for the scripts.
  --k8s-version VER     Kubernetes version (default: ${K8S_VERSION})
  --cni TYPE            CNI plugin for kubeadm (default: ${CNI_TYPE})
  --azure               Enable Azure CLI + /etc/azure-env (kubeadm, minikube, kind).
                        Ignored for --cluster aks (always enabled there).
  --role ROLE           Node role for role-specific templates: bastion, master, worker
                        When set, generates prerequisites-only scripts for the role.
                        Backward compatible: if omitted, selects by --cluster type.
  --with-timestamp      Include generation timestamp in output (creates git noise)
  --filename FILE       Specify the output filename for the bootstrap script (default: bootstrap.sh)
  --tools LIST          Comma-separated list of additional (non-mandatory) tools to install.
                        Examples: helm, kustomize, k9s, maven, skopeo
                        Mandatory tools are automatically included based on cluster type.
  -h, --help            Show this help message

Examples:
  # Scenario 1: Local single-node cluster (minikube)
  $0 --module m1 --type pt --cluster minikube

  # Scenario 1b: Local single-node management VM (kind)
  $0 --module m1 --type pt --cluster kind

  # Scenario 2: Local multi-node cluster (kubeadm only)
  $0 --module m2 --type hw --cluster kubeadm

  # Scenario 3: Azure-only management workstation
  $0 --module m5 --type pt --cluster aks

  # Scenario 4: Parallel kubeadm + AKS (unified admin workstation)
  $0 --module m7 --type hw --cluster kubeadm --azure

  # Adding optional tools (works with any cluster type)
  $0 --module m3 --type pt --cluster minikube --tools helm,k9s,kustomize
EOF
}

validate_args() {
    if [ -z "$MODULE_NUM" ] || [ -z "$MODULE_TYPE" ] || [ -z "$CLUSTER_TYPE" ]; then
        lib::error "Missing required arguments: --module, --type, and --cluster are all required."
        show_usage
        exit 1
    fi

    # Validate module type
    local valid_types=("hw" "pt")
    local type_found=0
    for valid_type in "${valid_types[@]}"; do
        if [ "$MODULE_TYPE" = "$valid_type" ]; then
            type_found=1
            break
        fi
    done
    if [ "$type_found" -eq 0 ]; then
        lib::error "Invalid module type: ${MODULE_TYPE}"
        lib::log "Supported types: ${valid_types[*]}"
        exit 1
    fi

    # Validate cluster type
    case "$CLUSTER_TYPE" in
        kubeadm|minikube|kind|aks|bastion) ;;
        *)
            lib::error "Invalid cluster type: ${CLUSTER_TYPE}"
            lib::log "Supported cluster types: kubeadm, minikube, aks, bastion"
            exit 1
            ;;
    esac

    # Validate role if provided
    if [ -n "$ROLE" ]; then
        case "$ROLE" in
            bastion|master|worker) ;;
            *) lib::error "Invalid role: $ROLE (expected: bastion|master|worker)"; exit 1 ;;
        esac
    fi

    # Validate CNI type (only relevant for kubeadm)
    if [ "$CLUSTER_TYPE" = "kubeadm" ]; then
        local valid_cnis=("calico" "flannel" "weave")
        local cni_found=0
        for valid_cni in "${valid_cnis[@]}"; do
            if [ "$CNI_TYPE" = "$valid_cni" ]; then
                cni_found=1
                break
            fi
        done
        if [ "$cni_found" -eq 0 ]; then
            lib::error "Invalid CNI type: ${CNI_TYPE}"
            lib::log "Supported CNI types: ${valid_cnis[*]}"
            exit 1
        fi
    fi

    # Validate Azure flag semantics by cluster type
    if [ "$AZURE_ENABLED" = "true" ]; then
        case "$CLUSTER_TYPE" in
            kubeadm|minikube|kind)
                : # allowed: Azure CLI + /etc/azure-env
                ;;
            aks)
                lib::warn "Ignoring --azure for aks. AKS template already handles Azure environment."
                AZURE_ENABLED="false"
                ;;
        esac
    fi

    # Role + cluster compatibility
    if [ -n "$ROLE" ]; then
        case "$CLUSTER_TYPE" in
            minikube)
                # Minikube is always a single collocated node; ignore role to keep correct installers.
                lib::warn "Ignoring --role for minikube. Minikube is always single-node (collocated)."
                ROLE=""
                ;;
            kind)
                # kind VM is a management host running Docker+kind; ignore role.
                lib::warn "Ignoring --role for kind. kind is managed as a single-node management VM."
                ROLE=""
                ;;
            aks)
                # AKS path is always a single management VM; ignore role to include Azure CLI.
                lib::warn "Ignoring --role for aks. Using AKS management workstation semantics."
                ROLE=""
                ;;
            bastion)
                # Bastion path is always a single management VM; ignore role.
                lib::warn "Ignoring --role for bastion. Bastion is always a management workstation."
                ROLE=""
                ;;
            kubeadm)
                case "$ROLE" in
                    bastion|master|worker) : ;; # ok
                    *) lib::error "Invalid role: $ROLE (expected bastion|master|worker)"; exit 1 ;;
                esac
                ;;
        esac
    fi

    # Auto-detect module directory and set output path
    if [ -z "$OUTPUT_DIR" ]; then
        lib::log "Detecting module directory for '${MODULE_TYPE}-${MODULE_NUM}'..."
        # Find the specific module directory like 'pt-m1' or 'hw-m7'
        MODULE_DIR=$(find . -maxdepth 2 -type d -name "${MODULE_TYPE}-${MODULE_NUM}" -print -quit)

        if [ -z "$MODULE_DIR" ]; then
            lib::error "Could not find a directory for module '${MODULE_TYPE}-${MODULE_NUM}'."
            lib::error "Please run this script from the project root or specify a path with --output-dir."
            exit 1
        fi

        # The output directory is the 'scripts' subdir within the found module directory
        OUTPUT_DIR="${MODULE_DIR}/scripts"
    fi
    lib::log "Target script directory: $OUTPUT_DIR"

    # Validate additional tools list early to avoid surprises later
    if [ -n "$INSTALL_TOOLS" ]; then
        local supported_tools
        supported_tools=$(ls -1 "${SCRIPT_DIR}"/install_*.sh 2>/dev/null | sed -E 's#.*/install_(.+)\.sh#\1#' | sort -u)
        IFS=',' read -ra ADDTL_TOOLS <<< "$INSTALL_TOOLS"
        local invalid=()
        local t
        for t in "${ADDTL_TOOLS[@]}"; do
            [ -z "$t" ] && continue
            # azure_cli is not supported in --tools; use --azure flag instead
            if [ "$t" = "azure_cli" ]; then
                invalid+=("$t (use --azure flag instead)")
                continue
            fi
            if ! echo "$supported_tools" | grep -qx "$t"; then
                invalid+=("$t")
            fi
        done
        if [ "${#invalid[@]}" -gt 0 ]; then
            lib::error "Unknown tool(s) in --tools: ${invalid[*]}"
            lib::log "Supported tools:"
            echo "$supported_tools" | sed 's/^/  - /'
            lib::log ""
            lib::log "Note: To enable Azure CLI, use --azure flag instead of adding 'azure_cli' to --tools"
            exit 1
        fi

        # Additional policy: for AKS and bastion, disallow heavy local cluster tools
        if [ "$CLUSTER_TYPE" = "aks" ] || [ "$CLUSTER_TYPE" = "bastion" ]; then
            local forbidden_tools=(minikube kube_binaries kind k3s)
            local forbidden_found=()
            for t in "${ADDTL_TOOLS[@]}"; do
                for f in "${forbidden_tools[@]}"; do
                    if [ "$t" = "$f" ]; then forbidden_found+=("$t"); fi
                done
            done
            if [ "${#forbidden_found[@]}" -gt 0 ]; then
                lib::error "The following tools are not allowed with --cluster ${CLUSTER_TYPE} due to resource constraints: ${forbidden_found[*]}"
                lib::log "Use one of these approaches instead:"
                lib::log "  - Generate a minikube module for local cluster with optional --azure flag"
                lib::log "  - Or use multi-kubeadm for local clusters and keep management VMs separate"
                exit 1
            fi
        fi
    fi
}

# --- Dependency Management ---
copy_dependencies() {
    local dest_dir="$1"
    lib::log "Copying dependencies to $dest_dir..."
    mkdir -p "$dest_dir"

    local all_script_names
    all_script_names=$(_get_all_install_script_names)

    while IFS= read -r dep; do
        if [ -f "${SCRIPT_DIR}/${dep}" ]; then
            cp "${SCRIPT_DIR}/${dep}" "${dest_dir}/"
            lib::log "  Copied ${dep}"
        else
            lib::warn "Dependency not found, skipping: ${dep}"
        fi
    done <<< "$all_script_names"

    # Always copy lib.sh as it's required by all bootstrap scripts
    if [ -f "${SCRIPT_DIR}/lib.sh" ]; then
        cp "${SCRIPT_DIR}/lib.sh" "${dest_dir}/"
        lib::log "  Copied lib.sh"
    else
        lib::error "Required dependency lib.sh not found in ${SCRIPT_DIR}"
        exit 1
    fi
}

# --- Common Helpers ---
_get_lock_file_path() {
    echo "/var/lib/k8s-bootstrap-${MODULE_NUM}-${MODULE_TYPE}.done"
}

# --- Azure Support Helpers ---
_get_azure_dependencies() {
    if [ "$AZURE_ENABLED" = "true" ]; then
        echo "install_azure_cli.sh"
    fi
}

_generate_azure_env_block() {
    if [ "$AZURE_ENABLED" != "true" ]; then
        return
    fi

    cat <<'AZURE_BLOCK'

    # Optional: Azure variables
    lib::log "Writing Azure variables to /etc/azure-env"
    cat > /etc/azure-env <<AZENVEOF
export AZ_LOCATION="${AZ_LOCATION:-westeurope}"
export AZ_RESOURCE_GROUP="${AZ_RESOURCE_GROUP:-rg-k8s-MODULE_NUM_PLACEHOLDER}"
export AKS_NAME="${AKS_NAME:-aks-MODULE_NUM_PLACEHOLDER-core}"
AZENVEOF
    chmod 644 /etc/azure-env
AZURE_BLOCK
}

_generate_azure_install_command() {
    if [ "$AZURE_ENABLED" = "true" ]; then
        echo 'bash "$SCRIPT_DIR/install_azure_cli.sh"'
    fi
}

_generate_additional_tool_installs() {
    local install_commands_array=()

    # Skip if no additional tools specified
    [ -z "$INSTALL_TOOLS" ] && return 0

    IFS=',' read -ra ADDTL_TOOLS <<< "$INSTALL_TOOLS"
    for tool in "${ADDTL_TOOLS[@]}"; do
        # Skip empty entries (e.g., from trailing commas)
        [ -z "$tool" ] && continue

        local tool_script="install_${tool}.sh"
        if [ -f "${SCRIPT_DIR}/${tool_script}" ]; then
            install_commands_array+=("    bash \"\$SCRIPT_DIR/${tool_script}\"")
        else
            lib::warn "Additional tool script not found, will not include in generated bootstrap: ${tool_script}"
        fi
    done
    printf "%s\n" "${install_commands_array[@]}"
}

_get_mandatory_install_script_names() {
    local mandatory_script_names=(
        "install_base_packages.sh"
    )

    if [ -n "$ROLE" ]; then
        case "$ROLE" in
            bastion)
                mandatory_script_names+=("install_kubectl.sh")
                ;;
            master|worker)
                mandatory_script_names+=("install_containerd.sh" "install_kube_binaries.sh")
                ;;
        esac
    else
        case "$CLUSTER_TYPE" in
            minikube)
                # kubectl + docker + minikube
                mandatory_script_names+=("install_kubectl.sh" "install_docker.sh" "install_minikube.sh")
                ;;
            kind)
                # kubectl + docker + kind
                mandatory_script_names+=("install_kubectl.sh" "install_docker.sh" "install_kind.sh")
                if [ "$AZURE_ENABLED" = "true" ]; then
                    mandatory_script_names+=("install_azure_cli.sh")
                fi
                ;;
            kubeadm)
                # kubeadm path uses containerd and kube binaries installer (includes kubectl)
                mandatory_script_names+=("install_containerd.sh" "install_kube_binaries.sh")
                if [ "$AZURE_ENABLED" = "true" ]; then
                    mandatory_script_names+=("install_azure_cli.sh")
                fi
                ;;
            aks)
                # management VM: base + kubectl + az
                mandatory_script_names+=("install_kubectl.sh" "install_azure_cli.sh")
                ;;
            bastion)
                # bastion management VM: base + kubectl (no cluster tools)
                mandatory_script_names+=("install_kubectl.sh")
                ;;
        esac
    fi
    printf "%s\n" "${mandatory_script_names[@]}"
}

_get_all_install_script_names() {
    local all_script_names=()
    local seen=()

    # Add mandatory scripts
    while IFS= read -r script_name; do
        all_script_names+=("$script_name")
        seen+=("$script_name")
    done <<< "$(_get_mandatory_install_script_names)"

    # Add additional tools specified by --tools, avoiding duplicates and non-existent scripts
    IFS=',' read -ra ADDTL_TOOLS <<< "$INSTALL_TOOLS"
    for tool in "${ADDTL_TOOLS[@]}"; do
        local tool_script="install_${tool}.sh"
        local is_duplicate=0
        for s in "${seen[@]}"; do
            if [ "$tool_script" = "$s" ]; then
                is_duplicate=1
                break
            fi
        done

        if [ "$is_duplicate" -eq 0 ]; then
            if [ -f "${SCRIPT_DIR}/${tool_script}" ]; then
                all_script_names+=("$tool_script")
                seen+=("$tool_script")
            else
                lib::warn "Additional tool script not found, skipping: ${tool_script}"
            fi
        else
            # Duplicate with mandatory list; skip quietly for azure_cli to reduce noise
            if [ "$tool_script" != "install_azure_cli.sh" ]; then
                lib::warn "Skipping additional tool (already mandatory): ${tool_script}"
            fi
        fi
    done
    printf "%s\n" "${all_script_names[@]}"
}

_get_mandatory_install_commands() {
    local mandatory_installs=(
        'bash "$SCRIPT_DIR/install_base_packages.sh"'
    )

    if [ -n "$ROLE" ]; then
        case "$ROLE" in
            bastion)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_kubectl.sh"'
                )
                ;;
            master|worker)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_containerd.sh"'
                    'bash "$SCRIPT_DIR/install_kube_binaries.sh"'
                )
                ;;
        esac
    else
        case "$CLUSTER_TYPE" in
            minikube)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_kubectl.sh"'
                    'bash "$SCRIPT_DIR/install_docker.sh"'
                    'bash "$SCRIPT_DIR/install_minikube.sh"'
                )
                ;;
            kubeadm)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_containerd.sh"'
                    'bash "$SCRIPT_DIR/install_kube_binaries.sh"'
                )
                if [ "$AZURE_ENABLED" = "true" ]; then
                    mandatory_installs+=('bash "$SCRIPT_DIR/install_azure_cli.sh"')
                fi
                ;;
            aks)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_kubectl.sh"'
                    'bash "$SCRIPT_DIR/install_azure_cli.sh"'
                )
                ;;
            bastion)
                mandatory_installs+=(
                    'bash "$SCRIPT_DIR/install_kubectl.sh"'
                )
                ;;
        esac
    fi
    printf "%s\n" "${mandatory_installs[@]}"
}

# --- Template selection ---
select_bootstrap_template() {
    local cluster_type="$1"
    local role="${2:-}"
    if [ -n "$role" ]; then
        case "$role" in
            bastion|master|worker)
                echo "${SCRIPT_DIR}/templates/bootstrap/${role}.sh.tpl"
                return 0
                ;;
            *)
                lib::error "Unknown role: $role"; return 1 ;;
        esac
    fi
    case "$cluster_type" in
        minikube) echo "${SCRIPT_DIR}/templates/bootstrap/minikube.sh.tpl" ;;
        kind)     echo "${SCRIPT_DIR}/templates/bootstrap/kind.sh.tpl"     ;;
        kubeadm)  echo "${SCRIPT_DIR}/templates/bootstrap/kubeadm.sh.tpl"  ;;
        aks)      echo "${SCRIPT_DIR}/templates/bootstrap/aks.sh.tpl"      ;;
        bastion)  echo "${SCRIPT_DIR}/templates/bootstrap/bastion.sh.tpl"  ;;
        *) lib::error "Unknown cluster type: $cluster_type"; return 1 ;;
    esac
}

# --- Template Generation ---
generate_bootstrap_content() {
    # Deprecated: generation now handled via template rendering.
    # Kept as a shim if needed in the future.
    :
}

_generate_minikube_bootstrap_template() { :; }

_generate_aks_bootstrap_template() { :; }

_generate_kubeadm_bootstrap_template() { :; }

# --- Main Logic ---
main() {
    lib::header "Bootstrap Generator"

    parse_args "$@"
    validate_args

    lib::log "Configuration:"
    lib::kv "Module" "${MODULE_NUM} (${MODULE_TYPE})"
    lib::kv "Cluster Type" "$CLUSTER_TYPE"
    if [ -n "$ROLE" ]; then lib::kv "Role" "$ROLE"; fi
    lib::kv "Output Dir" "$OUTPUT_DIR"
    lib::kv "Filename" "$BOOTSTRAP_FILENAME"

    lib::log ""
    lib::log "Generated bootstrap will install:"
    local ctx_label
    ctx_label="${ROLE:-$CLUSTER_TYPE}"
    lib::log "  Mandatory tools for $ctx_label:"
    while IFS= read -r tool; do
        lib::log "    - ${tool%.sh}"
    done <<< "$(_get_mandatory_install_script_names)"

    if [ -n "$INSTALL_TOOLS" ]; then
        lib::log "  Additional tools:"
        IFS=',' read -ra ADDTL_TOOLS <<< "$INSTALL_TOOLS"
        for tool in "${ADDTL_TOOLS[@]}"; do
            [ -z "$tool" ] && continue
            lib::log "    - $tool"
        done

        # Check for multiple cluster tools
        local cluster_tools=()
        for tool in "${ADDTL_TOOLS[@]}"; do
            [ -z "$tool" ] && continue
            case "$tool" in
                minikube|kind|k3s)
                    cluster_tools+=("$tool")
                    ;;
            esac
        done

        # Warn if installing additional cluster tools
        if [ "${#cluster_tools[@]}" -gt 0 ]; then
            lib::log ""
            lib::warn "Multiple cluster tools detected!"
            lib::warn "Primary cluster: $CLUSTER_TYPE"
            lib::warn "Additional cluster tools: ${cluster_tools[*]}"
            lib::log ""
            lib::log "Note: Running multiple clusters simultaneously may cause:"
            lib::log "  - Resource contention (CPU/RAM)"
            lib::log "  - Port conflicts"
            lib::log "  - Kubeconfig context confusion"
            lib::log ""
            lib::log "Recommendation: Only run one cluster at a time."
            lib::log "Switch contexts with: kubectl config use-context <context-name>"
        fi
    fi
    lib::log ""

    # Prepare common template variables
    export DOLLAR='$'
    if [ "$INCLUDE_TIMESTAMP" = "1" ]; then
        export TIMESTAMP_LINE="# Generated: $(date '+%Y-%m-%d %H:%M:%S')"
    else
        export TIMESTAMP_LINE=""
    fi
    export LOCK_FILE=$(_get_lock_file_path)

    # Build blocks depending on role or cluster type
    case "${ROLE:-$CLUSTER_TYPE}" in
        minikube|kind)
            # Build combined install commands block
            INSTALL_COMMANDS_BLOCK=""
            while IFS= read -r script_name; do
                INSTALL_COMMANDS_BLOCK+="    bash \"${DOLLAR}SCRIPT_DIR/${script_name}\""
                INSTALL_COMMANDS_BLOCK+=$'\n'
            done <<< "$(_get_all_install_script_names)"
            export INSTALL_COMMANDS_BLOCK

            # Optional Azure env defaults for minikube
            if [ "$AZURE_ENABLED" = "true" ]; then
                AZURE_ENV_BLOCK=$(_generate_azure_env_block | sed "s/MODULE_NUM_PLACEHOLDER/${MODULE_NUM}/g")
                export AZURE_ENV_BLOCK
            else
                export AZURE_ENV_BLOCK=""
            fi
            ;;
        kubeadm|master|worker)
            # Azure block (optional)
            AZURE_ENV_BLOCK=$(_generate_azure_env_block | sed "s/MODULE_NUM_PLACEHOLDER/${MODULE_NUM}/g")
            export AZURE_ENV_BLOCK

            # Mandatory + additional installers
            MANDATORY_INSTALLS_BLOCK=""
            while IFS= read -r cmd; do
                MANDATORY_INSTALLS_BLOCK+="    ${cmd}"
                MANDATORY_INSTALLS_BLOCK+=$'\n'
            done <<< "$(_get_mandatory_install_commands)"
            export MANDATORY_INSTALLS_BLOCK

            ADDITIONAL_INSTALLS_BLOCK=$(_generate_additional_tool_installs)
            export ADDITIONAL_INSTALLS_BLOCK
            ;;
        aks|bastion)
            MANDATORY_INSTALLS_BLOCK=""
            while IFS= read -r cmd; do
                MANDATORY_INSTALLS_BLOCK+="    ${cmd}"
                MANDATORY_INSTALLS_BLOCK+=$'\n'
            done <<< "$(_get_mandatory_install_commands)"
            export MANDATORY_INSTALLS_BLOCK

            ADDITIONAL_INSTALLS_BLOCK=$(_generate_additional_tool_installs)
            export ADDITIONAL_INSTALLS_BLOCK
            ;;
    esac

    # Render template for selected role or cluster type
    export MODULE_NUM MODULE_TYPE
    local tpl_path
    tpl_path=$(select_bootstrap_template "$CLUSTER_TYPE" "$ROLE")
    lib::log "Writing bootstrap script to: ${OUTPUT_DIR}/${BOOTSTRAP_FILENAME}"
    mkdir -p "$OUTPUT_DIR"
    render_template "$tpl_path" "${OUTPUT_DIR}/${BOOTSTRAP_FILENAME}"
    chmod +x "${OUTPUT_DIR}/${BOOTSTRAP_FILENAME}"

    copy_dependencies "$OUTPUT_DIR"

    # Scaffold local override files and hook directories (created only if missing)
    # These allow safe customizations without modifying generated files.
    {
        mkdir -p "${OUTPUT_DIR}/bootstrap.pre.d/common" \
                 "${OUTPUT_DIR}/bootstrap.post.d/common" \
                 "${OUTPUT_DIR}/env/cluster" \
                 "${OUTPUT_DIR}/env/role" \
                 "${OUTPUT_DIR}/env/cluster-role"

        # Create local env override stub
        if [ ! -f "${OUTPUT_DIR}/bootstrap.env.local" ]; then
            cat > "${OUTPUT_DIR}/bootstrap.env.local" <<'EOF'
# Local environment overrides for bootstrap (optional)
# Example:
# export K8S_VERSION="1.30.4"
# export EXTRA_VAR="hello"
EOF
        fi

        # Create pre/post local hook stubs
        if [ ! -f "${OUTPUT_DIR}/bootstrap.pre.local.sh" ]; then
            cat > "${OUTPUT_DIR}/bootstrap.pre.local.sh" <<'EOF'
#!/usr/bin/env bash
# Purpose: Local pre-bootstrap customizations (optional)
# Usage: automatically executed before the main installers

set -Eeuo pipefail
echo "[bootstrap.pre.local] running (no-op)"
EOF
            chmod +x "${OUTPUT_DIR}/bootstrap.pre.local.sh"
        fi
        if [ ! -f "${OUTPUT_DIR}/bootstrap.post.local.sh" ]; then
            cat > "${OUTPUT_DIR}/bootstrap.post.local.sh" <<'EOF'
#!/usr/bin/env bash
# Purpose: Local post-bootstrap customizations (optional)
# Usage: automatically executed after the main installers

set -Eeuo pipefail
echo "[bootstrap.post.local] running (no-op)"
EOF
            chmod +x "${OUTPUT_DIR}/bootstrap.post.local.sh"
        fi

        # Add small README stubs for discoverability (if not present)
        for d in "${OUTPUT_DIR}/bootstrap.pre.d" \
                 "${OUTPUT_DIR}/bootstrap.post.d" \
                 "${OUTPUT_DIR}/env/cluster" \
                 "${OUTPUT_DIR}/env/role" \
                 "${OUTPUT_DIR}/env/cluster-role"; do
            if [ ! -f "$d/README.md" ]; then
                cat > "$d/README.md" <<'EOF'
This directory contains optional scoped overrides.

- For hooks (.pre.d/.post.d), add executable .sh files. They run in lexicographic order.
- For env directories, add files ending with .env.local to export variables.

Examples:
- bootstrap.pre.d/common/10-custom.sh
- env/cluster/clu-m1-core.env.local
- env/role/master.env.local
- env/cluster-role/clu-m1-core-master.env.local
EOF
            fi
        done
    }

    lib::hr
    lib::success "Bootstrap package generated successfully!"
    local module_dir_safe
    module_dir_safe="${MODULE_DIR:-$(dirname "$OUTPUT_DIR")}"
    lib::log "Next step: Update the Vagrantfile in '${module_dir_safe}/' to remove any old references to shared scripts."
}

# --- Run ---
main "$@"
