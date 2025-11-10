---
status: Planning document
version: 1.2.0
scope: High-level DDD plan and phased roadmap for the k8s-generator CLI
---

# DDD Plan: k8s-generator CLI Implementation

---

## Problem Statement

**What we're building**: A Java CLI tool that generates complete, working Kubernetes learning environments for university courses.

**Why it matters**:
- **Educators**: Need reproducible, correct K8s environments for teaching (modules m1-m9)
- **Students**: Need fast setup (2-5 minutes vs 15-30 with manual YAML)
- **Consistency**: Convention-over-configuration eliminates configuration errors
- **Learning focus**: Students focus on K8s concepts, not infrastructure debugging

**User value**:
- **80% use case**: Zero-config CLI (`k8s-gen --module m1 --type pt kind`) - instant, works
- **20% use case**: YAML specs for genuinely complex multi-cluster scenarios
- **Quality**: Generated environments work correctly (validated against best practices)
- **Documentation**: SOLUTION.md guides students through manual steps

**Problem solved**: Eliminates the "works on my machine" problem for K8s education by providing validated, reproducible infrastructure-as-code.

---

## Proposed Solution

### High-Level Approach

Build a **CLI-first hybrid system** with ruthlessly simple architecture:

1. **CLI Layer**: Picocli handles structure/type validation
2. **Conversion Layer**: Pure mapping CLI → Spec (no validation)
3. **Validation Layer**: Three focused validators (structural/semantic/policy)
4. **Planning Layer**: Spec → Plan transformation (IP allocation, VM sizing)
5. **Rendering Layer**: JTE templates generate Vagrantfile + scripts
6. **I/O Layer**: File operations (read YAML, write outputs, copy resources)
7. **Orchestration**: Single service that knows the flow

**Core Insight**: Separate concerns into regeneratable "bricks" with clear "studs" (interfaces).

### Incremental Delivery (Vertical Slices)

**Phase 1** (MVP): Single-cluster `kind` generation
**Phase 2**: Add kubeadm + semantic validation
**Phase 3**: Multi-cluster + management VM
**Phase 4**: YAML spec file support
**Phase 5**: Export-spec command (future)

---

## Alternatives Considered

### Alternative 1: Monolithic Script-Based Generator

**Approach**: Single bash/python script with string templating (envsubst)

**Pros**:
- Faster initial development
- No compilation step
- Easier for non-Java developers

**Cons**:
- No type safety (runtime errors)
- Hard to test individual components
- String templating error-prone
- Can't leverage JTE compile-time safety
- Difficult to regenerate modules

**Decision**: ❌ Rejected - violates modular design philosophy

---

### Alternative 2: Full Spec-First (No CLI)

**Approach**: Require YAML spec for all scenarios (like Terraform)

**Pros**:
- Single code path (no CLI conversion)
- Explicit over implicit
- Reproducible by default

**Cons**:
- Violates 80/20 principle (forces complexity on simple cases)
- High barrier to entry (15-30 min setup)
- Bad classroom experience (context switching to YAML)

**Decision**: ❌ Rejected - spec says "convention-over-configuration for 80%"

---

### Alternative 3: Hybrid Architecture (Chosen)

**Approach**: CLI-first with optional YAML for complex cases

**Pros**:
- ✅ 80% case: instant (<60 sec to first cluster)
- ✅ 20% case: full power via YAML
- ✅ Smooth migration path (export-spec bridges gap)
- ✅ Philosophy-aligned (ruthless simplicity)

**Cons**:
- Two input paths to maintain
- CLI conversion adds complexity

**Decision**: ✅ **CHOSEN** - aligns with spec requirements and philosophy

**Mitigation**: Keep CLI conversion pure (no validation) to avoid duplication

---

### Alternative 4: Validation Strategy Options

**Option A**: Single mega-validator class
- ❌ Hard to test, hard to regenerate, violates SRP

**Option B**: Separate validators with aggregator
- ✅ **CHOSEN** - testable, regeneratable, clear separation

**Option C**: Validation embedded in domain objects
- ❌ Couples validation to models, can't swap strategies

---

## Architecture & Design

### Key Interfaces (The "Studs")

#### 1. SpecConverter (CLI → Spec)

