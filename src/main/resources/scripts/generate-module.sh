#!/usr/bin/env bash
#
# Purpose: Orchestrate multi-cluster module generation (bastion + multiple kubeadm clusters)
# Usage: ./scripts/generate-module.sh --module m8 --type hw --cluster multi-kubeadm \
#            --clusters "sofia:weave:192.168.56.110,plovdiv:calico:192.168.56.120" --bastion
#

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "${SCRIPT_DIR}/lib-generators.sh" ]; then
  # shellcheck disable=SC1091
  source "${SCRIPT_DIR}/lib-generators.sh"
else
  echo "ERROR: lib-generators.sh not found in scripts/" >&2; exit 1
fi
lib::strict
lib::setup_traps

MODULE_NUM=""
MODULE_TYPE=""
CLUSTER_TYPE="multi-kubeadm"  # supports: multi-kubeadm, minikube, kind, aks, bastion
CLUSTERS_SPEC=""
INCLUDE_BASTION="false"
MODULE_DIR=""
OUTPUT_DIR=""
TOOLS_BASTION=""
K8S_VERSION="1.30.2"
PROFILE="standard"
AZURE_ENABLED="false"
# Sizing overrides
VM_CPUS=""; VM_MEM=""; MASTER_CPUS=""; MASTER_MEM=""; WORKER_CPUS=""; WORKER_MEM=""; BASTION_CPUS=""; BASTION_MEM=""
HOST_CPUS=""; HOST_RAM=""   # Optional host capacity for warnings

show_usage() {
  cat <<EOF
Usage: $0 --module NUM --type TYPE --cluster <multi-kubeadm|minikube|kind|aks|bastion> [options]

Required:
  --module, -m NUM            Module number (e.g., m1, m8)
  --type, -t TYPE             Module type: hw or pt
  --cluster, -c TYPE          One of: multi-kubeadm, minikube, kind, aks, bastion
  --clusters SPEC             (multi-kubeadm only) Cluster list:
                              - CSV: name:cni:ip[:XmYw], separated by comma/semicolon (whitespace ignored)
                              - JSON: array of objects [{name,cni,ip,nodes?}]
                              - YAML: list of maps with keys (name,cni,ip,nodes?)
                              - @file: read spec from file (json/yaml/csv)
                              Examples:
                                --clusters "sofia:weave:192.168.56.110; plovdiv:calico:192.168.56.120:1m,3w"
                                --clusters '[{"name":"dev","cni":"calico","ip":"192.168.56.110","nodes":"1m,3w"}]'
                                --clusters @clusters.yaml

Optional (behavior):
  --bastion                   (multi-kubeadm) Include a dedicated bastion management VM
  --tools-bastion LIST        Extra tools on the management VM (bastion|aks|minikube|kind)
                              e.g., --tools-bastion "helm,kustomize"
  --output-dir DIR            Override auto-detected module directory (e.g., ./2025.../hw-m8)
  --k8s-version VER           Kubernetes version for node env (default: ${K8S_VERSION})
  --azure                     Enable Azure CLI + /etc/azure-env on management VM(s):
                              - minikube/kind: installs Azure CLI on the single VM
                              - kubeadm (single-cluster): installs Azure CLI on nodes (use sparingly)
                              - multi-kubeadm: requires --bastion; installs Azure CLI on bastion

Sizing profile and overrides:
  --profile NAME              Sizing profile: compact | standard (default) | power
  --vm-cpus N                 Single-node CPUs (minikube/aks)
  --vm-mem MB|GB              Single-node memory (minikube/aks), e.g., 4096 or 4g
  --master-cpus N             Kubeadm master CPUs
  --master-mem MB|GB          Kubeadm master memory
  --worker-cpus N             Kubeadm worker CPUs
  --worker-mem MB|GB          Kubeadm worker memory
  --bastion-cpus N            Bastion CPUs (multi-kubeadm or --cluster bastion)
  --bastion-mem MB|GB         Bastion memory (multi-kubeadm or --cluster bastion)

Host guardrails (optional):
  --host-cpus N               Host available CPUs for warning comparisons
  --host-ram MB|GB            Host available RAM for warning comparisons

Notes:
  - minikube and aks ignore --clusters. Use --tools-bastion to add tools on the single management VM.
  - For AKS, heavy local cluster tools are discouraged; prefer minikube with --azure for a hybrid on one VM.

Examples:
  # Multi-cluster (comma separated)
  $0 --module m8 --type hw --cluster multi-kubeadm \
     --clusters "sofia:weave:192.168.56.110,plovdiv:calico:192.168.56.120" --bastion

  # Multi-cluster (semicolon separated)
  $0 --module m8 --type hw --cluster multi-kubeadm \
     --clusters "dev:weave:192.168.56.110; staging:flannel:192.168.56.120" --bastion

  # Minikube single-node with Azure CLI
  $0 --module m1 --type pt --cluster minikube --azure

  # AKS management VM with extra tooling
  $0 --module m3 --type pt --cluster aks --tools-bastion helm,kustomize

  # Bastion-only management VM (no clusters)
  $0 --module m9 --type hw --cluster bastion --tools-bastion helm,k9s
EOF
}

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --module|-m) MODULE_NUM="$2"; shift 2 ;;
      --type|-t) MODULE_TYPE="$2"; shift 2 ;;
      --cluster|-c) CLUSTER_TYPE="$2"; shift 2 ;;
      --clusters)
        shift
        gen::capture_clusters_value CLUSTERS_SPEC "$@" || exit 1
        shift ${GEN_CONSUMED:-1}
        ;;
      --bastion) INCLUDE_BASTION="true"; shift ;;
      --azure) AZURE_ENABLED="true"; shift ;;
      --tools-bastion) TOOLS_BASTION="$2"; shift 2 ;;
      --profile) PROFILE="$2"; shift 2 ;;
      --vm-cpus) VM_CPUS="$2"; shift 2 ;;
      --vm-mem) VM_MEM="$2"; shift 2 ;;
      --master-cpus) MASTER_CPUS="$2"; shift 2 ;;
      --master-mem) MASTER_MEM="$2"; shift 2 ;;
      --worker-cpus) WORKER_CPUS="$2"; shift 2 ;;
      --worker-mem) WORKER_MEM="$2"; shift 2 ;;
      --bastion-cpus) BASTION_CPUS="$2"; shift 2 ;;
      --bastion-mem) BASTION_MEM="$2"; shift 2 ;;
      --host-cpus) HOST_CPUS="$2"; shift 2 ;;
      --host-ram) HOST_RAM="$2"; shift 2 ;;
      --output-dir) OUTPUT_DIR="$2"; shift 2 ;;
      --k8s-version) K8S_VERSION="$2"; shift 2 ;;
      -h|--help) show_usage; exit 0 ;;
      *) lib::error "Unknown option: $1"; show_usage; exit 1 ;;
    esac
  done
}

