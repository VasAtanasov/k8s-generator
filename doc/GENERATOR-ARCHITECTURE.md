---
status: Normative specification for the k8s-generator CLI.
version: 1.16.2
scope: Defines CLI behavior, inputs/outputs, conventions, and validation.
---

# k8s-generator: Specification (v1.16.2)

## Quick Reference

- CLI forms
  - Canonical: `k8s-gen --module <mN> --type <type> <cluster-type> [modifiers]`
  - Positional: `k8s-gen <mN> <type> <cluster-type> [modifiers]`
  - Modifiers: `--size small|medium|large`, `--nodes 1m,2w` (kubeadm), `--azure`, `--out`, `--dry-run`

- Cluster-types (user-facing) → Engines (internal)
  - `mgmt` → engine `none` (management machine)
  - `kind` → engine `kind`
  - `minikube` → engine `minikube`
  - `kubeadm` → engine `kubeadm`

- Defaults
  - Container runtime: containerd (kubeadm)
  - CNI: Calico (kubeadm) • Pod CIDR: `10.244.0.0/16` • Svc CIDR: `10.96.0.0/12`
  - Sizing (medium): 4 CPUs, 8192 MB
  - IPs: single-cluster `192.168.56.10` • multi-cluster requires `first_ip`
  - Management IP: `192.168.56.5`
  - Vagrant box: `ubuntu/jammy64`

- Naming & paths
  - Cluster: `clu-<module>-<type>-<engine>` • Namespace: `ns-<module>-<type>`
  - Output dir: `<type>-<module>/` (fails if exists; override with `--out`)

- Spec-gate
  - CLI only for simple, YAML for complex (multi-cluster, custom networking, per-node shaping)


## Specification Summary

This document specifies a CLI tool that scaffolds complete, working Kubernetes learning environments using **convention-over-configuration**. The CLI handles the common path (single cluster, sane defaults) without YAML and reserves YAML for genuinely complex topologies (multi‑cluster, custom networking, per‑node shaping). A management machine (mgmt) is supported for multi‑cluster orchestration and kubeconfig aggregation.

---

## Core Architecture Concept

### The Generator's Purpose

Generate **complete, working Kubernetes learning environments** using **convention-over-configuration**:

**The 80/20 Split**:
- **80% of use cases**: Simple CLI with smart defaults (zero YAML)
- **20% of use cases**: YAML specs for genuinely complex scenarios

### Simple Path (80% - CLI Only)

**Input** (CLI command with smart defaults):
```bash
# Zero configuration required
k8s-gen --module m1 --type pt kind
k8s-gen --module m2 --type exam-prep minikube --size large
k8s-gen --module m7 --type hw kubeadm --azure --nodes 1m,2w
```

**Output** (Generated files):
```
<out>/
├── Vagrantfile                    # VM topology with smart defaults
├── scripts/
│   ├── bootstrap.sh              # Role-aware bootstrap
│   ├── install_kubectl.sh        # Tool installers
│   ├── install_kind.sh           # (or minikube, kubeadm)
│   ├── bootstrap.pre.d/          # Pre-bootstrap hooks
│   ├── bootstrap.post.d/         # Post-bootstrap hooks
│   └── bootstrap.env.local       # Local overrides template
└── .gitignore
```

### Generated .gitignore

Contents:
```
.vagrant/
*.log
scripts/bootstrap.env.local
```

Rationale:
- `.vagrant/` contains machine-specific metadata
- `*.log` captures provisioning/bootstrap logs and should not be tracked
- `scripts/bootstrap.env.local` contains local overrides and secrets (e.g., Azure IDs)

**Time to first cluster**: 2-5 minutes (vs 15-30 with YAML)

### Complex Path (20% - YAML Specs)

**Input** (YAML spec for custom requirements):
```yaml
# multi-cluster.yaml - When you need fine-grained control
scenarios:
  - name: staging
    cluster_type: kind
    node_count: 3
    custom_networking:
      pod_cidr: 10.240.0.0/16
      service_cidr: 10.96.0.0/12

  - name: prod
    cluster_type: kubeadm
    nodes: { masters: 2, workers: 5 }
    custom_storage: true
```

**Command**:
```bash
k8s-gen --spec multi-cluster.yaml --out ./infra
```

**Output**: Same structure, but with custom configurations

**Philosophy**: **Ruthless simplicity for common cases, power when complexity is genuine**.

---

## Hybrid Architecture: CLI-First with Optional Spec Export

### Design Rationale

The generator employs a **hybrid architecture** that preserves CLI simplicity for common cases while enabling YAML-based reproducibility when genuinely needed. This approach avoids forcing YAML ceremony on beginners while providing a smooth evolution path to production-grade specifications.

**Key Insight**: YAML specs serve as an **internal representation** that users can optionally materialize, rather than a **required input** that gates all usage.

### Three Usage Paths

The architecture supports three distinct workflows, each appropriate for different scenarios:

#### Path A: Simple CLI (80% of users)

**Use case**: Getting started, exploration, classroom demos, single clusters with defaults

```bash
# Zero YAML required - instant manifest generation
k8s-gen --module m1 --type pt kind
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w
```

**Internal flow**:
```
CLI Flags → Generate Ephemeral YAML Spec → Validate → Generate Manifests
```

**Characteristics**:
- No YAML files created (unless explicitly requested)
- Time to first cluster: <60 seconds
- Perfect for beginners and classroom environments
- Full validation still applies (via internal spec)

#### Path B: Evolution to Specs (when complexity needed)

**Use case**: Starting simple but needing to add complexity (multi-cluster, custom networking, per-node sizing)

```bash
# Start with CLI
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w

# Later: export spec when complexity needed
k8s-gen export-spec --module m7 --type hw > m7-spec.yaml

# Edit m7-spec.yaml for multi-cluster, custom CIDRs, etc.

# Generate from spec
k8s-gen --spec m7-spec.yaml
```

**Internal flow**:
```
CLI Flags → Export YAML Spec → User Edits → Validate → Generate Manifests
```

**Characteristics**:
- Smooth upgrade path (no forced migration)
- YAML created only when genuinely needed
- Preserves all CLI customizations in exported spec
- Enables version control and reproducibility

#### Path C: Spec-First (production users)

**Use case**: Production deployments, infrastructure as code, team collaboration, complex topologies

```bash
# Create or receive spec.yaml
k8s-gen --spec production-spec.yaml --out ./infra/
```

**Internal flow**:
```
YAML Spec File → Validate → Generate Manifests
```

**Characteristics**:
- Full control and reproducibility
- Version control friendly (git diff on specs)
- Supports genuinely complex scenarios
- Team collaboration via shared specs

### Architectural Benefits

**1. Zero Forced Complexity**
- Beginners never see YAML unless they need it
- 80% of use cases remain friction-free
- Classroom demos work instantly

**2. Reproducibility When Needed**
- Export spec at any time for version control
- Production deployments use explicit specs
- Teams can share standardized configurations

**3. Single Validation Path**
- All inputs (CLI or YAML) flow through same validator
- Consistent error messages and guardrails
- No divergent validation logic

**4. Smooth Evolution**
- Natural progression: CLI → export spec → edit → re-generate
- No forced migration or breaking changes
- Users adopt specs only when complexity justifies it