```java
/**
 * Converts CLI arguments to internal spec representation
 *
 * Contract:
 * - Input: Validated Picocli @Command object
 * - Output: GeneratorSpec (immutable record)
 * - Side Effects: None (pure transformation)
 * - No validation (Picocli handles structure, Validator handles business rules)
 */
public interface SpecConverter {
    GeneratorSpec convert(GenerateCommand cmd);
}
```

#### 2. Validator (Spec Quality Gate)

```java
/**
 * Validates spec against all rules (structural/semantic/policy)
 *
 * Contract:
 * - Input: GeneratorSpec
 * - Output: List<ValidationError> (empty = valid)
 * - Side Effects: None
 * - Aggregates: StructuralValidator + SemanticValidator + PolicyValidator
 */
public interface Validator {
    List<ValidationError> validate(GeneratorSpec spec);
}
```

#### 3. PlanBuilder (Spec → Plan)

```java
/**
 * Converts validated spec to template-ready plan
 *
 * Contract:
 * - Input: GeneratorSpec (assumed valid)
 * - Output: ScaffoldPlan (with allocated IPs, resolved sizes, env vars)
 * - Dependencies: IpAllocator
 * - Side Effects: None (pure transformation)
 */
public interface PlanBuilder {
    ScaffoldPlan build(GeneratorSpec spec);
}
```

#### 4. Renderer (Plan → Files)

```java
/**
 * Renders templates from plan
 *
 * Contract:
 * - Input: ScaffoldPlan
 * - Output: Map<String, String> (filename → content)
 * - Side Effects: None (pure transformation)
 * - Dependencies: JTE template engine
 */
public interface Renderer {
    Map<String, String> render(ScaffoldPlan plan);
}
```

#### 5. ScaffoldService (Orchestrator)

```java
/**
 * Main orchestration (only place that knows the full flow)
 *
 * Contract:
 * - Input: CLI args OR spec file path
 * - Output: Exit code (0=success, 2=validation, 1=error)
 * - Side Effects: Writes files to output directory
 * - Flow: Read → Convert → Validate → Plan → Render → Write
 */
public class ScaffoldService {
    public int scaffold(GenerateCommand cmd);
}
```

---

### Module Boundaries

**6 Bricks (Self-Contained Modules)**:

#### Brick 1: `cli/` - Command Line Interface
- **Responsibility**: Parse CLI arguments, provide user interface
- **Stud (Public)**: `GenerateCommand` class (Picocli @Command)
- **Internal**: Command options, parameter validation
- **Dependencies**: None (depends on Picocli library)

#### Brick 2: `model/` - Domain Model
- **Responsibility**: Define all data structures (immutable records)
- **Stud (Public)**:
  - `spec/` subpackage (input models: GeneratorSpec, ClusterSpec, etc.)
  - `plan/` subpackage (output models: ScaffoldPlan, VmConfig, etc.)
  - `shared/` subpackage (enums: Engine, CniType, NodeRole)
- **Internal**: Record definitions with validation in constructors
- **Dependencies**: None (pure data)

#### Brick 3: `conversion/` - Transformations
- **Responsibility**: Convert between representations (CLI → Spec, Spec → Plan)
- **Stud (Public)**: `SpecConverter`, `PlanBuilder`, `IpAllocator` interfaces
- **Internal**: Implementation classes (CliToSpec, SpecToPlan, IpAllocator)
- **Dependencies**: `model/`

#### Brick 4: `validation/` - Quality Gates
- **Responsibility**: Validate specs against all rules
- **Stud (Public)**: `Validator` interface, `ValidationError` record
- **Internal**: StructuralValidator, SemanticValidator, PolicyValidator
- **Dependencies**: `model/`, ipaddress library (for IP validation)

#### Brick 5: `rendering/` - Template Engine
- **Responsibility**: Render JTE templates to strings
- **Stud (Public)**: `Renderer` interface
- **Internal**: JteRenderer implementation, template resources
- **Dependencies**: `model/` (for plan), JTE library

#### Brick 6: `io/` - File Operations
- **Responsibility**: Read YAML specs, write output files, copy resources
- **Stud (Public)**: `SpecReader`, `OutputWriter`, `ResourceCopier`
- **Internal**: Implementation using Jackson YAML, Files API
- **Dependencies**: `model/`, Jackson YAML library

