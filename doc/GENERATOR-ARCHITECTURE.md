---
status: Normative specification for the k8s-generator CLI
version: 1.19.2
scope: Defines CLI behavior, engines, validation, templates, and generation contracts
---

# k8s-generator: Normative Specification (v1.19.2)

## Overview

- CLI-first (80%): single cluster, smart defaults, zero YAML
- YAML optional (20%): multi-cluster, custom networking, per-node shaping
- Engines map 1:1 to `cluster-type`: `mgmt→none`, `kubeadm→kubeadm`
- Deterministic and idempotent generation; atomic writes; clear validation errors

## Architecture (6 Bricks + Orchestrator)

CLI → Orchestrator → [Model ↔ InputParser ↔ Validation] → Rendering → I/O

Contracts
- CLI: Picocli-based, stable options and error semantics
- Model: immutable records, value objects for domain concepts
- Validation: structural → semantic → policy (aggregate errors)
- Rendering: JTE with typed, precompiled templates
- I/O: atomic write, deterministic outputs, rollback on failure
- Engines: SPI with `EngineRegistry` (ServiceLoader)

This document is self-contained; future notes and reviews are omitted.

## CLI Contract

- Forms
  - Canonical: `k8s-gen --module <mN> --type <type> <cluster-type> [modifiers]`
  - Positional: `k8s-gen <mN> <type> <cluster-type> [modifiers]`
- Required
  - `--module <mN>` (e.g., m1, m7), `--type <type>` (e.g., pt, hw, exam, exam-prep)
  - `<cluster-type>`: `mgmt|kubeadm`
- Modifiers
  - `--size small|medium|large`, `--nodes 1m,2w` (kubeadm), `--azure`, `--out`, `--dry-run`
- Naming & paths
  - Cluster: `clu-<module>-<type>-<engine>`; Namespace: `ns-<module>-<type>`
  - Default output: `<type>-<module>/`; if exists → fail. Use `--out` to override.
- Spec-gate
  - Use CLI for simple cases; YAML required for multi-cluster, custom networking, per-node shaping
  - Error: `Spec-gate: YAML only for complex topologies. Use CLI for simple clusters.`

## Conventions

- Opinionated defaults
  - kubeadm: containerd; CNI=Calico; Pod CIDR `10.244.0.0/16`; Svc CIDR `10.96.0.0/12`
  - Sizing profiles: `small|medium|large` (default: medium → 4 CPU, 8192 MB)
  - Naming: `clu-<module>-<type>-<engine>`, namespaces `ns-<module>-<type>`
  - Box image default: `ubuntu/jammy64`
- Management machine (mgmt)
  - Standalone tools VM (engine `none`) or orchestrator for multi-cluster labs
  - Reserved IP `192.168.56.5`; aggregates kubeconfigs; optional Azure CLI when `--azure`
- Module/type semantics
  - `--module` is course scope (mN); `--type` is taxonomy (pt|hw|exam|exam-prep|lab|demo)
  - Default output `<type>-<module>/`; treat module+type as workspace key; collision fails

## YAML Input (Complex Scenarios Only)

- Use only for: multi-cluster, custom networking, per-node shaping, complex tool matrices
- Schema (summary)
  - `version: "1.0.0"`
  - `module: { num: mN, type: <type> }`
  - `management: { name?, tools?, aggregate_kubeconfigs?, providers? }` (optional)
  - `defaults: { box_image? }` (optional)
  - `clusters: [ { name, cluster_type, cni?, nodes?, pod_network?, svc_network?, first_ip?, vm_overrides? } ]`
- Minimum examples are omitted; this spec keeps only the contract

## Sizing

- Profiles: `small|medium|large` (default medium)
- Single-node engines: profile applies to the single VM
- Kubeadm: per-role defaults derived from profile; optional `vm_overrides` in YAML

## IP Address Allocation

