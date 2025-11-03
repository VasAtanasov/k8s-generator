#!/usr/bin/env bash
#
# Purpose: Generate a Vagrantfile for a new Kubernetes module.
# Usage: ./generate-vagrantfile.sh --module m1 --type pt --cluster minikube
#

# --- Strict Mode & lib sourcing ---
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
CLUSTER_TYPE="kubeadm" # kubeadm, minikube, kind, aks, bastion, or multi-kubeadm
NODES_SPEC=""          # e.g., 1m,2w for 1 master, 2 workers
K8S_VERSION="1.30.2"
K8S_POD_CIDR="10.244.0.0/16"
K8S_SVC_CIDR="10.96.0.0/12"
CNI_TYPE="calico"
AZURE_ENABLED="false"
OUTPUT_DIR=""
MODULE_DIR=""
CLUSTERS_SPEC=""
INCLUDE_BASTION="false"

# Sizing profile and overrides
PROFILE="standard"      # compact|standard|power
# Single-node overrides (minikube/aks)
VM_CPUS=""; VM_MEM=""
# Kubeadm single-cluster overrides
MASTER_CPUS=""; MASTER_MEM=""; WORKER_CPUS=""; WORKER_MEM=""
# Multi-kubeadm bastion overrides
BASTION_CPUS=""; BASTION_MEM=""

# Extras
declare -a EXTRA_SYNC_SPECS=()

# --- Argument Parsing & Validation ---
parse_args() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --module|-m) MODULE_NUM="$2"; shift 2 ;;
            --type|-t) MODULE_TYPE="$2"; shift 2 ;;
            --cluster|-c) CLUSTER_TYPE="$2"; shift 2 ;;
            --nodes|-n) NODES_SPEC="$2"; shift 2 ;;
            --k8s-version) K8S_VERSION="$2"; shift 2 ;;
            --cni) CNI_TYPE="$2"; shift 2 ;;
            --azure) AZURE_ENABLED="true"; shift ;;
            --clusters)
                shift
                gen::capture_clusters_value CLUSTERS_SPEC "$@" || exit 1
                shift ${GEN_CONSUMED:-1}
                ;;
            --bastion) INCLUDE_BASTION="true"; shift ;;
            --output-dir|-o) OUTPUT_DIR="$2"; shift 2 ;;
            --profile) PROFILE="$2"; shift 2 ;;
            --vm-cpus) VM_CPUS="$2"; shift 2 ;;
            --vm-mem) VM_MEM="$2"; shift 2 ;;
            --master-cpus) MASTER_CPUS="$2"; shift 2 ;;
            --master-mem) MASTER_MEM="$2"; shift 2 ;;
            --worker-cpus) WORKER_CPUS="$2"; shift 2 ;;
            --worker-mem) WORKER_MEM="$2"; shift 2 ;;
            --bastion-cpus) BASTION_CPUS="$2"; shift 2 ;;
            --bastion-mem) BASTION_MEM="$2"; shift 2 ;;
            --extra-sync)
                IFS=',' read -ra _syncs <<< "$2"; shift 2
                for s in "${_syncs[@]}"; do
                    [ -n "$s" ] && EXTRA_SYNC_SPECS+=("$s")
                done
                ;;
            -h|--help) show_usage; exit 0 ;;
            *) lib::error "Unknown option: $1"; show_usage; exit 1 ;;
        esac
    done
}

