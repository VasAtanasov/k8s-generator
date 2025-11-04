---
status: Planning document
version: 1.4.0
scope: Implementation plan for the k8s-generator CLI (architecture, phases, files)
---

# Code Implementation Plan - k8s-generator CLI

---

## Summary

Building a Java 25 CLI tool that scaffolds complete Kubernetes learning environments using convention-over-configuration. The system implements a hybrid CLI-first approach with optional YAML specs for complex topologies.

**Original State**: Greenfield project - basic Maven structure, no source code
**Current State**: ✅ **Phase 2 COMPLETE** - 296 tests passing, all P0 items implemented
**Target State**: Fully functional CLI with 6 modular components following "bricks and studs" philosophy

### Implementation Status (Updated 2025-11-04)

| Phase | Status | Tests | Key Deliverables |
|-------|--------|-------|------------------|
| **Phase 1** | ✅ COMPLETE | 296 | CLI, Models, Parser, Validation (Structural), Rendering, I/O, Orchestrator |
| **Phase 2** | ✅ COMPLETE | Included | IP Allocator, VM Generator, Full Validation (Semantic/Policy), kubeadm support |
| **Phase 3** | ⚠️ DEFERRED | N/A | Management VM, Multi-cluster orchestration (future) |
| **Phase 4** | 🔴 NOT STARTED | N/A | YAML spec file reading (Jackson integration) |

**All P0 Critical Items**: ✅ IMPLEMENTED
- ✅ IP Allocation Algorithm (`SequentialIpAllocator` + 23 tests)
- ✅ Atomic File Generation (`SimpleAtomicFileWriter`)
- ✅ Validation Strategy Refinement (3-layer validation)

**Package Structure**: ✅ Matches specification + bonus packages (`ip/`, `orchestrator/`)

---

## Architecture Overview

### The Six Bricks (Self-Contained Modules)

Each brick is a self-contained package with clear public interfaces ("studs") and internal implementation:

```
com.k8s.generator/
├── cli/            # Brick 1: Command line interface (Picocli)
├── model/          # Brick 2: Domain models (immutable records)
├── conversion/     # Brick 3: Transformations (CLI→Spec, Spec→Plan)
├── validation/     # Brick 4: Quality gates (structural/semantic/policy)
├── rendering/      # Brick 5: Template engine (JTE integration)
├── io/             # Brick 6: File operations (read YAML, write files, copy resources)
└── orchestration/  # Orchestrator: Composes the flow
```

### Data Flow

```
CLI Args OR YAML Spec
    ↓
SpecConverter → GeneratorSpec (validated model)
    ↓
Validator → ValidationErrors (empty = valid)
    ↓
PlanBuilder → ScaffoldPlan (template-ready with allocated IPs)
    ↓
Renderer → Map<filename, content>
    ↓
OutputWriter → Files on disk
```

---

## Implementation Phases

We'll implement in 4 vertical slices, each adding capabilities incrementally:

### Phase 1: MVP (kind single-cluster) - ~3-4 days
**Goal**: Single command works end-to-end
```bash
k8s-gen --module m1 --type pt kind --out test-output/
```
**Delivers**: Working kind cluster generation with basic validation

### Phase 2: kubeadm + Full Validation - ~4-5 days
**Goal**: Multi-node clusters with complete validation
```bash
k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w --out test-output/
```
**Delivers**: 3-node kubeadm cluster with IP allocation and CNI selection

### Phase 3: Multi-cluster + Management - ~3-4 days
**Goal**: Multiple clusters with management VM
```bash
k8s-gen --spec multi-cluster.yaml --out test-output/
```
**Delivers**: Management VM + multiple clusters with merged kubeconfigs

### Phase 4: YAML Spec Support - ~2-3 days
**Goal**: Read YAML spec files
```bash
k8s-gen --spec m7.yaml --out test-output/
```
**Delivers**: Full YAML spec file support with same output as CLI

**Total Estimated Time**: 12-16 days

---

## Phase 1: MVP Implementation (kind single-cluster)

### Files to Create

#### Brick 1: CLI (Picocli Integration)

**`src/main/java/com/k8s/generator/cli/GenerateCommand.java`**
- **Purpose**: Picocli @Command entry point
- **Exports**: Annotated command class with options
- **Dependencies**: model.spec.*, Picocli
- **Key Methods**:
  - CLI option definitions (`--module`, `--type`, engine, `--out`, `--azure`)
  - Input validation (module format, type format)
- **Tests**: `tests/cli/GenerateCommandTest.java`

**Implementation Notes**:
```java
@Command(name = "k8s-gen", description = "Generate Kubernetes learning environments")
public class GenerateCommand implements Callable<Integer> {
    @Option(names = {"--module"}, required = true, description = "Module number (e.g., m1, m7)")
    String module;

    @Option(names = {"--type"}, required = true, description = "Type (e.g., pt, hw, exam)")
    String type;

    @Parameters(index = "0", description = "Cluster type: kind|minikube|kubeadm|mgmt")
    String clusterType;

    @Option(names = {"--out"}, description = "Output directory")
    String outDir;

    @Option(names = {"--azure"}, description = "Enable Azure integration (installs CLI)")
    boolean azure = false;

    // Future-proofing for other providers could look like:
    // @Option(names = {"--provider"}, description = "Enable cloud provider integration (e.g., aws, gcp)")
    // List<String> providers;

    @Override
    public Integer call() {
        // Delegate to ScaffoldService
        return new ScaffoldService().scaffold(this);
    }
}
```

#### Brick 2: Model (Domain Records)

