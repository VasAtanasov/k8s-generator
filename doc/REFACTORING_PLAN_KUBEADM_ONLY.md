---
status: Refactoring Plan
version: 1.0.0
scope: Remove kind and minikube support, simplify to kubeadm-only architecture
date: 2025-11-11
---

# k8s-generator Refactoring Plan: Kubeadm-Only Architecture

## Executive Summary

**Objective**: Simplify k8s-generator by removing support for kind and minikube cluster engines, retaining only kubeadm and management-only modes.

**Rationale**:
- Reduce maintenance burden by supporting only one real cluster engine (kubeadm)
- Eliminate unnecessary multi-engine abstractions
- Align with educational focus (manual kubeadm setup teaches Kubernetes fundamentals)
- Simplify codebase by 15-20% (estimated)

**Scope**: Breaking change - no backward compatibility maintained

**Estimated Effort**: ~4 hours of focused development work

**Risk Level**: Medium (well-defined changes, comprehensive test coverage)

---

## Table of Contents

1. [Architecture Impact Analysis](#architecture-impact-analysis)
2. [Refactoring Phases](#refactoring-phases)
3. [Detailed Implementation Steps](#detailed-implementation-steps)
4. [File-by-File Change Manifest](#file-by-file-change-manifest)
5. [Testing Strategy](#testing-strategy)
6. [Rollback Plan](#rollback-plan)
7. [Philosophy Alignment](#philosophy-alignment)

---

## Architecture Impact Analysis

### Current State

The application currently supports 4 cluster types via a sealed interface:
- `Kind` - Kubernetes in Docker (single-node)
- `Minikube` - Local Kubernetes (single-node)
- `Kubeadm` - Manual multi-node Kubernetes setup ✅ **KEEP**
- `NoneCluster` (mgmt) - Management-only, no cluster ✅ **KEEP**

### Target State

Simplified 2-engine architecture:
- `Kubeadm` - Multi-node Kubernetes clusters (primary use case)
- `NoneCluster` (mgmt) - Management-only machines

### Key Architectural Changes

#### 1. Model Layer - Minimal Changes
- **Delete**: `Kind.java`, `Minikube.java` (2 files)
- **Simplify**: `ClusterType.java` - remove kind/minikube from factory methods
- **Keep**: Sealed interface pattern (enables future cloud engines if needed)

#### 2. Validation Layer - Streamlined Logic
- **Simplify**: Pattern matching from 4-way to 2-way switches
- **Delete**: Kind/minikube-specific CNI validation (lines 147-179 in PolicyValidator)
- **Delete**: Kind/minikube node constraint validation (lines 155-156 in SemanticValidator)

#### 3. Parser/CLI Layer - Clear Error Messages
- **Update**: CLI parameter descriptions (remove kind/minikube examples)
- **Enhance**: Validation to reject kind/minikube with helpful error messages
- **Simplify**: Cluster type parsing logic

#### 4. Assets - Significant Cleanup
- **Delete**: 6 template files (kind/minikube bootstrap and Vagrantfile templates)
- **Delete**: 2 installer scripts (install_kind.sh, install_minikube.sh)
- **Delete**: Template snippets specific to kind/minikube

#### 5. Tests - Focused Coverage
- **Delete**: All kind/minikube test cases
- **Retain**: Kubeadm and management-only test coverage
- **Target**: Maintain ≥60% code coverage

### Dependency Map

```
Components Affected by Removal:
├── Model (HIGH IMPACT)
│   ├── ClusterType.java - factory methods
│   ├── Kind.java - DELETE
│   └── Minikube.java - DELETE
│
├── Validation (MEDIUM IMPACT)
│   ├── PolicyValidator.java - CNI rules, VM predictions
│   └── SemanticValidator.java - node constraints
│
├── Parser/CLI (MEDIUM IMPACT)
│   ├── CliToSpec.java - cluster type parsing
│   └── GenerateCommand.java - help text
│
├── Utilities (LOW IMPACT)
│   └── ToolInstallers.java - installer mappings
│
├── Templates (DELETE)
│   ├── bootstrap/kind.sh.tpl
│   ├── bootstrap/minikube.sh.tpl
│   ├── vagrantfile/kind.rb.tpl
│   ├── vagrantfile/minikube.rb.tpl
│   └── vagrantfile/snippets/{kind,minikube}_node.rb.tpl
│
└── Scripts (DELETE)
    ├── install_kind.sh
    └── install_minikube.sh
```

---

## Refactoring Phases

### Phase 1: Delete Dead Code ⚠️ **BREAKING** (30 minutes)

**Objective**: Remove all kind/minikube files that can be cleanly deleted

**Actions**:
1. Delete Java source files (2 files)
2. Delete template files (6 files)
3. Delete installer scripts (2 files)
4. Delete test files (TBD based on project structure)

**Expected State**: Project will NOT compile (references broken - expected)

**Verification**:
```bash
mvn clean compile  # Should fail with compilation errors
```

---

### Phase 2: Fix Model Layer (45 minutes)

**Objective**: Update ClusterType to only reference kubeadm and management

**Actions**:
1. Update `ClusterType.java` factory methods
2. Update JavaDoc in `ClusterSpec.java`

**Expected State**: Model compiles, basic instantiation works

**Verification**:
```bash
mvn clean compile  # Should succeed
mvn test -Dtest=ClusterTypeTest  # Unit tests pass
```

---

### Phase 3: Fix Validation Layer (1 hour)

**Objective**: Simplify validation logic to handle only 2 cluster types

**Actions**:
1. Update `PolicyValidator.java` - remove kind/minikube CNI validation
2. Update `SemanticValidator.java` - remove kind/minikube constraints
3. Simplify VM prediction logic

**Expected State**: Validation tests pass for kubeadm/management

**Verification**:
```bash
mvn test -Dtest=PolicyValidatorTest,SemanticValidatorTest
```

---

### Phase 4: Fix Parser/CLI Layer (30 minutes)

**Objective**: Update CLI to reject kind/minikube with helpful errors

**Actions**:
1. Update `CliToSpec.java` cluster type parsing
2. Update `GenerateCommand.java` help text
3. Add clear error messages

**Expected State**: CLI parsing works, rejects kind/minikube gracefully

**Verification**:
```bash
# Should fail with helpful error
java -jar target/k8s-generator.jar --module m1 --type pt kind

# Should work
java -jar target/k8s-generator.jar --module m1 --type pt kubeadm
```

---

### Phase 5: Fix Utilities (15 minutes)

**Objective**: Remove kind/minikube tool installer mappings

**Actions**:
1. Update `ToolInstallers.java`

**Expected State**: Tool installer lookup doesn't crash

**Verification**:
```bash
mvn test -Dtest=ToolInstallersTest
```

---

### Phase 6: Update Orchestrator (30 minutes)

**Objective**: Simplify any pattern matching in orchestration logic

**Actions**:
1. Check `DefaultVmGenerator.java` for 4-way switches
2. Simplify to 2-way pattern matching

**Expected State**: VM generation works for kubeadm/management

**Verification**:
```bash
mvn test -Dtest=DefaultVmGeneratorTest
```

---

### Phase 7: Update Documentation (30 minutes)

**Objective**: Remove all kind/minikube references from docs

**Actions**:
1. Update `GENERATOR-ARCHITECTURE.md`
2. Update `AGENTS.md`
3. Update README examples
4. Update any tutorial/guide documents

**Expected State**: Documentation matches code reality

**Verification**: Manual review of all markdown files

---

### Phase 8: Clean Up Tests (30 minutes)

**Objective**: Ensure test suite covers kubeadm/management only

**Actions**:
1. Delete kind/minikube test files
2. Update integration tests
3. Verify code coverage ≥60%

**Expected State**: Full test suite passes

**Verification**:
```bash
mvn clean test
mvn jacoco:report  # Check coverage
```

---

## Detailed Implementation Steps

### Phase 1: Delete Dead Code

#### Step 1.1: Delete Java Source Files

```bash
# From project root
rm src/main/java/com/k8s/generator/model/Kind.java
rm src/main/java/com/k8s/generator/model/Minikube.java
```

#### Step 1.2: Delete Template Files

```bash
# Bootstrap templates
rm src/main/resources/scripts/templates/bootstrap/kind.sh.tpl
rm src/main/resources/scripts/templates/bootstrap/minikube.sh.tpl

# Vagrantfile templates
rm src/main/resources/scripts/templates/vagrantfile/kind.rb.tpl
rm src/main/resources/scripts/templates/vagrantfile/minikube.rb.tpl

# Vagrantfile snippets
rm src/main/resources/scripts/templates/vagrantfile/snippets/kind_node.rb.tpl
rm src/main/resources/scripts/templates/vagrantfile/snippets/minikube_node.rb.tpl
```

#### Step 1.3: Delete Installer Scripts

```bash
rm src/main/resources/scripts/install_kind.sh
rm src/main/resources/scripts/install_minikube.sh
```

#### Step 1.4: Delete Test Files

```bash
# Find and delete kind/minikube test files
find src/test -name "*Kind*" -o -name "*Minikube*" | xargs rm -f
```

**Checkpoint**: Commit deletions
```bash
git add -A
git commit -m "refactor: delete kind and minikube support files"
```

---

### Phase 2: Fix Model Layer

#### Step 2.1: Update ClusterType.java

**File**: `src/main/java/com/k8s/generator/model/ClusterType.java`

**Changes**:

1. **Remove from `fromString()` method** (around line 94):
```java
// BEFORE
public static ClusterType fromString(String s) {
    return switch (s.toLowerCase()) {
        case "kind" -> Kind.INSTANCE;
        case "minikube" -> Minikube.INSTANCE;
        case "kubeadm" -> Kubeadm.INSTANCE;
        case "none", "mgmt", "management" -> NoneCluster.INSTANCE;
        default -> throw new IllegalArgumentException("Unknown cluster type: " + s);
    };
}

// AFTER
public static ClusterType fromString(String s) {
    return switch (s.toLowerCase()) {
        case "kubeadm" -> Kubeadm.INSTANCE;
        case "none", "mgmt", "management" -> NoneCluster.INSTANCE;
        case "kind", "minikube" -> throw new IllegalArgumentException(
            "Cluster type '" + s + "' is no longer supported. " +
            "k8s-generator now supports only 'kubeadm' and 'mgmt' (management-only). " +
            "For single-node clusters, use: kubeadm --nodes 1"
        );
        default -> throw new IllegalArgumentException("Unknown cluster type: " + s);
    };
}
```

2. **Remove from `byId()` method** (around line 150):
```java
// BEFORE
public static Optional<ClusterType> byId(String id) {
    return switch (id) {
        case "kind" -> Optional.of(Kind.INSTANCE);
        case "minikube" -> Optional.of(Minikube.INSTANCE);
        case "kubeadm" -> Optional.of(Kubeadm.INSTANCE);
        case "none" -> Optional.of(NoneCluster.INSTANCE);
        default -> Optional.empty();
    };
}

// AFTER
public static Optional<ClusterType> byId(String id) {
    return switch (id) {
        case "kubeadm" -> Optional.of(Kubeadm.INSTANCE);
        case "none" -> Optional.of(NoneCluster.INSTANCE);
        default -> Optional.empty();
    };
}
```

3. **Update `values()` method** (around line 180):
```java
// BEFORE
public static List<ClusterType> values() {
    return List.of(Kind.INSTANCE, Minikube.INSTANCE, Kubeadm.INSTANCE, NoneCluster.INSTANCE);
}

// AFTER
public static List<ClusterType> values() {
    return List.of(Kubeadm.INSTANCE, NoneCluster.INSTANCE);
}
```

4. **Update JavaDoc**:
```java
/**
 * Represents the type of Kubernetes cluster to generate.
 *
 * <p>Supported types:
 * <ul>
 *   <li><b>kubeadm</b>: Multi-node Kubernetes cluster with manual setup (educational)</li>
 *   <li><b>none/mgmt</b>: Management-only machine without Kubernetes cluster</li>
 * </ul>
 *
 * <p>The sealed interface ensures type-safe pattern matching and compiler-enforced
 * exhaustiveness checking.
 *
 * @see Kubeadm
 * @see NoneCluster
 */
public sealed interface ClusterType permits Kubeadm, NoneCluster {
    // ... rest of interface
}
```

#### Step 2.2: Update ClusterSpec.java JavaDoc

**File**: `src/main/java/com/k8s/generator/model/ClusterSpec.java`

**Changes**: Update example in class JavaDoc:

```java
/**
 * Immutable specification for a Kubernetes cluster configuration.
 *
 * <p>Examples:
 * <pre>{@code
 * // Kubeadm multi-node cluster
 * ClusterSpec kubeadm = ClusterSpec.builder()
 *     .name(ClusterName.of("clu-m1-pt-kubeadm"))
 *     .type(Kubeadm.INSTANCE)
 *     .nodes(NodeTopology.of(1, 2))  // 1 master, 2 workers
 *     .build();
 *
 * // Management-only machine
 * ClusterSpec mgmt = ClusterSpec.builder()
 *     .name(ClusterName.of("mgmt"))
 *     .type(NoneCluster.INSTANCE)
 *     .build();
 * }</pre>
 */
@Builder
public record ClusterSpec(
    // ... fields
) {}
```

**Checkpoint**: Compile and test
```bash
mvn clean compile
mvn test -Dtest=ClusterTypeTest
git add -A
git commit -m "refactor(model): update ClusterType to support only kubeadm and management"
```

---

### Phase 3: Fix Validation Layer

#### Step 3.1: Update PolicyValidator.java

**File**: `src/main/java/com/k8s/generator/validate/PolicyValidator.java`

**Changes**:

1. **Delete kind/minikube CNI validation** (lines 147-179):
```java
// DELETE THIS ENTIRE BLOCK (lines 147-179)
case Kind k -> {
    // Kind-specific CNI validation
    if (cluster.cni() != null && !VALID_KIND_CNIS.contains(cluster.cni())) {
        errors.add(new ValidationError(
            "cluster.cni",
            ValidationLevel.POLICY,
            "Kind cluster CNI '" + cluster.cni() + "' not in allowed list: " + VALID_KIND_CNIS,
            "Use one of: " + String.join(", ", VALID_KIND_CNIS)
        ));
    }
}
case Minikube m -> {
    // Minikube-specific CNI validation
    if (cluster.cni() != null && !VALID_MINIKUBE_CNIS.contains(cluster.cni())) {
        errors.add(new ValidationError(
            "cluster.cni",
            ValidationLevel.POLICY,
            "Minikube cluster CNI '" + cluster.cni() + "' not in allowed list: " + VALID_MINIKUBE_CNIS,
            "Use one of: " + String.join(", ", VALID_MINIKUBE_CNIS)
        ));
    }
}
```

2. **Simplify CNI validation method**:
```java
// BEFORE (4-way switch)
private List<ValidationError> validateCniPolicy(ClusterSpec cluster) {
    List<ValidationError> errors = new ArrayList<>();

    switch (cluster.type()) {
        case Kind k -> { /* validation */ }
        case Minikube m -> { /* validation */ }
        case NoneCluster nc -> { /* validation */ }
        case Kubeadm ku -> { /* validation */ }
    }

    return errors;
}

// AFTER (2-way switch)
private List<ValidationError> validateCniPolicy(ClusterSpec cluster) {
    List<ValidationError> errors = new ArrayList<>();

    switch (cluster.type()) {
        case NoneCluster nc -> {
            // Management-only: CNI should not be specified
            if (cluster.cni() != null) {
                errors.add(new ValidationError(
                    "cluster.cni",
                    ValidationLevel.POLICY,
                    "Management-only cluster should not specify CNI",
                    "Remove CNI configuration for management-only clusters"
                ));
            }
        }
        case Kubeadm ku -> {
            // Kubeadm: CNI is required
            if (cluster.cni() == null) {
                errors.add(new ValidationError(
                    "cluster.cni",
                    ValidationLevel.STRUCTURAL,
                    "Kubeadm cluster requires CNI specification",
                    "Specify one of: calico, flannel, weave, cilium, antrea"
                ));
            } else if (!VALID_KUBEADM_CNIS.contains(cluster.cni())) {
                errors.add(new ValidationError(
                    "cluster.cni",
                    ValidationLevel.POLICY,
                    "Kubeadm cluster CNI '" + cluster.cni() + "' not in allowed list: " + VALID_KUBEADM_CNIS,
                    "Use one of: " + String.join(", ", VALID_KUBEADM_CNIS)
                ));
            }
        }
    }

    return errors;
}
```

3. **Simplify `predictVmNames()` method** (around line 354):
```java
// BEFORE
private Set<String> predictVmNames(ClusterSpec cluster) {
    Set<String> names = new HashSet<>();

    switch (cluster.type()) {
        case Kind k, Minikube m -> names.add(cluster.name().value());
        case NoneCluster nc -> names.add("mgmt");
        case Kubeadm ku -> {
            if (cluster.nodes() != null) {
                int masters = cluster.nodes().masters();
                int workers = cluster.nodes().workers();
                for (int i = 1; i <= masters; i++) {
                    names.add(cluster.name().value() + "-master-" + i);
                }
                for (int i = 1; i <= workers; i++) {
                    names.add(cluster.name().value() + "-worker-" + i);
                }
            }
        }
    }

    return names;
}

// AFTER
private Set<String> predictVmNames(ClusterSpec cluster) {
    Set<String> names = new HashSet<>();

    switch (cluster.type()) {
        case NoneCluster nc -> names.add("mgmt");
        case Kubeadm ku -> {
            if (cluster.nodes() != null) {
                int masters = cluster.nodes().masters();
                int workers = cluster.nodes().workers();
                for (int i = 1; i <= masters; i++) {
                    names.add(cluster.name().value() + "-master-" + i);
                }
                for (int i = 1; i <= workers; i++) {
                    names.add(cluster.name().value() + "-worker-" + i);
                }
            }
        }
    }

    return names;
}
```

4. **Simplify `calculateExpectedVmCount()` method** (around line 330):
```java
// BEFORE
private int calculateExpectedVmCount(ClusterSpec cluster) {
    return switch (cluster.type()) {
        case Kind k, Minikube m -> 1;
        case NoneCluster nc -> 1;
        case Kubeadm ku -> {
            if (cluster.nodes() != null) {
                yield cluster.nodes().masters() + cluster.nodes().workers();
            }
            yield 1;  // Default single-node
        }
    };
}

// AFTER
private int calculateExpectedVmCount(ClusterSpec cluster) {
    return switch (cluster.type()) {
        case NoneCluster nc -> 1;
        case Kubeadm ku -> {
            if (cluster.nodes() != null) {
                yield cluster.nodes().masters() + cluster.nodes().workers();
            }
            yield 1;  // Default single-node
        }
    };
}
```

#### Step 3.2: Update SemanticValidator.java

**File**: `src/main/java/com/k8s/generator/validate/SemanticValidator.java`

**Changes**:

1. **Delete kind/minikube node constraint validation** (lines 155-156):
```java
// BEFORE
private List<ValidationError> validateNodeConstraints(ClusterSpec cluster) {
    List<ValidationError> errors = new ArrayList<>();

    switch (cluster.type()) {
        case Kind k, Minikube m -> {
            // Single-node only
            if (cluster.nodes() != null &&
                (cluster.nodes().masters() + cluster.nodes().workers() > 1)) {
                errors.add(new ValidationError(
                    "cluster.nodes",
                    ValidationLevel.SEMANTIC,
                    "Kind/Minikube clusters support only single-node configurations",
                    "Remove --nodes parameter or use kubeadm for multi-node"
                ));
            }
        }
        case NoneCluster nc -> {
            // No cluster, so no nodes
            if (cluster.nodes() != null) {
                errors.add(new ValidationError(
                    "cluster.nodes",
                    ValidationLevel.SEMANTIC,
                    "Management-only cluster should not specify node topology",
                    "Remove --nodes parameter"
                ));
            }
        }
        case Kubeadm ku -> {
            // Multi-node validation
            if (cluster.nodes() != null) {
                if (cluster.nodes().masters() < 1) {
                    errors.add(new ValidationError(
                        "cluster.nodes.masters",
                        ValidationLevel.SEMANTIC,
                        "Kubeadm cluster requires at least 1 master node",
                        "Specify at least 1 master: --nodes 1m,Nw"
                    ));
                }
            }
        }
    }

    return errors;
}

// AFTER
private List<ValidationError> validateNodeConstraints(ClusterSpec cluster) {
    List<ValidationError> errors = new ArrayList<>();

    switch (cluster.type()) {
        case NoneCluster nc -> {
            // No cluster, so no nodes
            if (cluster.nodes() != null) {
                errors.add(new ValidationError(
                    "cluster.nodes",
                    ValidationLevel.SEMANTIC,
                    "Management-only cluster should not specify node topology",
                    "Remove --nodes parameter"
                ));
            }
        }
        case Kubeadm ku -> {
            // Multi-node validation
            if (cluster.nodes() != null) {
                if (cluster.nodes().masters() < 1) {
                    errors.add(new ValidationError(
                        "cluster.nodes.masters",
                        ValidationLevel.SEMANTIC,
                        "Kubeadm cluster requires at least 1 master node",
                        "Specify at least 1 master: --nodes 1m,Nw"
                    ));
                }
            }
        }
    }

    return errors;
}
```

**Checkpoint**: Compile and test
```bash
mvn clean compile
mvn test -Dtest=PolicyValidatorTest,SemanticValidatorTest
git add -A
git commit -m "refactor(validation): simplify validation for kubeadm-only support"
```

---

### Phase 4: Fix Parser/CLI Layer

#### Step 4.1: Update CliToSpec.java

**File**: `src/main/java/com/k8s/generator/parser/CliToSpec.java`

**Changes**: Cluster type parsing already delegates to `ClusterType.fromString()`, which we updated in Phase 2. No changes needed here unless there's additional validation.

**Verify**: The error message from `ClusterType.fromString()` will handle kind/minikube rejection.

#### Step 4.2: Update GenerateCommand.java

**File**: `src/main/java/com/k8s/generator/cli/GenerateCommand.java`

**Changes**:

1. **Update parameter description** (around line 36):
```java
// BEFORE
@Parameters(
    index = "0",
    description = "Cluster type: kind, minikube, kubeadm, or mgmt"
)
private String clusterType;

// AFTER
@Parameters(
    index = "0",
    description = "Cluster type: kubeadm (multi-node) or mgmt (management-only)"
)
private String clusterType;
```

2. **Update command description and examples**:
```java
// BEFORE
@Command(
    name = "generate",
    description = "Generate Kubernetes learning environment",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    examples = {
        "k8s-gen --module m1 --type pt kind",
        "k8s-gen --module m1 --type pt minikube",
        "k8s-gen --module m1 --type pt kubeadm --nodes 1m,2w",
        "k8s-gen --module m1 --type pt mgmt"
    }
)

// AFTER
@Command(
    name = "generate",
    description = "Generate Kubernetes learning environment",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    examples = {
        "k8s-gen --module m1 --type pt kubeadm",
        "k8s-gen --module m1 --type pt kubeadm --nodes 1m,2w",
        "k8s-gen --module m1 --type pt kubeadm --nodes 3m,5w",
        "k8s-gen --module m1 --type pt mgmt"
    }
)
```

**Checkpoint**: Compile and test
```bash
mvn clean package
# Test error message
java -jar target/k8s-generator.jar --module m1 --type pt kind
# Should output helpful error

# Test success
java -jar target/k8s-generator.jar --module m1 --type pt kubeadm --dry-run
# Should succeed

git add -A
git commit -m "refactor(cli): update help text and examples for kubeadm-only"
```

---

### Phase 5: Fix Utilities

#### Step 5.1: Update ToolInstallers.java

**File**: `src/main/java/com/k8s/generator/util/ToolInstallers.java`

**Changes**: Remove kind/minikube installer mappings:

```java
// BEFORE
private static final Map<String, String> INSTALLER_SCRIPTS = Map.of(
    "kubectl", "install_kubectl.sh",
    "docker", "install_docker.sh",
    "kind", "install_kind.sh",
    "minikube", "install_minikube.sh",
    "containerd", "install_containerd.sh",
    "kubeBinaries", "install_kube_binaries.sh"
);

// AFTER
private static final Map<String, String> INSTALLER_SCRIPTS = Map.of(
    "kubectl", "install_kubectl.sh",
    "containerd", "install_containerd.sh",
    "kubeBinaries", "install_kube_binaries.sh"
);
```

**Note**: Docker installer removed because it was only needed for kind/minikube. Kubeadm uses containerd.

**Checkpoint**: Test
```bash
mvn test -Dtest=ToolInstallersTest
git add -A
git commit -m "refactor(util): remove kind/minikube installer mappings"
```

---

### Phase 6: Update Orchestrator

#### Step 6.1: Check DefaultVmGenerator.java

**File**: `src/main/java/com/k8s/generator/orchestrator/DefaultVmGenerator.java`

**Action**: Search for pattern matching on `ClusterType` and simplify:

```bash
# Search for ClusterType pattern matching
grep -n "switch.*type()" src/main/java/com/k8s/generator/orchestrator/DefaultVmGenerator.java
```

**Expected Changes**: Similar to validation layer - convert 4-way switches to 2-way.

**Example**:
```java
// BEFORE
switch (cluster.type()) {
    case Kind k, Minikube m -> generateSingleVm(...);
    case NoneCluster nc -> generateManagementVm(...);
    case Kubeadm ku -> generateKubeadmVms(...);
}

// AFTER
switch (cluster.type()) {
    case NoneCluster nc -> generateManagementVm(...);
    case Kubeadm ku -> generateKubeadmVms(...);
}
```

**Checkpoint**: Test
```bash
mvn test -Dtest=DefaultVmGeneratorTest
git add -A
git commit -m "refactor(orchestrator): simplify VM generation for kubeadm-only"
```

---

### Phase 7: Update Documentation

#### Step 7.1: Update GENERATOR-ARCHITECTURE.md

**File**: `ai_working/k8s-generator/doc/GENERATOR-ARCHITECTURE.md`

**Changes**:

1. **Update Overview section** (lines 10-14):
```markdown
<!-- BEFORE -->
- Engines map 1:1 to `cluster-type`: `mgmt→none`, `kind→kind`, `minikube→minikube`, `kubeadm→kubeadm`

<!-- AFTER -->
- Engines: `mgmt→none` (management-only), `kubeadm→kubeadm` (multi-node Kubernetes)
```

2. **Update CLI Contract section** (lines 37-38):
```markdown
<!-- BEFORE -->
  - `<cluster-type>`: `mgmt|minikube|kind|kubeadm`

<!-- AFTER -->
  - `<cluster-type>`: `mgmt|kubeadm`
```

3. **Update Conventions section** (line 50):
```markdown
<!-- BEFORE -->
  - Engine explicit; independent from `--type`

<!-- AFTER -->
  - Engine: kubeadm (multi-node) or mgmt (management-only)
```

4. **Remove Engine Extensibility section** (lines 168-188):
```markdown
<!-- DELETE THIS ENTIRE SECTION -->
## Engine Extensibility

### Engine Contract (Java SPI)
...
```

**Rationale**: The Engine SPI was never implemented. With only 2 cluster types, extensibility is not needed.

5. **Update examples throughout** - Replace kind/minikube with kubeadm.

#### Step 7.2: Update AGENTS.md

**File**: `ai_working/k8s-generator/AGENTS.md`

**Changes**:

1. **Update Project Overview** (lines 28-33):
```markdown
<!-- BEFORE -->
- **Multiple engines**: kind, minikube, kubeadm, management-only (extensible via SPI)

<!-- AFTER -->
- **Two modes**: kubeadm (multi-node clusters), management-only (no cluster)
```

2. **Update Engines & SPI section** (lines 60-64):
```markdown
<!-- BEFORE -->
### Engines & SPI

- Internal engines map 1:1 to the CLI `cluster-type` values.
- Provide an `Engine` interface with `id()` and render/orchestration hooks; register via an `EngineRegistry`.
- New engines (e.g., k3s, microk8s) integrate via SPI without modifying core bricks.

<!-- AFTER -->
### Cluster Modes

- **Kubeadm**: Multi-node Kubernetes cluster with manual setup (educational focus)
- **Management-only**: Standalone machine for multi-cluster labs (no Kubernetes installed)
- Extensibility via sealed interface pattern allows future cloud engines (AKS, EKS) if needed
```

3. **Update Role/cluster semantics** (lines 243-245):
```markdown
<!-- BEFORE -->
  - `minikube` and `aks` are always single-node management VMs; any `--role` input is ignored by design.

<!-- AFTER -->
  - Management-only clusters are single-VM; no roles specified.
```

#### Step 7.3: Update README (if exists)

Search for and update any README files with examples:
```bash
find . -name "README.md" -exec grep -l "kind\|minikube" {} \;
```

**Checkpoint**: Manual review
```bash
git add -A
git commit -m "docs: remove kind/minikube references, update architecture docs"
```

---

### Phase 8: Clean Up Tests

#### Step 8.1: Identify Test Files to Delete

```bash
# Find test files referencing kind/minikube
find src/test -name "*.java" -exec grep -l "Kind\|Minikube" {} \;
```

**Action**: Review each file and either:
- **Delete** if exclusively testing kind/minikube
- **Update** if testing shared functionality with kind/minikube examples

#### Step 8.2: Update Integration Tests

**Likely files**:
- `EndToEndTest.java` - Remove kind/minikube test cases
- `ValidationIntegrationTest.java` - Remove kind/minikube scenarios
- `GenerationIntegrationTest.java` - Keep only kubeadm/management tests

#### Step 8.3: Verify Code Coverage

```bash
mvn clean test jacoco:report
# Open target/site/jacoco/index.html
# Verify coverage ≥60%
```

**Checkpoint**: Final test
```bash
mvn clean verify
git add -A
git commit -m "test: remove kind/minikube tests, verify coverage"
```

---

## File-by-File Change Manifest

### Files to DELETE (10+ files)

**Source Code** (2 files):
- [ ] `src/main/java/com/k8s/generator/model/Kind.java`
- [ ] `src/main/java/com/k8s/generator/model/Minikube.java`

**Templates** (6 files):
- [ ] `src/main/resources/scripts/templates/bootstrap/kind.sh.tpl`
- [ ] `src/main/resources/scripts/templates/bootstrap/minikube.sh.tpl`
- [ ] `src/main/resources/scripts/templates/vagrantfile/kind.rb.tpl`
- [ ] `src/main/resources/scripts/templates/vagrantfile/minikube.rb.tpl`
- [ ] `src/main/resources/scripts/templates/vagrantfile/snippets/kind_node.rb.tpl`
- [ ] `src/main/resources/scripts/templates/vagrantfile/snippets/minikube_node.rb.tpl`

**Scripts** (2 files):
- [ ] `src/main/resources/scripts/install_kind.sh`
- [ ] `src/main/resources/scripts/install_minikube.sh`

**Tests** (TBD):
- [ ] Find and delete via: `find src/test -name "*Kind*" -o -name "*Minikube*"`

---

### Files to MODIFY (8-10 files)

**Model Layer**:
- [ ] `src/main/java/com/k8s/generator/model/ClusterType.java`
  - Remove Kind/Minikube from factory methods
  - Update JavaDoc
  - Enhance error messages

- [ ] `src/main/java/com/k8s/generator/model/ClusterSpec.java`
  - Update JavaDoc examples

**Validation Layer**:
- [ ] `src/main/java/com/k8s/generator/validate/PolicyValidator.java`
  - Delete kind/minikube CNI validation (lines 147-179)
  - Simplify `predictVmNames()` (line 354)
  - Simplify `calculateExpectedVmCount()` (line 330)

- [ ] `src/main/java/com/k8s/generator/validate/SemanticValidator.java`
  - Delete kind/minikube node constraints (lines 155-156)

**Parser/CLI Layer**:
- [ ] `src/main/java/com/k8s/generator/cli/GenerateCommand.java`
  - Update parameter description (line 36)
  - Update command examples

**Utilities**:
- [ ] `src/main/java/com/k8s/generator/util/ToolInstallers.java`
  - Remove kind/minikube installer mappings

**Orchestrator** (if needed):
- [ ] `src/main/java/com/k8s/generator/orchestrator/DefaultVmGenerator.java`
  - Simplify pattern matching (TBD based on code review)

**Documentation**:
- [ ] `doc/GENERATOR-ARCHITECTURE.md`
  - Remove kind/minikube references throughout
  - Delete Engine SPI section
  - Update examples

- [ ] `AGENTS.md`
  - Update engine descriptions
  - Remove SPI extensibility references
  - Update role/cluster semantics

- [ ] README files (if any)
  - Update examples

---

## Testing Strategy

### Unit Tests (60%)

**Coverage Areas**:
- [ ] `ClusterType.fromString()` - Verify kind/minikube rejection with clear error
- [ ] `ClusterType.byId()` - Verify only kubeadm/none return values
- [ ] `ClusterType.values()` - Verify returns 2 types only
- [ ] `PolicyValidator` - CNI validation for kubeadm/management
- [ ] `SemanticValidator` - Node constraints for kubeadm/management
- [ ] `ToolInstallers` - Verify kind/minikube installer lookup fails gracefully

**Test Cases to Add**:
```java
@Test
void rejectsKindClusterType() {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> ClusterType.fromString("kind"));

    assertThat(ex.getMessage())
        .contains("no longer supported")
        .contains("kubeadm")
        .contains("mgmt");
}

@Test
void rejectsMinikubeClusterType() {
    var ex = assertThrows(IllegalArgumentException.class,
        () -> ClusterType.fromString("minikube"));

    assertThat(ex.getMessage())
        .contains("no longer supported")
        .contains("kubeadm")
        .contains("mgmt");
}
```

---

### Integration Tests (30%)

**Coverage Areas**:
- [ ] End-to-end kubeadm cluster generation
- [ ] End-to-end management-only generation
- [ ] Vagrantfile syntax validation
- [ ] Bootstrap script syntax validation
- [ ] IP allocation for kubeadm multi-node
- [ ] Tool installation for kubeadm (containerd, kubectl, kubeBinaries)

**Test Scenarios**:
```java
@Test
void generatesKubeadmMultiNodeCluster() {
    // Given
    ClusterSpec spec = ClusterSpec.builder()
        .name(ClusterName.of("clu-m1-pt-kubeadm"))
        .type(Kubeadm.INSTANCE)
        .nodes(NodeTopology.of(1, 2))
        .cni("calico")
        .build();

    // When
    Result<Path, String> result = orchestrator.generate(spec);

    // Then
    assertThat(result.isSuccess()).isTrue();
    Path outputDir = result.value();

    assertThat(outputDir.resolve("Vagrantfile")).exists();
    assertThat(outputDir.resolve("scripts/bootstrap.sh")).exists();

    // Verify 3 VMs defined (1 master + 2 workers)
    String vagrantfile = Files.readString(outputDir.resolve("Vagrantfile"));
    assertThat(vagrantfile).contains("clu-m1-pt-kubeadm-master-1");
    assertThat(vagrantfile).contains("clu-m1-pt-kubeadm-worker-1");
    assertThat(vagrantfile).contains("clu-m1-pt-kubeadm-worker-2");
}
```

---

### E2E Tests (10%)

**Smoke Tests**:
- [ ] CLI help text displays correctly
- [ ] CLI rejects kind/minikube with helpful error
- [ ] CLI accepts kubeadm and mgmt
- [ ] Generated Vagrantfile is valid Ruby (`ruby -c Vagrantfile`)
- [ ] Generated bootstrap.sh is valid Bash (`bash -n bootstrap.sh`)

**Manual Test Script**:
```bash
#!/usr/bin/env bash
# Manual smoke test for kubeadm-only refactoring

set -euo pipefail

echo "=== k8s-generator Smoke Test ==="

# Build
echo "Building project..."
mvn clean package -DskipTests

# Test 1: Reject kind
echo "Test 1: Reject kind cluster type..."
if java -jar target/k8s-generator.jar --module m1 --type pt kind 2>&1 | grep -q "no longer supported"; then
    echo "✅ PASS: Kind rejected with helpful error"
else
    echo "❌ FAIL: Kind should be rejected"
    exit 1
fi

# Test 2: Reject minikube
echo "Test 2: Reject minikube cluster type..."
if java -jar target/k8s-generator.jar --module m1 --type pt minikube 2>&1 | grep -q "no longer supported"; then
    echo "✅ PASS: Minikube rejected with helpful error"
else
    echo "❌ FAIL: Minikube should be rejected"
    exit 1
fi

# Test 3: Accept kubeadm single-node
echo "Test 3: Accept kubeadm single-node..."
java -jar target/k8s-generator.jar --module m1 --type pt kubeadm --out /tmp/k8s-test-single --dry-run
echo "✅ PASS: Kubeadm single-node accepted"

# Test 4: Accept kubeadm multi-node
echo "Test 4: Accept kubeadm multi-node..."
java -jar target/k8s-generator.jar --module m1 --type pt kubeadm --nodes 1m,2w --out /tmp/k8s-test-multi --dry-run
echo "✅ PASS: Kubeadm multi-node accepted"

# Test 5: Accept management-only
echo "Test 5: Accept management-only..."
java -jar target/k8s-generator.jar --module m1 --type pt mgmt --out /tmp/k8s-test-mgmt --dry-run
echo "✅ PASS: Management-only accepted"

# Test 6: Generate and validate Vagrantfile
echo "Test 6: Validate generated Vagrantfile..."
java -jar target/k8s-generator.jar --module m1 --type pt kubeadm --nodes 1m,2w --out /tmp/k8s-test-validate
ruby -c /tmp/k8s-test-validate/Vagrantfile
echo "✅ PASS: Vagrantfile is valid Ruby"

# Test 7: Validate generated bootstrap script
echo "Test 7: Validate generated bootstrap script..."
bash -n /tmp/k8s-test-validate/scripts/bootstrap.sh
echo "✅ PASS: Bootstrap script is valid Bash"

# Cleanup
rm -rf /tmp/k8s-test-*

echo ""
echo "=== All Smoke Tests Passed ✅ ==="
```

---

## Rollback Plan

### Pre-Refactoring Backup

**Before starting Phase 1**:
```bash
# Create backup branch
git checkout -b backup/pre-kubeadm-only-refactoring
git push origin backup/pre-kubeadm-only-refactoring

# Return to main branch
git checkout main

# Create refactoring branch
git checkout -b refactor/kubeadm-only
```

### Rollback Triggers

**Abort refactoring if**:
1. ❌ Code coverage drops below 50%
2. ❌ More than 5 unexpected compilation errors after Phase 2
3. ❌ Cannot generate valid kubeadm cluster after Phase 6
4. ❌ Integration tests fail for kubeadm/management after Phase 8

### Rollback Procedure

**If rollback needed**:
```bash
# Discard all changes
git checkout main
git reset --hard origin/main

# Delete refactoring branch
git branch -D refactor/kubeadm-only

# Restore from backup
git checkout backup/pre-kubeadm-only-refactoring
git checkout -b main-restored
```

**Post-Rollback**:
- Document what went wrong
- Identify root cause
- Revise refactoring plan
- Attempt again with corrected approach

---

## Philosophy Alignment

### Ruthless Simplicity ✅

**Applied**:
- Removing 50% of cluster types (2 of 4 engines deleted)
- Eliminating unnecessary multi-engine abstractions
- Simplifying pattern matching from 4-way to 2-way
- Deleting 10+ files of unused code

**Evidence**:
- Lines of code reduced by ~15-20%
- Cognitive load reduced (fewer cases to consider)
- Maintenance burden reduced (fewer engines to test/document)

---

### Avoid Future-Proofing ✅

**Applied**:
- Keeping sealed interface for type safety, NOT for extensibility
- Deleting kind/minikube permanently (no deprecation, no toggle)
- No "legacy mode" or backward compatibility layer

**Evidence**:
- Clean break - no configuration to enable/disable engines
- No deprecated code warnings
- No "coming soon" placeholders

---

### Fail-Fast Error Handling ✅

**Applied**:
- CLI rejects kind/minikube immediately with clear, actionable error
- Validation errors explain what's supported and how to migrate
- No silent fallbacks or defaults

**Example Error Message**:
```
Error: Cluster type 'kind' is no longer supported

k8s-generator now supports only:
  - kubeadm: Multi-node Kubernetes clusters (manual setup for learning)
  - mgmt: Management-only machines (no cluster installed)

For single-node Kubernetes, use:
  k8s-gen --module m1 --type pt kubeadm --nodes 1m,0w

For multi-node clusters, use:
  k8s-gen --module m1 --type pt kubeadm --nodes 1m,2w
```

---

### End-to-End Thinking ✅

**Applied**:
- Refactoring proceeds in vertical slices (Phase 1 → Phase 8)
- Each phase delivers complete functionality
- Testing at each phase ensures nothing breaks

**Evidence**:
- Phase 2 = Model layer working end-to-end
- Phase 3 = Validation working end-to-end
- Phase 6 = Full generation pipeline working

---

### Areas NOT Simplified (Intentionally)

**Security**: Not applicable
**Data Integrity**: Atomic file writer remains unchanged
**Core UX**: Clear error messages prioritized
**Error Visibility**: Validation errors remain comprehensive

---

## Success Criteria

### Must-Pass Requirements

- [ ] ✅ Project compiles with zero errors
- [ ] ✅ All unit tests pass (kubeadm/management only)
- [ ] ✅ All integration tests pass
- [ ] ✅ Code coverage ≥60%
- [ ] ✅ CLI rejects kind/minikube with helpful error
- [ ] ✅ CLI accepts kubeadm and mgmt
- [ ] ✅ Generated Vagrantfile is valid Ruby syntax
- [ ] ✅ Generated bootstrap scripts are valid Bash syntax
- [ ] ✅ Can generate kubeadm single-node cluster
- [ ] ✅ Can generate kubeadm multi-node cluster (1m,2w)
- [ ] ✅ Can generate management-only cluster
- [ ] ✅ Documentation updated (no kind/minikube references)

### Nice-to-Have Goals

- [ ] 📊 Lines of code reduced by ≥15%
- [ ] 📏 Pattern matching exhaustiveness warnings = 0
- [ ] 🎯 Code coverage ≥65%
- [ ] ⚡ Build time reduced (fewer files to compile)

---

## Completion Checklist

### Phase 1: Delete Dead Code
- [ ] Deleted `Kind.java` and `Minikube.java`
- [ ] Deleted 6 template files
- [ ] Deleted 2 installer scripts
- [ ] Deleted kind/minikube test files
- [ ] Committed: "refactor: delete kind and minikube support files"

### Phase 2: Fix Model Layer
- [ ] Updated `ClusterType.fromString()`
- [ ] Updated `ClusterType.byId()`
- [ ] Updated `ClusterType.values()`
- [ ] Updated JavaDoc in ClusterType
- [ ] Updated JavaDoc in ClusterSpec
- [ ] Verified: Model compiles
- [ ] Verified: ClusterTypeTest passes
- [ ] Committed: "refactor(model): update ClusterType to support only kubeadm and management"

### Phase 3: Fix Validation Layer
- [ ] Deleted kind/minikube CNI validation in PolicyValidator
- [ ] Simplified `predictVmNames()` in PolicyValidator
- [ ] Simplified `calculateExpectedVmCount()` in PolicyValidator
- [ ] Deleted kind/minikube constraints in SemanticValidator
- [ ] Verified: PolicyValidatorTest passes
- [ ] Verified: SemanticValidatorTest passes
- [ ] Committed: "refactor(validation): simplify validation for kubeadm-only support"

### Phase 4: Fix Parser/CLI Layer
- [ ] Updated GenerateCommand parameter description
- [ ] Updated GenerateCommand examples
- [ ] Verified: CLI help text shows only kubeadm/mgmt
- [ ] Verified: CLI rejects kind with helpful error
- [ ] Verified: CLI rejects minikube with helpful error
- [ ] Committed: "refactor(cli): update help text and examples for kubeadm-only"

### Phase 5: Fix Utilities
- [ ] Updated ToolInstallers.java
- [ ] Verified: ToolInstallersTest passes
- [ ] Committed: "refactor(util): remove kind/minikube installer mappings"

### Phase 6: Update Orchestrator
- [ ] Reviewed DefaultVmGenerator.java
- [ ] Simplified pattern matching (if needed)
- [ ] Verified: DefaultVmGeneratorTest passes
- [ ] Committed: "refactor(orchestrator): simplify VM generation for kubeadm-only"

### Phase 7: Update Documentation
- [ ] Updated GENERATOR-ARCHITECTURE.md
- [ ] Updated AGENTS.md
- [ ] Updated README files (if any)
- [ ] Verified: No kind/minikube references remain
- [ ] Committed: "docs: remove kind/minikube references, update architecture docs"

### Phase 8: Clean Up Tests
- [ ] Deleted kind/minikube test files
- [ ] Updated integration tests
- [ ] Verified: All tests pass
- [ ] Verified: Code coverage ≥60%
- [ ] Committed: "test: remove kind/minikube tests, verify coverage"

### Final Verification
- [ ] Run smoke test script
- [ ] Manual test: Generate kubeadm cluster
- [ ] Manual test: Generate management cluster
- [ ] Validate Vagrantfile syntax
- [ ] Validate bootstrap script syntax
- [ ] Code review: Check for leftover kind/minikube references
- [ ] Final commit: "refactor: complete kubeadm-only simplification"

---

## Post-Refactoring Actions

### Update Project Documentation
- [ ] Update changelog/release notes
- [ ] Update migration guide (if users exist)
- [ ] Update getting started guide
- [ ] Update troubleshooting guide

### Communicate Changes
- [ ] Announce breaking change to users (if applicable)
- [ ] Update GitHub README
- [ ] Close related issues/feature requests
- [ ] Update project roadmap

### Monitor
- [ ] Watch for user feedback on simplified architecture
- [ ] Track build/test times (should improve)
- [ ] Monitor code coverage trends

---

## Estimated Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Delete Dead Code | 30 min | None |
| Phase 2: Fix Model Layer | 45 min | Phase 1 |
| Phase 3: Fix Validation Layer | 1 hour | Phase 2 |
| Phase 4: Fix Parser/CLI Layer | 30 min | Phase 2 |
| Phase 5: Fix Utilities | 15 min | Phase 1 |
| Phase 6: Update Orchestrator | 30 min | Phase 2, 3 |
| Phase 7: Update Documentation | 30 min | All phases |
| Phase 8: Clean Up Tests | 30 min | All phases |
| **Total** | **~4 hours** | Sequential |

---

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Undiscovered dependencies on kind/minikube | Medium | High | Comprehensive grep search before Phase 1 |
| Template rendering breaks | Low | High | Integration tests in Phase 6 |
| IP allocation breaks for management | Low | Medium | Unit tests in Phase 3 |
| Tool installation conflicts | Low | Medium | Verify tool lists in Phase 5 |
| Documentation out of sync | Medium | Low | Systematic doc review in Phase 7 |
| Test coverage drops | Low | Medium | Monitor coverage in Phase 8 |

---

## Conclusion

This refactoring plan provides a systematic approach to simplifying k8s-generator by removing kind and minikube support. The phased approach ensures:

1. **Safety**: Each phase is testable and reversible
2. **Clarity**: Explicit steps with verification at each stage
3. **Completeness**: All affected components identified
4. **Philosophy Alignment**: Ruthless simplicity without future-proofing

**Key Insight**: The current architecture uses sealed types + pattern matching rather than a complex Engine SPI. This makes the refactoring simpler than expected - just delete implementations and simplify switches.

**Estimated Outcome**:
- ~15-20% reduction in codebase size
- Simpler validation logic (4-way → 2-way switches)
- Clearer CLI (only 2 cluster types)
- Easier maintenance (fewer engines to test/document)

**Next Step**: Begin Phase 1 (Delete Dead Code) and proceed sequentially through Phase 8.

---

## Document History

| Version | Date       | Author      | Changes                                          |
|---------|------------|-------------|--------------------------------------------------|
| 1.0.0   | 2025-11-11 | refactoring | Initial refactoring plan for kubeadm-only architecture |