show_usage() {
    cat <<EOF
Usage: $0 --module NUM --type TYPE --cluster TYPE [--nodes SPEC] [OPTIONS]

Generates a Vagrantfile for a course module.

Required:
  --module, -m NUM      Module number (e.g., m1, m7)
  --type, -t TYPE       Module type: hw (homework) or pt (practice)
  --cluster, -c TYPE    Cluster type: kubeadm, minikube, kind, aks, bastion, or multi-kubeadm

Optional:
  --nodes, -n SPEC      Node specification for kubeadm (e.g., 1m, 1m,2w, 3m,5w)
                        Format: XmYw where X=masters, Y=workers (at least 1 master required)
                        Examples: 1m (1 master), 1m,3w (1 master + 3 workers)
                        Note: Ignored for minikube, aks, and bastion (always 1 management VM)
  --k8s-version VER     Kubernetes version (default: ${K8S_VERSION})
  --cni TYPE            CNI plugin for kubeadm (default: ${CNI_TYPE})
  --azure               Enable Azure CLI + /etc/azure-env (kubeadm, minikube, kind).
                        Ignored for --cluster aks (always enabled there).
  --clusters SPEC       Multi-cluster spec (multi-kubeadm):
                        - CSV: entries use name:cni:ip[:XmYw]; separate by comma/semicolon. Whitespace ignored.
                        - JSON: array of objects [{name,cni,ip,nodes?}]
                        - YAML: list of maps with keys (name,cni,ip,nodes?)
                        - @file: read spec from file (json/yaml/csv)
                        Examples:
                          dev:calico:192.168.56.110; prod:weave:192.168.56.120:1m,3w
                          '[{"name":"dev","cni":"calico","ip":"192.168.56.110","nodes":"1m,3w"}]'
                          @clusters.yaml
  --bastion             Include a dedicated bastion VM (only for multi-kubeadm)
  --profile NAME        Sizing profile: compact, standard (default), power
  --vm-cpus N           Single-node VM CPUs (minikube/kind/aks)
  --vm-mem MB           Single-node VM memory in MB (minikube/kind/aks)
  --master-cpus N       Kubeadm master CPUs
  --master-mem MB       Kubeadm master memory in MB
  --worker-cpus N       Kubeadm worker CPUs
  --worker-mem MB       Kubeadm worker memory in MB
  --bastion-cpus N      Bastion CPUs (multi-kubeadm)
  --bastion-mem MB      Bastion memory in MB (multi-kubeadm)
  --output-dir, -o DIR  Manually specify the output directory for the Vagrantfile.
  --extra-sync SPEC     Additional synced folder(s). Repeat or comma-separate.
                        Format: host_path:guest_path (e.g., assets:/assets/m1-pt)
  -h, --help            Show this help message

Examples:
  # Generate minikube environment
  $0 --module m1 --type pt --cluster minikube

  # Generate kubeadm multi-node cluster
  $0 --module m7 --type hw --cluster kubeadm --nodes 1m,2w

  # Generate AKS management workstation
  $0 --module m5 --type pt --cluster aks

  # Generate standalone bastion (no local cluster)
  $0 --module m9 --type pt --cluster bastion

  # Generate multi-kubeadm with default node counts (1m+1w per cluster)
  $0 --module m10 --type hw --cluster multi-kubeadm \\
    --clusters "dev:calico:192.168.56.110,prod:weave:192.168.56.120"

  # Generate multi-kubeadm with custom node counts per cluster
  $0 --module m10 --type hw --cluster multi-kubeadm \\
    --clusters "dev:calico:192.168.56.110:1m,3w,prod:weave:192.168.56.120:3m,5w"

  # Generate multi-kubeadm with bastion and custom nodes
  $0 --module m10 --type hw --cluster multi-kubeadm --bastion \\
    --clusters "dev:calico:192.168.56.110:1m,2w,staging:flannel:192.168.56.120:1m"
EOF
}