Policy
- Single-cluster: default base IP `192.168.56.10` (override via YAML `first_ip`)
- Multi-cluster: every cluster MUST specify `first_ip`
- Allocation within a cluster: sequential from `first_ip` (masters first, then workers)
- Management VM reserved IP: `192.168.56.5`
- Reserved IPs: `192.168.56.1`, `192.168.56.2`, `192.168.56.5`
Validation
- Detect overlaps, subnet boundary overflow, and reserved IP usage
- Library: `com.github.seancfoley:ipaddress` for parsing and checks

### Philosophy Alignment

✅ Explicit over implicit (multi-cluster requires `first_ip`)
✅ Sensible defaults (`192.168.56.10` for single-cluster)
✅ Fail-fast validation (detect before VM creation)
✅ No magic (sequential allocation only)
✅ Manual-first (YAML owns explicit IPs)

## Tool Management

Engine-immutable tool sets
- Each engine defines its required tools; no user override via CLI/YAML
- Additional tools are added via bootstrap hooks (`scripts/bootstrap.post.d/*.sh`)
- Policy forbids conflicting engines or installing engines on `mgmt`
- CNIs (kubeadm): `calico` (default), `flannel`, `weave`, `cilium`, `antrea`

## Environment Files Strategy

File roles
- `/etc/k8s-env`: cluster-scoped non-secret vars; always when Kubernetes is in scope
- `/etc/azure-env`: Azure configuration; only when `--azure`/`providers: [azure]`
- `/etc/profile.d/50-k8s-env.sh`: sources `/etc/k8s-env` and `/etc/azure-env`
- `scripts/bootstrap.env.local`: untracked local overrides (scaffolded)
Precedence (provision-time)
1) `scripts/bootstrap.env.local` → 2) Vagrant `shell.env` → 3) bootstrap defaults
Notes
- Azure values may be empty; bootstrap emits WARN when workflows need them
- Script standards (summary): idempotent; enable strict mode and traps; persist env to `/etc/*-env`; support pre/post hooks; avoid secrets in repo

## Validation Strategy

Three layers
- Structural: required fields present and well-formed
- Semantic: domain rules (IP collisions, CIDR overlap, node shapes)
- Policy: engine/tool compatibility, forbidden combinations, sizing/role constraints

Execution
- Validate before any file generation
- Aggregate errors into single comprehensive message
- Fail-fast with actionable error messages

### Validation Error Format (Normative)

Structure
```
Validation failed for spec '<filename>':

[Structural] <description>
  → <suggestion line 1>
  → <suggestion line 2>

[Semantic] <description>
  → <suggestion>

[Policy] <description>
  → <suggestion>
```

Levels
- Structural: schema/required fields
- Semantic: business rules
- Policy: compatibility and constraints

Exit Codes
- 0: Success
- 2: Validation failure
- 1: Unexpected internal error

## Common Errors (Reference)

- Multi-cluster missing `first_ip` → Structural
- Cluster IP overlap → Semantic
- Subnet boundary exceeded → Semantic
- Reserved IP usage → Semantic
- Pod/Svc CIDR overlap → Semantic
- Kubeadm missing CNI → Structural



## Atomic File Generation

To prevent partial/corrupt output, all writes MUST be atomic and idempotent.

### AtomicFileWriter Contract

```java
public interface AtomicFileWriter {
    Result<Path, String> writeAll(Path outputDir, Map<Path, String> files);
}
```

### Implementation Strategy

1) Write all files to a temp directory
2) Validate generated files if applicable
3) Atomically move temp → final (or rollback on failure)

## Regeneration Strategy (Future)

Generation metadata `.k8s-generator.yaml` is created in output directory to detect drift and control overwrite/merge behavior.

### Regeneration Modes

