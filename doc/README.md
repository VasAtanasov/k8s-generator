---
status: Documentation Index
version: 1.0.0
scope: Overview and navigation guide for k8s-generator documentation
---

# K8s-Generator Documentation

This directory contains all normative specifications, design documents, and planning materials for the k8s-generator CLI tool.

---

## Quick Navigation

### Core Specifications (Normative)

1. **[GENERATOR-ARCHITECTURE.md](GENERATOR-ARCHITECTURE.md)** (v1.15)
   - **Status**: Normative specification
   - **Purpose**: Complete CLI behavior, YAML schema, conventions, validation rules
   - **Audience**: Implementers, contributors, technical reviewers
   - **Key Sections**:
     - CLI specification and examples
     - YAML schema and validation
     - IP allocation strategy
     - Tool management and engine extensibility
     - Template engine (JTE) and context building
     - Validation strategy (3 layers)

2. **[ARCHITECTURE-REVIEW-2025-11-03.md](ARCHITECTURE-REVIEW-2025-11-03.md)** (v1.0.0)
   - **Status**: Architectural review recommendations
   - **Purpose**: P0/P1/P2 changes from technical architect review
   - **Audience**: Core team, implementers
   - **Key Sections**:
     - P0: IP allocator interface, atomic file writes, validation refinement
     - P1: Package rename (Conversion → InputParser), regeneration contract, error format
     - P2: Phase exit criteria, text blocks vs JTE trade-offs

### Planning & Design

3. **[GENERATOR_PLAN.md](GENERATOR_PLAN.md)**
   - **Status**: DDD planning document
   - **Purpose**: High-level modular design ("6 bricks")
   - **Audience**: Architects, designers, contributors
   - **Key Sections**:
     - Modular architecture (CLI, Model, InputParser, Validation, Rendering, I/O, Orchestrator)
     - Design philosophy (ruthless simplicity, convention-over-configuration)
     - Domain-driven design patterns

4. **[GENERATOR_CODE_PLAN.md](GENERATOR_CODE_PLAN.md)**
   - **Status**: Implementation phases and file breakdown
   - **Purpose**: Detailed code structure, ~50 files, 4 phases
   - **Audience**: Developers, implementers
   - **Key Sections**:
     - Phase 1: MVP (kind/minikube)
     - Phase 2: kubeadm support
     - Phase 3: Multi-cluster orchestration
     - Phase 4: YAML spec path
     - File-by-file breakdown
     - Test strategy (60% unit / 30% integration / 10% E2E)

---

## Document Relationships

```
GENERATOR-ARCHITECTURE.md (normative spec)
    ↓
ARCHITECTURE-REVIEW-2025-11-03.md (P0/P1/P2 changes)
    ↓
GENERATOR_PLAN.md (high-level design)
    ↓
GENERATOR_CODE_PLAN.md (implementation phases)
```

**Read order for new contributors**:
1. Start with GENERATOR-ARCHITECTURE.md (understand "what")
2. Review ARCHITECTURE-REVIEW-2025-11-03.md (understand "must-fix")
3. Read GENERATOR_PLAN.md (understand "why" and design principles)
4. Study GENERATOR_CODE_PLAN.md (understand "how" - implementation)

---

## Key Concepts

### CLI-First Hybrid Architecture

The generator uses a **hybrid approach**:
- **80% path**: Simple CLI with smart defaults (zero YAML)
- **20% path**: YAML specs for complex scenarios (multi-cluster, custom networking)
- **Internal flow**: CLI → ephemeral YAML → validate → generate

### Convention-Over-Configuration

**Opinionated defaults**:
- Container runtime: containerd (kubeadm)
- CNI: Calico (kubeadm)
- Pod CIDR: `10.244.0.0/16`
- Service CIDR: `10.96.0.0/12`
- Sizing: small/medium/large profiles
- IPs: single-cluster `192.168.56.10`, multi-cluster requires explicit `first_ip`

### Modular Architecture (6 Bricks)

```
CLI → Orchestrator → [Model ↔ InputParser ↔ Validation] → Rendering → I/O
```

1. **CLI**: Picocli-based argument parsing
2. **InputParser**: Converts CLI/YAML to domain model (formerly "Conversion")
3. **Model**: Immutable records (ClusterSpec, VmConfig, etc.)
4. **Validation**: 3-layer strategy (Structural/Semantic/Policy)
5. **Rendering**: JTE template engine
6. **I/O**: File system operations (atomic writes)
7. **Orchestrator**: Coordinates the pipeline

---

## Implementation Status

| Phase | Status | Target |
|-------|--------|--------|
| **Phase 1**: MVP (kind/minikube) | 🔴 Not started | Week 3 |
| **Phase 2**: kubeadm support | 🔴 Not started | Week 4 |
| **Phase 3**: Multi-cluster | 🔴 Not started | Week 6 |
| **Phase 4**: YAML spec path | 🔴 Not started | Week 8 |

---

## Critical P0 Changes (Must Fix Before Coding)