validate_args() {
  if [ -z "$MODULE_NUM" ] || [ -z "$MODULE_TYPE" ] || [ -z "$CLUSTER_TYPE" ]; then
    lib::error "Missing required args: --module, --type, --cluster"
    exit 1
  fi
  case "$CLUSTER_TYPE" in
    multi-kubeadm)
      if [ -z "$CLUSTERS_SPEC" ]; then
        lib::error "--clusters spec is required for multi-kubeadm"
        exit 1
      fi
      # Support CSV/JSON/YAML/@file and normalize to CSV
      gen::clusters_spec_to_csv || exit 1
      ;;
    minikube|kind|aks|bastion)
      if [ -n "$CLUSTERS_SPEC" ]; then
        lib::warn "--clusters is ignored for $CLUSTER_TYPE"
      fi
      ;;
    *)
      lib::error "Unsupported --cluster: $CLUSTER_TYPE (expected: multi-kubeadm|minikube|aks|bastion)"
      exit 1
      ;;
  esac

  # Azure semantics
  if [ "$AZURE_ENABLED" = "true" ]; then
    case "$CLUSTER_TYPE" in
      aks)
        lib::warn "Ignoring --azure for aks (Azure is implicit)."
        AZURE_ENABLED="false"
        ;;
      multi-kubeadm)
        if [ "$INCLUDE_BASTION" != "true" ]; then
          lib::error "--azure with multi-kubeadm requires --bastion (Azure CLI installs on bastion)."
          exit 1
        fi
        ;;
    esac
  fi

  if [ -z "$OUTPUT_DIR" ]; then
    MODULE_DIR=$(find . -maxdepth 2 -type d -name "${MODULE_TYPE}-${MODULE_NUM}" -print -quit)
    if [ -z "$MODULE_DIR" ]; then
      lib::error "Could not find module directory '${MODULE_TYPE}-${MODULE_NUM}'"
      exit 1
    fi
    OUTPUT_DIR="$MODULE_DIR"
  fi
}