validate_args() {
    if [ -z "$MODULE_NUM" ] || [ -z "$MODULE_TYPE" ] || [ -z "$CLUSTER_TYPE" ]; then
        lib::error "Missing required arguments: --module, --type, and --cluster are all required."
        show_usage
        exit 1
    fi

    # no-op

    # Validate cluster type
    case "$CLUSTER_TYPE" in
        kubeadm|minikube|kind|aks|bastion|multi-kubeadm) ;;
        *)
            lib::error "Invalid cluster type: $CLUSTER_TYPE"
            lib::log "Supported types: kubeadm, minikube, kind, aks, bastion, multi-kubeadm"
            exit 1
            ;;
    esac

    # Handle cluster-specific node specifications
    case "$CLUSTER_TYPE" in
        minikube)
            if [ -n "$NODES_SPEC" ] && [ "$NODES_SPEC" != "1m" ]; then
                lib::warn "Minikube only supports a single node. Ignoring --nodes specification."
            fi
            NODES_SPEC="1m"
            ;;
        kind)
            if [ -n "$NODES_SPEC" ] && [ "$NODES_SPEC" != "1m" ]; then
                lib::warn "kind uses a single management VM. Ignoring --nodes specification."
            fi
            NODES_SPEC="1m"
            ;;
        aks)
            if [ -n "$NODES_SPEC" ]; then
                lib::warn "AKS uses a single management VM. Ignoring --nodes specification."
            fi
            NODES_SPEC="1m"  # Represents the management VM
            ;;
        bastion)
            if [ -n "$NODES_SPEC" ]; then
                lib::warn "Bastion uses a single management VM. Ignoring --nodes specification."
            fi
            NODES_SPEC="1m"  # Represents the management VM
            ;;
        kubeadm)
            if [ -z "$NODES_SPEC" ]; then
                lib::error "Kubeadm requires --nodes specification (e.g., 1m,2w)"
                exit 1
            fi

            # Validate that kubeadm has at least one master node
            if ! [[ "$NODES_SPEC" =~ [0-9]+m ]]; then
                lib::error "Kubeadm requires at least one master node in --nodes specification"
                lib::error "Examples: 1m (1 master only), 1m,2w (1 master + 2 workers), 3m,5w (3 masters + 5 workers)"
                exit 1
            fi
            ;;
        multi-kubeadm)
            if [ -n "$NODES_SPEC" ]; then
                lib::warn "--nodes is ignored for multi-kubeadm. Use per-cluster node specs in --clusters instead."
                lib::warn "Format: --clusters \"name:cni:ip:XmYw,name:cni:ip:XmYw\" (e.g., dev:calico:192.168.56.110:1m,3w)"
            fi
            if [ -z "$CLUSTERS_SPEC" ]; then
                lib::error "multi-kubeadm requires --clusters \"name:cni:ip[:nodes],name:cni:ip[:nodes],...\""
                exit 1
            fi
            # Support CSV/JSON/YAML/@file and normalize to CSV
            gen::clusters_spec_to_csv || exit 1
            ;;
    esac

    # Auto-detect module directory and set output path
    if [ -z "$OUTPUT_DIR" ]; then
        lib::log "Detecting module directory for '${MODULE_NUM}'..."
        MODULE_DIR=$(find . -maxdepth 2 -type d -name "*${MODULE_NUM}*" | head -n 1)

        if [ -z "$MODULE_DIR" ]; then
            lib::error "Could not find a directory for module '${MODULE_NUM}'."
            lib::error "Please run this script from the project root or specify a path with --output-dir."
            exit 1
        fi

        OUTPUT_DIR="${MODULE_DIR}"
    fi
    lib::log "Target Vagrantfile directory: $OUTPUT_DIR"

    # Apply sizing profile and overrides (shared helpers)
    gen::apply_sizing_profile
    gen::apply_sizing_overrides

    # Guardrail: minikube requires >=2 vCPU
    if [ "$CLUSTER_TYPE" = "minikube" ] && [ "$MK_CPUS" -lt 2 ]; then
        lib::error "Minikube requires at least 2 vCPUs. Use --vm-cpus 2 or a higher profile."
        exit 1
    fi
}

# --- Configuration Constants ---
readonly IP_BASE="192.168.56"
readonly IP_START=100
readonly POD_CIDR_BASE_OCTET=20  # 10.20+.0.0/16, 10.22+.0.0/16, etc.
readonly SVC_CIDR_BASE_OCTET=21  # 10.21+.0.0/16, 10.23+.0.0/16, etc.
readonly LF=$'\n'                # Line feed for string concatenation