From [ARCHITECTURE-REVIEW-2025-11-03.md](ARCHITECTURE-REVIEW-2025-11-03.md):

1. ⚠️ **IP Allocation Algorithm**: Define explicit `IpAllocator` interface with edge case handling
   - Status: ✅ Already specified in GENERATOR-ARCHITECTURE.md (lines 1301-1361)

2. ⚠️ **Atomic File Generation**: Implement `AtomicFileWriter` for all-or-nothing file writes
   - Status: 🔴 Needs implementation

3. ⚠️ **Validation Strategy Refinement**: Hybrid approach (constructor + validator + policy)
   - Status: 🟡 Needs documentation update

---

## Contributing

### Before Making Changes

1. **Read the normative spec**: GENERATOR-ARCHITECTURE.md is the source of truth
2. **Check review recommendations**: ARCHITECTURE-REVIEW-2025-11-03.md contains must-fix items
3. **Follow naming conventions**:
   - Packages: `com.k8s.generator.{cli,model,parser,validate,render,fs,app}`
   - Classes: PascalCase for records/classes, camelCase for methods
   - Cluster names: `clu-<module>-<type>-<engine>`
   - Namespace: `ns-<module>-<type>`

### Documentation Updates

All documents follow the **frontmatter + history** pattern:

```yaml
---
status: <type>
version: <semver>
scope: <one-line description>
---

# Document Title

... content ...

## Document History

| Version | Date       | Author      | Changes |
|---------|------------|-------------|---------|
| 1.0.0   | YYYY-MM-DD | contributor | Initial version |
```

**Version bumping rules**:
- **PATCH** (x.y.Z): Typos, formatting, clarifications
- **MINOR** (x.Y.0): Additive changes, new sections, backward-compatible
- **MAJOR** (X.0.0): Breaking changes, deprecations, policy changes

---

## Technology Stack

### Core Dependencies

- **Language**: Java 21+
- **Build**: Maven
- **CLI**: Picocli 4.7.5
- **Templates**: JTE 3.1.9
- **IP/CIDR**: com.github.seancfoley:ipaddress:5.4.0
- **YAML**: Jackson YAML 2.15.2
- **Testing**: JUnit 5, AssertJ

### Generated Outputs

- **Vagrantfile**: Ruby VM topology
- **bootstrap.sh**: Bash provisioning scripts
- **install_*.sh**: Tool installers (kubectl, kind, minikube, etc.)
- **.gitignore**: Excludes .vagrant/, *.log, bootstrap.env.local

---

## Architectural Principles

### Design Philosophy

1. **Ruthless simplicity**: 80% use case should be trivial
2. **Convention-over-configuration**: Smart defaults, override only when needed
3. **Start minimal, grow as needed**: CLI → export spec → edit → regenerate
4. **Avoid future-proofing**: Don't force YAML upfront
5. **80/20 principle**: Optimize for the common case
6. **Explicit over implicit**: Multi-cluster requires explicit IPs

### Code Quality Standards

- **Immutable records** for domain model
- **Pure functions** where possible
- **Fail-fast validation** with actionable error messages
- **Type safety**: Leverage Java records and sealed types
- **No global mutable state**
- **Explicit contracts** between layers

---

## Common Questions

### Q: Why JTE instead of text blocks?

**A**: JTE provides type safety, compile-time errors, template composition, and easier testing. Text blocks are simpler but lack these benefits for complex templates.

### Q: Why hybrid CLI + YAML instead of pure YAML?

**A**: 80% of users need simple, single-cluster environments. Forcing YAML adds friction for beginners. The hybrid approach provides smooth evolution: start CLI, export spec when complexity justifies it.

### Q: Why immutable records instead of builder pattern?

**A**: Immutable records are concise, type-safe, and make invalid states impossible. Builders add verbosity without significant benefits for our use case.

### Q: Why SPI for engines instead of hardcoded?

**A**: SPI enables adding new cluster types (k3s, k3d, microk8s, AKS, EKS, GKE) without modifying core code. Supports open-source contributions.

---

## References

### External Documentation

- [Picocli Documentation](https://picocli.info/)
- [JTE Template Engine](https://jte.gg/)
- [IPAddress Library](https://seancfoley.github.io/IPAddress/)
- [Jackson YAML](https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml)
- [Vagrant Documentation](https://www.vagrantup.com/docs)

### Related Projects

- [Kubernetes kubeadm](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/)
- [kind (Kubernetes in Docker)](https://kind.sigs.k8s.io/)
- [minikube](https://minikube.sigs.k8s.io/)

---

## Changelog

See [ARCHITECTURE-REVIEW-2025-11-03.md](ARCHITECTURE-REVIEW-2025-11-03.md) for recent architectural decisions and the Document History sections in each spec for detailed change logs.

---

## License

[To be determined - typically Apache 2.0 or MIT for educational tools]

---

## Document History

| Version | Date       | Author      | Changes                    |
|---------|------------|-------------|----------------------------|
| 1.0.0   | 2025-11-03 | repo-maint  | Initial documentation index |