**Orchestrator** (knows all bricks):
- `orchestration/ScaffoldService` - composes the flow

---

### Data Models

The project employs a hybrid strategy for data modeling to balance safety and convenience:

- **Immutable Records**: All domain models are implemented as Java `records` to guarantee immutability and atomic construction. This is the default for simple data carriers.
- **Builder Pattern for Complexity**: For complex records with a large number of fields, especially optional ones (e.g., `ClusterSpec`), the builder pattern is used via Lombok's `@Builder`. This provides a fluent, readable API for object creation, avoiding verbose constructors.

This approach combines the compile-time safety of records with the readability and convenience of builders for the models that need it most.

#### Input Models (`model/spec/`)

```java
// Root specification
public record GeneratorSpec(
    ModuleInfo module,
    Optional<ManagementSpec> management,
    List<ClusterSpec> clusters
) {}

public record ModuleInfo(String num, String type) {
    public ModuleInfo {
        if (!num.matches("m\\d+")) throw new IllegalArgumentException("Invalid module format");
        if (!type.matches("[a-z][a-z0-9-]*")) throw new IllegalArgumentException("Invalid type format");
    }
}

public record ClusterSpec(
    String name,
    Engine engine,
    Optional<CniType> cni,           // Required for kubeadm
    Optional<NodeCounts> nodes,      // Required for kubeadm
    Optional<String> firstIp,        // Required for multi-cluster
    Optional<String> podNetwork,     // Default: 10.244.0.0/16
    Optional<String> svcNetwork,     // Default: 10.96.0.0/12
    Optional<VmOverrides> vmOverrides
) {}

public record ManagementSpec(
    String name,
    List<String> tools,
    boolean aggregateKubeconfigs,
    Optional<List<String>> providers // For cloud integration like 'azure'
) {}

public record NodeCounts(int masters, int workers) {
    public NodeCounts {
        if (masters < 1) throw new IllegalArgumentException("At least 1 master required");
        if (workers < 0) throw new IllegalArgumentException("Workers must be >= 0");
    }
}
```

#### Output Models (`model/plan/`)

```java
// Root plan (ready for templates)
public record ScaffoldPlan(
    ModuleInfo module,
    List<VmConfig> vms,              // Ordered: mgmt, then masters, then workers
    Map<String, String> envVars,     // Module-level environment variables
    Set<String> providers            // e.g., {"azure"}
) {}

public record VmConfig(
    String name,
    InetAddress ip,
    NodeRole role,
    int cpus,
    int memoryMb,
    String boxImage
) {}

public enum NodeRole {
    MANAGEMENT, MASTER, WORKER
}
```

#### Shared Enums (`model/shared/`)

```java
public enum Engine {
    NONE,      // Management machine (mgmt)
    KIND,      // Single-node Docker-in-Docker
    MINIKUBE,  // Single-node VM-based
    KUBEADM    // Multi-node cluster
}

public enum CniType {
    CALICO, FLANNEL, WEAVE, CILIUM, ANTREA
}
```

---

## Files to Change

### Non-Code Files (Phase 2 - Documentation)

#### New Documentation

- [ ] `ai_working/202511-kubernetes/k8s-generator/README.md` - Project overview, quick start, philosophy
- [ ] `ai_working/202511-kubernetes/k8s-generator/docs/USER_GUIDE.md` - Complete user documentation
- [ ] `ai_working/202511-kubernetes/k8s-generator/docs/ARCHITECTURE.md` - Implementation architecture (this plan)
- [ ] `ai_working/202511-kubernetes/k8s-generator/docs/CONTRIBUTING.md` - Development guide
- [ ] `ai_working/202511-kubernetes/k8s-generator/docs/examples/` - Example specs and outputs
  - [ ] `kind-single.yaml` - Simplest case
  - [ ] `kubeadm-multi-node.yaml` - 3-node cluster
  - [ ] `multi-cluster-mgmt.yaml` - Multi-cluster with management

#### Updated Specification

- [ ] `ai_working/202511-kubernetes/doc/GENERATOR-ARCHITECTURE.md` - Mark as "implementation in progress", reference actual code