# Convert memory string to MB (accepts plain MB or e.g., 16g/16G)
_mem_to_mb() {
  local v="$1"
  if [[ "$v" =~ ^[0-9]+$ ]]; then
    echo "$v"; return 0
  fi
  if [[ "$v" =~ ^([0-9]+)[gG]$ ]]; then
    echo $(( ${BASH_REMATCH[1]} * 1024 )); return 0
  fi
  # Unknown format
  echo ""; return 1
}

compute_sizing() {
  # Shared helpers set the same variables across generators
  gen::apply_sizing_profile
  gen::apply_sizing_overrides
}

print_sizing_summary() {
  compute_sizing
  local total_cpus=0 total_mem_mb=0
  local lines=()

  case "$CLUSTER_TYPE" in
    kind)
      lines+=("kind-mgmt: CPUs=${MK_CPUS}, MEM=${MK_MEM}MB, count=1")
      total_cpus=$(( total_cpus + MK_CPUS ))
      total_mem_mb=$(( total_mem_mb + MK_MEM ))
      ;;
    minikube)
      lines+=("minikube: CPUs=${MK_CPUS}, MEM=${MK_MEM}MB, count=1")
      total_cpus=$(( total_cpus + MK_CPUS ))
      total_mem_mb=$(( total_mem_mb + MK_MEM ))
      ;;
    aks)
      lines+=("aks-mgmt: CPUs=${AKS_CPUS}, MEM=${AKS_MEM}MB, count=1")
      total_cpus=$(( total_cpus + AKS_CPUS ))
      total_mem_mb=$(( total_mem_mb + AKS_MEM ))
      ;;
    bastion)
      lines+=("bastion: CPUs=${BASTION_D_CPUS}, MEM=${BASTION_D_MEM}MB, count=1")
      total_cpus=$(( total_cpus + BASTION_D_CPUS ))
      total_mem_mb=$(( total_mem_mb + BASTION_D_MEM ))
      ;;
    multi-kubeadm)
      local clusters_count=0
      local entries
      entries=$(gen::split_clusters_entries "$CLUSTERS_SPEC")
      clusters_count=$(printf "%s\n" "$entries" | sed '/^$/d' | wc -l | tr -d ' ')
      if [ "$INCLUDE_BASTION" = "true" ]; then
        lines+=("bastion: CPUs=${BASTION_D_CPUS}, MEM=${BASTION_D_MEM}MB, count=1")
        total_cpus=$(( total_cpus + BASTION_D_CPUS ))
        total_mem_mb=$(( total_mem_mb + BASTION_D_MEM ))
      fi
      if [ "$clusters_count" -gt 0 ]; then
        lines+=("master: CPUs=${KM_MASTER_CPUS}, MEM=${KM_MASTER_MEM}MB, count=${clusters_count}")
        lines+=("worker: CPUs=${KM_WORKER_CPUS}, MEM=${KM_WORKER_MEM}MB, count=${clusters_count}")
        total_cpus=$(( total_cpus + KM_MASTER_CPUS*clusters_count + KM_WORKER_CPUS*clusters_count ))
        total_mem_mb=$(( total_mem_mb + KM_MASTER_MEM*clusters_count + KM_WORKER_MEM*clusters_count ))
      fi
      ;;
  esac

  lib::subheader "Sizing Summary"
  for l in "${lines[@]}"; do lib::log "  - $l"; done
  lib::kv "Total CPUs" "$total_cpus"
  lib::kv "Total Memory (MB)" "$total_mem_mb"

  # Host capacity warnings (shown only if user provides host caps)
  local host_cpus_limit="" host_mem_mb_limit="" warn_flag=0
  if [ -n "$HOST_CPUS" ]; then host_cpus_limit="$HOST_CPUS"; fi
  if [ -n "$HOST_RAM" ]; then
    local m
    m=$(_mem_to_mb "$HOST_RAM" || true)
    if [ -n "$m" ]; then host_mem_mb_limit="$m"; fi
  fi
  if [ -n "$host_cpus_limit" ] && [ "$host_cpus_limit" -gt 0 ] && [ "$total_cpus" -gt "$host_cpus_limit" ]; then
    lib::warn "Requested CPUs ($total_cpus) exceed host CPUs ($host_cpus_limit)"
    warn_flag=1
  fi
  if [ -n "$host_mem_mb_limit" ] && [ "$host_mem_mb_limit" -gt 0 ] && [ "$total_mem_mb" -gt "$host_mem_mb_limit" ]; then
    lib::warn "Requested Memory ${total_mem_mb}MB exceeds host RAM ${host_mem_mb_limit}MB"
    warn_flag=1
  fi
  if [ "$warn_flag" = "1" ]; then
    lib::warn "Consider using --profile compact or adjusting override flags."
  fi
}