# --- Template selection ---
select_vagrantfile_template() {
    local ct="$1"
    case "$ct" in
        minikube) echo "${SCRIPT_DIR}/templates/vagrantfile/minikube.rb.tpl" ;;
        kind) echo "${SCRIPT_DIR}/templates/vagrantfile/kind.rb.tpl" ;;
        aks) echo "${SCRIPT_DIR}/templates/vagrantfile/aks.rb.tpl" ;;
        bastion) echo "${SCRIPT_DIR}/templates/vagrantfile/bastion.rb.tpl" ;;
        kubeadm) echo "${SCRIPT_DIR}/templates/vagrantfile/kubeadm.rb.tpl" ;;
        multi-kubeadm) echo "${SCRIPT_DIR}/templates/vagrantfile/multi_kubeadm.rb.tpl" ;;
        *) echo "${SCRIPT_DIR}/templates/vagrantfile/base.rb.tpl" ;;
    esac
}

# --- Helper Functions ---

# Renders a snippet template and appends to nodes_config
# Usage: render_node_snippet <snippet_name>
# Modifies: nodes_config (parent scope)
render_node_snippet() {
    local snippet_name=${1:?snippet name required}
    local snippet_path="${SCRIPT_DIR}/templates/vagrantfile/snippets/${snippet_name}.rb.tpl"
    local tmp_file

    tmp_file=$(mktemp) || {
        lib::error "Failed to create temp file for snippet: $snippet_name"
        return 1
    }

    lib::debug "Rendering snippet: $snippet_name (NODE_DEFINE=${NODE_DEFINE:-}, NODE_IP=${NODE_IP:-})"

    if render_template "$snippet_path" "$tmp_file"; then
        nodes_config+=$(cat "$tmp_file")$'\n\n'
        rm -f "$tmp_file"
        return 0
    else
        rm -f "$tmp_file"
        lib::error "Failed to render snippet: $snippet_name"
        return 1
    fi
}

# Calculates next IP in sequence
# Usage: next_ip <base> <suffix>
# Example: next_ip "192.168.56" 100 → "192.168.56.100"
next_ip() {
    local base=${1:?IP base required}
    local suffix=${2:?IP suffix required}
    echo "${base}.${suffix}"
}

# Increments last octet of an IP address
# Usage: increment_ip "192.168.56.110" → "192.168.56.111"
increment_ip() {
    local ip=${1:?IP address required}
    local o1 o2 o3 o4

    IFS='.' read -r o1 o2 o3 o4 <<< "$ip"
    echo "${o1}.${o2}.${o3}.$((o4 + 1))"
}

# Calculates per-cluster CIDRs to avoid conflicts
# Usage: calculate_cluster_cidrs <cluster_index>
# Returns: POD_CIDR SVC_CIDR (space-separated)
calculate_cluster_cidrs() {
    local idx=${1:?cluster index required}
    local pod_octet=$((POD_CIDR_BASE_OCTET + idx * 2))
    local svc_octet=$((SVC_CIDR_BASE_OCTET + idx * 2))

    echo "10.${pod_octet}.0.0/16 10.${svc_octet}.0.0/16"
}

# Parses NODES_SPEC into master and worker counts
# Usage: parse_nodes_spec "1m,2w"
# Returns: MASTER_COUNT WORKER_COUNT (space-separated)
parse_nodes_spec() {
    local spec=${1:?nodes spec required}
    local master_count=0 worker_count=0
    local part

    IFS=',' read -ra parts <<< "$spec"
    for part in "${parts[@]}"; do
        if [[ "$part" =~ ^([0-9]+)m$ ]]; then
            master_count="${BASH_REMATCH[1]}"
        elif [[ "$part" =~ ^([0-9]+)w$ ]]; then
            worker_count="${BASH_REMATCH[1]}"
        fi
    done

    echo "$master_count $worker_count"
}

# Splits CLUSTERS_SPEC into individual cluster entries
# Handles entries that may contain commas in nodes spec (e.g., "dev:calico:192.168.56.110:1m,2w")
# Format: name:cni:ip[:nodes], where nodes can contain commas
# Usage: split_clusters_spec "dev:calico:192.168.56.110:1m,2w,prod:weave:192.168.56.120"
# Returns: Array of cluster entries (one per line)
split_clusters_spec() {
    local spec="$1"

    # Strategy: Split by looking for pattern ",<word>:" which indicates start of next cluster
    # Replace those occurrences with newline marker, then split
    # This preserves commas within nodes spec (e.g., "1m,2w")

    # Use perl for more robust regex replacement
    if command -v perl &>/dev/null; then
        echo "$spec" | perl -pe 's/,([a-zA-Z0-9_-]+):/\n\1:/g'
    else
        # Fallback: Use sed (less robust but works for most cases)
        # Look for comma followed by word chars and colon (start of next entry)
        echo "$spec" | sed 's/,\([a-zA-Z0-9_-]\+\):/\n\1:/g'
    fi
}