#### Build Configuration

- [ ] `ai_working/202511-kubernetes/k8s-generator/pom.xml` - Complete Maven configuration with all dependencies
- [ ] `ai_working/202511-kubernetes/k8s-generator/.gitignore` - Ignore target/, .idea/, *.iml
- [ ] `ai_working/202511-kubernetes/k8s-generator/Makefile` - Build shortcuts (build, test, run)

---

### Code Files (Phase 4 - Implementation)

#### Phase 1: MVP (Single-cluster kind)

**Core Flow**:
- [ ] `src/main/java/com/k8s/generator/Main.java` - Entry point, delegate to ScaffoldService
- [ ] `src/main/java/com/k8s/generator/orchestration/ScaffoldService.java` - Main flow orchestrator

**CLI Brick**:
- [ ] `src/main/java/com/k8s/generator/cli/GenerateCommand.java` - Picocli command definition

**Model Brick** (minimal for kind):
- [ ] `src/main/java/com/k8s/generator/model/spec/GeneratorSpec.java`
- [ ] `src/main/java/com/k8s/generator/model/spec/ModuleInfo.java`
- [ ] `src/main/java/com/k8s/generator/model/spec/ClusterSpec.java`
- [ ] `src/main/java/com/k8s/generator/model/plan/ScaffoldPlan.java`
- [ ] `src/main/java/com/k8s/generator/model/plan/VmConfig.java`
- [ ] `src/main/java/com/k8s/generator/model/plan/SizeProfile.java`
- [ ] `src/main/java/com/k8s/generator/model/shared/Engine.java`
- [ ] `src/main/java/com/k8s/generator/model/shared/NodeRole.java`

**Conversion Brick** (minimal):
- [ ] `src/main/java/com/k8s/generator/conversion/SpecConverter.java` - Interface
- [ ] `src/main/java/com/k8s/generator/conversion/CliToSpec.java` - CLI → Spec
- [ ] `src/main/java/com/k8s/generator/conversion/PlanBuilder.java` - Interface
- [ ] `src/main/java/com/k8s/generator/conversion/SpecToPlan.java` - Spec → Plan

**Validation Brick** (structural only for MVP):
- [ ] `src/main/java/com/k8s/generator/validation/Validator.java` - Interface
- [ ] `src/main/java/com/k8s/generator/validation/ValidationError.java` - Error record
- [ ] `src/main/java/com/k8s/generator/validation/StructuralValidator.java` - Required fields check

**Rendering Brick**:
- [ ] `src/main/java/com/k8s/generator/rendering/Renderer.java` - Interface
- [ ] `src/main/java/com/k8s/generator/rendering/JteRenderer.java` - JTE implementation
- [ ] `src/main/resources/templates/Vagrantfile.jte` - VM topology template
- [ ] `src/main/resources/templates/bootstrap.sh.jte` - Bootstrap script template
- [ ] `src/main/resources/templates/.gitignore.jte` - Generated .gitignore template

**I/O Brick**:
- [ ] `src/main/java/com/k8s/generator/io/OutputWriter.java` - Write files to disk
- [ ] `src/main/java/com/k8s/generator/io/ResourceCopier.java` - Copy install scripts

**Resources** (install scripts):
- [ ] `src/main/resources/scripts/install_kubectl.sh`
- [ ] `src/main/resources/scripts/install_docker.sh`
- [ ] `src/main/resources/scripts/install_kind.sh`

**Tests** (MVP):
- [ ] `src/test/java/com/k8s/generator/EndToEndKindTest.java` - Full flow test
- [ ] `src/test/java/com/k8s/generator/conversion/CliToSpecTest.java` - Unit test
- [ ] `src/test/java/com/k8s/generator/conversion/SpecToPlanTest.java` - Unit test

#### Phase 2: Add kubeadm + Full Validation

**Model additions**:
- [ ] `src/main/java/com/k8s/generator/model/spec/NodeCounts.java`
- [ ] `src/main/java/com/k8s/generator/model/spec/VmOverrides.java`
- [ ] `src/main/java/com/k8s/generator/model/shared/CniType.java`

**Conversion additions**:
- [ ] `src/main/java/com/k8s/generator/conversion/IpAllocator.java` - IP allocation logic