**`src/main/java/com/k8s/generator/model/spec/GeneratorSpec.java`**
- **Purpose**: Root specification record
- **Exports**: `GeneratorSpec` immutable record
- **Dependencies**: ModuleInfo, ClusterSpec
- **Key Fields**: `ModuleInfo module, Optional<ManagementSpec> management, List<ClusterSpec> clusters`

**`src/main/java/com/k8s/generator/model/spec/ModuleInfo.java`**
- **Purpose**: Module metadata (num + type)
- **Exports**: `ModuleInfo` record with validation
- **Constructor Validation**:
```java
public ModuleInfo {
    if (!num.matches("m\\d+"))
        throw new IllegalArgumentException("Invalid module format: " + num);
    if (!type.matches("[a-z][a-z0-9-]*"))
        throw new IllegalArgumentException("Invalid type format: " + type);
}
```

**`src/main/java/com/k8s/generator/model/spec/ClusterSpec.java`**
- **Purpose**: Cluster specification
- **Exports**: `ClusterSpec` record
- **Key Fields**: `String name, Engine engine, Optional<CniType> cni, Optional<NodeCounts> nodes, Optional<String> firstIp, Optional<String> podNetwork, Optional<String> svcNetwork, Optional<VmOverrides> vmOverrides`

**`src/main/java/com/k8s/generator/model/plan/ScaffoldPlan.java`**
- **Purpose**: Template-ready plan with allocated IPs
- **Exports**: `ScaffoldPlan` record
- **Key Fields**: `ModuleInfo module, List<VmConfig> vms, Map<String, String> envVars, Set<String> providers`

**`src/main/java/com/k8s/generator/model/plan/VmConfig.java`**
- **Purpose**: VM configuration
- **Exports**: `VmConfig` record
- **Key Fields**: `String name, InetAddress ip, NodeRole role, int cpus, int memoryMb, String boxImage`
- **Validation**: cpus >= 1, memoryMb >= 1024

**`src/main/java/com/k8s/generator/model/plan/SizeProfile.java`**
- **Purpose**: Size presets (small/medium/large)
- **Exports**: Enum with CPU/memory/box mappings

**`src/main/java/com/k8s/generator/model/shared/Engine.java`**
- **Purpose**: Engine types
- **Exports**: Enum `NONE, KIND, MINIKUBE, KUBEADM`

**`src/main/java/com/k8s/generator/model/shared/NodeRole.java`**
- **Purpose**: Node roles
- **Exports**: Enum `MANAGEMENT, MASTER, WORKER`

#### Brick 3: Conversion (Transformations)

**`src/main/java/com/k8s/generator/conversion/SpecConverter.java`**
- **Purpose**: Interface for CLI → Spec conversion
- **Contract**:
```java
public interface SpecConverter {
    GeneratorSpec convert(GenerateCommand cmd);
}
```

**`src/main/java/com/k8s/generator/conversion/CliToSpec.java`**
- **Purpose**: Implementation of CLI → Spec conversion
- **Exports**: Implementation
- **Dependencies**: GenerateCommand, GeneratorSpec, ModuleInfo, ClusterSpec
- **Key Logic**:
  - Parse CLI args to ModuleInfo
  - Create single ClusterSpec from CLI args
  - If `--azure` is true, create a `ManagementSpec` with `providers` containing "azure".
  - Apply defaults (e.g., medium size if not specified)
  - Pure transformation - NO validation (Picocli handles structure, Validator handles business rules)

**`src/main/java/com/k8s/generator/conversion/PlanBuilder.java`**
- **Purpose**: Interface for Spec → Plan transformation
- **Contract**:
```java
public interface PlanBuilder {
    ScaffoldPlan build(GeneratorSpec spec);
}
```

**`src/main/java/com/k8s/generator/conversion/SpecToPlan.java`**
- **Purpose**: Implementation of Spec → Plan
- **Exports**: Implementation
- **Dependencies**: GeneratorSpec, ScaffoldPlan, VmConfig, SizeProfile
- **Key Logic**:
  - Determine sizing (from size profile)
  - Allocate IPs (use 192.168.56.10 for single-cluster MVP)
  - Build VmConfig list
  - Create environment variables map
  - Populate `providers` set from `GeneratorSpec`
  - Pure transformation - assumes validated spec

#### Brick 4: Validation (Quality Gates)

**`src/main/java/com/k8s/generator/validation/Validator.java`**
- **Purpose**: Interface for spec validation
- **Contract**:
```java
public interface Validator {
    List<ValidationError> validate(GeneratorSpec spec);
}
```

**`src/main/java/com/k8s/generator/validation/ValidationError.java`**
- **Purpose**: Error record
- **Exports**: Record with level (STRUCTURAL/SEMANTIC/POLICY), description, suggestions

**`src/main/java/com/k8s/generator/validation/StructuralValidator.java`**
- **Purpose**: Required fields validation
- **Exports**: Implementation
- **Dependencies**: GeneratorSpec
- **Key Checks** (MVP):
  - Module num/type present
  - Cluster name present
  - Engine specified
  - For kind: no extra fields required

#### Brick 5: Rendering (JTE Templates)

**`src/main/java/com/k8s/generator/rendering/Renderer.java`**
- **Purpose**: Interface for template rendering
- **Contract**:
```java
public interface Renderer {
    Map<String, String> render(ScaffoldPlan plan);
}
```

**`src/main/java/com/k8s/generator/rendering/JteRenderer.java`**
- **Purpose**: JTE integration
- **Exports**: Implementation
- **Dependencies**: ScaffoldPlan, JTE library
- **Key Logic**:
  - Initialize JTE engine
  - Render Vagrantfile.jte with plan context
  - Render bootstrap.sh.jte, passing provider info for conditional generation of files like `/etc/azure-env`.
  - Render .gitignore.jte
  - Return Map<filename, content>