**5. Philosophy Alignment**
- ✅ Ruthless simplicity (80% path remains simple)
- ✅ Convention-over-configuration (defaults work)
- ✅ Start minimal, grow as needed (export when ready)
- ✅ Avoid future-proofing (don't force YAML upfront)
- ✅ 80/20 principle (optimize for common case)

### Implementation Architecture

**Module: CliToSpecConverter**
- **Purpose**: Convert CLI flags to internal YAML spec representation
- **Input**: Validated CLI arguments
- **Output**: YAML spec conforming to schema
- **Side effects**: None (pure transformation)

**Module: SpecExporter** (Phase 2)
- **Purpose**: Materialize internal spec to editable file
- **Input**: CLI flags OR generated spec
- **Output**: YAML spec file
- **Side effects**: Writes spec file to disk

**Module: SpecValidator**
- **Purpose**: Validate YAML spec against schema
- **Input**: YAML spec (from file or internal conversion)
- **Output**: Validated spec OR aggregated errors
- **Dependencies**: JSON schema validator

**Module: ManifestGenerator**
- **Purpose**: Generate Kubernetes manifests from validated spec
- **Input**: Validated spec
- **Output**: Kubernetes YAML manifests, scripts, Vagrantfile
- **Side effects**: None (pure transformation)

### User Experience Comparison

| Aspect | CLI-First | Hybrid | Pure YAML-First |
|--------|-----------|--------|-----------------|
| **Time to first cluster** | <60 sec | <60 sec (CLI path) | 10-15 min (learn schema) |
| **Beginner friction** | None | None | High (YAML required) |
| **Classroom viable** | Yes | Yes | No (too slow) |
| **Reproducibility** | Limited | Full (via export) | Full |
| **Version control** | No | Yes (exported specs) | Yes |
| **Complex scenarios** | Limited | Full (via specs) | Full |
| **Philosophy alignment** | Perfect | Perfect | Poor (1/5) |

### Spec Export Command (Future)

**Syntax**:
```bash
k8s-gen export-spec --module <mN> --type <type> <cluster-type> [modifiers] [--out <file>]
```

**Examples**:
```bash
# Export current CLI config to spec file
k8s-gen export-spec --module m7 --type hw kubeadm --nodes 1m,2w > m7-spec.yaml

# Export with custom output location
k8s-gen export-spec --module m1 --type pt kind --out specs/m1-kind.yaml
```

**Behavior**:
- Converts CLI flags to YAML spec format
- Includes all defaults explicitly (for clarity)
- Validates before export (ensures valid spec)
- Written spec is immediately usable with `--spec` flag

### Migration Path

Users naturally progress through paths as complexity grows:

**Day 1**: Simple CLI
```bash
k8s-gen --module m1 --type pt kind  # Instant gratification
```

**Week 4**: Need customization
```bash
k8s-gen --module m1 --type pt kind --size large --out ./custom/
```

**Month 3**: Need multi-cluster
```bash
# Export working config
k8s-gen export-spec --module m1 --type pt kind > m1-base.yaml

# Edit m1-base.yaml to add second cluster

# Generate from spec
k8s-gen --spec m1-base.yaml
```

**Month 6**: Production deployment
```bash
# Specs in version control
git add production-spec.yaml
git commit -m "Update staging cluster sizing"

# Generate from tracked spec
k8s-gen --spec production-spec.yaml --out ./prod-infra/
```

---

## Convention-Over-Configuration Manifest

This generator is intentionally opinionated. Conventions deliver predictable, fast results; configuration exists only when the problem is complex enough to justify it.

### Guardrails (Spec-Gate)

- CLI-only, no YAML allowed for:
  - Single-cluster Kind
  - Single-cluster Minikube
  - Kubeadm clusters up to 3 nodes with default networking and sizing
- YAML required for:
  - Multi-cluster topologies (independent clusters) with a management machine
  - Custom networking (non-default Pod/Service CIDRs, additional NICs, routed inter-cluster networks)
  - Per-node resource shaping beyond size profiles (role-specific CPU/RAM/disks)
  - Tooling matrices that vary by cluster or role

CLI will reject `--spec` for simple engines with a clear message:

```
Spec-gate: YAML only for complex topologies. Use CLI for simple clusters.
```

### Opinionated Defaults (applied automatically)

- Engine is explicit; no coupling to `--type`. Any type may use any supported engine.
- Container runtime: containerd
- CNI: Calico for kubeadm; engine defaults for kind/minikube
- Sizing: profiles `small|medium|large` mapped to CPU/RAM; medium default
- Networking: Pod `10.244.0.0/16`, Service `10.96.0.0/12` for kubeadm unless YAML overrides
- Naming: `clu-<module>-<type>-<engine>`; namespaces `ns-<module>-<type>`
- Tooling: immutable set per engine; extras via bootstrap hooks

These defaults are designed to “just work” in classroom/dev laptops while leaving room to extend via YAML only when necessary.

### Management Machine Pattern

- Purpose: Provide a single entry point (management machine) with kubeconfig context switching to manage multiple isolated clusters.
- Behavior:
  - Generates a dedicated management VM (`mgmt`) with SSH access to cluster VMs
  - Aggregates and merges kubeconfigs into `~/.kube/config` on `mgmt` and exports a contexts index
  - Exposes helper scripts: `kctx <name>`, `kubens <ns>`, `merge-kubeconfigs.sh`
  - Optionally installs Azure CLI when `--azure` is used to align local and AKS operations

YAML specs control the number of clusters, their interconnect, and any non-default networking; the management machine is the orchestrator and operational control plane for the lab.

### Terminology: Management Machine (mgmt)

Definition: A VM providing operational control for Kubernetes environments.

Modes:
- Standalone: Tools-only VM (no local cluster). Engine: `none` (CLI `mgmt`).
- Orchestrator: Aggregates kubeconfigs for multi-cluster labs; provides context switching and SSH access.

Naming Guidelines:
- Use “management machine” in user docs; use `mgmt` in code/CLI.
- Avoid synonyms like “jumpbox” or “bastion” in normative sections.

## Consolidated Interaction Model

### The Taxonomy

**Base Model × Engine × Modifiers**

Replaces the previous "7 scenarios" with a compact, normalized taxonomy:

#### Base Models (Mutually Exclusive)

1. **Management Machine**: Single VM with CLI tooling; optional Azure
2. **Single-Node**: One VM running a local cluster (minikube or kind)
3. **Multi-Node**: Kubeadm cluster (1+ masters, N workers)
4. **Multi-Cluster Orchestrator**: Multiple kubeadm clusters with a management machine (mandatory when clusters are isolated); optional cloud tools

#### Engines (Plug into Base Model)

- **none** → Management Machine
- **minikube** → Single-Node
- **kind** → Single-Node
- **kubeadm** → Multi-Node / Multi-Cluster

#### Modifiers (Orthogonal)

- **azure**: Installs Azure CLI, persists `/etc/azure-env`, adds AKS workflows
- **size**: `small|medium|large` profile presets

### Mapping Old Scenarios → New Model

| Old Scenario | New Model |
|--------------|-----------|
| Local minikube | Single-Node + engine=minikube |
| Local kind | Single-Node + engine=kind |
| Multi-node kubeadm | Multi-Node + engine=kubeadm |
| AKS management VM | Management Machine + azure |
| Management-only | Management Machine |
| Parallel kubeadm+AKS | Multi-Node + azure |
| Multi-cluster with management | Multi-Cluster Orchestrator + management section |

**Benefits**:
- Removes scenario duplication
- Centralizes policy enforcement
- Keeps documentation succinct
- Simplifies implementation

---

## Implementation Notes (Non‑Normative)

### Reference Stack (suggested)

- Language/Build: Java 25 + Maven
- CLI: Picocli
- Templates: JTE
- YAML: Jackson YAML
- Testing: JUnit 5, AssertJ

Implementations MAY use any stack that conforms to the CLI behavior and outputs defined by this specification.

### Key Dependencies (reference)

```xml
<!-- CLI framework -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
    <version>4.7.5</version>
</dependency>

<!-- Template engine -->
<dependency>
    <groupId>gg.jte</groupId>
    <artifactId>jte</artifactId>
    <version>3.1.9</version>
</dependency>

<!-- IP/CIDR handling -->
<dependency>
    <groupId>com.github.seancfoley</groupId>
    <artifactId>ipaddress</artifactId>
    <version>5.4.0</version>
</dependency>

<!-- YAML parsing -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
    <version>2.15.2</version>
</dependency>

<!-- Testing -->
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.10.0</version>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.24.2</version>
</dependency>
```

### Package Structure

```
ai_working/202511-kubernetes/k8s-generator/
├── pom.xml
├── src/main/java/com/k8s/generator/
│   ├── cli/
│   │   └── ScaffoldCommand.java          # Picocli entrypoint
│   ├── model/
│   │   ├── Module.java                   # records/enums
│   │   ├── Cluster.java
│   │   ├── Node.java
│   │   ├── ClusterType.java
│   │   ├── SizeProfile.java
│   │   └── Tools.java
│   ├── validate/
│   │   ├── StructuralValidator.java      # Required fields present
│   │   ├── SemanticValidator.java        # Business rules
│   │   └── PolicyValidator.java          # Tool/engine compatibility
│   ├── ip/
│   │   └── CidrHelper.java               # IPv4 CIDR validation & overlap checks (no auto-allocation)
│   ├── plan/
│   │   └── ScaffoldPlan.java             # Maps inputs → template contexts
│   ├── render/
│   │   ├── JteRenderer.java              # JTE wrapper
│   │   └── ContextBuilder.java           # Builds template contexts
│   ├── fs/
│   │   ├── SpecReader.java               # YAML spec reader
│   │   ├── FsWriter.java                 # File writer
│   │   └── ResourceCopier.java           # Copy install_*.sh scripts
│   └── app/
│       └── ScaffoldService.java          # Orchestrates: read → validate → plan → render → write
└── src/main/resources/templates/
    ├── Vagrantfile.jte                    # VM topology template
    ├── bootstrap.sh.jte                   # Bootstrap template
    └── partials/
        ├── minikube-setup.jte
        ├── kind-setup.jte
        └── kubeadm-setup.jte
```

### Design Principles

**SOLID and Separation of Concerns**:
- Immutable records for domain models
- Pure functions where possible
- Explicit contracts between layers
- No global mutable state
- Fail-fast validations with clear messages

**Type Safety**:
```java
public record Node(
    String name,
    InetAddress ip,
    NodeRole role,
    int memoryMb,
    int cpus
) {
    public Node {
        if (memoryMb < 1024) {
            throw new IllegalArgumentException("Memory must be >= 1024 MB");
        }
        if (cpus < 1) {
            throw new IllegalArgumentException("CPUs must be >= 1");
        }
    }
}

public enum NodeRole {
    MASTER, WORKER, BASTION
}
```

---

## CLI Specification

### CLI Overview (Cheat Sheet)

- Commands
  - Canonical: `k8s-gen --module <mN> --type <type> <cluster-type> [modifiers]`
  - Positional: `k8s-gen <mN> <type> <cluster-type> [modifiers]`
  - Future (opt-in): `k8s-gen provision|verify|destroy` (automation, not default)
  - Discovery (future): `k8s-gen --list-engines`
- Required
  - `--module <mN>` (e.g., m1, m7) and `--type <type>` (e.g., pt, hw, exam, exam-prep)
  - `<cluster-type>`: one of `mgmt|minikube|kind|kubeadm`
- Engines
  - `mgmt` (engine `none`): management machine (no local cluster)
  - `minikube`, `kind`: single-node local clusters
  - `kubeadm`: multi-node (and multi-cluster via YAML)
- Modifiers
  - `--size small|medium|large`, `--nodes 1m,2w` (kubeadm), `--azure`, `--out`, `--dry-run`
- Naming & Paths
  - Cluster: `clu-<module>-<type>-<engine>`; Namespace: `ns-<module>-<type>`; Output dir: `<type>-<module>/`
  - Collision policy: If the inferred output directory already exists, generation fails. Use `--out <dir>` to generate into a different path.
- Spec-Gate
  - Use CLI for simple cases; YAML only for complex topologies
  - Error: `Spec-gate: YAML only for complex topologies. Use CLI for simple clusters.`

### Terminology: Cluster Type vs Engine

- User-facing term: `cluster-type`
  - The positional/required CLI argument selecting the cluster technology: `kind|minikube|kubeadm|mgmt`.
  - Used in CLI help and user documentation.
- Internal term: `engine`
  - Implementation bound to a `cluster-type` (Engine interface with `id()` matching the CLI value).
  - EngineRegistry maps the CLI `cluster-type` string to an Engine implementation.
- Relationship
  - The CLI `cluster-type` directly selects an Engine. Use “cluster-type” in user-facing docs; use “engine” in internal architecture and code.

### Convention-Over-Configuration Approach

The CLI provides two paths:
- **Simple path (80%)**: Direct CLI commands with smart defaults
- **Complex path (20%)**: YAML specs for genuine complexity

### Simple Path: CLI-First (Zero Configuration)

**Command Structure**:
```bash
# Canonical (separate flags)
k8s-gen --module <mN> --type <type> <cluster-type> [modifiers]

# Positional (separate tokens) — optional convenience
k8s-gen <mN> <type> <cluster-type> [modifiers]
```

**Arguments**:
- `--module <mN>`: Module number (e.g., `m1`, `m7`) — kept separate
- `--type <type>`: Taxonomy label (e.g., `pt|hw|exam|exam-prep|lab|demo`) — kept separate
- `<cluster-type>`: `kind|minikube|kubeadm|mgmt` (explicit; no coupling with type)

**Modifiers**:
- `--size small|medium|large` (default: medium)
- `--nodes N` (kubeadm: total nodes, infers 1m + N-1w)
- `--nodes NmMw` (explicit: `1m,2w` = 1 master, 2 workers)
- `--azure` (adds Azure CLI + /etc/azure-env)
- `--out <dir>` (default: inferred from module context)
- `--dry-run` (show plan without generating)

**Examples**:
```bash
# Simple examples
k8s-gen --module m1 --type pt kind
k8s-gen --module m2 --type exam-prep minikube --size large
k8s-gen --module m7 --type hw kubeadm --azure --nodes 1m,2w

# 3-node kubeadm (1 master + 2 workers)
k8s-gen --module m7 --type exam kubeadm --nodes 1m,2w

# Management with Azure CLI on the management machine
k8s-gen --module m9 --type lab mgmt --azure
```

### Complex Path: YAML Specs (Genuine Complexity)

**Command Structure**:
```bash
k8s-gen --spec <yaml> --out <dir> [--size <profile>] [--dry-run]
```

**When to use YAML**:
- Multi-cluster topologies with a management machine
- Custom networking (specific CIDRs)
- Per-node resource customization
- Complex tool matrices across clusters

Spec-gate examples (accepted):

- Two independent kubeadm clusters (prod, staging) with a management machine and distinct Pod/Service CIDRs; prod has 2 masters, 5 workers; staging 1 master, 1 worker.
- Three clusters with routed interconnect (Calico VXLAN to BGP), shared artifact registry on mgmt, and per-role VM shapes.

**Example**:
```bash
k8s-gen --spec multi-cluster.yaml --out ./infra/
```

---

## Convention-Over-Configuration Rules

### Auto-Detected Defaults



**Module and Type Semantics**:
```bash
# Module and type are separate, explicit inputs
m1 pt          → Output dir: ./pt-m1/
m7 hw          → Output dir: ./hw-m7/
m3 exam-prep   → Output dir: ./exam-prep-m3/
```

- `--module` is a course scoping identifier (`m1`, `m7`, ...)
- `--type` is an extensible taxonomy label: `pt|hw|exam|exam-prep|lab|demo|…`
- Engine selection is independent of `--type` and must be provided explicitly
- Naming uses both for uniqueness and clarity: `clu-<module>-<type>-<engine>`, `ns-<module>-<type>`
- Module + type together form the unique workspace identifier; default output directory is `<type>-<module>/`.

**Output Directory Collision Handling**:

- The generator FAILS if the inferred output directory already exists.
- Use `--out <dir>` to generate into a different directory, or choose a different `--type` to create a separate workspace for the same module.
- Rationale: Treat module+type as a composite key to avoid accidental overwrites and keep course workspaces predictable.

**Example**:
```bash
# First run (OK)
k8s-gen --module m1 --type pt kind     # → ./pt-m1/

# Second run with a different cluster-type but same module+type (ERROR)
k8s-gen --module m1 --type pt minikube # → ERROR: output directory ./pt-m1 already exists

# Use explicit output directory to keep both
k8s-gen --module m1 --type pt minikube --out ./pt-m1-minikube/
```

### Type Taxonomy and Validation

- Allowed pattern: lowercase alphanumerics and dashes, start with a letter: `[a-z][a-z0-9-]*`
- Examples: `pt`, `hw`, `exam`, `exam-prep`, `lab`, `demo`
- Validation: reject unknown characters; do not restrict to a fixed set
- Semantics: `--type` scopes output directories, names, and documentation context only; it never selects or implies a cluster engine

**Cluster Type Defaults**:

| Cluster Type | Default Tools | VM Sizing (medium) | Networks |
|--------------|---------------|-------------------|----------|
| **kind** | kubectl, docker, kind | 4 CPU, 8192 MB | 10.244.0.0/16, 10.96.0.0/12 |
| **minikube** | kubectl, docker, minikube | 4 CPU, 8192 MB | Minikube defaults |
| **kubeadm** | kubectl, containerd, kube_binaries | 4 CPU, 8192 MB per node | 10.244.0.0/16, 10.96.0.0/12 |
| **mgmt** | kubectl | 2 CPU, 4096 MB | N/A (no cluster) |

**Environment Variables** (inferred):
```bash
CLUSTER_NAME="clu-<module>-<type>-<engine>"  # e.g., clu-m1-pt-kind
NAMESPACE_DEFAULT="ns-<module>-<type>"       # e.g., ns-m1-pt
```

### Decision Tree: CLI vs YAML

```
Need to generate Kubernetes environment?
│
├─ Single cluster with standard configuration?
│  └─ USE CLI
│     ✓ Kind for module practice
│     ✓ Minikube for homework
│     ✓ Kubeadm with default networks
│     ✓ 1-3 node clusters
│
├─ Custom networking (non-default CIDRs)?
│  └─ USE YAML (complex)
│
├─ Multi-cluster topology?
│  └─ USE YAML (complex)
│
├─ Per-node resource customization?
│  └─ USE YAML (complex)
│
└─ Want it to work with sensible defaults?
   └─ USE CLI (this is 80% of cases)
```

### Visual Decision Tree

```
                 +--------------------------------------+
                 | Need to generate K8s environment?    |
                 +-------------------------+------------+
                                           |
                                           v
              +----------------------------+-----------------------------+
              | Single cluster with standard configuration (1–3 nodes)?  |
              +----------------------+-----------------------------------+
                                     |Yes                                |No
                                     v                                   v
                            +--------+---------------+ +-----------------+------------------+
                            |            USE CLI     | |  Any of these?                     |
                            |  kind|minikube|kubeadm | |  - Custom networking (CIDRs)       |
                            |  with defaults         | |  - Multi-cluster topology          |
                            +------------------------+ |  - Per-node resource shaping       |
                                                       +-----------------+------------------+
                                                                         |Yes               |No
                                                                         v                  v
                                                                +--------+------+   +------+---------+
                                                                |    USE YAML   |   |     USE CLI    |
                                                                |  Provide spec |   |  with defaults |
                                                                +--------------- +   +---------------+
```

### Style and Naming

- Section headers use Title Case; CLI tokens and YAML fields are lowercase.
- Canonical terms: “management machine” (mgmt), “cluster-type” (CLI-facing), “engine” (internal).
- Avoid synonyms in normative text: do not use “jumpbox”, “bastion”, or “workstation”.

**Rule of Thumb**: If defaults work or a single modifier fixes it → CLI. If you're saying "but I need..." more than once → YAML.

---

## Input Specification (YAML)

### Spec Schema

Use YAML only for complex scenarios. The schema below summarizes supported fields with required/optional markers (external clusters marked as future):

```yaml
# REQUIRED schema version (for future evolution)
version: "1.0.0"

# REQUIRED module metadata
module:
  num: mN          # REQUIRED (e.g., m1, m7)
  type: <type>     # REQUIRED (e.g., pt, hw, exam, exam-prep)

# OPTIONAL management (mgmt)
management:
  name: mgmt                           # OPTIONAL (default: mgmt)
  tools: [kubectl, kustomize, helm]    # OPTIONAL (mgmt-only extras; clusters use engine-immutable tool sets; names match `[a-z][a-z0-9_]*`)
  aggregate_kubeconfigs: true          # OPTIONAL (default: true)
  providers: [azure]                   # OPTIONAL (installs provider CLIs on mgmt; does not create cloud clusters)

# OPTIONAL global defaults (applied unless overridden per-cluster)
defaults:
  box_image: "ubuntu/jammy64"          # OPTIONAL (default vagrant box for all VMs)

# Notes
# - `module.num` in YAML corresponds to CLI `--module` (e.g., m7). The field name remains
#   `num` to emphasize the `mN` convention used across course modules and keeps YAML concise.

# REQUIRED clusters list (at least one, unless management-only scenario)
clusters:
  - name: <cluster-name>               # REQUIRED
    cluster_type: kubeadm|kind|minikube  # REQUIRED (engine selection)

    # kubeadm-only fields (REQUIRED where noted)
    cni: calico|flannel|weave|cilium|antrea  # REQUIRED for kubeadm; IGNORED for kind/minikube
    nodes: { masters: 1, workers: 2 }        # REQUIRED for kubeadm
    pod_network: 10.244.0.0/16               # OPTIONAL (default: 10.244.0.0/16)
    svc_network: 10.96.0.0/12                # OPTIONAL (default: 10.96.0.0/12)

    # IP allocation
    first_ip: 192.168.56.111           # REQUIRED for multi-cluster; OPTIONAL for single-cluster

    # VM overrides (OPTIONAL)
    vm_overrides:
      master: { cpus: 2, memory_mb: 4096 }   # OPTIONAL
      worker: { cpus: 4, memory_mb: 8192 }   # OPTIONAL

    # Note: No 'tools' field for clusters — engine determines required tools

    # External cloud clusters (future)
    # external: true
    # provider: azure|aws|gcp
    # resource_group: rg-k8s-mN        # azure example
    # aks_name: aks-mN-staging         # azure example
    # get_credentials: true            # scaffold helper to merge kubeconfig on mgmt
```

Notes:
- Local kubeadm clusters require explicit `nodes` and support CNI and network fields.
- Presence of `management` implies a management VM; no separate `bastion` or `jumpbox` boolean is required.
- External cloud clusters are a future extension (documented here for planning; not implemented yet).

### Minimum Viable Examples

```yaml
# Minimum single-cluster kind
version: "1.0.0"
module: { num: m1, type: pt }
clusters:
  - name: dev
    cluster_type: kind
```

```yaml
# Minimum kubeadm
version: "1.0.0"
module: { num: m7, type: hw }
clusters:
  - name: lab
    cluster_type: kubeadm
    cni: calico
    nodes: { masters: 1, workers: 2 }
```

```yaml
# Minimum multi-cluster (requires first_ip)
version: "1.0.0"
module: { num: m9, type: hw }
management:
  name: mgmt
clusters:
  - name: sofia
    cluster_type: kubeadm
    cni: calico
    first_ip: 192.168.56.111
    nodes: { masters: 1, workers: 1 }
  - name: plovdiv
    cluster_type: kubeadm
    cni: calico
    first_ip: 192.168.56.121
    nodes: { masters: 1, workers: 1 }
```

### Kind Single-Node Example

```yaml
module:
  num: m1
  type: pt

clusters:
  - name: dev
    cluster_type: kind
    vm:
      cpus: 2
      memory_mb: 4096
    # Tools determined by engine: kubectl, docker, kind
```

### Kubeadm Multi-Node Example

```yaml
module:
  num: m7
  type: hw

clusters:
  - name: kubeadm-lab
    cluster_type: kubeadm
    cni: calico
    pod_network: 10.20.0.0/16
    svc_network: 10.21.0.0/16
    nodes:
      masters: 1
      workers: 2
    vm_overrides:
      master: { cpus: 2, memory_mb: 4096 }
      worker: { cpus: 2, memory_mb: 4096 }
    # Tools determined by engine: kubectl, containerd, kubelet, kubeadm, calico
```

### Multi-Cluster with Management (Jumpbox) Example

```yaml
module:
  num: m9
  type: hw


clusters:
  - name: sofia
    cluster_type: kubeadm
    cni: weave
    first_ip: 192.168.56.111
    nodes:
      masters: 1
      workers: 1

  - name: plovdiv
    cluster_type: kubeadm
    cni: antrea
    first_ip: 192.168.56.121
    nodes:
      masters: 1
      workers: 1

  - name: varna
    cluster_type: kubeadm
    cni: flannel
    first_ip: 192.168.56.131
    nodes:
      masters: 1
      workers: 1

management:
  name: mgmt
  tools: [kubectl, kustomize, helm]
  aggregate_kubeconfigs: true
```

### Future: Mixed Local + Cloud (External) Example

This shape is not implemented yet; it documents the intended future extension for clarity.

```yaml
module: { num: m9, type: exam-prep }

management:
  name: mgmt
  tools: [kubectl, kustomize, helm]
  aggregate_kubeconfigs: true
  # providers: [azure]   # installs CLIs only

clusters:
  - name: prod-local
    cluster_type: kubeadm
    cni: calico
    nodes: { masters: 1, workers: 2 }

  - name: aks-staging
    external: true           # future
    provider: azure          # future
    resource_group: rg-k8s-m9
    aks_name: aks-m9-staging
    get_credentials: true    # scaffold helper to merge kubeconfig on mgmt
```
```

---

## Sizing Profiles

### Definitions

| Profile | CPUs | Memory | Use Case |
|---------|------|--------|----------|
| **small** | 2 | 4096 MB | Basic learning, limited resources |
| **medium** | 4 | 8192 MB | Standard scenarios, comfortable margin |
| **large** | 6 | 12288 MB | Complex scenarios, multi-cluster labs |

### Application

**Single-Node Engines** (minikube, kind):
- Profile applies directly to the single VM

**Multi-Node Kubeadm**:
- Per-role defaults derived from profile
- Role overrides via `vm_overrides` in spec

**Example**:
```bash
# All nodes get medium sizing (4 CPU, 8192 MB)
k8s-gen scaffold --spec m7.yaml --out m7/ --size medium

# Spec can override per-role:
vm_overrides:
  master: { cpus: 2, memory_mb: 4096 }
  worker: { cpus: 4, memory_mb: 8192 }
```

### Vagrant Box Images

**Default**: `ubuntu/jammy64` (Ubuntu 22.04 LTS)

**Sizing profiles include box**:
```yaml
small:
  cpus: 2
  memory_mb: 4096
  box_image: "ubuntu/jammy64"

medium:
  cpus: 4
  memory_mb: 8192
  box_image: "ubuntu/jammy64"

large:
  cpus: 6
  memory_mb: 12288
  box_image: "ubuntu/jammy64"
```

**Override in YAML (per-cluster)**:
```yaml
clusters:
  - name: dev
    cluster_type: kind
    vm_overrides:
      box_image: "ubuntu/focal64"  # Override to Ubuntu 20.04
```

**Global override** (all VMs use same box):
```yaml
defaults:
  box_image: "ubuntu/focal64"
```

**Validation**:
- Warn (do not error) when `box_image` does not start with `ubuntu/`.
- Generators may add provider-specific notes if a chosen box is known to be incompatible.

---

## IP Address Allocation

### Strategy Overview

The generator uses a **two-mode approach** optimized for the 80/20 principle:

| Mode | When | IP Specification | Rationale |
|------|------|------------------|-----------|
| **Single-Cluster** | One cluster only | Optional (defaults to 192.168.56.10) | Zero-config convenience for 80% case |
| **Multi-Cluster** | Two or more clusters | **Required** for each cluster | Prevent collisions through explicitness |

### Default IP Allocation (Single-Cluster)

**Default base IP**: `192.168.56.10`

```bash
# Single-cluster kind (zero config)
k8s-gen --module m1 --type pt kind
# → VM gets IP: 192.168.56.10

# Single-cluster kubeadm (1 master + 2 workers)
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w
# → master-1: 192.168.56.10
# → worker-1: 192.168.56.11
# → worker-2: 192.168.56.12
```

**Override**: Users can override the default via YAML:
```yaml
clusters:
  - name: dev
    cluster_type: kind
    first_ip: 192.168.56.100  # Custom IP
```

### Multi-Cluster IP Allocation

**Rule**: Every cluster in a multi-cluster topology **MUST** specify `first_ip`.

**Validation**: Generator fails if any cluster in a multi-cluster configuration omits `first_ip`.

```yaml
# VALID multi-cluster configuration
clusters:
  - name: sofia
    first_ip: 192.168.56.20  # REQUIRED
    cluster_type: kubeadm
    nodes: { masters: 1, workers: 1 }

  - name: plovdiv
    first_ip: 192.168.56.30  # REQUIRED
    cluster_type: kubeadm
    nodes: { masters: 1, workers: 1 }
```

**Error Example**:
```
[Structural] Multi-cluster configuration requires explicit first_ip for each cluster
  → Cluster 'plovdiv' is missing first_ip
  → Add: first_ip: 192.168.56.30 (or another non-overlapping IP)
```

### Sequential Allocation Within Cluster

IPs are allocated sequentially from `first_ip`:

**Allocation Order**:
1. Master nodes first (in order)
2. Worker nodes next (in order)

**Algorithm**:
```
IP[0] = first_ip           # First master
IP[1] = first_ip + 1       # Second master (if exists)
IP[n] = first_ip + n       # First worker (after all masters)
IP[n+1] = first_ip + n + 1 # Second worker
...
```

**Example** (3-node kubeadm cluster):
```yaml
clusters:
  - name: lab
    first_ip: 192.168.56.20
    nodes: { masters: 1, workers: 2 }

# Allocated IPs:
# - lab-master-1: 192.168.56.20  (first_ip + 0)
# - lab-worker-1: 192.168.56.21  (first_ip + 1)
# - lab-worker-2: 192.168.56.22  (first_ip + 2)
```

### Management VM IP

**Reserved IP**: `192.168.56.5`

When a management VM is present, it uses a reserved IP below the default cluster range:

```yaml
management:
  name: mgmt  # Gets IP: 192.168.56.5

clusters:
  - name: cluster1
    first_ip: 192.168.56.20  # Separated from management
```

**Rationale**:
- Clearly separated from cluster IPs (below default .10 range)
- Consistent and predictable location
- Avoids collision with default single-cluster allocation

### Validation Rules

The generator enforces these IP validation rules:

**V1: No IP Overlap Between Clusters**

```
[Semantic] IP collision: 192.168.56.21 used by multiple clusters
  → Cluster 'sofia' uses: 192.168.56.20-22 (1 master, 2 workers)
  → Cluster 'plovdiv' starts at: 192.168.56.21
  → Suggestion: Use first_ip: 192.168.56.30 for 'plovdiv'
```

**V2: Subnet Boundary Check**

```
[Semantic] IP range 192.168.56.250 + 10 nodes exceeds subnet boundary (192.168.56.254)
  → Maximum nodes with this first_ip: 4
  → Suggestion: Use first_ip: 192.168.56.100 or reduce node count
```

**V3: Reserved IP Check**

```
[Semantic] IP 192.168.56.1 is reserved (VirtualBox host)
  → Reserved IPs: 192.168.56.1, 192.168.56.2
  → Suggestion: Use first_ip: 192.168.56.10 or higher
```

**Reserved IPs**:
- `192.168.56.1`: VirtualBox host interface
- `192.168.56.2`: Potential gateway
- `192.168.56.5`: Management VM (when present)

### IP Allocation Examples

**Example 1: Default Single-Cluster**
```bash
k8s-gen --module m1 --type pt kind
```
**Result**: One VM at `192.168.56.10` (default)

**Example 2: Multi-Node Single-Cluster**
```bash
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w
```
**Result**:
- `m7-hw-master-1`: `192.168.56.10`
- `m7-hw-worker-1`: `192.168.56.11`
- `m7-hw-worker-2`: `192.168.56.12`

**Example 3: Multi-Cluster with Spacing**
```yaml
clusters:
  - name: sofia
    first_ip: 192.168.56.20
    nodes: { masters: 1, workers: 1 }  # Uses .20, .21

  - name: plovdiv
    first_ip: 192.168.56.30
    nodes: { masters: 1, workers: 1 }  # Uses .30, .31

  - name: varna
    first_ip: 192.168.56.40
    nodes: { masters: 1, workers: 1 }  # Uses .40, .41
```
**Result**: No overlap, validation passes ✅

**Example 4: Adjacent Clusters (Valid)**
```yaml
clusters:
  - name: cluster1
    first_ip: 192.168.56.20
    nodes: { masters: 1, workers: 2 }  # Uses .20-.22

  - name: cluster2
    first_ip: 192.168.56.23  # Starts immediately after cluster1
    nodes: { masters: 1, workers: 1 }  # Uses .23-.24
```
**Result**: Adjacent but non-overlapping, validation passes ✅

**Example 5: With Management VM**
```yaml
management:
  name: mgmt  # IP: 192.168.56.5

clusters:
  - name: prod
    first_ip: 192.168.56.20
    nodes: { masters: 2, workers: 3 }  # Uses .20-.24
```
**Result**: Management VM at .5, cluster starts at .20 ✅

### Multi-Cluster Network Separation (Future)

**Scope (v1)**:
- All VMs attach to a single host-only subnet (`192.168.56.0/24`).
- Per-cluster Pod/Service CIDRs are distinct by default, but VM NICs share the same L2 network.

**Out of Scope (v1)**:
- Per-cluster host network separation (multiple host-only subnets).
- Multi-homing the management VM across multiple subnets.

**Future Direction** (non-normative):
- Add optional `host_network_cidr` per cluster (must contain `first_ip`).
- Reserve `.5` per subnet for management when present.
- Extend validations to ensure `first_ip ∈ host_network_cidr`, no overlap within each subnet, and range within subnet boundaries.

**Rationale**: Preserve "no magic" and keep v1 simple. Users can still isolate clusters logically via Pod/Service CIDRs without altering the host network topology.

### Implementation Notes

**IP Parsing and Validation**:
Uses `com.github.seancfoley:ipaddress:5.4.0` library for:
- IPv4 format validation
- IP arithmetic (incrementing)
- Range overlap detection
- Subnet boundary checks

**Allocation Algorithm** (Java):
```java
public List<VmConfig> allocateIps(ClusterSpec cluster, boolean isMultiCluster) {
    // Determine first_ip
    String firstIpStr = cluster.firstIp().orElse(
        isMultiCluster
            ? throw new ValidationException("first_ip required for multi-cluster")
            : "192.168.56.10"  // Default for single-cluster
    );

    IPv4Address firstIp = new IPv4AddressString(firstIpStr).getAddress();
    List<VmConfig> vms = new ArrayList<>();
    int offset = 0;

    // Masters first
    for (int i = 0; i < cluster.masterCount(); i++) {
        vms.add(new VmConfig(
            cluster.name() + "-master-" + (i + 1),
            firstIp.increment(offset).toString(),
            cluster.masterCpus(),
            cluster.masterMemory(),
            cluster.boxImage()
        ));
        offset++;
    }

    // Workers next
    for (int i = 0; i < cluster.workerCount(); i++) {
        vms.add(new VmConfig(
            cluster.name() + "-worker-" + (i + 1),
            firstIp.increment(offset).toString(),
            cluster.workerCpus(),
            cluster.workerMemory(),
            cluster.boxImage()
        ));
        offset++;
    }

    return vms;
}
```

### Philosophy Alignment

✅ **Explicit over implicit**: Multi-cluster requires explicit `first_ip`
✅ **Sensible defaults**: Single-cluster gets `192.168.56.10` automatically
✅ **Fail-fast validation**: Detects collisions before VM creation
✅ **No magic**: Sequential allocation, no CIDR math or auto-spacing
✅ **Manual-first**: Users see and control all IPs in YAML
✅ **Convention over configuration**: Default works for 80% case

---

## Tool Management

### Design Philosophy: Engine-Immutable Tool Sets

Each cluster engine defines **exactly** the tools it requires. This immutable contract eliminates configuration complexity and prevents invalid tool combinations.

**Benefits**:
- ✅ Zero invalid configurations (impossible to specify incompatible tools)
- ✅ Clear engine contracts (each engine owns its dependencies)
- ✅ No validation logic needed for tool compatibility
- ✅ 95% of users get the right tools automatically

### Required Tools by Engine

| Engine | Required Tools | Purpose |
|--------|---------------|---------|
| **kind** | kubectl, docker, kind | Docker-in-Docker cluster runtime |
| **minikube** | kubectl, docker, minikube | VM-based single-node cluster |
| **kubeadm** | kubectl, containerd, kubelet, kubeadm, [CNI] | Multi-node cluster bootstrap |
| **mgmt** (none) | kubectl | Management machine (no local cluster) |

**CNI Options** (kubeadm only):
- `calico` (default), `flannel`, `weave`, `cilium`, `antrea`

### Optional Tools (Via Bootstrap Hooks)

For tools beyond the required set (helm, kustomize, k9s, stern, etc.), use bootstrap hooks:

**Method 1: Post-Bootstrap Script**
```bash
# scripts/bootstrap.post.d/50-install-helm.sh
#!/bin/bash
curl -fsSL https://get.helm.sh/helm-v3.14.0-linux-amd64.tar.gz | \
  tar xz -C /usr/local/bin --strip-components=1 linux-amd64/helm
```

**Method 2: Local Override File**
```bash
# scripts/bootstrap.env.local (not tracked)
INSTALL_HELM=1
INSTALL_K9S=1
```

### Skipping Pre-Installed Tools

If tools are already present (e.g., Docker from a previous setup), skip reinstallation:

```bash
export SKIP_TOOLS="docker,containerd"
k8s-gen --module m1 --type pt kind

# Generator verifies tool presence; skips if functional
```

**Environment Variable**:
```bash
SKIP_TOOLS="docker,helm"  # Comma-separated list
```

**Bootstrap Behavior**:
- Checks if tool exists with `command -v <tool>`
- Verifies tool is functional (e.g., `docker ps`, `kubectl version --client`)
- Skips installation if both checks pass
- Logs skipped tools for visibility

### Generator Output

The generator displays installed/skipped tools for transparency:

```
✓ Cluster engine: kind
✓ Required tools:
  - kubectl (1.30.2)
  - docker (24.0.7) [skipped: already installed]
  - kind (0.20.0)
✓ Bootstrap hooks: scripts/bootstrap.post.d/ (for additional tools)
ℹ Add extra tools via bootstrap.post.d/*.sh hooks
```

### Policy Enforcement

**Forbidden Combinations** (enforced at validation):
- Cluster engines on mgmt-only nodes (e.g., no minikube/kind on `mgmt`)
- Conflicting engines on same node (e.g., no minikube + kind together)
- Heavy local tools on AKS-only setups (e.g., no kubeadm when using only AKS)

**Validation Errors**:
```
[Policy] Cannot install 'kind' on management-only node
  → Management nodes (engine=none) support only kubectl and cloud CLIs
  → For local clusters, use engine=kind|minikube|kubeadm

[Policy] Conflicting engines: minikube + kind on same node
  → Choose one engine per node
```

### Design Decision: Removal of `--tools` Flag

**Removed**: The `--tools <csv>` CLI flag and YAML `tools` list override capability.

**Rationale**:
- Each engine has one canonical, immutable tool set
- 95% of users never needed tool overrides
- The 5% with special needs use bootstrap hooks (`bootstrap.post.d/*.sh`)
- Eliminates entire classes of invalid configurations
- Makes the system impossible to misconfigure

**Migration Path**:
- **For additional tools**: Use `scripts/bootstrap.post.d/install-helm.sh` hooks
- **For skipping tools**: Set `SKIP_TOOLS="docker,helm"` environment variable
- **For tool version control**: Override via `Vagrantfile.local.rb` or `bootstrap.env.local`

**Trade-offs**:

| Lost Capability | Gained Benefit | Solution |
|----------------|----------------|----------|
| One fewer CLI flag | Zero invalid configurations | Bootstrap hooks cover all real needs |
| Quick tool override for debug | Impossible to break dependencies | SKIP_TOOLS handles the 5% use case |
| Tool swapping flexibility | Clear engine contracts | Well-defined extension points |

**Example**: Before removing `--tools`, a user could specify `--tools kubectl,minikube,kind` which would fail at runtime. Now, the engine contract makes this configuration **impossible to express**, preventing the error entirely.

---

## Environment Files Strategy

### File Roles

| File | Purpose | When Present |
|------|---------|--------------|
| `/etc/k8s-env` | Cluster-scoped non-secret variables | Always when Kubernetes in scope |
| `/etc/azure-env` | Cloud-scoped Azure configuration | Only when Azure modifier active |
| `/etc/profile.d/50-k8s-env.sh` | Auto-sourcing hook for k8s-env | Always when Kubernetes in scope |
| `/etc/profile.d/50-azure-env.sh` | Auto-sourcing hook for azure-env | Only when Azure active |
| `scripts/bootstrap.env.local` | Untracked local overrides | Always scaffolded (template) |

### Variables

**Kubernetes** (`/etc/k8s-env`):
```bash
export CLUSTER_NAME="clu-m7-core"
export CLUSTER_TYPE="kubeadm"
export K8S_VERSION="1.30.2"
export K8S_POD_CIDR="10.244.0.0/16"
export K8S_SVC_CIDR="10.96.0.0/12"
export CNI_TYPE="calico"
export NAMESPACE_DEFAULT="ns-m7-labs"
export NODE_ROLE="master"
export KUBE_MASTER_IP="192.168.56.10"
export KUBE_API_PORT="6443"
```

**Azure** (`/etc/azure-env`):
```bash
export AZ_LOCATION="westeurope"
export AZ_RESOURCE_GROUP="rg-k8s-m7"
export AKS_NAME="aks-m7-core"
export AZ_SUBSCRIPTION_ID=""
export ACR_NAME=""
```

#### Azure Usage Guidance

- Scope: Azure variables are used only when the Azure provider is requested (e.g., CLI flag `--azure` or `management.providers: [azure]`).
- Generation: `/etc/azure-env` is created with defaults/placeholders; sensitive values (subscription ID, ACR name) are left empty by design.
- User responsibilities:
  - Populate Azure values in `scripts/bootstrap.env.local` before running any Azure workflows.
  - Keep `bootstrap.env.local` untracked; it is already added to `.gitignore` by the generator.
- Example `scripts/bootstrap.env.local`:
  ```bash
  # Azure settings (if using Azure workflows)
  export AZ_SUBSCRIPTION_ID="<your-subscription-id>"
  export AZ_RESOURCE_GROUP="rg-k8s-m7"
  export AZ_LOCATION="westeurope"
  export AKS_NAME="aks-m7-core"
  export ACR_NAME="acrsoftuni001"   # optional
  ```
- What breaks if empty:
  - `az login` works (interactive device flow), but subsequent AKS operations may fail.
  - `az account set --subscription $AZ_SUBSCRIPTION_ID` fails if `AZ_SUBSCRIPTION_ID` is empty.
  - `az aks get-credentials -g $AZ_RESOURCE_GROUP -n $AKS_NAME` fails if `AZ_RESOURCE_GROUP`/`AKS_NAME` are empty.
- Validation behavior:
  - The generator does not error on empty Azure vars (non-blocking), but bootstrap logs WARN messages when Azure workflows are invoked without required values.
  - Recommendation messages point users to `scripts/bootstrap.env.local` to populate missing fields.

### Precedence (Provision-Time)

1. `scripts/bootstrap.env.local` (local overrides, highest priority)
2. Vagrant `shell.env` (generator-provided defaults)
3. Bootstrap script defaults (last-resort sane defaults)

### Persistence Pattern

```bash
# Bootstrap sources local overrides first
if [ -f "$SCRIPT_DIR/bootstrap.env.local" ]; then
  set -a
  source "$SCRIPT_DIR/bootstrap.env.local"
  set +a
fi

# Apply defaults for any missing variables
: "${CLUSTER_NAME:=clu-m7-core}"
: "${K8S_VERSION:=1.30.2}"

# Write resolved values to /etc/*-env (idempotent)
write_env_files() {
  tmp_k8s="$(mktemp)"
  cat >"$tmp_k8s" <<EOF
export CLUSTER_NAME="$CLUSTER_NAME"
export K8S_VERSION="$K8S_VERSION"
# ... other variables
EOF

  # Only replace if content changed
  if ! cmp -s "$tmp_k8s" /etc/k8s-env 2>/dev/null; then
    install -m 0644 "$tmp_k8s" /etc/k8s-env
  fi
  rm -f "$tmp_k8s"
}

# Ensure profile.d hook exists
cat >/etc/profile.d/50-k8s-env.sh <<'EOF'
[ -f /etc/k8s-env ] && . /etc/k8s-env
[ -f /etc/azure-env ] && . /etc/azure-env
EOF
chmod 0644 /etc/profile.d/50-k8s-env.sh
```

### Session Sourcing

**Login Shells**: `/etc/profile` sources `/etc/profile.d/*.sh` automatically

**Non-Login/Automation Shells**: All automation scripts explicitly source:
```bash
[ -f /etc/k8s-env ] && . /etc/k8s-env
[ -f /etc/azure-env ] && . /etc/azure-env
```

**Azure Toggling**: If Azure disabled on later run, bootstrap removes `/etc/profile.d/50-azure-env.sh` and `/etc/azure-env`.

---

## Validation Strategy

### Three Layers

**1. Structural Validation** (Required fields present):
- Module number and type
- Cluster name and type
- Required fields per cluster type (e.g., `pod_network`/`svc_network` for kubeadm)
- Nodes shape valid (master/worker counts for kubeadm)

**2. Semantic Validation** (Business rules):
- At least one control-plane node for kubeadm
- No pod/svc network overlap
- Valid CIDR formats
- Unique node names/IPs
- Memory >= 1024 MB, CPUs >= 1

**3. Policy Validation** (Tool/engine compatibility):
- Tool compatibility (e.g., no minikube on mgmt-only)
- Engine compatibility (e.g., kubeadm requires specific tools)
- Sizing constraints
- Modifier conflicts

**Execution**:
- All validations run before any file generation
- Aggregate errors into single comprehensive message
- Fail-fast with actionable error messages

### Validation Error Format (Normative)

**Structure**:
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

**Levels**:
- Structural: Missing/invalid required fields, schema violations.
- Semantic: Business rules (IP collisions, subnet bounds, CIDR overlap, invalid node shapes).
- Policy: Engine/tool compatibility, forbidden combinations, sizing/role constraints.

**Exit Codes**:
- 0: Success (generation completed).
- 2: Validation failure (any Structural/Semantic/Policy errors).
- 1: Unexpected internal error (I/O, template compilation, unhandled exceptions).

**Aggregation**:
- Collect and print all validation errors in a single run (do not short-circuit on first error).
- Preserve input order where possible; group by level for readability.

**Example Error**:
```
Validation failed for spec 'm7-kubeadm.yaml':

[Semantic] Pod network 10.20.0.0/16 overlaps with service network 10.20.128.0/17
  → Use non-overlapping CIDRs (e.g., pod: 10.20.0.0/16, svc: 10.21.0.0/16)

[Policy] Conflicting engine tools detected
  → Engine 'kubeadm' requires: kubectl, containerd, kubelet, kubeadm
  → Cannot mix with 'minikube' or 'kind' on the same node
  → Use one engine per node

[Structural] Missing required field 'cni' for kubeadm cluster 'lab'
  → Add: cni: calico|flannel|weave|cilium|antrea
```

---

## Common Errors (Reference)

- Missing first_ip in multi-cluster
  - Message: `[Structural] Multi-cluster configuration requires explicit first_ip for each cluster`
  - Fix: Add `first_ip` to every cluster (e.g., `.20`, `.30`, `.40`)

- Cluster IP overlap
  - Message: `[Semantic] IP collision: 192.168.56.21 used by multiple clusters`
  - Fix: Adjust `first_ip` ranges to be non-overlapping

- Subnet boundary exceeded
  - Message: `[Semantic] IP range 192.168.56.250 + 10 nodes exceeds subnet boundary (192.168.56.254)`
  - Fix: Lower `first_ip` or reduce nodes

- Reserved IP usage
  - Message: `[Semantic] IP 192.168.56.1 is reserved (VirtualBox host)`
  - Fix: Use `.10` or higher; avoid `.1`, `.2`, `.5` (mgmt)

- Pod/Svc CIDR overlap
  - Message: `[Semantic] Pod network <pod> overlaps with service network <svc>`
  - Fix: Choose non-overlapping CIDRs (e.g., `10.244.0.0/16` and `10.96.0.0/12`)

- Kubeadm missing CNI
  - Message: `[Structural] Missing required field 'cni' for kubeadm cluster '<name>'`
  - Fix: Add `cni: calico|flannel|weave|cilium|antrea`

## Engine Extensibility

### Goals

- Add new engines without modifying core orchestration
- Keep CLI stable (`<cluster-type>` accepts any registered engine id)
- Encapsulate engine-specific installers, templates, and validation
- Enable both local engines (e.g., k3s, k3d, microk8s) and provider engines (e.g., aks/eks/gke) when ready

### Engine Contract (Java SPI)

```java
public interface Engine {
  String id();                            // e.g., "kubeadm", "kind", "minikube", "none"
  Set<BaseModel> supports();              // MANAGEMENT, SINGLE_NODE, MULTI_NODE, MULTI_CLUSTER
  List<String> requiredTools();           // immutable set (e.g., ["kubectl", "docker", "kind"])
  List<String> recommendedTools();        // optional (e.g., ["helm", "k9s", "stern"])
  List<String> forbiddenTools();          // conflicts (e.g., minikube forbids ["kind", "kubeadm"])
  void validate(PlanInput input) throws ValidationException; // engine-specific rules
  String vagrantfileTemplateKey();        // template path key for VM topology
  String bootstrapTemplateKey();          // template path key for bootstrap
}
```

Implementation is discovered via `ServiceLoader<Engine>` and registered at startup in `EngineRegistry` keyed by `id()`.

### Registry & Discovery

- `EngineRegistry` loads engines with `ServiceLoader` and exposes:
  - `get(String id)` → Engine
  - `list()` → Collection<Engine>
- CLI validates `<cluster-type>` against `EngineRegistry.list()`.
- `k8s-gen --list-engines` prints available engine ids and a short description.

### Templates & Installers Layout

```
src/main/resources/templates/
  engines/
    kubeadm/
      vagrantfile.jte
      bootstrap/
        master.jte
        worker.jte
    kind/
      vagrantfile.jte
      bootstrap.jte
    minikube/
      vagrantfile.jte
      bootstrap.jte
    none/
      vagrantfile.jte    # management machine
      bootstrap.jte
scripts/install_*.sh      # shared installers; engines reference by name
```

Engines reference installer names (e.g., `install_containerd.sh`), and the generator copies them automatically based on `requiredTools()`.

### Validation & Policy

- Structural validation: required fields present (common layer)
- Semantic validation: engine-specific rules (via `Engine.validate`)
- Policy validation: tool/engine compatibility (centralized)

### Adding New Engines

1) Create implementation `com.k8s.generator.engines.K3sEngine` (or provider-specific like `AksEngine`) implementing `Engine`.
2) Add templates under `templates/engines/<id>/...` as needed.
3) Add `META-INF/services/com.k8s.generator.Engine` with the implementation class for `ServiceLoader`.
4) Optional: extend policy rules if tool compatibility differs.
5) Add unit tests (validator, template golden tests) and a CLI smoke test.

Candidate local engines: `k3s`, `k3d`, `microk8s`.

Provider engines (future): `aks`, `eks`, `gke` — introduce only when provisioning is in scope. Until then, treat cloud clusters as external and manage via the `mgmt` engine (`none`) with provider CLIs.

### Cloud Positioning

- Do not add a generic `cloud` engine. Prefer explicit provider engines when/if provisioning is supported.
- Before provider engines exist, use:
  - Engine `none` for management + `azure|aws|gcp` modifiers to install CLIs
  - YAML entries for external clusters whose kubeconfigs are merged on the mgmt host

---

## Provisioning (Deferred Automation)

### Goals

- Manual-first now: scaffold ephemeral VMs and install tools only
- Automation later: optional, explicit provisioning flows (never default)
- Keep separation of concerns so adding automation does not impact scaffolding

### Phases

- Scaffold: create VMs, install tools, persist env files; no cluster state changes
- Provision (future, opt-in): create/join clusters (e.g., kubeadm init/join, kind create, minikube start, aks create)
- Verify (future, opt-in): quick health checks (e.g., kubectl get nodes, CNI ready)
- Destroy/Reset (future, opt-in): teardown to clean slate (e.g., kubeadm reset, kind delete, minikube delete)

Default mode is scaffold-only. Provision/verify/destroy require explicit invocation and confirmation.

### CLI Shapes (Future)

```bash
# Scaffold remains default; explicit subcommand is a no-op alias
k8s-gen --module m7 --type exam kubeadm --nodes 1m,2w
k8s-gen scaffold --module m7 --type exam kubeadm --nodes 1m,2w

# Opt-in automation (local)
k8s-gen provision --module m7 --type exam kubeadm --nodes 1m,2w --yes
k8s-gen verify   --module m7 --type exam
k8s-gen destroy  --module m7 --type exam --yes

# Mgmt + cloud (external) scaffold
k8s-gen scaffold --module m9 --type lab mgmt --azure
# Then use provider helpers from mgmt to fetch credentials and merge kubeconfigs
# Add helm via: scripts/bootstrap.post.d/50-install-helm.sh
```

### Generated Artifacts (Manual-First)

```
<out>/
├── Vagrantfile
├── scripts/
│   ├── bootstrap.sh
│   ├── provision/              # not executed automatically
│   │   ├── kubeadm-init.sh
│   │   ├── kubeadm-join.sh
│   │   ├── kind-create.sh
│   │   └── minikube-start.sh
│   ├── destroy/
│   │   ├── kubeadm-reset.sh
│   │   ├── kind-delete.sh
│   │   └── minikube-delete.sh
│   ├── verify/
│   │   ├── kubectl-get-nodes.sh
│   │   └── cni-ready.sh
│   └── cloud/
│       ├── aks-get-credentials.sh
│       └── merge-kubeconfigs.sh
└── Makefile                     # shortcuts: provision/verify/destroy (opt-in)
```

SOLUTION.md remains the canonical manual guide. Scripts mirror those steps but are not auto-run.

### Provisioner SPI (Deferred)

Removed from the normative body for v1 to preserve manual-first scope and avoid over-engineering. Future versions MAY introduce a pluggable provisioner, but v1 defers automation to simple, optional scripts under `scripts/{provision,verify,destroy}` without any SPI or runtime discovery.

### Idempotency & Ephemerality

- Bootstrap remains idempotent (lock file per module/type)
- Provision scripts check state and no-op when already provisioned
- Destroy scripts reset to a clean state for re-runs

### Cloud Positioning

- Do not add a generic `cloud` engine
- Before provider engines exist, treat cloud clusters as external:
  - Use engine `none` (mgmt) with cloud modifiers (`azure|aws|gcp`) to install CLIs
  - Provide helper scripts to fetch credentials and merge kubeconfigs on mgmt

---

## Template Engine (JTE)

### Why JTE Over Envsubst?

| Feature | envsubst (bash) | JTE (Java) |
|---------|-----------------|------------|
| Type safety | None | Strong (typed context objects) |
| Error handling | Fails silently | Compile-time errors |
| Logic | Requires shell | Native (if/for/etc.) |
| Includes | Manual | Built-in (@include) |
| Testing | Hard | Easy (unit tests) |
| IDE support | Minimal | Full (IntelliJ) |

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

public record VmConfig(
    String name,
    String ip,
    int cpus,
    int memoryMb,
    String boxImage
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

**Contract Documentation** (top of each template):
```java
@param VagrantfileContext context - VM topology and environment
  - moduleName: Module identifier (e.g., "m7")
  - vms: List of VM configurations (name, ip, cpus, memoryMb, boxImage)
  - envVars: Environment variables to pass to bootstrap (CLUSTER_NAME, K8S_VERSION, etc.)
```

**Validation Before Render**:
- Check all required fields present
- Validate types match expectations
- Fail compilation if contract violated

### Context Building Algorithm (Normative)

**Goal**: Produce `VagrantfileContext` from validated inputs in a deterministic, documented way.

**Ordering**:
- Management VM first (if present).
- Then cluster VMs in declaration order; within each cluster: masters first, then workers.

**Environment Scoping**:
- `envVars` in `VagrantfileContext` are module-level only (e.g., `MODULE_ID`, `TYPE`).
- Cluster-scoped variables (e.g., `CLUSTER_NAME`, `CNI`, `POD_CIDR`) are resolved and persisted by `scripts/bootstrap.sh` per VM via `shell.env` and `/etc/k8s-env`; they are NOT aggregated globally to avoid conflicts across clusters.

**VM Naming**:
- Management: `${module.type}.mgmt` (templates may shorten to `mgmt`).
- Masters: `${cluster.name}-master-${ordinal}` starting at 1.
- Workers: `${cluster.name}-worker-${ordinal}` starting at 1.

**Algorithm (pseudocode)**:
```java
VagrantfileContext buildContext(Plan plan) {
  List<VmConfig> vms = new ArrayList<>();

  // 1) Management VM (optional)
  if (plan.management().isPresent()) {
    vms.add(new VmConfig(
      plan.management().get().name(),
      "192.168.56.5",
      plan.profiles().mgmtCpus(),
      plan.profiles().mgmtMemory(),
      plan.defaults().boxImage()
    ));
  }

  // 2) Cluster VMs
  for (ClusterSpec c : plan.clusters()) {
    IPv4Address base = ip(c.firstIp());
    int offset = 0;
    // masters
    for (int i = 0; i < c.masters(); i++) {
      vms.add(new VmConfig(
        c.name()+"-master-"+(i+1),
        base.increment(offset++).toString(),
        c.masterCpus(),
        c.masterMemory(),
        c.boxImageOr(plan.defaults().boxImage())
      ));
    }
    // workers
    for (int i = 0; i < c.workers(); i++) {
      vms.add(new VmConfig(
        c.name()+"-worker-"+(i+1),
        base.increment(offset++).toString(),
        c.workerCpus(),
        c.workerMemory(),
        c.boxImageOr(plan.defaults().boxImage())
      ));
    }
  }

  Map<String,String> moduleEnv = Map.of(
    "MODULE_ID", plan.module().num(),
    "TYPE", plan.module().type()
  );

  return new VagrantfileContext(plan.module().num(), vms, moduleEnv);
}
```

**Error Conditions**:
- Missing `first_ip` in multi-cluster mode (caught in semantic validation).
- IP collisions, subnet boundary violations, or reserved IP usage (caught in semantic validation).
- Output directory collision (caught before writing; see CLI “Naming & Paths”).

**Example (Multi-Cluster)**:
```text
mgmt (192.168.56.5)
sofia-master-1 (192.168.56.20)
sofia-worker-1 (192.168.56.21)
plovdiv-master-1 (192.168.56.30)
plovdiv-worker-1 (192.168.56.31)
```

---

## Testing Guidance (Non‑Normative)

### Test Pyramid

**Unit Tests** (60%):
- Domain model validation (records with compact constructors)
- YAML parsing (SpecReader)
- Sizing/tools resolution
- CIDR validation (no auto-allocation)
- Policy checks

**Integration Tests** (30%):
- ScaffoldService end-to-end (read → validate → plan → render → write)
- Template rendering with real contexts
- File system operations (FsWriter, ResourceCopier)

**CLI Smoke Tests** (10%):
- `scaffold` command with sample specs
- Assert files exist with expected content
- Validate Vagrantfile syntax (Ruby syntax check)
- Validate bootstrap.sh syntax (shellcheck)

### Golden Tests

Store expected outputs for key scenarios:
- **Kind Single-Node**: Vagrantfile + bootstrap.sh
- **Kubeadm Multi-Node**: Vagrantfile + bootstrap.sh (master + worker)
- **Multi-Cluster with Management**: Vagrantfile + bootstrap.sh (management + 2 clusters)

**Process**:
1. Generate output with `scaffold --spec test.yaml --out temp/`
2. Compare with stored golden files (normalize whitespace)
3. Fail if differences detected (review and update golden if intentional)

### Manual Validation

After each milestone:
1. Generate module: `k8s-gen scaffold --spec m7.yaml --out m7/`
2. Provision VMs: `cd m7 && vagrant up`
3. Run doctor script: `vagrant ssh kmaster -c '/vagrant/scripts/doctor.sh'`
4. Verify env files: `vagrant ssh kmaster -c 'cat /etc/k8s-env'`

---

## Future Extensions (Non‑Normative)

## Future Extensions (Non‑Normative)

**Not in Initial Release**:
- Native image packaging (GraalVM)
- Auto-CIDR allocation
- Interactive wizard mode
- Web UI for spec generation

**Possible Future Additions**:
- Multi-platform support (Hyper-V, Parallels)
- Cloud provider integration (AWS, GCP)
- Pre-built module library
- Validation suggestions with AI

---

## Conformance

A conforming implementation MUST:
- Implement the CLI forms and arguments defined in this specification
- Enforce the Spec‑Gate rules as described
- Produce the output structure (Vagrantfile + scripts) as specified
- Apply naming conventions for clusters, namespaces, and directories
- Treat provisioning as opt‑in and not run by default

---

### CIDR Validation and IP Library Usage (Normative)

**Inputs**:
- `pod_network` (kubeadm): IPv4 CIDR string (default `10.244.0.0/16`)
- `svc_network` (kubeadm): IPv4 CIDR string (default `10.96.0.0/12`)

**Operations (Required)**:
- Validate IPv4 CIDR syntax for both fields.
- Ensure `pod_network` and `svc_network` do not overlap.
- Ensure each is a prefix block (no host-bits set outside the prefix).

**Operations (Explicitly Not Performed)**:
- No auto-allocation or auto-correction of CIDRs.
- No suggestion of alternate CIDRs beyond static examples in messages.
- No enforcement that CIDRs are RFC1918-only (recommended but not required).

**Reference Library**:
- `com.github.seancfoley:ipaddress` used for parsing, validation, and overlap detection of CIDRs and arithmetic on host IPs (e.g., `first_ip` incrementing).

**Helper**:
- `CidrHelper` centralizes CIDR parsing/validation and overlap checks; it does not allocate CIDRs or modify inputs.

**Sketch** (illustrative):
```java
var pod = parseCidr(spec.podNetwork());   // throws on invalid
var svc = parseCidr(spec.svcNetwork());   // throws on invalid

if (overlaps(pod, svc)) {
  error("[Semantic] Pod network %s overlaps with service network %s", pod, svc);
}
```

## Document History

| Version | Date       | Author | Changes |
|---------|------------|--------|---------|
| 1.16.2  | 2025-11-04 | repo-maint | History table reordered to descending version per policy |
| 1.16.1  | 2025-11-03 | repo-maint | Semver normalization of version fields; updated YAML examples to 1.0.0; aligned frontmatter/title to 1.16.1 |
| 1.16.0  | 2025-11-02 | spec-owner | Hybrid Architecture: CLI-First with Optional Spec Export |
| 1.15.0  | 2025-10-31 | spec-owner | Consistency fixes: corrected CLI command examples (k8s-gen), tool name regex note, and YAML note mapping module.num to --module |
| 1.14.0  | 2025-10-31 | spec-owner | Visual Decision Tree added; Style and Naming guidelines clarified |
| 1.13.0  | 2025-10-31 | spec-owner | Quick Reference added; Generated .gitignore contents specified; Common Errors reference section included |
| 1.12.0  | 2025-10-31 | spec-owner | Vagrant box defaults added (ubuntu/jammy64), overrides (per-cluster and global), and warning policy for non-ubuntu boxes |
| 1.11.0  | 2025-10-31 | spec-owner | CIDR validation documented; ipaddress library usage clarified; CidrHelper responsibility stated (no auto-allocation) |
| 1.10.0  | 2025-10-31 | spec-owner | Provisioner SPI moved to deferred note; removed `--list-provisioners` reference from CLI; kept manual scripts only |
| 1.9.0   | 2025-10-31 | spec-owner | Validation error format specified (structure, levels, exit codes, aggregation behavior) |
| 1.8.0   | 2025-10-31 | spec-owner | Azure usage clarified: user responsibilities, bootstrap.env.local example, precedence, and WARN semantics for missing values |
| 1.7.0   | 2025-10-31 | spec-owner | Restored `management.tools` (mgmt-only extras) with clear scope; clusters remain engine-immutable |
| 1.6.0   | 2025-10-31 | spec-owner | YAML schema completed: version field, required/optional markers, minimum viable examples |
| 1.5.0   | 2025-10-31 | spec-owner | Terminology unified (Management Machine); Terminology: Cluster-Type vs Engine section; taxonomy/mapping/examples synchronized |
| 1.4.0   | 2025-10-31 | spec-owner | Future: Multi-Cluster Network Separation section (scope clarification) |
| 1.3.0   | 2025-10-31 | spec-owner | Context Building Algorithm added (ordering, env scoping, naming, pseudocode) |
| 1.2.0   | 2025-10-31 | spec-owner | Module+type composite workspace identifier; output directory collision policy and CLI notes |
| 1.1.0   | 2025-10-31 | spec-owner | IP allocation policy added (single-cluster default, multi-cluster first_ip requirement), reserved mgmt IP, validations, examples |
| 1.0.0   | 2025-10-31 | spec-owner | Initial normative baseline |