**Validation additions**:
- [ ] `src/main/java/com/k8s/generator/validation/SemanticValidator.java` - Business rules, IP collisions
- [ ] `src/main/java/com/k8s/generator/validation/PolicyValidator.java` - Tool/engine compatibility
- [ ] `src/main/java/com/k8s/generator/validation/CompositeValidator.java` - Aggregator

**Templates additions**:
- [ ] `src/main/resources/templates/engines/kubeadm/bootstrap-master.jte`
- [ ] `src/main/resources/templates/engines/kubeadm/bootstrap-worker.jte`

**Resources additions**:
- [ ] `src/main/resources/scripts/install_containerd.sh`
- [ ] `src/main/resources/scripts/install_kubeadm.sh`
- [ ] `src/main/resources/scripts/install_calico.sh`

**Tests**:
- [ ] `src/test/java/com/k8s/generator/validation/SemanticValidatorTest.java`
- [ ] `src/test/java/com/k8s/generator/validation/PolicyValidatorTest.java`
- [ ] `src/test/java/com/k8s/generator/conversion/IpAllocatorTest.java`
- [ ] `src/test/java/com/k8s/generator/EndToEndKubeadmTest.java`

#### Phase 3: Multi-cluster + Management

**Model additions**:
- [ ] `src/main/java/com/k8s/generator/model/spec/ManagementSpec.java`

**Templates additions**:
- [ ] `src/main/resources/templates/engines/none/bootstrap-mgmt.jte`
- [ ] `src/main/resources/scripts/merge-kubeconfigs.sh`

**Tests**:
- [ ] `src/test/java/com/k8s/generator/EndToEndMultiClusterTest.java`

#### Phase 4: YAML Spec Support

**I/O additions**:
- [ ] `src/main/java/com/k8s/generator/io/SpecReader.java` - Read YAML specs

**Tests**:
- [ ] `src/test/java/com/k8s/generator/io/SpecReaderTest.java`
- [ ] `src/test/resources/specs/` - Test YAML files

---

## Philosophy Alignment

### Ruthless Simplicity

#### Start Minimal
- **MVP**: Only kind generation (single cluster, no YAML)
- **No future-proofing**: Don't build export-spec until CLI-first proves itself
- **80/20**: CLI covers 80%, YAML added only when needed

#### Avoid Future-Proofing
- **NOT building**:
  - External cloud clusters (AKS/EKS/GKE) - marked as future
  - Auto-CIDR allocation - explicit IPs only
  - Per-cluster host networks - single subnet for v1
  - Provisioning/verification/destroy - scripts only, no automation

#### Clear Over Clever
- **Explicit IP allocation**: IpAllocator class (not hidden in Spec → Plan)
- **Three separate validators**: Not one mega-class
- **Pure transformations**: Converters have no side effects
- **Immutable records**: Can't modify state accidentally

---

### Modular Design

#### Bricks (Self-Contained Modules)

1. **CLI Brick** (`cli/`)
   - Can regenerate: Parse different arguments, change command structure
   - Stud: `GenerateCommand` class with Picocli annotations

2. **Model Brick** (`model/`)
   - Can regenerate: Add fields, change validation logic
   - Stud: Public records in spec/, plan/, shared/

3. **Conversion Brick** (`conversion/`)
   - Can regenerate: Change transformation logic
   - Stud: `SpecConverter`, `PlanBuilder`, `IpAllocator` interfaces

4. **Validation Brick** (`validation/`)
   - Can regenerate: Add new validation rules, change error messages
   - Stud: `Validator` interface, `ValidationError` record

5. **Rendering Brick** (`rendering/`)
   - Can regenerate: Switch template engines, change templates
   - Stud: `Renderer` interface

6. **I/O Brick** (`io/`)
   - Can regenerate: Change file formats, add compression
   - Stud: `SpecReader`, `OutputWriter`, `ResourceCopier`

#### Studs (Clear Interfaces)

Each brick exposes exactly ONE public interface:
- Other bricks depend on interface, not implementation
- Can swap implementations without affecting consumers
- AI can regenerate implementation from interface contract

#### Regeneratable

**Test**: Can I describe a brick's contract and have AI rebuild it?