# Parses CLUSTERS_SPEC entry into components
# Usage: parse_cluster_entry "sofia:calico:192.168.56.110:1m,2w"
# Returns: NAME CNI IP NODES_SPEC (space-separated)
# Notes: NODES_SPEC defaults to "1m,1w" if not provided (backward compatible)
parse_cluster_entry() {
    local entry=${1:?cluster entry required}
    local name cni ip nodes_spec

    IFS=':' read -r name cni ip nodes_spec <<< "$entry"

    # Validation
    if [ -z "$name" ] || [ -z "$cni" ] || [ -z "$ip" ]; then
        lib::error "Invalid cluster entry format: $entry (expected name:cni:ip[:nodes])"
        return 1
    fi

    # Default to 1m,1w for backward compatibility
    if [ -z "$nodes_spec" ]; then
        nodes_spec="1m,1w"
    fi

    # Validate nodes spec format
    if ! [[ "$nodes_spec" =~ [0-9]+m ]]; then
        lib::error "Invalid nodes specification in cluster entry: $entry (must contain at least Xm for masters)"
        return 1
    fi

    echo "$name $cni $ip $nodes_spec"
}

# Exports common node variables
# Usage: export_node_vars <hostname> <ip> <memory> <cpus> <vm_name>
export_node_vars() {
    local hostname=${1:?hostname required}
    local ip=${2:?ip required}
    local memory=${3:?memory required}
    local cpus=${4:?cpus required}
    local vm_name=${5:?vm_name required}

    export NODE_DEFINE="$hostname"
    export NODE_HOSTNAME="$hostname"
    export NODE_IP="$ip"
    export VM_MEMORY="$memory"
    export VM_CPUS="$cpus"
    export VM_NAME="$vm_name"
}

# Exports Kubernetes cluster variables
# Usage: export_k8s_vars <cluster_name> <namespace> [k8s_version] [pod_cidr] [svc_cidr] [cni_type] [azure_enabled]
export_k8s_vars() {
    local cluster_name=${1:?cluster name required}
    local namespace=${2:?namespace required}
    local k8s_version=${3:-$K8S_VERSION}
    local pod_cidr=${4:-$K8S_POD_CIDR}
    local svc_cidr=${5:-$K8S_SVC_CIDR}
    local cni_type=${6:-$CNI_TYPE}
    local azure_enabled=${7:-$AZURE_ENABLED}

    export CLUSTER_NAME="$cluster_name"
    export NAMESPACE_DEFAULT="$namespace"
    export K8S_VERSION="$k8s_version"
    export K8S_POD_CIDR="$pod_cidr"
    export K8S_SVC_CIDR="$svc_cidr"
    export CNI_TYPE="$cni_type"
    export AZURE_ENABLED="$azure_enabled"
}

# Exports Azure-specific variables
# Usage: export_azure_vars <location> <resource_group> <aks_name> <acr_name>
export_azure_vars() {
    local location=${1:?location required}
    local resource_group=${2:?resource group required}
    local aks_name=${3:?aks name required}
    local acr_name=${4:?acr name required}

    export AZ_LOCATION="$location"
    export AZ_RESOURCE_GROUP="$resource_group"
    export AKS_NAME="$aks_name"
    export ACR_NAME="$acr_name"
}

# --- Cluster Type Renderers ---