```
# Default: fail if modified files are detected
k8s-gen --module m1 --type pt kind --out pt-m1/
# → ERROR: Generated files have been modified. Use --force to overwrite.

# Force: overwrite all regeneratable files
k8s-gen --module m1 --type pt kind --out pt-m1/ --force

# Future: three-way merge (defer to v2.0)
k8s-gen --module m1 --type pt kind --out pt-m1/ --merge
```

## Provisioning (Deferred Automation)

Manual-first now; automation optional later
- Scaffold only by default; provision/verify/destroy are opt-in scripts
- No Provisioner SPI in v1; future notes omitted
  (manual steps are outside this spec; scripts may mirror those steps)

## Template Engine (JTE)

Why JTE
- Typed contexts, compile-time validation, partials/includes, good testability

### Template Structure

```
src/main/resources/templates/
├── Vagrantfile.jte              # Main VM topology
├── bootstrap.sh.jte             # Main bootstrap script
└── partials/
    ├── minikube-setup.jte       # Minikube-specific setup
    ├── kind-setup.jte           # Kind-specific setup
    ├── kubeadm-master.jte       # Kubeadm master setup
    └── kubeadm-worker.jte       # Kubeadm worker setup
```

### Template Context (Typed)

```java
public record VagrantfileContext(
    String moduleName,
    List<VmConfig> vms,
    Map<String, String> envVars
) {}
```

### Example Template (Vagrantfile.jte)

```ruby
Vagrant.configure("2") do |config|
  @for(var vm : vms)
  config.vm.define "${vm.name()}" do |node|
    node.vm.box = "${vm.boxImage()}"
    node.vm.hostname = "${vm.name()}"
    node.vm.network "private_network", ip: "${vm.ip()}"

    node.vm.provider "virtualbox" do |vb|
      vb.memory = ${vm.memoryMb()}
      vb.cpus = ${vm.cpus()}
    end

    node.vm.provision "shell", path: "scripts/bootstrap.sh", env: {
      @for(var entry : envVars.entrySet())
      "${entry.getKey()}" => "${entry.getValue()}",
      @endfor
    }
  end
  @endfor
end
```

### Template Validation

Contract documentation at top of each template; precompile and fail on contract violations before rendering.

### Context Building Algorithm (Normative)

Ordering: mgmt first (if present), then clusters in declaration order (masters first, then workers).
Environment scoping: module-level vars in `VagrantfileContext`; cluster-scoped vars are persisted per VM by bootstrap and not aggregated globally.
VM naming: mgmt=`mgmt`; masters=`<cluster>-master-{1..}`; workers=`<cluster>-worker-{1..}`.

## Testing Guidance (Non‑Normative)

Test Pyramid
- Unit 60%: domain records, parsing, sizing/tools, CIDR, policy
- Integration 30%: end-to-end pipeline, rendering, FS ops
- CLI smoke 10%: scaffold samples, syntax checks (Vagrantfile, shell)

## Future Extensions (Non‑Normative)

- Native image packaging (GraalVM)
- Auto-CIDR allocation
- Interactive wizard mode / Web UI
- Provider engines (aks/eks/gke) when provisioning in scope

## Conformance

A conforming implementation MUST:
- Implement the CLI forms and arguments defined here
- Enforce Spec‑Gate rules
- Produce the output structure (Vagrantfile + scripts)
- Apply naming conventions for clusters, namespaces, and directories
- Treat provisioning as opt‑in and not run by default

### CIDR Validation and IP Library Usage (Normative)

Inputs
- `pod_network` (kubeadm): IPv4 CIDR string (default `10.244.0.0/16`)
- `svc_network` (kubeadm): IPv4 CIDR string (default `10.96.0.0/12`)

Required operations
- Validate IPv4 CIDR syntax for both fields
- Ensure `pod_network` and `svc_network` do not overlap
- Ensure each is a prefix block (no host-bits set outside the prefix)

Explicitly not performed
- No auto-allocation or auto-correction of CIDRs
- No dynamic suggestions beyond static examples