✅ **YES** for each brick:
- `Validator`: "Given GeneratorSpec, return List<ValidationError>"
- `PlanBuilder`: "Given GeneratorSpec, return ScaffoldPlan with allocated IPs"
- `Renderer`: "Given ScaffoldPlan, return Map<filename, content>"

Each brick's contract is 10-20 lines. Implementation can be fully regenerated.

---

## Test Strategy

### Unit Tests (60%)

**Focus**: Individual brick logic, no dependencies

#### Conversion Tests
- `CliToSpecTest`: CLI args → Spec mapping
- `SpecToPlanTest`: Spec → Plan transformation
- `IpAllocatorTest`: IP allocation logic (single/multi-cluster, overlaps, boundaries)

#### Validation Tests
- `StructuralValidatorTest`: Required fields, schema violations
- `SemanticValidatorTest`: IP collisions, CIDR overlaps, subnet boundaries
- `PolicyValidatorTest`: Engine/tool compatibility, forbidden combinations

#### Model Tests
- `RecordValidationTest`: Record constructor constraints (NodeCounts > 0, valid module format)

**Coverage Target**: 60% of codebase (all business logic)

---

### Integration Tests (30%)

**Focus**: Multiple bricks working together

#### Flow Tests
- `ValidationFlowTest`: Spec → all three validators → aggregated errors
- `ConversionFlowTest`: CLI → Spec → Plan → verify IPs allocated correctly
- `TemplateRenderingTest`: Plan → JTE → verify Vagrantfile structure

#### End-to-End Tests
- `EndToEndKindTest`: CLI → Validate → Plan → Render → Write → verify files exist
- `EndToEndKubeadmTest`: Multi-node cluster generation
- `EndToEndMultiClusterTest`: Management + 2 clusters

**Coverage Target**: 30% of codebase (integration paths)

---

### User Testing (10%)

**Focus**: Real usage, golden file comparison

#### CLI Smoke Tests
- `CliSmokeTest`: Actual CLI commands work (Picocli integration)
- `GoldenFileTest`: Compare generated output vs expected (text diff)

#### Manual Validation
After each milestone:
1. Generate output: `java -jar target/k8s-generator.jar --module m1 --type pt kind --out test-output/`
2. Verify files: `test-output/Vagrantfile`, `test-output/scripts/bootstrap.sh`
3. Test with Vagrant: `cd test-output && vagrant up`
4. Verify cluster: `vagrant ssh -c 'kubectl get nodes'`

**Coverage Target**: 10% of codebase (user-facing paths)

---

## Implementation Approach

### Phase 2 (Docs) - Update Documentation

**Order** (docs come first):

1. **Project README** (`ai_working/202511-kubernetes/k8s-generator/README.md`)
   - Overview, quick start, installation
   - Example commands
   - Link to USER_GUIDE.md

2. **User Guide** (`docs/USER_GUIDE.md`)
   - CLI reference (all commands, options)
   - Examples (kind, kubeadm, multi-cluster)
   - YAML spec format
   - Troubleshooting

3. **Architecture Doc** (`docs/ARCHITECTURE.md`)
   - Package structure (6 bricks)
   - Interface contracts (studs)
   - Data flow diagram
   - Testing strategy

4. **Contributing Guide** (`docs/CONTRIBUTING.md`)
   - Build setup (Maven, Java 25)
   - Running tests
   - Adding new engines
   - Philosophy adherence

5. **Examples** (`docs/examples/`)
   - kind-single.yaml + expected output
   - kubeadm-multi-node.yaml + expected output
   - multi-cluster-mgmt.yaml + expected output

6. **Build Config** (`pom.xml`, `.gitignore`, `Makefile`)
   - Maven dependencies (Picocli, JTE, Jackson, ipaddress, JUnit)
   - Build commands (compile, test, package)
   - Makefile shortcuts

**Philosophy**: Document the "why" and "what" before implementing "how"

---

### Phase 4 (Code) - Implementation Chunks

**Chunk 1: MVP (kind generation)** (~3-4 days)

*Goal*: Single command works end-to-end

```bash
k8s-gen --module m1 --type pt kind --out test-output/
# → Generates Vagrantfile + scripts for kind cluster
```