render_aks_cluster() {
    local ip
    ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
    current_ip_suffix=$((current_ip_suffix + 1))

    export_node_vars "kadmin" "$ip" "$AKS_MEM" "$AKS_CPUS" \
        "${MODULE_NUM}-${MODULE_TYPE}-kadmin"
    export NAMESPACE_DEFAULT="ns-${MODULE_NUM}-labs"
    export_azure_vars "westeurope" "rg-k8s-${MODULE_NUM}" \
        "aks-${MODULE_NUM}-core" "acr${MODULE_NUM}example"

    render_node_snippet "aks_node"
}

render_bastion_cluster() {
    local ip
    ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
    current_ip_suffix=$((current_ip_suffix + 1))

    export_node_vars "bastion" "$ip" "$BASTION_D_MEM" "$BASTION_D_CPUS" \
        "${MODULE_NUM}-${MODULE_TYPE}-bastion"
    export NAMESPACE_DEFAULT="ns-${MODULE_NUM}-labs"

    render_node_snippet "bastion_node"
}

render_minikube_cluster() {
    local ip
    ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
    current_ip_suffix=$((current_ip_suffix + 1))

    export_node_vars "minikube" "$ip" "$MK_MEM" "$MK_CPUS" \
        "${MODULE_NUM}-${MODULE_TYPE}-minikube"
    export CLUSTER_TYPE="minikube"
    export CLUSTER_NAME="clu-${MODULE_NUM}-minikube"
    export NAMESPACE_DEFAULT="ns-${MODULE_NUM}-labs"

    render_node_snippet "minikube_node"
}

render_kind_cluster() {
    local ip
    ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
    current_ip_suffix=$((current_ip_suffix + 1))

    export_node_vars "kind" "$ip" "$MK_MEM" "$MK_CPUS" \
        "${MODULE_NUM}-${MODULE_TYPE}-kind"
    export CLUSTER_TYPE="kind"
    export CLUSTER_NAME="clu-${MODULE_NUM}-kind"
    export NAMESPACE_DEFAULT="ns-${MODULE_NUM}-labs"

    render_node_snippet "kind_node"
}

render_multi_kubeadm_cluster() {
    # Optional bastion
    if [ "$INCLUDE_BASTION" = "true" ]; then
        export_node_vars "bastion" "$(next_ip "$IP_BASE" 100)" \
            "$BASTION_D_MEM" "$BASTION_D_CPUS" \
            "${MODULE_NUM}-${MODULE_TYPE}-bastion"
        render_node_snippet "multi_bastion"
    fi

    # Parse clusters spec using the new helper that handles commas in nodes spec
    local cluster_idx=0
    local entry
    local -a cluster_entries
    mapfile -t cluster_entries < <(split_clusters_spec "$CLUSTERS_SPEC")

    for entry in "${cluster_entries[@]}"; do
        cluster_idx=$((cluster_idx + 1))

        # Parse cluster entry (now includes optional nodes spec)
        local cluster_info cluster_name cluster_cni first_ip cluster_nodes
        cluster_info=$(parse_cluster_entry "$entry") || continue
        IFS=' ' read -r cluster_name cluster_cni first_ip cluster_nodes <<< "$cluster_info"

        # Parse node counts for this cluster
        local counts master_count worker_count
        counts=$(parse_nodes_spec "$cluster_nodes")
        IFS=' ' read -r master_count worker_count <<< "$counts"

        # Calculate cluster CIDRs
        local cidrs pod_cidr svc_cidr
        cidrs=$(calculate_cluster_cidrs "$cluster_idx")
        IFS=' ' read -r pod_cidr svc_cidr <<< "$cidrs"

        # Track IP allocation for this cluster
        local cluster_ip_parts
        IFS='.' read -r -a cluster_ip_parts <<< "$first_ip"
        local cluster_base_suffix="${cluster_ip_parts[3]}"
        local node_ip_offset=0

        # Render master nodes
        for ((i=1; i<=master_count; i++)); do
            local master_ip master_hostname
            master_ip=$(next_ip "$IP_BASE" $((cluster_base_suffix + node_ip_offset)))
            node_ip_offset=$((node_ip_offset + 1))

            if [ "$master_count" -eq 1 ]; then
                master_hostname="${cluster_name}-master"
            else
                master_hostname="${cluster_name}-master${i}"
            fi

            export_node_vars "$master_hostname" "$master_ip" \
                "$KM_MASTER_MEM" "$KM_MASTER_CPUS" \
                "${MODULE_NUM}-${MODULE_TYPE}-${master_hostname}"
            export_k8s_vars "$cluster_name" "ns-${MODULE_NUM}-labs" \
                "$K8S_VERSION" "$pod_cidr" "$svc_cidr" "$cluster_cni" "false"
            export NODE_ROLE="master"
            render_node_snippet "multi_master"
        done

        # Render worker nodes
        for ((i=1; i<=worker_count; i++)); do
            local worker_ip worker_hostname
            worker_ip=$(next_ip "$IP_BASE" $((cluster_base_suffix + node_ip_offset)))
            node_ip_offset=$((node_ip_offset + 1))

            worker_hostname="${cluster_name}-worker${i}"

            export_node_vars "$worker_hostname" "$worker_ip" \
                "$KM_WORKER_MEM" "$KM_WORKER_CPUS" \
                "${MODULE_NUM}-${MODULE_TYPE}-${worker_hostname}"
            export_k8s_vars "$cluster_name" "ns-${MODULE_NUM}-labs" \
                "$K8S_VERSION" "$pod_cidr" "$svc_cidr" "$cluster_cni" "false"
            export NODE_ROLE="worker"
            render_node_snippet "multi_worker"
        done
    done
}