**Templates** (resources):

**`src/main/resources/templates/Vagrantfile.jte`**
- **Purpose**: VM topology template
- **Context**: VagrantfileContext (moduleName, List<VmConfig>, envVars)
- **Output**: Vagrantfile defining VMs

**`src/main/resources/templates/bootstrap.sh.jte`**
- **Purpose**: Bootstrap script template
- **Context**: Same as Vagrantfile
- **Output**: Shell script for VM provisioning

**`src/main/resources/templates/.gitignore.jte`**
- **Purpose**: Generated .gitignore
- **Output**: `.vagrant/`, `*.log`, `scripts/bootstrap.env.local`

#### Brick 6: I/O (File Operations)

**`src/main/java/com/k8s/generator/io/OutputWriter.java`**
- **Purpose**: Write rendered files and scaffold hook directories.
- **Exports**: `writeFiles(Map<String, String> files, Path outDir)`, `scaffoldHooks(Path outDir)`
- **Dependencies**: Java NIO
- **Key Logic**:
  - `writeFiles`:
    - Create output directory if needed.
    - Check for collisions (fail if exists).
    - Write each file from the input map.
    - Set executable permissions on scripts.
  - `scaffoldHooks`:
    - **Create directories** if they don't exist:
      - `scripts/bootstrap.pre.d/common`
      - `scripts/bootstrap.post.d/common`
      - `scripts/env/cluster`
      - `scripts/env/role`
      - `scripts/env/cluster-role`
    - **Create stub files** if they don't exist:
      - `scripts/bootstrap.env.local` (with example content)
      - `scripts/bootstrap.pre.local.sh` (with shebang, `set -Eeuo pipefail`, `echo` statement, and executable permissions)
      - `scripts/bootstrap.post.local.sh` (with shebang, `set -Eeuo pipefail`, `echo` statement, and executable permissions)
    - **Create `README.md` files** with explanatory content in each of the following directories if they don't exist:
      - `scripts/bootstrap.pre.d`
      - `scripts/bootstrap.post.d`
      - `scripts/env/cluster`
      - `scripts/env/role`
      - `scripts/env/cluster-role`

**`src/main/java/com/k8s/generator/io/ResourceCopier.java`**
- **Purpose**: Copy install scripts from resources
- **Exports**: `copyScripts(ScaffoldPlan plan, Path outDir)`
- **Dependencies**: Java NIO, ClassLoader
- **Key Logic**:
  - Determine required scripts based on engine and providers in the plan.
  - Copy scripts from `src/main/resources/scripts/` to `{outDir}/scripts/`
  - Example scripts: `install_kubectl.sh`, `install_docker.sh`, `install_kind.sh`, `install_azure_cli.sh`

#### Orchestrator

**`src/main/java/com/k8s/generator/orchestration/ScaffoldService.java`**
- **Purpose**: Main orchestration - knows the full flow
- **Exports**: `int scaffold(GenerateCommand cmd)`
- **Dependencies**: All bricks
- **Flow**:
```java
public int scaffold(GenerateCommand cmd) {
    // 1. Convert CLI → Spec
    GeneratorSpec spec = new CliToSpec().convert(cmd);

    // 2. Validate
    List<ValidationError> errors = new StructuralValidator().validate(spec);
    if (!errors.isEmpty()) {
        // Print errors, return exit code 2
        return 2;
    }

    // 3. Build plan
    ScaffoldPlan plan = new SpecToPlan().build(spec);

    // 4. Render templates
    Map<String, String> files = new JteRenderer().render(plan);

    // 5. Write files
    Path outDir = determineOutDir(cmd, spec);
    OutputWriter writer = new OutputWriter();
    writer.writeFiles(files, outDir);

    // 6. Copy scripts
    new ResourceCopier().copyScripts(plan, outDir);

    // 7. Scaffold hooks
    writer.scaffoldHooks(outDir);

    return 0; // Success
}
```

**`src/main/java/com/k8s/generator/Main.java`**
- **Purpose**: Entry point
- **Exports**: main() method
- **Dependencies**: Picocli, GenerateCommand
- **Implementation**:
```java
public static void main(String[] args) {
    int exitCode = new CommandLine(new GenerateCommand()).execute(args);
    System.exit(exitCode);
}
```

#### Resources (Install Scripts)

**`src/main/resources/scripts/install_kubectl.sh`**
- Download and install kubectl
- Set executable permissions
- Verify installation

**`src/main/resources/scripts/install_docker.sh`**
- Install Docker CE
- Add user to docker group
- Start docker service

**`src/main/resources/scripts/install_kind.sh`**
- Download and install kind binary
- Verify installation

### Tests (MVP)

**`src/test/java/com/k8s/generator/EndToEndKindTest.java`**
- **Purpose**: Full flow test for kind generation
- **Test Scenario**:
  1. Create GenerateCommand with `--module m1 --type pt kind`
  2. Call ScaffoldService.scaffold()
  3. Assert exit code 0
  4. Assert files exist: Vagrantfile, scripts/bootstrap.sh, scripts/install_*.sh
  5. Assert Vagrantfile contains expected VM config
  6. Assert bootstrap.sh contains kind setup

**`src/test/java/com/k8s/generator/conversion/CliToSpecTest.java`**
- **Purpose**: Unit test for CLI → Spec conversion
- **Tests**:
  - Valid CLI args → correct GeneratorSpec
  - Module/type parsing
  - Default values applied

