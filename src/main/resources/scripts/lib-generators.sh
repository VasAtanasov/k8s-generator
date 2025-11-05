#!/usr/bin/env bash

# Generator-specific Bash helpers for generate-*.sh scripts.
# These functions are only needed by generator scripts and should NOT be
# sourced by runtime/bootstrap scripts. See doc/CONTRIBUTING_BASH.md for guidelines.
#
# Safe to source multiple times. Define functions only if missing.

if [ -n "${_LIB_GENERATORS_INCLUDED:-}" ]; then
    return 0 2>/dev/null || exit 0
fi
readonly _LIB_GENERATORS_INCLUDED=1

# Source the base runtime library for logging and error handling
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
source "${SCRIPT_DIR}/lib.sh"

# --- Template rendering helper ---
# Renders a template file by substituting a safe, whitelisted set of variables.
# This avoids expanding runtime shell variables like $SCRIPT_DIR in the template.
# Usage: render_template <template_file> <output_file>
if ! declare -F render_template >/dev/null 2>&1; then
render_template() {
    local template_file=${1:?template file required}
    local output_file=${2:?output file required}

    if [ ! -f "$template_file" ]; then
        lib::error "Template file not found: $template_file"
        return 1
    fi

    if ! command -v envsubst >/dev/null 2>&1; then
        lib::error "envsubst not found. Please install gettext-base (envsubst) to render templates."
        return 127
    fi

    # Whitelist variables that templates are allowed to expand.
    # Keep runtime shell variables like $SCRIPT_DIR untouched.
    local allow_vars=(
        'MODULE_NUM' 'MODULE_TYPE' 'LOCK_FILE' 'TIMESTAMP_LINE'
        'INSTALL_COMMANDS_BLOCK' 'MANDATORY_INSTALLS_BLOCK' 'ADDITIONAL_INSTALLS_BLOCK'
        'AZURE_ENV_BLOCK' 'DOLLAR' 'EXTRA_SYNC_BLOCK' 'NODES_CONFIG'
        'NODE_DEFINE' 'NODE_HOSTNAME' 'NODE_IP' 'VM_MEMORY' 'VM_CPUS' 'VM_NAME'
        'CLUSTER_TYPE' 'CLUSTER_NAME' 'NAMESPACE_DEFAULT' 'AZ_LOCATION' 'AZ_RESOURCE_GROUP' 'AKS_NAME' 'ACR_NAME'
        'K8S_VERSION' 'K8S_POD_CIDR' 'K8S_SVC_CIDR' 'CNI_TYPE' 'AZURE_ENABLED' 'NODE_ROLE'
    )
    local var_list=""
    local v
    for v in "${allow_vars[@]}"; do
        var_list+="$"$v" "
    done

    # shellcheck disable=SC2016
    envsubst "$var_list" < "$template_file" > "$output_file"
    lib::log "Rendered template: $template_file -> $output_file"
}
fi