render_kubeadm_cluster() {
    local counts master_count worker_count
    counts=$(parse_nodes_spec "$NODES_SPEC")

    # Ensure IFS is set correctly for read to split on space
    IFS=' ' read -r master_count worker_count <<< "$counts"

    local cluster_name="clu-${MODULE_NUM}-${MODULE_TYPE}"
    local namespace="ns-${MODULE_NUM}-labs"

    # Render master nodes
    for ((i=1; i<=master_count; i++)); do
        local ip
        ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
        current_ip_suffix=$((current_ip_suffix + 1))

        export_node_vars "kmaster" "$ip" "$KM_MASTER_MEM" "$KM_MASTER_CPUS" \
            "${MODULE_NUM}-${MODULE_TYPE}-kmaster"
        export_k8s_vars "$cluster_name" "$namespace"
        export NODE_ROLE="master"
        render_node_snippet "kubeadm_master"
    done

    # Render worker nodes
    for ((i=1; i<=worker_count; i++)); do
        local ip
        ip=$(next_ip "$IP_BASE" $((current_ip_suffix + 1)))
        current_ip_suffix=$((current_ip_suffix + 1))

        export_node_vars "kworker${i}" "$ip" "$KM_WORKER_MEM" "$KM_WORKER_CPUS" \
            "${MODULE_NUM}-${MODULE_TYPE}-kworker${i}"
        export_k8s_vars "$cluster_name" "$namespace"
        export NODE_ROLE="worker"
        render_node_snippet "kubeadm_worker"
    done
}

# --- Main Rendering Function (Refactored) ---
render_vagrantfile_from_templates() {
    # Note: nodes_config and current_ip_suffix are intentionally NOT local
    # They need to be accessible to the render_*_cluster functions
    nodes_config=""
    current_ip_suffix=$IP_START
    local extra_sync_block=""

    # Build extra sync folders block
    if [ ${#EXTRA_SYNC_SPECS[@]} -gt 0 ]; then
        local spec host guest
        for spec in "${EXTRA_SYNC_SPECS[@]}"; do
            if [[ "$spec" == *:* ]]; then
                host="${spec%%:*}"
                guest="${spec#*:}"
                extra_sync_block+="  config.vm.synced_folder '${host}', '${guest}'${LF}"
            else
                lib::warn "Ignoring malformed --extra-sync spec (expected host:guest): $spec"
            fi
        done
    fi

    # Render nodes based on cluster type
    case "$CLUSTER_TYPE" in
        aks)
            render_aks_cluster
            ;;
        bastion)
            render_bastion_cluster
            ;;
        minikube)
            render_minikube_cluster
            ;;
        kind)
            render_kind_cluster
            ;;
        multi-kubeadm)
            render_multi_kubeadm_cluster
            ;;
        kubeadm)
            render_kubeadm_cluster
            ;;
        *)
            lib::error "Unknown cluster type: $CLUSTER_TYPE"
            return 1
            ;;
    esac

    # Export for final template rendering
    export EXTRA_SYNC_BLOCK="${extra_sync_block%$LF}"
    export NODES_CONFIG="$nodes_config"

    # Render final Vagrantfile
    local template_path output_path
    template_path=$(select_vagrantfile_template "$CLUSTER_TYPE")
    output_path="${OUTPUT_DIR}/Vagrantfile"

    lib::log "Writing Vagrantfile to: $output_path"
    mkdir -p "$OUTPUT_DIR"
    render_template "$template_path" "$output_path"
}