**`src/test/java/com/k8s/generator/conversion/SpecToPlanTest.java`**
- **Purpose**: Unit test for Spec → Plan transformation
- **Tests**:
  - Single kind cluster → 1 VM at .10
  - Sizing applied correctly
  - Environment variables populated

### Phase 1 Success Criteria

- [ ] Command executes: `k8s-gen --module m1 --type pt kind --out test-output/`
- [ ] Output directory created at `test-output/`
- [ ] Vagrantfile generated with 1 VM at 192.168.56.10
- [ ] bootstrap.sh script generated
- [ ] Install scripts copied to `test-output/scripts/`
- [ ] All tests passing (make check)

---

## Phase 2: kubeadm + Full Validation

### New Files to Create

#### Model Additions

**`src/main/java/com/k8s/generator/model/spec/NodeCounts.java`**
- **Purpose**: Master/worker node counts
- **Exports**: Record with validation (masters >= 1, workers >= 0)

**`src/main/java/com/k8s/generator/model/spec/VmOverrides.java`**
- **Purpose**: Per-role VM overrides
- **Exports**: Record with Optional<RoleOverride> for master/worker

**`src/main/java/com/k8s/generator/model/shared/CniType.java`**
- **Purpose**: CNI options
- **Exports**: Enum `CALICO, FLANNEL, WEAVE, CILIUM, ANTREA`

#### Conversion Additions

**`src/main/java/com/k8s/generator/conversion/IpAllocator.java`**
- **Purpose**: Explicit IP allocation logic
- **Exports**: `allocate(List<ClusterSpec>, boolean isMultiCluster) → Map<String, List<InetAddress>>`
- **Dependencies**: ipaddress library
- **Key Logic**:
  - Single-cluster: use 192.168.56.10 as base
  - Multi-cluster: require explicit firstIp for each
  - Sequential allocation: masters first, then workers
  - Return Map<clusterName, List<IPs>>

#### Validation Additions