# --- Cluster spec helpers (shared by generators) ---
if ! declare -F normalize_clusters_spec >/dev/null 2>&1; then
normalize_clusters_spec() {
    # Accept comma, semicolon, or newline between entries; ignore whitespace
    # Modifies global CLUSTERS_SPEC
    local spec="${CLUSTERS_SPEC:-}"
    spec=${spec//$'\n'/,}
    spec=${spec//;/,}
    spec=$(printf "%s" "$spec" | tr -d ' \t')
    # Collapse multiple commas and trim leading/trailing commas
    spec=$(printf "%s" "$spec" | sed -E 's/,+/,/g; s/^,+//; s/,+$//')
    CLUSTERS_SPEC="$spec"
}
fi

if ! declare -F validate_clusters_spec >/dev/null 2>&1; then
validate_clusters_spec() {
    # Validates global CLUSTERS_SPEC entries after normalization
    local spec="${CLUSTERS_SPEC:-}"
    local invalid=()
    # Split robustly: commas only separate entries when followed by a new entry (word colon)
    local entries
    if command -v perl >/dev/null 2>&1; then
        entries=$(printf "%s" "$spec" | perl -pe 's/,([a-zA-Z0-9_-]+):/\n\1:/g')
    else
        entries=$(printf "%s" "$spec" | sed 's/,\([a-zA-Z0-9_-]\+\):/\n\1:/g')
    fi
    while IFS= read -r e; do
        [ -z "$e" ] && continue
        # Accept optional :nodes tail (e.g., :1m,2w)
        if [[ ! "$e" =~ ^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+:[0-9]{1,3}(\.[0-9]{1,3}){3}(:.+)?$ ]]; then
            invalid+=("$e")
        fi
    done <<< "$entries"
    if [ ${#invalid[@]} -gt 0 ]; then
        lib::error "Invalid --clusters entries (expected name:cni:master_ip[:nodes]): ${invalid[*]}"
        lib::error "Examples: sofia:weave:192.168.56.110, plovdiv:calico:192.168.56.120:1m,3w"
        return 1
    fi
}
fi

# --- Cluster spec format support (CSV | JSON | YAML | @file) ---
if ! declare -F gen::read_clusters_spec_content >/dev/null 2>&1; then
gen::read_clusters_spec_content() {
    local raw="${CLUSTERS_SPEC:-}"
    if [[ "$raw" =~ ^@(.+) ]]; then
        local path=${BASH_REMATCH[1]}
        if [ ! -f "$path" ]; then
            lib::error "--clusters file not found: $path"
            return 1
        fi
        cat -- "$path"
    else
        printf "%s" "$raw"
    fi
}
fi

if ! declare -F gen::detect_clusters_format >/dev/null 2>&1; then
gen::detect_clusters_format() {
    local content="$1"
    # Get first non-space character of the content's first line
    local first_line first_char
    first_line=$(printf "%s" "$content" | sed -n 's/^[[:space:]]*//;1p;q')
    first_char=$(printf "%s" "$first_line" | cut -c1)
    if [ "$first_char" = "{" ] || [ "$first_char" = "[" ]; then
        echo json
        return 0
    fi
    # Simple YAML heuristic: presence of name:, cni:, ip: keys
    if printf "%s" "$content" | grep -qE '^[[:space:]]*-\s*name:|^[[:space:]]*name:\s' && \
       printf "%s" "$content" | grep -qE '^[[:space:]]*cni:\s' && \
       printf "%s" "$content" | grep -qE '^[[:space:]]*ip:\s'; then
        echo yaml
        return 0
    fi
    echo csv
}
fi

if ! declare -F gen::clusters_spec_to_csv >/dev/null 2>&1; then
gen::clusters_spec_to_csv() {
    local content format
    content=$(gen::read_clusters_spec_content) || return 1
    format=$(gen::detect_clusters_format "$content")

    case "$format" in
        csv)
            CLUSTERS_SPEC="$content"
            ;;
        json)
            if ! command -v jq >/dev/null 2>&1; then
            lib::error "Parsing JSON requires jq. Install jq or pass CSV/YAML or @file."
                return 1
            fi
            # Validate JSON first to provide a clearer error when shell quoting breaks it
            if ! printf "%s" "$content" | jq -e . >/dev/null 2>&1; then
                lib::error "Invalid JSON passed to --clusters (shell quoting likely broke it)."
                lib::error "Tip: use single quotes around JSON, or use --clusters @clusters.json"
                return 1
            fi
            CLUSTERS_SPEC=$(printf "%s" "$content" | jq -r '. as $in | (if type=="array" then . else [.] end) | .[] | "\(.name):\(.cni):\(.ip)\(if .nodes then ":" + .nodes else "" end)"' | paste -sd , -)
            ;;
        yaml)
            if command -v yq >/dev/null 2>&1; then
                # Mike Farah yq syntax
                CLUSTERS_SPEC=$(printf "%s" "$content" | yq -r '.[] | "\(.name):\(.cni):\(.ip)\(if .nodes then ":" + .nodes else "" end)"' | paste -sd , -)
            else
                lib::error "Parsing YAML requires yq. Install yq (Mike Farah) or pass JSON/CSV, or @json file."
                return 1
            fi
            ;;
    esac

    # Normalize and validate
    normalize_clusters_spec
    validate_clusters_spec || return 1
}
fi

# --- Cluster entry splitting and parsing (shared) ---
if ! declare -F gen::split_clusters_entries >/dev/null 2>&1; then
gen::split_clusters_entries() {
    # Usage: gen::split_clusters_entries [spec]
    # Returns newline-separated cluster entries where commas inside nodes are preserved
    local spec="${1:-${CLUSTERS_SPEC:-}}"
    if [ -z "$spec" ]; then return 0; fi
    if command -v perl >/dev/null 2>&1; then
        printf "%s" "$spec" | perl -pe 's/,([a-zA-Z0-9_-]+):/\n\1:/g'
    else
        printf "%s" "$spec" | sed 's/,\([a-zA-Z0-9_-]\+\):/\n\1:/g'
    fi
}
fi

if ! declare -F gen::parse_cluster_entry >/dev/null 2>&1; then
gen::parse_cluster_entry() {
    # Usage: gen::parse_cluster_entry "name:cni:ip[:nodes]"
    # Prints: NAME CNI IP NODES_SPEC
    local entry=${1:?cluster entry required}
    local name cni ip nodes_spec
    IFS=':' read -r name cni ip nodes_spec <<< "$entry"
    if [ -z "$name" ] || [ -z "$cni" ] || [ -z "$ip" ]; then
        lib::error "Invalid cluster entry: $entry (expected name:cni:ip[:nodes])"
        return 1
    fi
    if [ -z "$nodes_spec" ]; then nodes_spec="1m,1w"; fi
    printf "%s %s %s %s\n" "$name" "$cni" "$ip" "$nodes_spec"
}
fi

# Capture potentially pretty multi-token JSON/YAML value for --clusters
if ! declare -F gen::capture_clusters_value >/dev/null 2>&1; then
gen::capture_clusters_value() {
    # Usage: gen::capture_clusters_value OUTVAR "$@"
    # Sets OUTVAR to captured value. Sets GEN_CONSUMED to number of tokens consumed.
    local -n __out=$1; shift
    local consumed=0 buf="" tok starts_json=0 brace=0 bracket=0
    if [ $# -eq 0 ]; then
        lib::error "--clusters expects a value"
        return 1
    fi
    tok="$1"; consumed=1
    # Simple single-token cases: @file, CSV token
    if [[ "$tok" == @* ]] || echo "$tok" | grep -qE '^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+:'; then
        __out="$tok"; GEN_CONSUMED=$consumed; return 0
    fi
    # Pretty JSON/YAML: accumulate until braces/brackets balance or next option starts
    buf="$tok"
    if [[ "$tok" =~ ^\{ ]] || [[ "$tok" =~ ^\[ ]]; then
        starts_json=1
        brace=$(printf "%s" "$tok" | awk '{print gsub(/{/ , "")-gsub(/}/ , "")}')
        bracket=$(printf "%s" "$tok" | awk '{print gsub(/\[/ , "")-gsub(/\]/ , "")}')
        shift
        while [ $# -gt 0 ]; do
            tok="$1"; consumed=$((consumed+1))
            buf+=" $tok"
            brace=$(( brace + $(printf "%s" "$tok" | awk '{print gsub(/{/ , "")-gsub(/}/ , "")}') ))
            bracket=$(( bracket + $(printf "%s" "$tok" | awk '{print gsub(/\[/ , "")-gsub(/\]/ , "")}') ))
            shift
            if [ $brace -le 0 ] && [ $bracket -le 0 ]; then break; fi
        done
        __out="$buf"; GEN_CONSUMED=$consumed; return 0
    fi
    # Fallback: consume until next option (starts with --)
    shift
    while [ $# -gt 0 ]; do
        case "$1" in
            --*) break ;;
            *) buf+=" $1"; consumed=$((consumed+1)); shift ;;
        esac
    done
    __out="$buf"; GEN_CONSUMED=$consumed; return 0
}
fi

# --- Sizing profiles and overrides (shared) ---
if ! declare -F gen::apply_sizing_profile >/dev/null 2>&1; then
gen::apply_sizing_profile() {
    # Uses global PROFILE; sets global sizing vars
    # Defaults (standard profile)
    MK_CPUS=2; MK_MEM=6144
    AKS_CPUS=1; AKS_MEM=2048
    KM_MASTER_CPUS=2; KM_MASTER_MEM=4096
    KM_WORKER_CPUS=2; KM_WORKER_MEM=3072
    BASTION_D_CPUS=1; BASTION_D_MEM=2048

    case "${PROFILE:-standard}" in
        compact)
            MK_MEM=4096
            KM_MASTER_MEM=3072; KM_WORKER_MEM=2048
            BASTION_D_MEM=1024
            ;;
        power)
            MK_CPUS=4; MK_MEM=8192
            AKS_CPUS=2; AKS_MEM=4096
            KM_MASTER_CPUS=4; KM_MASTER_MEM=8192
            KM_WORKER_CPUS=4; KM_WORKER_MEM=6144
            BASTION_D_CPUS=2; BASTION_D_MEM=2048
            ;;
        standard) : ;;
        *) lib::warn "Unknown profile '${PROFILE}'. Using 'standard'." ;;
    esac
}
fi