render_role_bootstraps() {
  local scripts_dir="$OUTPUT_DIR/scripts"
  mkdir -p "$scripts_dir"

  case "$CLUSTER_TYPE" in
    multi-kubeadm)
      if [ "$INCLUDE_BASTION" = "true" ]; then
        lib::log "Generating bastion bootstrap..."
        local b_args=(
          --module "$MODULE_NUM"
          --type "$MODULE_TYPE"
          --cluster kubeadm
          --role bastion
          --filename "bootstrap-bastion.sh"
          --output-dir "$scripts_dir"
        )
        if [ -n "$TOOLS_BASTION" ]; then
          b_args+=( --tools "$TOOLS_BASTION" )
        fi
        if [ "$AZURE_ENABLED" = "true" ]; then b_args+=( --azure ); fi
        "${SCRIPT_DIR}/generate-bootstrap.sh" "${b_args[@]}"
      fi

      local entries entry
      entries=$(gen::split_clusters_entries "$CLUSTERS_SPEC")
      while IFS= read -r entry; do
        [ -z "$entry" ] && continue
        local info cname ccni _cip _nodes
        info=$(gen::parse_cluster_entry "$entry") || continue
        IFS=' ' read -r cname ccni _cip _nodes <<< "$info"
        lib::log "Generating bootstraps for cluster: $cname (CNI=$ccni)"
        "${SCRIPT_DIR}/generate-bootstrap.sh" \
          --module "$MODULE_NUM" \
          --type "$MODULE_TYPE" \
          --cluster kubeadm \
          --role master \
          --cni "$ccni" \
          --filename "bootstrap-${cname}-master.sh" \
          --output-dir "$scripts_dir"

        "${SCRIPT_DIR}/generate-bootstrap.sh" \
          --module "$MODULE_NUM" \
          --type "$MODULE_TYPE" \
          --cluster kubeadm \
          --role worker \
          --cni "$ccni" \
          --filename "bootstrap-${cname}-worker.sh" \
          --output-dir "$scripts_dir"
      done <<< "$entries"
      ;;
    minikube)
      lib::log "Generating minikube bootstrap..."
      local mk_args=(
        --module "$MODULE_NUM"
        --type "$MODULE_TYPE"
        --cluster minikube
        --filename "bootstrap.sh"
        --output-dir "$scripts_dir"
      )
      if [ -n "$TOOLS_BASTION" ]; then
        mk_args+=( --tools "$TOOLS_BASTION" )
      fi
      if [ "$AZURE_ENABLED" = "true" ]; then mk_args+=( --azure ); fi
      "${SCRIPT_DIR}/generate-bootstrap.sh" "${mk_args[@]}"
      ;;
    kind)
      lib::log "Generating kind bootstrap..."
      local kind_args=(
        --module "$MODULE_NUM"
        --type "$MODULE_TYPE"
        --cluster kind
        --filename "bootstrap.sh"
        --output-dir "$scripts_dir"
      )
      if [ -n "$TOOLS_BASTION" ]; then
        kind_args+=( --tools "$TOOLS_BASTION" )
      fi
      if [ "$AZURE_ENABLED" = "true" ]; then kind_args+=( --azure ); fi
      "${SCRIPT_DIR}/generate-bootstrap.sh" "${kind_args[@]}"
      ;;
    aks)
      lib::log "Generating AKS management bootstrap..."
      local aks_args=(
        --module "$MODULE_NUM"
        --type "$MODULE_TYPE"
        --cluster aks
        --filename "bootstrap.sh"
        --output-dir "$scripts_dir"
      )
      if [ -n "$TOOLS_BASTION" ]; then aks_args+=( --tools "$TOOLS_BASTION" ); fi
      "${SCRIPT_DIR}/generate-bootstrap.sh" "${aks_args[@]}"
      ;;
    bastion)
      lib::log "Generating bastion bootstrap..."
      local bastion_args=(
        --module "$MODULE_NUM"
        --type "$MODULE_TYPE"
        --cluster bastion
        --filename "bootstrap.sh"
        --output-dir "$scripts_dir"
      )
      if [ -n "$TOOLS_BASTION" ]; then
        bastion_args+=( --tools "$TOOLS_BASTION" )
      fi
      if [ "$AZURE_ENABLED" = "true" ]; then bastion_args+=( --azure ); fi
      "${SCRIPT_DIR}/generate-bootstrap.sh" "${bastion_args[@]}"
      ;;
  esac
}