Reference library
- `com.github.seancfoley:ipaddress` for parsing, validation, overlap detection

## Document History

| Version | Date       | Author      | Changes                                                              |
|---------|------------|-------------|----------------------------------------------------------------------|
| 1.20.0  | 2025-11-11 | repo-maint  | Removed kind and minikube support; simplified to kubeadm-only architecture. |
| 1.19.2  | 2025-11-11 | repo-maint  | Made spec self-contained: removed cross-references and external doc links; minor clarifications. |
| 1.19.1  | 2025-11-11 | repo-maint  | Distilled normative spec; removed verbose examples; consolidated future notes; aligned title/version. |
| 1.19.0  | 2025-11-11 | repo-maint  | Aligned with architecture reviews: added Atomic Writes, Regeneration Strategy, updated package structure. |
| 1.18.0  | 2025-11-10 | repo-maint  | Formalized use of DDD Value Objects as a core design principle.      |
| 1.17.0  | 2025-11-10 | repo-maint  | Updated design principles to include builder pattern for complex models. |
| 1.16.2  | 2025-11-04 | repo-maint  | History table reordered to descending version per policy |
| 1.16.1  | 2025-11-03 | repo-maint  | Semver normalization of version fields; updated YAML examples to 1.0.0; aligned frontmatter/title to 1.16.1 |
| 1.16.0  | 2025-11-02 | spec-owner  | Hybrid Architecture: CLI-First with Optional Spec Export |
| 1.15.0  | 2025-10-31 | spec-owner  | Consistency fixes: corrected CLI command examples (k8s-gen), tool name regex note, and YAML note mapping module.num to --module |
| 1.14.0  | 2025-10-31 | spec-owner  | Visual Decision Tree added; Style and Naming guidelines clarified |
| 1.13.0  | 2025-10-31 | spec-owner  | Quick Reference added; Generated .gitignore contents specified; Common Errors reference section included |
| 1.12.0  | 2025-10-31 | spec-owner  | Vagrant box defaults added (ubuntu/jammy64), overrides (per-cluster and global), and warning policy for non-ubuntu boxes |
| 1.11.0  | 2025-10-31 | spec-owner  | CIDR validation documented; ipaddress library usage clarified; CidrHelper responsibility stated (no auto-allocation) |
| 1.10.0  | 2025-10-31 | spec-owner  | Provisioner SPI moved to deferred note; removed `--list-provisioners` reference from CLI; kept manual scripts only |
| 1.9.0   | 2025-10-31 | spec-owner  | Validation error format specified (structure, levels, exit codes, aggregation behavior) |
| 1.8.0   | 2025-10-31 | spec-owner  | Azure usage clarified: user responsibilities, bootstrap.env.local example, precedence, and WARN semantics for missing values |
| 1.7.0   | 2025-10-31 | spec-owner  | Restored `management.tools` (mgmt-only extras) with clear scope; clusters remain engine-immutable |
| 1.6.0   | 2025-10-31 | spec-owner  | YAML schema completed: version field, required/optional markers, minimum viable examples |
| 1.5.0   | 2025-10-31 | spec-owner  | Terminology unified (Management Machine); Terminology: Cluster-Type vs Engine section; taxonomy/mapping/examples synchronized |
| 1.4.0   | 2025-10-31 | spec-owner  | Future: Multi-Cluster Network Separation section (scope clarification) |
| 1.3.0   | 2025-10-31 | spec-owner  | Context Building Algorithm added (ordering, env scoping, naming, pseudocode) |
| 1.2.0   | 2025-10-31 | spec-owner  | Module+type composite workspace identifier; output directory collision policy and CLI notes |
| 1.1.0   | 2025-10-31 | spec-owner  | IP allocation policy added (single-cluster default, multi-cluster first_ip requirement), reserved mgmt IP, validations, examples |
| 1.0.0   | 2025-10-31 | spec-owner  | Initial normative baseline |