if ! declare -F gen::apply_sizing_overrides >/dev/null 2>&1; then
gen::apply_sizing_overrides() {
    # Applies explicit override globals if present
    if [ -n "${VM_CPUS:-}" ]; then MK_CPUS="$VM_CPUS"; AKS_CPUS="$VM_CPUS"; BASTION_D_CPUS="$VM_CPUS"; fi
    if [ -n "${VM_MEM:-}" ]; then MK_MEM="$VM_MEM"; AKS_MEM="$VM_MEM"; BASTION_D_MEM="$VM_MEM"; fi
    if [ -n "${MASTER_CPUS:-}" ]; then KM_MASTER_CPUS="$MASTER_CPUS"; fi
    if [ -n "${MASTER_MEM:-}" ]; then KM_MASTER_MEM="$MASTER_MEM"; fi
    if [ -n "${WORKER_CPUS:-}" ]; then KM_WORKER_CPUS="$WORKER_CPUS"; fi
    if [ -n "${WORKER_MEM:-}" ]; then KM_WORKER_MEM="$WORKER_MEM"; fi
    if [ -n "${BASTION_CPUS:-}" ]; then BASTION_D_CPUS="$BASTION_CPUS"; fi
    if [ -n "${BASTION_MEM:-}" ]; then BASTION_D_MEM="$BASTION_MEM"; fi
}
fi