render_vagrantfile() {
  lib::log "Generating Vagrantfile..."
  local gv_args=( --module "$MODULE_NUM" --type "$MODULE_TYPE" --output-dir "$OUTPUT_DIR" --profile "$PROFILE" )
  case "$CLUSTER_TYPE" in
    multi-kubeadm)
      gv_args+=( --cluster multi-kubeadm --clusters "$CLUSTERS_SPEC" )
      if [ "$INCLUDE_BASTION" = "true" ]; then gv_args+=( --bastion ); fi
      # Per-role sizing for multi-kubeadm
      [ -n "$MASTER_CPUS" ] && gv_args+=( --master-cpus "$MASTER_CPUS" )
      [ -n "$MASTER_MEM" ] && gv_args+=( --master-mem "$MASTER_MEM" )
      [ -n "$WORKER_CPUS" ] && gv_args+=( --worker-cpus "$WORKER_CPUS" )
      [ -n "$WORKER_MEM" ] && gv_args+=( --worker-mem "$WORKER_MEM" )
      [ -n "$BASTION_CPUS" ] && gv_args+=( --bastion-cpus "$BASTION_CPUS" )
      [ -n "$BASTION_MEM" ] && gv_args+=( --bastion-mem "$BASTION_MEM" )
      ;;
    minikube)
      gv_args+=( --cluster minikube )
      [ -n "$VM_CPUS" ] && gv_args+=( --vm-cpus "$VM_CPUS" )
      [ -n "$VM_MEM" ] && gv_args+=( --vm-mem "$VM_MEM" )
      ;;
    kind)
      gv_args+=( --cluster kind )
      [ -n "$VM_CPUS" ] && gv_args+=( --vm-cpus "$VM_CPUS" )
      [ -n "$VM_MEM" ] && gv_args+=( --vm-mem "$VM_MEM" )
      ;;
    aks)
      gv_args+=( --cluster aks )
      [ -n "$VM_CPUS" ] && gv_args+=( --vm-cpus "$VM_CPUS" )
      [ -n "$VM_MEM" ] && gv_args+=( --vm-mem "$VM_MEM" )
      ;;
    bastion)
      gv_args+=( --cluster bastion )
      [ -n "$BASTION_CPUS" ] && gv_args+=( --bastion-cpus "$BASTION_CPUS" )
      [ -n "$BASTION_MEM" ] && gv_args+=( --bastion-mem "$BASTION_MEM" )
      ;;
  esac
  "${SCRIPT_DIR}/generate-vagrantfile.sh" "${gv_args[@]}"
}

main() {
  lib::header "Multi-Cluster Module Generator"
  parse_args "$@"
  validate_args
  lib::kv "Module" "${MODULE_NUM} (${MODULE_TYPE})"
  lib::kv "Cluster" "$CLUSTER_TYPE"
  if [ "$CLUSTER_TYPE" = "multi-kubeadm" ]; then
    lib::kv "Clusters" "$CLUSTERS_SPEC"
    lib::kv "Bastion" "$INCLUDE_BASTION"
  fi
  lib::kv "Output Dir" "$OUTPUT_DIR"
  if [ "$AZURE_ENABLED" = "true" ]; then lib::kv "Azure" "enabled"; fi

  print_sizing_summary

  render_role_bootstraps
  render_vagrantfile

  lib::hr
  lib::success "Multi-cluster module generated!"
  lib::log "Next: cd '${OUTPUT_DIR}' and run: vagrant up"
}

main "$@"