**Files** (in order):
1. Model records (GeneratorSpec, ScaffoldPlan, VmConfig, Engine enum)
2. CliToSpec (CLI → Spec conversion)
3. StructuralValidator (basic validation)
4. SpecToPlan (Spec → Plan, hardcoded IPs for now)
5. JteRenderer + templates (Vagrantfile.jte, bootstrap.sh.jte)
6. OutputWriter (write files)
7. ScaffoldService (orchestrate flow)
8. Main + GenerateCommand (CLI entry)
9. Test: EndToEndKindTest

**Success**: Can generate working kind environment

---

**Chunk 2: kubeadm + Full Validation** (~4-5 days)

*Goal*: Multi-node clusters with complete validation

```bash
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w --out test-output/
# → Generates 3-node kubeadm cluster (1 master, 2 workers)
```

**Files** (in order):
1. NodeCounts, CniType models
2. IpAllocator (explicit IP allocation)
3. SemanticValidator (IP collisions, CIDR overlaps)
4. PolicyValidator (engine/tool compatibility)
5. CompositeValidator (aggregate all three)
6. Update SpecToPlan (use IpAllocator)
7. kubeadm templates (bootstrap-master.jte, bootstrap-worker.jte)
8. Install scripts (containerd, kubeadm, calico)
9. Tests: SemanticValidatorTest, IpAllocatorTest, EndToEndKubeadmTest

**Success**: Can generate 3-node kubeadm cluster with validation

---

**Chunk 3: Multi-cluster + Management** (~3-4 days)

*Goal*: Multiple clusters with management VM

```bash
k8s-gen --spec multi-cluster.yaml --out test-output/
# → Generates mgmt + 2 kubeadm clusters with merged kubeconfigs
```

**Files** (in order):
1. ManagementSpec model
2. Update SpecToPlan (handle management VM at IP .5)
3. Management template (bootstrap-mgmt.jte)
4. Kubeconfig merge script (merge-kubeconfigs.sh)
5. Test: EndToEndMultiClusterTest

**Success**: Can generate multi-cluster topology with management

---

**Chunk 4: YAML Spec Support** (~2-3 days)

*Goal*: Read YAML spec files

```bash
k8s-gen --spec m7.yaml --out test-output/
# → Reads YAML, generates cluster
```

**Files** (in order):
1. SpecReader (Jackson YAML integration)
2. Update ScaffoldService (handle spec file path)
3. Test YAML files (specs/ directory)
4. Tests: SpecReaderTest

**Success**: Can read YAML specs and generate same output as CLI

---

**Dependencies Between Chunks**:
- Chunk 2 depends on Chunk 1 (MVP must work first)
- Chunk 3 depends on Chunk 2 (need multi-node before multi-cluster)
- Chunk 4 independent (can be done anytime after Chunk 1)

**Philosophy**: Each chunk is a complete vertical slice (works end-to-end)

---

## Success Criteria

### Functional Success

✅ **CLI works**:
- `k8s-gen --module m1 --type pt kind` generates valid Vagrant environment
- `k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w` generates 3-node cluster
- `k8s-gen --spec multi-cluster.yaml` generates multi-cluster setup

✅ **Validation catches errors**:
- Missing required fields → Structural error with suggestion
- IP collisions → Semantic error with fix
- Incompatible tools → Policy error with explanation

✅ **Generated environments work**:
- `vagrant up` succeeds
- `kubectl get nodes` shows correct nodes
- Students can follow SOLUTION.md

---

### Quality Success