main() {
    lib::header "Vagrantfile Generator"

    parse_args "$@"
    validate_args

    lib::log "Configuration:"
    lib::kv "Module" "${MODULE_NUM} (${MODULE_TYPE})"
    lib::kv "Cluster Type" "$CLUSTER_TYPE"
    lib::kv "Nodes" "$NODES_SPEC"
    lib::kv "K8s Version" "$K8S_VERSION"
    lib::kv "CNI Type" "$CNI_TYPE"
    # Show Azure Enabled only for kubeadm scenarios where it may affect guidance
    if [ "$CLUSTER_TYPE" = "kubeadm" ]; then
      lib::kv "Azure Enabled" "$AZURE_ENABLED"
    fi
    lib::kv "Output Dir" "$OUTPUT_DIR"

    # Sizing Summary
    lib::subheader "Sizing Summary"
    case "$CLUSTER_TYPE" in
      minikube)
        lib::log "  - minikube: CPUs=${MK_CPUS}, MEM=${MK_MEM}MB, count=1"
        ;;
      aks)
        lib::log "  - aks-mgmt: CPUs=${AKS_CPUS}, MEM=${AKS_MEM}MB, count=1"
        ;;
      bastion)
        lib::log "  - bastion: CPUs=${BASTION_D_CPUS}, MEM=${BASTION_D_MEM}MB, count=1"
        ;;
      kubeadm)
        # Derive counts for single-cluster
        local masters=0 workers=0
        IFS=',' read -ra ADDR <<< "$NODES_SPEC"
        for i in "${ADDR[@]}"; do
          if [[ "$i" =~ ^([0-9]+)m$ ]]; then masters="${BASH_REMATCH[1]}"; fi
          if [[ "$i" =~ ^([0-9]+)w$ ]]; then workers="${BASH_REMATCH[1]}"; fi
        done
        lib::log "  - master: CPUs=${KM_MASTER_CPUS}, MEM=${KM_MASTER_MEM}MB, count=${masters}"
        if [ "$workers" -gt 0 ]; then
          lib::log "  - worker: CPUs=${KM_WORKER_CPUS}, MEM=${KM_WORKER_MEM}MB, count=${workers}"
        fi
        ;;
      multi-kubeadm)
        # estimate clusters count using robust splitter (preserve commas inside nodes)
        local entries ccount
        entries=$(gen::split_clusters_entries "$CLUSTERS_SPEC")
        ccount=$(printf "%s\n" "$entries" | sed '/^$/d' | wc -l | tr -d ' ')
        if [ "$INCLUDE_BASTION" = "true" ]; then
          lib::log "  - bastion: CPUs=${BASTION_D_CPUS}, MEM=${BASTION_D_MEM}MB, count=1"
        fi
        lib::log "  - master: CPUs=${KM_MASTER_CPUS}, MEM=${KM_MASTER_MEM}MB, count=${ccount}"
        lib::log "  - worker: CPUs=${KM_WORKER_CPUS}, MEM=${KM_WORKER_MEM}MB, count=${ccount}"
        ;;
    esac

    render_vagrantfile_from_templates

    lib::hr
    lib::success "Vagrantfile generated successfully!"
    lib::log "Next step: Run 'generate-bootstrap.sh' for this module to create the role-aware bootstrap script and copy dependencies."
}

# --- Run ---
main "$@"