**`src/main/java/com/k8s/generator/validation/SemanticValidator.java`**
- **Purpose**: Business rules validation
- **Exports**: Implementation
- **Key Checks**:
  - kubeadm requires CNI specified
  - kubeadm requires nodes specified
  - No IP collisions (use IpAllocator to check)
  - No CIDR overlaps (Pod vs Service networks)
  - Subnet boundary checks (IPs don't exceed .254)
  - Reserved IP checks (avoid .1, .2, .5)

**`src/main/java/com/k8s/generator/validation/PolicyValidator.java`**
- **Purpose**: Engine/tool compatibility validation
- **Exports**: Implementation
- **Key Checks**:
  - kind: no CNI or nodes allowed
  - minikube: no CNI or nodes allowed
  - kubeadm: CNI and nodes required
  - mgmt: no cluster engine allowed

**`src/main/java/com/k8s/generator/validation/CompositeValidator.java`**
- **Purpose**: Aggregator for all three validators
- **Exports**: Implementation combining Structural + Semantic + Policy
- **Key Logic**:
```java
public List<ValidationError> validate(GeneratorSpec spec) {
    List<ValidationError> errors = new ArrayList<>();
    errors.addAll(new StructuralValidator().validate(spec));
    errors.addAll(new SemanticValidator().validate(spec));
    errors.addAll(new PolicyValidator().validate(spec));
    return errors;
}
```

#### Templates Additions

**`src/main/resources/templates/engines/kubeadm/bootstrap-master.jte`**
- **Purpose**: Kubeadm master node bootstrap
- **Logic**: kubeadm init with Pod/Service CIDRs, CNI installation

**`src/main/resources/templates/engines/kubeadm/bootstrap-worker.jte`**
- **Purpose**: Kubeadm worker node bootstrap
- **Logic**: kubeadm join with token from master

#### Resources Additions

**`src/main/resources/scripts/install_containerd.sh`**
**`src/main/resources/scripts/install_kubeadm.sh`**
**`src/main/resources/scripts/install_calico.sh`**

### Files to Modify

**`src/main/java/com/k8s/generator/conversion/SpecToPlan.java`**
- **Change**: Use IpAllocator for IP assignment
- **Before**: Hardcoded .10 for single VM
- **After**: Call IpAllocator.allocate(), iterate through returned IPs for each node

**`src/main/java/com/k8s/generator/orchestration/ScaffoldService.java`**
- **Change**: Use CompositeValidator instead of just StructuralValidator
- **Before**: `new StructuralValidator().validate(spec)`
- **After**: `new CompositeValidator().validate(spec)`

### Tests

**`src/test/java/com/k8s/generator/validation/SemanticValidatorTest.java`**
- IP collision detection
- CIDR overlap detection
- Subnet boundary checks

**`src/test/java/com/k8s/generator/validation/PolicyValidatorTest.java`**
- Engine/tool compatibility rules

**`src/test/java/com/k8s/generator/conversion/IpAllocatorTest.java`**
- Single-cluster allocation
- Multi-cluster allocation
- Overlap detection
- Boundary checks

**`src/test/java/com/k8s/generator/EndToEndKubeadmTest.java`**
- Full flow test for 3-node kubeadm cluster

### Phase 2 Success Criteria

- [ ] Command executes: `k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w --out test-output/`
- [ ] Vagrantfile with 3 VMs (1 master at .10, 2 workers at .11, .12)
- [ ] Validation catches missing CNI
- [ ] Validation catches IP collisions
- [ ] Validation catches CIDR overlaps
- [ ] All tests passing

---

## Phase 3: Multi-cluster + Management

### New Files to Create

#### Model Additions

**`src/main/java/com/k8s/generator/model/spec/ManagementSpec.java`**
- **Purpose**: Management VM specification
- **Exports**: Record with name, tools, aggregate_kubeconfigs flag, and cloud providers
- **Key Fields**: `String name, List<String> tools, boolean aggregateKubeconfigs, Optional<List<String>> providers`

### Files to Modify

**`src/main/java/com/k8s/generator/conversion/SpecToPlan.java`**
- **Change**: Add management VM at IP .5 when present
- **Logic**: Check if ManagementSpec exists, add VmConfig with NodeRole.MANAGEMENT at 192.168.56.5

**`src/main/java/com/k8s/generator/validation/SemanticValidator.java`**
- **Change**: Enforce firstIp requirement for multi-cluster
- **Logic**: If clusters.size() > 1, ensure every cluster has firstIp

#### Templates Additions

**`src/main/resources/templates/engines/none/bootstrap-mgmt.jte`**
- **Purpose**: Management machine bootstrap
- **Logic**: Install tools, merge kubeconfigs, set up kctx/kubens

#### Resources Additions

**`src/main/resources/scripts/merge-kubeconfigs.sh`**
- **Purpose**: Merge kubeconfig files from multiple clusters

### Tests

**`src/test/java/com/k8s/generator/EndToEndMultiClusterTest.java`**
- Management VM + 2 kubeadm clusters
- Verify mgmt at .5, clusters at specified firstIp ranges
- Verify kubeconfig merge script present

### Phase 3 Success Criteria

- [ ] Command with YAML spec executes
- [ ] Management VM at .5
- [ ] Multiple clusters with non-overlapping IPs
- [ ] Kubeconfig merge script generated

---

## Phase 4: YAML Spec Support

### New Files to Create

**`src/main/java/com/k8s/generator/io/SpecReader.java`**
- **Purpose**: Read YAML spec files
- **Exports**: `GeneratorSpec read(Path specFile)`
- **Dependencies**: Jackson YAML library
- **Key Logic**:
  - Parse YAML to GeneratorSpec
  - Use Jackson for deserialization
  - Validate schema

### Files to Modify

**`src/main/java/com/k8s/generator/cli/GenerateCommand.java`**
- **Change**: Add `--spec` option
- **Logic**: If --spec provided, ignore other options and use spec file

**`src/main/java/com/k8s/generator/orchestration/ScaffoldService.java`**
- **Change**: Handle both CLI and spec file paths
- **Logic**:
```java
public int scaffold(GenerateCommand cmd) {
    GeneratorSpec spec;
    if (cmd.specFile != null) {
        spec = new SpecReader().read(cmd.specFile);
    } else {
        spec = new CliToSpec().convert(cmd);
    }
    // ... rest of flow unchanged
}
```

### Tests

**`src/test/java/com/k8s/generator/io/SpecReaderTest.java`**
- Parse valid YAML
- Error on invalid YAML
- Error on schema violations

**`src/test/resources/specs/`** - Test YAML files

### Phase 4 Success Criteria

- [ ] Can read YAML spec: `k8s-gen --spec m7.yaml --out test-output/`
- [ ] Same output as equivalent CLI command
- [ ] Validation errors from YAML parsing

---

## Build Configuration

### Maven Dependencies to Add to pom.xml

```xml
<properties>
    <maven.compiler.source>25</maven.compiler.source>
    <maven.compiler.target>25</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
</properties>

<dependencies>
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
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>3.24.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.11.0</version>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.1.2</version>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.k8s.generator.Main</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## Testing Strategy

### Unit Tests (60% of codebase)

Focus on individual brick logic without dependencies.

#### Conversion Tests
- **CliToSpecTest**: CLI args → Spec mapping (valid inputs, defaults, edge cases)
- **SpecToPlanTest**: Spec → Plan transformation (single/multi-node, sizing, env vars)
- **IpAllocatorTest**: IP allocation (single/multi-cluster, overlaps, boundaries)

#### Validation Tests
- **StructuralValidatorTest**: Required fields, schema violations
- **SemanticValidatorTest**: IP collisions, CIDR overlaps, subnet boundaries, reserved IPs
- **PolicyValidatorTest**: Engine/tool compatibility, forbidden combinations

#### Model Tests
- **RecordValidationTest**: Constructor constraints (NodeCounts > 0, valid module format, memory >= 1024, cpus >= 1)

**Coverage Target**: 60% of codebase

### Integration Tests (30% of codebase)

Multiple bricks working together.

#### Flow Tests
- **ValidationFlowTest**: Spec → all validators → aggregated errors
- **ConversionFlowTest**: CLI → Spec → Plan (verify IPs allocated correctly)
- **TemplateRenderingTest**: Plan → JTE → verify Vagrantfile structure

#### End-to-End Tests
- **EndToEndKindTest**: CLI → Validate → Plan → Render → Write (verify files exist, content correct)
- **EndToEndKubeadmTest**: Multi-node cluster generation (3 VMs, correct IPs)
- **EndToEndMultiClusterTest**: Management + 2 clusters (verify topology)

**Coverage Target**: 30% of codebase

### User Testing (10% of codebase)

Real usage, golden file comparison.

#### CLI Smoke Tests
- **CliSmokeTest**: Actual CLI commands work (Picocli integration)
- **GoldenFileTest**: Compare generated output vs expected (text diff)

#### Manual Validation

After each milestone:
1. Generate output: `java -jar target/k8s-generator.jar --module m1 --type pt kind --out test-output/`
2. Verify files: `test-output/Vagrantfile`, `test-output/scripts/bootstrap.sh`
3. Test with Vagrant: `cd test-output && vagrant up`
4. Verify cluster: `vagrant ssh -c 'kubectl get nodes'`

**Coverage Target**: 10% of codebase

### Test Execution

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=EndToEndKindTest

# Run with coverage
mvn test jacoco:report
```

---

## Agent Orchestration Strategy

### Primary Agents

**modular-builder** - For module implementation:
```
Task modular-builder: "Implement [brick name] according to spec in
code_plan.md. Create all files in the brick package with tests."
```

**zen-architect** - For architecture reviews:
```
Task zen-architect: "Review [module] for philosophy compliance:
ruthless simplicity, modular design, clear interfaces"
```

**test-coverage** - For test planning:
```
Task test-coverage: "Suggest comprehensive test cases for [module]
covering edge cases, error conditions, integration points"
```

**bug-hunter** - For debugging:
```
Task bug-hunter: "Debug issue with [specific problem], analyze root cause,
suggest fix"
```

### Execution Strategy

**Sequential** (dependencies between phases):
```
Phase 1 MVP → Phase 2 kubeadm → Phase 3 multi-cluster → Phase 4 YAML
```

Within each phase, bricks can be built in parallel where dependencies allow:

**Phase 1 Parallel Opportunities**:
```
Model brick (foundation)
    ↓
CLI brick ⎤
Conversion brick ⎥ → Can build in parallel
Validation brick ⎦
    ↓
Rendering brick ⎤
I/O brick ⎦ → Can build in parallel
    ↓
Orchestrator (wires everything together)
```

Use sequential for this project because:
- Each phase builds on previous (MVP → kubeadm → multi-cluster → YAML)
- Within phase, some parallelization possible but orchestrator must be last
- Clearer for user to see incremental progress

---

## Philosophy Compliance

### Ruthless Simplicity

**Start Minimal** (Phase 1 MVP):
- Only kind generation (single cluster, no YAML)
- Basic structural validation only
- Hardcoded IPs for single cluster
- No future-proofing (no export-spec, no provisioning, no cloud)

**Avoid Future-Proofing** (What we're NOT building):
- External cloud clusters (AKS/EKS/GKE) - marked as future
- Auto-CIDR allocation - explicit IPs only
- Per-cluster host networks - single subnet for v1
- Provisioning/verification/destroy - scripts only, no automation
- Export-spec command - not needed until CLI-first proves itself

**Clear Over Clever**:
- Explicit IP allocation (IpAllocator class, not hidden in Spec → Plan)
- Three separate validators (not one mega-class)
- Pure transformations (Converters have no side effects)
- Immutable records (can't modify state accidentally)

**Evidence**:
- Each brick < 500 lines
- Each interface < 20 lines
- No inheritance hierarchies
- No reflection or metaprogramming
- No design patterns for patterns' sake

### Modular Design (Bricks and Studs)

**Bricks** (Self-Contained Modules):

1. **CLI Brick** (`cli/`)
   - Can regenerate: Parse different arguments, change command structure
   - Stud: `GenerateCommand` class with Picocli annotations
   - Regeneratable from: "Parse CLI args for module/type/engine/options"

2. **Model Brick** (`model/`)
   - Can regenerate: Add fields, change validation logic
   - Stud: Public records in spec/, plan/, shared/
   - Regeneratable from: "Define immutable data structures for spec and plan"

3. **Conversion Brick** (`conversion/`)
   - Can regenerate: Change transformation logic
   - Stud: `SpecConverter`, `PlanBuilder`, `IpAllocator` interfaces
   - Regeneratable from: "Convert CLI → Spec, Spec → Plan, allocate IPs"

4. **Validation Brick** (`validation/`)
   - Can regenerate: Add new validation rules, change error messages
   - Stud: `Validator` interface, `ValidationError` record
   - Regeneratable from: "Validate structural/semantic/policy rules, return errors"

5. **Rendering Brick** (`rendering/`)
   - Can regenerate: Switch template engines, change templates
   - Stud: `Renderer` interface
   - Regeneratable from: "Render JTE templates to Map<filename, content>"

6. **I/O Brick** (`io/`)
   - Can regenerate: Change file formats, add compression
   - Stud: `SpecReader`, `OutputWriter`, `ResourceCopier`
   - Regeneratable from: "Read YAML specs, write files, copy resources"

**Studs** (Clear Interfaces):

Each brick exposes exactly ONE public interface (or a set of related records):
- Other bricks depend on interface, not implementation
- Can swap implementations without affecting consumers
- AI can regenerate implementation from interface contract

**Regeneratable Test**:

Can I describe a brick's contract and have AI rebuild it?

✅ **YES** for each brick:
- `Validator`: "Given GeneratorSpec, return List<ValidationError>"
- `PlanBuilder`: "Given GeneratorSpec, return ScaffoldPlan with allocated IPs"
- `Renderer`: "Given ScaffoldPlan, return Map<filename, content>"

Each brick's contract is 10-20 lines. Implementation can be fully regenerated.

---

## Commit Strategy

### Phase 1: MVP (kind generation)

**Commit 1**: Setup project structure
```
chore: Initialize Maven project with dependencies

- Add pom.xml with Java 25, Picocli, JTE, ipaddress, Jackson
- Configure maven-shade-plugin for JAR packaging
- Add .gitignore for target/, .idea/, *.iml
```

**Commit 2**: Add domain models
```
feat: Add core domain models

- Add GeneratorSpec, ModuleInfo, ClusterSpec (spec models)
- Add ScaffoldPlan, VmConfig, SizeProfile (plan models)
- Add Engine, NodeRole enums (shared)
- All models are immutable records with validation
- Tests: RecordValidationTest
```

**Commit 3**: Add CLI brick
```
feat: Add Picocli CLI command

- Add GenerateCommand with @Command annotations
- Options: --module, --type, cluster type (positional), --out
- Basic structure validation via Picocli
- Tests: GenerateCommandTest
```

**Commit 4**: Add conversion brick
```
feat: Add CLI to Spec and Spec to Plan converters

- Add SpecConverter interface + CliToSpec implementation
- Add PlanBuilder interface + SpecToPlan implementation
- Pure transformations, no validation
- Hardcoded .10 IP for single-cluster MVP
- Tests: CliToSpecTest, SpecToPlanTest
```

**Commit 5**: Add validation brick
```
feat: Add structural validation

- Add Validator interface + ValidationError record
- Add StructuralValidator (required fields only for MVP)
- Tests: StructuralValidatorTest
```

**Commit 6**: Add rendering brick
```
feat: Add JTE template rendering

- Add Renderer interface + JteRenderer implementation
- Add Vagrantfile.jte template (VM topology)
- Add bootstrap.sh.jte template (kind setup)
- Add .gitignore.jte template
- Tests: TemplateRenderingTest
```

**Commit 7**: Add I/O brick
```
feat: Add file operations

- Add OutputWriter (write files to disk)
- Add ResourceCopier (copy install scripts)
- Add install scripts: kubectl, docker, kind
- Tests: OutputWriterTest, ResourceCopierTest
```

**Commit 8**: Add orchestrator
```
feat: Add ScaffoldService orchestrator

- Wire all bricks together in flow
- Add Main.java entry point
- Complete end-to-end flow: CLI → Spec → Validate → Plan → Render → Write
- Tests: EndToEndKindTest
```

**Commit 9**: Fix issues found in testing
```
fix: Address end-to-end test failures

- [List specific fixes]
- All tests passing
```

**Commit 10**: Phase 1 complete
```
chore: Phase 1 MVP complete - kind generation works

Summary:
- CLI: k8s-gen --module m1 --type pt kind
- Output: Vagrantfile + scripts for single kind VM
- Tests: 15 unit tests, 3 integration tests, 1 E2E test
- All tests passing

Ready for Phase 2: kubeadm + full validation
```

### Phase 2: kubeadm + Full Validation

**Commit 11**: Add model extensions
```
feat: Add NodeCounts, VmOverrides, CniType models

- NodeCounts record with validation
- VmOverrides for per-role customization
- CniType enum (CALICO, FLANNEL, WEAVE, CILIUM, ANTREA)
- Tests: Extended RecordValidationTest
```

**Commit 12**: Add IP allocator
```
feat: Add explicit IP allocation

- Add IpAllocator class
- Single-cluster: use .10 as base
- Multi-cluster: require explicit firstIp for each
- Sequential allocation: masters first, then workers
- Tests: IpAllocatorTest
```

**Commit 13**: Add semantic validation
```
feat: Add semantic validation

- Add SemanticValidator
- Checks: IP collisions, CIDR overlaps, subnet boundaries, reserved IPs
- Checks: kubeadm requires CNI and nodes
- Tests: SemanticValidatorTest
```

**Commit 14**: Add policy validation
```
feat: Add policy validation

- Add PolicyValidator
- Checks: engine/tool compatibility
- Checks: kind/minikube forbid CNI/nodes
- Tests: PolicyValidatorTest
```

**Commit 15**: Add composite validator
```
feat: Add CompositeValidator aggregator

- Combine Structural + Semantic + Policy
- Update ScaffoldService to use CompositeValidator
- Tests: ValidationFlowTest
```

**Commit 16**: Add kubeadm templates
```
feat: Add kubeadm templates and install scripts

- Add bootstrap-master.jte (kubeadm init + CNI)
- Add bootstrap-worker.jte (kubeadm join)
- Add install scripts: containerd, kubeadm, calico
- Tests: Template rendering for kubeadm
```

**Commit 17**: Update SpecToPlan to use IpAllocator
```
feat: Use IpAllocator in SpecToPlan

- Replace hardcoded .10 with IpAllocator
- Handle multi-node clusters (masters + workers)
- Tests: SpecToPlanTest extended for multi-node
```

**Commit 18**: Phase 2 complete
```
chore: Phase 2 complete - kubeadm + full validation

Summary:
- CLI: k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w
- Output: 3-node kubeadm cluster with validation
- Validation: Structural + Semantic + Policy
- Tests: 28 unit tests, 6 integration tests, 2 E2E tests
- All tests passing

Ready for Phase 3: multi-cluster + management
```

### Phase 3: Multi-cluster + Management

**Commit 19**: Add ManagementSpec model
```
feat: Add ManagementSpec for management VM

- ManagementSpec record
- Optional tools list (mgmt-only extras)
- aggregate_kubeconfigs flag
- Tests: RecordValidationTest extended
```

**Commit 20**: Add management VM to plan
```
feat: Add management VM at .5 in SpecToPlan

- Check for ManagementSpec
- Add VmConfig with NodeRole.MANAGEMENT at .5
- Tests: SpecToPlanTest with management
```

**Commit 21**: Enforce firstIp for multi-cluster
```
feat: Require firstIp for multi-cluster in SemanticValidator

- If clusters.size() > 1, every cluster needs firstIp
- Clear error message with suggestion
- Tests: SemanticValidatorTest multi-cluster cases
```

**Commit 22**: Add management templates
```
feat: Add management VM templates and scripts

- Add bootstrap-mgmt.jte
- Add merge-kubeconfigs.sh script
- Tests: Template rendering for management
```

**Commit 23**: Phase 3 complete
```
chore: Phase 3 complete - multi-cluster + management

Summary:
- CLI: k8s-gen --spec multi-cluster.yaml
- Output: Management VM + multiple clusters
- Management at .5, clusters with explicit firstIp
- Tests: 32 unit tests, 8 integration tests, 3 E2E tests
- All tests passing

Ready for Phase 4: YAML spec support
```

### Phase 4: YAML Spec Support

**Commit 24**: Add SpecReader
```
feat: Add YAML spec file reader

- Add SpecReader using Jackson YAML
- Parse YAML to GeneratorSpec
- Schema validation
- Tests: SpecReaderTest
```

**Commit 25**: Update CLI to support --spec
```
feat: Add --spec option to CLI

- GenerateCommand accepts --spec <file>
- Mutually exclusive with other options
- Tests: CliSmokeTest with --spec
```

**Commit 26**: Update ScaffoldService to handle spec files
```
feat: Support spec files in ScaffoldService

- Check if --spec provided
- Use SpecReader if spec file, CliToSpec if CLI args
- Rest of flow unchanged
- Tests: EndToEndSpecFileTest
```

**Commit 27**: Phase 4 complete
```
chore: Phase 4 complete - YAML spec support

Summary:
- CLI: k8s-gen --spec m7.yaml
- Same output as equivalent CLI command
- Tests: 36 unit tests, 9 integration tests, 4 E2E tests
- All tests passing

Ready for release: v1.0.0
```

---

## Risk Assessment

### High Risk Changes

**IP Allocation Logic**:
- **Risk**: Off-by-one errors, subnet boundary violations
- **Mitigation**: Comprehensive unit tests with boundary cases, use ipaddress library for validation

**Template Rendering**:
- **Risk**: JTE template syntax errors, invalid Vagrantfile output
- **Mitigation**: Golden file tests, validate output with `vagrant validate`

**Validation Gaps**:
- **Risk**: Missing edge cases, incorrect validation logic
- **Mitigation**: Test-driven development, review spec for all validation requirements

### Dependencies to Watch

**Picocli** (4.7.5):
- Stable library, widely used
- Version constraint: >= 4.7.0

**JTE** (3.1.9):
- Compile-time template engine
- Version constraint: >= 3.1.0

**ipaddress** (5.4.0):
- Java library for IP/CIDR handling
- Version constraint: >= 5.0.0

**Jackson YAML** (2.15.2):
- YAML parsing
- Version constraint: >= 2.15.0

### Breaking Changes

None - this is a greenfield project (v1.0.0).

Future versions may:
- Change YAML spec schema (breaking)
- Change CLI options (breaking)
- Change generated file structure (potentially breaking for users who parse Vagrantfile)

---

## Success Criteria

Code is ready when:

- [ ] All documented behavior implemented (per GENERATOR-ARCHITECTURE.md)
- [ ] All tests passing (unit + integration + E2E)
- [ ] User testing works as documented:
  - [ ] `k8s-gen --module m1 --type pt kind` generates working kind VM
  - [ ] `k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w` generates 3-node cluster
  - [ ] `k8s-gen --spec multi-cluster.yaml` generates multi-cluster topology
  - [ ] `vagrant up` works with generated Vagrantfile
  - [ ] Clusters come up correctly
- [ ] No regressions in existing functionality
- [ ] Code follows philosophy principles:
  - [ ] Each brick < 500 lines
  - [ ] Clear interfaces (studs)
  - [ ] Regeneratable from specs
  - [ ] No future-proofing
  - [ ] Immutable data structures
- [ ] Ready for Phase 4 implementation

---

## Next Steps

✅ **Code plan complete and detailed**

**Summary**:
- **Files to create**: 50+ Java files across 6 bricks + orchestrator
- **Implementation phases**: 4 (MVP → kubeadm → multi-cluster → YAML)
- **Tests**: ~36 unit tests, ~9 integration tests, ~4 E2E tests
- **Estimated commits**: 27 across all phases
- **Total estimated time**: 12-16 days

⚠️ **USER APPROVAL REQUIRED**

Please review the code plan above. Key decisions:

1. **Architecture**: 6 modular bricks + orchestrator (follows bricks/studs philosophy)
2. **Phasing**: 4 incremental phases (MVP → kubeadm → multi-cluster → YAML)
3. **Testing**: 60% unit / 30% integration / 10% E2E
4. **Philosophy**: Ruthless simplicity (no future-proofing), modular design (regeneratable bricks)

When approved, proceed to implementation:

```
/ddd:4-code
```

Phase 4 will implement the plan incrementally, with authorization required for each commit.

---

## Document History

| Version | Date       | Author     | Changes                                                         |
|---------|------------|------------|-----------------------------------------------------------------|
| 1.4.0   | 2025-11-04 | repo-maint | Expanded hook scaffolding details in I/O brick to match shell script |
| 1.3.0   | 2025-11-04 | repo-maint | Added bootstrap hook scaffolding to I/O brick in code plan |
| 1.2.0   | 2025-11-04 | repo-maint | Added cloud provider integration details (Azure) to code plan |
| 1.1.1   | 2025-11-04 | repo-maint | Moved Document History to final section; bumped version; minor compliance/formatting fixes |
| 1.1.0   | 2025-11-04 | repo-maint | Updated implementation status: Phase 1 & 2 complete (296 tests), added status summary table |
| 1.0.0   | 2025-11-03 | repo-maint | Added YAML frontmatter and Document History per AGENTS.md rules |