✅ **Philosophy aligned**:
- Each brick regeneratable from its contract
- No future-proofing (only what's needed now)
- Clear over clever (explicit IP allocation, not magic)

✅ **Test coverage**:
- 60% unit tests (all validators, converters, allocators)
- 30% integration tests (multi-brick flows)
- 10% E2E tests (real CLI commands)

✅ **Documentation complete**:
- README explains quick start
- USER_GUIDE covers all commands
- ARCHITECTURE explains design decisions
- Examples show common scenarios

---

### Developer Experience

✅ **Easy to extend**:
- Adding new engine: Implement Engine enum, add templates, update PolicyValidator
- Adding new validation: Create new Validator, add to CompositeValidator
- Changing templates: Edit .jte files, tests catch breakage

✅ **Easy to regenerate**:
- Any brick can be regenerated from its contract
- Tests verify contract still met
- AI can rebuild bricks from specification

✅ **Clear boundaries**:
- Junior developer can understand each brick in isolation
- Package structure matches mental model
- Each class has single responsibility

---

## Next Steps

✅ **Phase 1 Complete**: Planning Approved

**Ready for**:

```bash
/ddd:2-docs
```

**This will**:
- Update all non-code files (docs, configs, READMEs)
- Write as if the system already exists (retcon writing)
- Create example specs and expected outputs
- Prepare build configuration (pom.xml, Makefile)

**After Phase 2**, proceed to:
- `/ddd:3-code-plan` - Detailed implementation plan per chunk
- `/ddd:4-code` - Implementation (with incremental commits)
- `/ddd:5-finish` - Testing, cleanup, final verification

---

## Notes for AI Assistants

### When Implementing

**Brick-by-brick approach**:
1. Define the stud (interface + contract)
2. Write tests (using the stud)
3. Implement (satisfy the tests)
4. Integrate (wire into ScaffoldService)

**Validation checks before committing**:
- [ ] Does this brick have ONE clear responsibility?
- [ ] Can this brick be regenerated from its contract?
- [ ] Are dependencies via interfaces, not implementations?
- [ ] Is this the simplest approach that works?
- [ ] Would a junior developer understand this?

**Red flags**:
- ⚠️ Brick depends on implementation, not interface
- ⚠️ Multiple responsibilities in one class
- ⚠️ Future-proofing (solving non-existent problems)
- ⚠️ Clever code (simpler approach available)

### When Regenerating

**To regenerate any brick**:
1. Read its interface (the stud)
2. Read its tests (the contract)
3. Regenerate implementation
4. Verify tests still pass
5. Other bricks unaffected (they depend on interface)

**Example**: Regenerate IpAllocator
```
Interface: IpAllocator.allocate(clusters, isMultiCluster) → Map<String, List<IP>>
Tests: IpAllocatorTest (single/multi-cluster, overlaps, boundaries)
Regenerate: New implementation satisfying tests
Verify: SemanticValidator still works (depends on interface)
```

---

## Appendix: Key Design Decisions

### Decision 1: Java over Python/Bash

**Rationale**:
- Type safety eliminates entire classes of bugs
- JTE provides compile-time template safety
- Record types perfect for immutable data
- Maven handles dependencies reliably

**Trade-off**: Slower initial development vs long-term maintainability

---

### Decision 2: Picocli over Custom CLI Parser

**Rationale**:
- Battle-tested, handles edge cases
- Automatic help generation
- Annotation-based (declarative)
- Validation built-in

**Trade-off**: Dependency vs custom code

---

### Decision 3: JTE over Envsubst/StringTemplate

**Rationale**:
- Compile-time template validation
- Type-safe contexts (no runtime errors)
- Clean separation (templates in resources/)

**Trade-off**: Learning curve vs runtime safety

---

### Decision 4: Three Validators over One

**Rationale**:
- Clear separation: structural/semantic/policy
- Each regeneratable independently
- Easier to test in isolation

**Trade-off**: More classes vs clarity

---

### Decision 5: Explicit IP Allocation

**Rationale**:
- Makes allocation visible (not hidden in Spec → Plan)
- Easier to test
- Easier to regenerate
- Spec says "no magic" (line 1357)

**Trade-off**: Extra class vs visibility

---

**Plan Version**: 1.0.0
**Last Updated**: 2025-11-03
**Status**: Ready for Phase 2 (Documentation)
 
## Document History

| Version | Date       | Author     | Changes                                                              |
|---------|------------|------------|----------------------------------------------------------------------|
| 1.2.0   | 2025-11-10 | repo-maint | Added data model strategy explaining hybrid record/builder approach. |
| 1.1.1   | 2025-11-04 | repo-maint | History sorted descending; prepended new entry per policy |
| 1.1.0   | 2025-11-04 | repo-maint | Added high-level details for cloud provider integration (Azure) |
| 1.0.0   | 2025-11-03 | repo-maint | Added YAML frontmatter and Document History per AGENTS.md rules |
