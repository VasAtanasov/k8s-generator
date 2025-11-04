---
status: Architectural Review Recommendations
version: 1.1.0
scope: Alignment of implementation plans with the architecture specification, based on review dated 2025-11-04
---

# Architecture Sync & Refinement (2025-11-04)

## Executive Summary

**Assessment**: GO with planning refinements

This review session focused on analyzing discrepancies between the reference shell scripts (`generate-*.sh`) and the normative architecture documents (`GENERATOR-ARCHITECTURE.md`). The outcome is a set of amendments to the implementation plans (`GENERATOR_CODE_PLAN.md`, `GENERATOR_PLAN.md`) to ensure the planned Java implementation aligns perfectly with the architectural intent. All P0 items represent gaps that have now been filled in the planning documents.

---

## P0 - Must Fix in Plan Before Implementation

### 1. Formalize Cloud Provider Integration in Code Plan ⚠️ CRITICAL

**Problem**: The implementation plan lacked specifics on how provider integration (e.g., the `--azure` flag) would flow through the system's data models and components.

**Required Changes (Now Reflected in `GENERATOR_CODE_PLAN.md`):**

- **`GenerateCommand.java`**: The `--azure` flag is formally included as a boolean option.
- **`ManagementSpec.java`**: A new `Optional<List<String>> providers` field was added to the record to hold enabled providers like "azure".
- **`CliToSpec.java`**: The converter's logic is updated to populate the `ManagementSpec` with the provider if the `--azure` flag is true.
- **`ScaffoldPlan.java`**: A `Set<String> providers` field was added to ensure the set of enabled providers is passed to the rendering and resource-copying stages.
- **`ResourceCopier.java`**: Logic is updated to use the `providers` set in the `ScaffoldPlan` to determine if provider-specific scripts (e.g., `install_azure_cli.sh`) need to be copied.

**Status**: ✅ `GENERATOR_CODE_PLAN.md` and `GENERATOR_PLAN.md` have been amended.

---

### 2. Formalize Bootstrap Hook Scaffolding in Code Plan ⚠️ CRITICAL

**Problem**: The implementation plan was missing the step to create the hook directory structure and stub files for user customizations, a feature present in the reference scripts and `lib.sh`.

**Required Changes (Now Reflected in `GENERATOR_CODE_PLAN.md`):**

- **`OutputWriter.java`**: The responsibility of this I/O brick component has been expanded.
- **New `scaffoldHooks(Path outDir)` method**: This method is added to the `OutputWriter`'s public interface.
- **Key Logic**: The `scaffoldHooks` method is now planned to:
  - Create the full directory structure: `scripts/bootstrap.pre.d/common`, `scripts/bootstrap.post.d/common`, and the `scripts/env/*` subdirectories.
  - Create stub files with placeholder content and correct permissions: `bootstrap.env.local`, `bootstrap.pre.local.sh`, `bootstrap.post.local.sh`.
  - Create `README.md` files in all hook and env directories to explain their usage.
- **`ScaffoldService.java`**: The orchestration flow is updated to call `outputWriter.scaffoldHooks(outDir)` as the final step of generation.

**Status**: ✅ `GENERATOR_CODE_PLAN.md` has been amended.

---

## P2 - Nice to Have

### 1. Defer Host Capacity Guardrails

**Observation**: The reference `generate-module.sh` script includes `--host-cpus` and `--host-ram` flags to warn users about potential resource over-allocation on their host machine.

**Decision**: This is a useful feature but is not considered essential for the initial version. It can be added in a future release.

**Documentation**: No changes needed. The feature is not in the current architecture.

---

## Confirmed Architectural Decisions

This review confirmed that in cases of discrepancy, `GENERATOR-ARCHITECTURE.md` is the definitive source of truth. The following features found in the reference scripts are **explicitly not part of the planned Java implementation**:

- **`--tools` flag**: Deprecated. Custom tool installation is to be handled via the now-scaffolded bootstrap hooks (`bootstrap.post.d/`).
- **`--role` flag**: The granular, role-specific generation from the CLI is not a requirement. The architecture correctly focuses on generating environments based on the higher-level cluster engine type.
- **Granular Sizing Flags**: The `--size` profile approach (`small`, `medium`, `large`) specified in the architecture is the correct and preferred CLI abstraction. The more granular flags like `--master-cpus` from the scripts will not be implemented.
- **`--clusters` flag**: The architecture is definitive that complex multi-cluster topologies are a YAML-only feature. The script's implementation of a CLI-based multi-cluster definition will not be ported.
- **Default IP Address**: The architecture's specified default starting IP of `192.168.56.10` is correct. The script's default of `.100` is a deviation.

---

## Script vs. Java Plan: Implementation Alignment

This review analyzed the reference shell scripts (`generate-module.sh`, `generate-bootstrap.sh`) as a functional prototype against the normative architecture and Java implementation plans. The following summarizes the alignment and divergence.

### 1. Orchestration and Flow
- **Alignment**: Both approaches utilize a high-level orchestrator to coordinate file generation.
- **Divergence**:
  - **Scripts**: Employ a multi-process model where `generate-module.sh` executes other shell scripts.
  - **Java Plan**: Implements a single-process, object-oriented model where `ScaffoldService` orchestrates various Java components ("bricks"). The Java approach is more cohesive and testable.

### 2. Bootstrap Script Generation (Multi-Machine)
- **Alignment**: Both methods produce a dedicated, role-specific bootstrap script for each machine in the topology.
- **Divergence**:
  - **Scripts**: Use a procedural approach, calling `generate-bootstrap.sh` in a loop with different `--role` and `--filename` parameters.
  - **Java Plan**: Uses a data-driven approach. The `JteRenderer` will iterate over a list of `VmConfig` objects in the `ScaffoldPlan` and render a template for each, passing the VM's specific role and name as context.

### 3. Configuration Input (CLI vs. YAML)
- **Alignment**: Both support a simple CLI workflow for single-node clusters.
- **Divergence**:
  - **Scripts**: Feature a powerful `--clusters` flag that parses complex multi-cluster definitions from the command line.
  - **Java Plan**: Enforces a strict separation. The CLI is for simple cases only. Complex multi-cluster definitions are exclusively handled via a dedicated YAML file (`--spec`). This is a key architectural decision to favor clarity over CLI complexity.

### 4. Extensibility
- **Alignment**: Both support customization via bootstrap hook directories (`.pre.d`, `.post.d`), and the Java plan now explicitly includes scaffolding for them.
- **Divergence**:
  - **Scripts**: Provide a redundant `--tools` flag for adding tools.
  - **Java Plan**: Adheres to the architecture which deprecates the `--tools` flag, relying solely on the more robust hook mechanism for user customizations.

---

## References

- Original Architecture Spec: `/doc/GENERATOR-ARCHITECTURE.md` v1.16.1
- Implementation Plan: `/doc/GENERATOR_CODE_PLAN.md` v1.4.0
- High-Level Plan: `/doc/GENERATOR_PLAN.md` v1.1.1

---

## Document History

| Version | Date       | Author      | Changes                                              |
|---------|------------|-------------|------------------------------------------------------|
| 1.1.0   | 2025-11-04 | repo-maint  | Added Script vs. Java Plan implementation alignment section |
| 1.0.0   | 2025-11-04 | repo-maint  | Initial capture of architecture sync recommendations |
