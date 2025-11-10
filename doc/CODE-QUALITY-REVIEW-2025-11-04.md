---
status: Architectural Review and Remediation Plan
version: 1.0.0
scope: Comprehensive code quality analysis of k8s-generator Java codebase with detailed implementation guidance for manual fixes
---

# Code Quality Review & Remediation Plan (2025-11-04)

## Executive Summary

**Review Date**: November 4, 2025
**Reviewer**: Technical Software Architect Agent
**Codebase Status**: Phase 2 Complete (296 passing tests)
**Philosophy Compliance Score**: 7.5/10

The k8s-generator codebase demonstrates **strong architectural fundamentals** with clean brick separation, excellent test coverage, and solid adherence to immutability patterns using Java records. The "bricks and studs" philosophy is well-implemented with clear interfaces between modules and minimal coupling.

However, the analysis reveals **several critical gaps** that must be addressed before Phase 3:

1. **Model incompleteness**: Missing critical fields (P5) that break contract with downstream bricks
2. **Type system inconsistency**: Duplicate Result types creating coupling issues (P6)
3. **CLI gaps**: Missing Azure integration flag (P7)
4. **I/O incompleteness**: Missing scaffoldHooks() contract violation (P8)

**Total Estimated Effort for Critical + High Items**: 10-15 hours

---

## Table of Contents

1. [Gap Analysis: P5-P8 Verification](#gap-analysis-p5-p8-verification)
2. [Anti-Patterns Found](#anti-patterns-found)
3. [Code Quality Issues by Brick](#code-quality-issues-by-brick)
4. [Prioritized Recommendations](#prioritized-recommendations)
5. [Detailed Implementation Guide](#detailed-implementation-guide)
6. [Philosophy Compliance Assessment](#philosophy-compliance-assessment)
7. [Testing Strategy](#testing-strategy)
8. [References](#references)

---

## Gap Analysis: P5-P8 Verification

### ❌ P5: Model Brick Missing Fields (CRITICAL)

**Priority**: CRITICAL
**Estimated Effort**: 2-4 hours
**Risk Level**: High - Breaks contract between bricks

#### Issue Description

Three domain records are missing critical fields required by downstream bricks (Parser, Renderer, Orchestrator). This creates contract violations where consumers expect data that isn't available.

#### Affected Files

1. **`src/main/java/com/k8s/generator/model/GeneratorSpec.java`**
   - **Missing**: `Management management` field
   - **Current State**:
     ```java
     public record GeneratorSpec(
         String outputPath,
         List<ClusterSpec> clusters
     ) {
         // MISSING: Management management field
     }
     ```

2. **`src/main/java/com/k8s/generator/model/ClusterSpec.java`**
   - **Missing**: `String podNetwork` field
   - **Missing**: `String svcNetwork` field
   - **Current State**:
     ```java
     public record ClusterSpec(
         String name,
         ClusterType type,
         EngineType engine,
         String baseIp,
         List<VmSpec> vms,
         List<String> tools,
         Optional<AzureSpec> azure
     ) {
         // MISSING: String podNetwork field
         // MISSING: String svcNetwork field
     }
     ```

3. **`src/main/java/com/k8s/generator/model/ScaffoldPlan.java`**
   - **Missing**: `List<ProviderPlan> providers` field
   - **Current State**:
     ```java
     public record ScaffoldPlan(
         Path outputDir,
         List<VmPlan> vmPlans,
         List<BootstrapPlan> bootstrapPlans,
         List<InstallerPlan> installerPlans
     ) {
         // MISSING: List<ProviderPlan> providers field
     }
     ```

#### Impact Analysis

- **Parser Brick**: Cannot populate management configuration from CLI `--azure` flag
- **Renderer Brick**: Cannot generate `/etc/azure-env` or cloud-specific templates
- **Validation Brick**: Cannot validate Kubernetes network CIDRs (pod/service networks)
- **Orchestrator Brick**: Cannot coordinate multi-cluster management VM setup

#### Detailed Fix Instructions

##### Step 1: Add Management Field to GeneratorSpec

**File**: `src/main/java/com/k8s/generator/model/GeneratorSpec.java`

**Changes**:
```java
public record GeneratorSpec(
    String outputPath,
    List<ClusterSpec> clusters,
    Optional<Management> management  // ADD THIS LINE
) {
    // Compact constructor validation
    public GeneratorSpec {
        Objects.requireNonNull(outputPath, "outputPath cannot be null");
        Objects.requireNonNull(clusters, "clusters cannot be null");
        Objects.requireNonNull(management, "management cannot be null");

        if (clusters.isEmpty()) {
            throw new IllegalArgumentException("At least one cluster required");
        }

        // Defensive copy
        clusters = List.copyOf(clusters);
        management = management == null ? Optional.empty() : management;
    }
}
```

**Notes**:
- Use `Optional<Management>` because management VM is optional
- Add null check in compact constructor
- Ensure defensive copy for immutability

##### Step 2: Add Network Fields to ClusterSpec

**File**: `src/main/java/com/k8s/generator/model/ClusterSpec.java`

**Changes**:
```java
public record ClusterSpec(
    String name,
    ClusterType type,
    EngineType engine,
    String baseIp,
    List<VmSpec> vms,
    List<String> tools,
    Optional<AzureSpec> azure,
    String podNetwork,   // ADD THIS LINE - Kubernetes pod network CIDR
    String svcNetwork    // ADD THIS LINE - Kubernetes service network CIDR
) {
    // Compact constructor validation
    public ClusterSpec {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(engine, "engine cannot be null");
        Objects.requireNonNull(baseIp, "baseIp cannot be null");
        Objects.requireNonNull(vms, "vms cannot be null");
        Objects.requireNonNull(tools, "tools cannot be null");
        Objects.requireNonNull(azure, "azure cannot be null");
        Objects.requireNonNull(podNetwork, "podNetwork cannot be null");  // ADD
        Objects.requireNonNull(svcNetwork, "svcNetwork cannot be null");  // ADD

        // Validate CIDR format for podNetwork
        if (!podNetwork.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+$")) {
            throw new IllegalArgumentException("Invalid podNetwork CIDR format: " + podNetwork);
        }

        // Validate CIDR format for svcNetwork
        if (!svcNetwork.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+$")) {
            throw new IllegalArgumentException("Invalid svcNetwork CIDR format: " + svcNetwork);
        }

        // Validate prefix length (8-30 is practical range)
        int podPrefix = Integer.parseInt(podNetwork.substring(podNetwork.indexOf('/') + 1));
        int svcPrefix = Integer.parseInt(svcNetwork.substring(svcNetwork.indexOf('/') + 1));

        if (podPrefix < 8 || podPrefix > 30) {
            throw new IllegalArgumentException("Pod network prefix must be 8-30, got: " + podPrefix);
        }

        if (svcPrefix < 8 || svcPrefix > 30) {
            throw new IllegalArgumentException("Service network prefix must be 8-30, got: " + svcPrefix);
        }

        // Defensive copies
        vms = List.copyOf(vms);
        tools = List.copyOf(tools);
        azure = azure == null ? Optional.empty() : azure;
    }
}
```

**Default Values** (to use in Parser):
- `podNetwork`: `"10.244.0.0/16"` (Kubernetes default)
- `svcNetwork`: `"10.96.0.0/12"` (Kubernetes default)

##### Step 3: Add Providers Field to ScaffoldPlan

**File**: `src/main/java/com/k8s/generator/model/ScaffoldPlan.java`

**Changes**:
```java
public record ScaffoldPlan(
    Path outputDir,
    List<VmPlan> vmPlans,
    List<BootstrapPlan> bootstrapPlans,
    List<InstallerPlan> installerPlans,
    Set<String> providers  // ADD THIS LINE - Cloud providers (azure, aws, gcp)
) {
    // Compact constructor validation
    public ScaffoldPlan {
        Objects.requireNonNull(outputDir, "outputDir cannot be null");
        Objects.requireNonNull(vmPlans, "vmPlans cannot be null");
        Objects.requireNonNull(bootstrapPlans, "bootstrapPlans cannot be null");
        Objects.requireNonNull(installerPlans, "installerPlans cannot be null");
        Objects.requireNonNull(providers, "providers cannot be null");  // ADD

        // Defensive copies
        vmPlans = List.copyOf(vmPlans);
        bootstrapPlans = List.copyOf(bootstrapPlans);
        installerPlans = List.copyOf(installerPlans);
        providers = Set.copyOf(providers);  // ADD - Use Set to avoid duplicates
    }
}
```

**Notes**:
- Use `Set<String>` instead of `List<String>` to avoid duplicate providers
- Common values: `"azure"`, `"aws"`, `"gcp"`
- Empty set is valid (no cloud providers)

##### Step 4: Update Parser to Populate New Fields

**File**: `src/main/java/com/k8s/generator/parser/CliToSpec.java`

**Changes**:
```java
public class CliToSpec implements SpecConverter {

    @Override
    public GeneratorSpec convert(GenerateCommand cmd) {
        // ... existing cluster parsing logic ...

        // NEW: Parse management if --azure flag is set
        Optional<Management> management = Optional.empty();
        if (cmd.isAzure()) {
            management = Optional.of(new Management(
                "mgmt",  // Default management VM name
                List.of("azure"),  // Providers
                true,  // aggregateKubeconfigs
                List.of("kubectl", "azure_cli")  // Tools
            ));
        }

        // NEW: Add default network CIDRs to clusters
        List<ClusterSpec> clustersWithNetworks = clusters.stream()
            .map(cluster -> new ClusterSpec(
                cluster.name(),
                cluster.type(),
                cluster.engine(),
                cluster.baseIp(),
                cluster.vms(),
                cluster.tools(),
                cluster.azure(),
                "10.244.0.0/16",  // Default pod network
                "10.96.0.0/12"    // Default service network
            ))
            .toList();

        return new GeneratorSpec(
            cmd.getOutputPath(),
            clustersWithNetworks,
            management  // ADD THIS
        );
    }
}
```

**File**: `src/main/java/com/k8s/generator/parser/SpecToPlan.java`

**Changes**:
```java
public class SpecToPlan implements PlanBuilder {

    @Override
    public ScaffoldPlan build(GeneratorSpec spec) {
        // ... existing plan building logic ...

        // NEW: Extract providers from management spec
        Set<String> providers = spec.management()
            .map(Management::providers)
            .map(HashSet::new)
            .orElse(new HashSet<>());

        return new ScaffoldPlan(
            outputDir,
            vmPlans,
            bootstrapPlans,
            installerPlans,
            providers  // ADD THIS
        );
    }
}
```

##### Step 5: Update Tests

**New Test File**: `src/test/java/com/k8s/generator/model/GeneratorSpecTest.java`

```java
package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class GeneratorSpecTest {

    @Test
    void shouldAcceptValidSpecWithManagement() {
        Management mgmt = new Management("mgmt", List.of("azure"), true, List.of("kubectl"));
        ClusterSpec cluster = new ClusterSpec(
            "test-cluster",
            ClusterType.KIND,
            EngineType.KIND,
            "192.168.56.10",
            List.of(),
            List.of("kubectl"),
            Optional.empty(),
            "10.244.0.0/16",
            "10.96.0.0/12"
        );

        GeneratorSpec spec = new GeneratorSpec(
            "/tmp/output",
            List.of(cluster),
            Optional.of(mgmt)
        );

        assertThat(spec.management()).isPresent();
        assertThat(spec.management().get().name()).isEqualTo("mgmt");
    }

    @Test
    void shouldAcceptValidSpecWithoutManagement() {
        ClusterSpec cluster = new ClusterSpec(
            "test-cluster",
            ClusterType.KIND,
            EngineType.KIND,
            "192.168.56.10",
            List.of(),
            List.of("kubectl"),
            Optional.empty(),
            "10.244.0.0/16",
            "10.96.0.0/12"
        );

        GeneratorSpec spec = new GeneratorSpec(
            "/tmp/output",
            List.of(cluster),
            Optional.empty()
        );

        assertThat(spec.management()).isEmpty();
    }
}
```

**Update Existing Test**: `src/test/java/com/k8s/generator/model/ClusterSpecTest.java`

```java
@Test
void shouldValidatePodNetworkCIDR() {
    assertThatThrownBy(() -> new ClusterSpec(
        "test",
        ClusterType.KUBEADM,
        EngineType.KUBEADM,
        "192.168.56.10",
        List.of(),
        List.of("kubectl"),
        Optional.empty(),
        "invalid-cidr",  // Invalid CIDR
        "10.96.0.0/12"
    ))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("Invalid podNetwork CIDR format");
}

@Test
void shouldValidateServiceNetworkCIDR() {
    assertThatThrownBy(() -> new ClusterSpec(
        "test",
        ClusterType.KUBEADM,
        EngineType.KUBEADM,
        "192.168.56.10",
        List.of(),
        List.of("kubectl"),
        Optional.empty(),
        "10.244.0.0/16",
        "10.96.0.0/99"  // Invalid prefix
    ))
    .isInstanceOf(IllegalArgumentException.class)
    .hasMessageContaining("prefix must be 8-30");
}
```

---

### ❌ P6: Redundant Result Type in IpAllocator (CRITICAL)

**Priority**: CRITICAL
**Estimated Effort**: 1 hour
**Risk Level**: Medium - Type system inconsistency

#### Issue Description

The `IpAllocator` interface defines its own nested `Result` record that duplicates the global `com.k8s.generator.model.Result` type. This creates:
- Violation of DRY principle
- Type confusion (which Result to use?)
- Unnecessary coupling between IP and Model bricks
- Inconsistent error handling patterns

#### Affected Files

1. **`src/main/java/com/k8s/generator/model/Result.java`** - Global Result type (correct)
2. **`src/main/java/com/k8s/generator/ip/IpAllocator.java`** - Contains redundant nested Result

#### Current State

**Global Result Type** (`model/Result.java`):
```java
public sealed interface Result<T, E> permits Result.Success, Result.Failure {
    record Success<T, E>(T value) implements Result<T, E> {}
    record Failure<T, E>(E error) implements Result<T, E> {}

    default boolean isSuccess() {
        return this instanceof Success;
    }

    default boolean isFailure() {
        return this instanceof Failure;
    }
}
```

**IpAllocator with Nested Result** (lines 15-20):
```java
public interface IpAllocator {
    // REDUNDANT - duplicates model.Result
    record Result(IPAddress ip, String error) {
        public boolean isSuccess() { return error == null; }
        public boolean isFailure() { return error != null; }
    }

    Result allocate(IPAddressString subnet, int vmIndex);
}
```

#### Detailed Fix Instructions

##### Step 1: Update IpAllocator Interface

**File**: `src/main/java/com/k8s/generator/ip/IpAllocator.java`

**Before**:
```java
package com.k8s.generator.ip;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public interface IpAllocator {
    // Nested Result record
    record Result(IPAddress ip, String error) {
        public boolean isSuccess() { return error == null; }
        public boolean isFailure() { return error != null; }
    }

    Result allocate(IPAddressString subnet, int vmIndex);
}
```

**After**:
```java
package com.k8s.generator.ip;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import com.k8s.generator.model.Result;  // ADD THIS IMPORT

public interface IpAllocator {
    // REMOVE nested Result record entirely

    // Use global Result type
    Result<IPAddress, String> allocate(IPAddressString subnet, int vmIndex);
}
```

**Changes**:
1. Add import for `com.k8s.generator.model.Result`
2. Remove entire nested `record Result(...)` definition
3. Update method signature to return `Result<IPAddress, String>`

##### Step 2: Update SequentialIpAllocator Implementation

**File**: `src/main/java/com/k8s/generator/ip/SequentialIpAllocator.java`

**Before**:
```java
public class SequentialIpAllocator implements IpAllocator {

    @Override
    public IpAllocator.Result allocate(IPAddressString subnet, int vmIndex) {
        try {
            IPAddress base = subnet.getAddress();
            IPAddress allocated = base.increment(vmIndex);

            // Check reserved IPs (.1, .2, .5)
            if (isReserved(allocated)) {
                return new IpAllocator.Result(null, "IP is reserved: " + allocated);
            }

            return new IpAllocator.Result(allocated, null);
        } catch (Exception e) {
            return new IpAllocator.Result(null, "Allocation failed: " + e.getMessage());
        }
    }
}
```

**After**:
```java
import com.k8s.generator.model.Result;  // ADD THIS IMPORT

public class SequentialIpAllocator implements IpAllocator {

    @Override
    public Result<IPAddress, String> allocate(IPAddressString subnet, int vmIndex) {
        try {
            IPAddress base = subnet.getAddress();
            IPAddress allocated = base.increment(vmIndex);

            // Check reserved IPs (.1, .2, .5)
            if (isReserved(allocated)) {
                return new Result.Failure<>("IP is reserved: " + allocated);
            }

            return new Result.Success<>(allocated);
        } catch (Exception e) {
            return new Result.Failure<>("Allocation failed: " + e.getMessage());
        }
    }

    private boolean isReserved(IPAddress ip) {
        String ipStr = ip.toString();
        return ipStr.endsWith(".1") ||
               ipStr.endsWith(".2") ||
               ipStr.endsWith(".5");
    }
}
```

**Changes**:
1. Add import for `com.k8s.generator.model.Result`
2. Update return type to `Result<IPAddress, String>`
3. Replace `new IpAllocator.Result(allocated, null)` with `new Result.Success<>(allocated)`
4. Replace `new IpAllocator.Result(null, error)` with `new Result.Failure<>(error)`

##### Step 3: Update Consumer Code

**File**: Any file that uses `IpAllocator.Result` (likely in Parser or Orchestrator)

**Example - Before**:
```java
IpAllocator.Result result = allocator.allocate(subnet, index);
if (result.isSuccess()) {
    IPAddress ip = result.ip();
    // ...
} else {
    String error = result.error();
    // ...
}
```

**Example - After**:
```java
import com.k8s.generator.model.Result;

Result<IPAddress, String> result = allocator.allocate(subnet, index);
if (result.isSuccess()) {
    IPAddress ip = ((Result.Success<IPAddress, String>) result).value();
    // ...
} else {
    String error = ((Result.Failure<IPAddress, String>) result).error();
    // ...
}

// OR use pattern matching (Java 21+)
switch (result) {
    case Result.Success<IPAddress, String>(var ip) -> {
        // Use ip
    }
    case Result.Failure<IPAddress, String>(var error) -> {
        // Handle error
    }
}
```

##### Step 4: Update Tests

**File**: `src/test/java/com/k8s/generator/ip/SequentialIpAllocatorTest.java`

**Before**:
```java
@Test
void shouldAllocateValidIp() {
    IpAllocator allocator = new SequentialIpAllocator();
    IpAllocator.Result result = allocator.allocate(
        new IPAddressString("192.168.56.0/24"),
        10
    );

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.ip().toString()).isEqualTo("192.168.56.10");
}
```

**After**:
```java
import com.k8s.generator.model.Result;

@Test
void shouldAllocateValidIp() {
    IpAllocator allocator = new SequentialIpAllocator();
    Result<IPAddress, String> result = allocator.allocate(
        new IPAddressString("192.168.56.0/24"),
        10
    );

    assertThat(result).isInstanceOf(Result.Success.class);

    IPAddress ip = ((Result.Success<IPAddress, String>) result).value();
    assertThat(ip.toString()).isEqualTo("192.168.56.10");
}

@Test
void shouldFailOnReservedIp() {
    IpAllocator allocator = new SequentialIpAllocator();
    Result<IPAddress, String> result = allocator.allocate(
        new IPAddressString("192.168.56.0/24"),
        1  // .1 is reserved
    );

    assertThat(result).isInstanceOf(Result.Failure.class);

    String error = ((Result.Failure<IPAddress, String>) result).error();
    assertThat(error).contains("reserved");
}
```

---

### ⚠️ P7: CLI Missing --azure Flag (HIGH)

**Priority**: HIGH
**Estimated Effort**: 1-2 hours
**Risk Level**: Low - Feature gap, not architectural

#### Issue Description

The `GenerateCommand` CLI class lacks the `--azure` flag required to enable Azure/AKS integration workflows. This forces users to manually configure Azure settings via YAML, when it should be a simple CLI flag for the common case.

#### Affected Files

1. **`src/main/java/com/k8s/generator/cli/GenerateCommand.java`** - Missing option

#### Current State

**Existing Options**:
```java
@Command(name = "k8s-gen", description = "Generate Kubernetes learning environments")
public class GenerateCommand implements Callable<Integer> {

    @Option(names = {"-m", "--module"}, required = true, description = "Module number (e.g., m1, m7)")
    private String module;

    @Option(names = {"-t", "--type"}, required = true, description = "Type (e.g., pt, hw, exam)")
    private String type;

    @Parameters(index = "0", description = "Cluster type: kind|minikube|kubeadm|mgmt")
    private String clusterType;

    @Option(names = {"-o", "--output"}, description = "Output directory")
    private Path outputPath = Paths.get(".");

    @Option(names = {"--tools"}, description = "Additional tools to install", split = ",")
    private List<String> tools = new ArrayList<>();

    @Option(names = {"--size"}, description = "Size profile: small|medium|large")
    private String size = "medium";

    @Option(names = {"--nodes"}, description = "Node configuration (e.g., 1m,2w)")
    private String nodes;

    // MISSING: --azure flag
}
```

#### Detailed Fix Instructions

##### Step 1: Add Azure Option to GenerateCommand

**File**: `src/main/java/com/k8s/generator/cli/GenerateCommand.java`

**Add After Existing Options**:
```java
@Option(
    names = {"--azure"},
    description = "Enable Azure/AKS integration (installs Azure CLI, generates /etc/azure-env)"
)
private boolean azure = false;

// Add getter for use in parser
public boolean isAzure() {
    return azure;
}
```

**Full Context** (showing where to add):
```java
@Command(name = "k8s-gen", description = "Generate Kubernetes learning environments")
public class GenerateCommand implements Callable<Integer> {

    @Option(names = {"-m", "--module"}, required = true)
    private String module;

    @Option(names = {"-t", "--type"}, required = true)
    private String type;

    @Parameters(index = "0")
    private String clusterType;

    @Option(names = {"-o", "--output"})
    private Path outputPath = Paths.get(".");

    @Option(names = {"--tools"}, split = ",")
    private List<String> tools = new ArrayList<>();

    @Option(names = {"--size"})
    private String size = "medium";

    @Option(names = {"--nodes"})
    private String nodes;

    // ADD THIS SECTION
    @Option(
        names = {"--azure"},
        description = "Enable Azure/AKS integration (installs Azure CLI, generates /etc/azure-env)"
    )
    private boolean azure = false;

    // ADD GETTER
    public boolean isAzure() {
        return azure;
    }
    // END NEW SECTION

    @Override
    public Integer call() {
        // ... existing implementation
    }
}
```

##### Step 2: Auto-Enable azure_cli Tool When Flag Set

**File**: `src/main/java/com/k8s/generator/cli/GenerateCommand.java`

**Add Validation Method** (or update existing one):
```java
private void validateAndProcessFlags() {
    // Auto-add azure_cli tool when --azure flag is set
    if (azure && !tools.contains("azure_cli")) {
        tools.add("azure_cli");
    }

    // Existing validations...
}
```

**Call from execute()** or constructor:
```java
@Override
public Integer call() {
    validateAndProcessFlags();  // ADD THIS CALL

    // ... rest of execution
}
```

##### Step 3: Update Parser to Use Azure Flag

**File**: `src/main/java/com/k8s/generator/parser/CliToSpec.java`

**Already covered in P5 Step 4**, but for completeness:
```java
@Override
public GeneratorSpec convert(GenerateCommand cmd) {
    // ... existing cluster parsing ...

    // Use the new azure flag
    Optional<Management> management = Optional.empty();
    if (cmd.isAzure()) {  // USE NEW GETTER
        management = Optional.of(new Management(
            "mgmt",
            List.of("azure"),
            true,
            List.of("kubectl", "azure_cli")
        ));
    }

    return new GeneratorSpec(outputPath, clusters, management);
}
```

##### Step 4: Update Help Documentation

**File**: `src/main/java/com/k8s/generator/cli/GenerateCommand.java`

**Enhanced Description**:
```java
@Option(
    names = {"--azure"},
    description = {
        "Enable Azure/AKS integration:",
        "  - Installs Azure CLI (az)",
        "  - Generates /etc/azure-env with Azure variables",
        "  - Creates helper scripts for AKS kubeconfig retrieval",
        "Example: k8s-gen -m m9 -t lab mgmt --azure"
    }
)
private boolean azure = false;
```

##### Step 5: Add Tests

**New Test File**: `src/test/java/com/k8s/generator/cli/AzureFlagTest.java`

```java
package com.k8s.generator.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.*;

class AzureFlagTest {

    @Test
    void shouldParseAzureFlag() {
        GenerateCommand cmd = new GenerateCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute(
            "-m", "m9",
            "-t", "lab",
            "mgmt",
            "--azure"
        );

        assertThat(cmd.isAzure()).isTrue();
    }

    @Test
    void shouldDefaultToNoAzure() {
        GenerateCommand cmd = new GenerateCommand();
        CommandLine cli = new CommandLine(cmd);

        int exitCode = cli.execute(
            "-m", "m1",
            "-t", "pt",
            "kind"
        );

        assertThat(cmd.isAzure()).isFalse();
    }

    @Test
    void shouldAutoAddAzureCliTool() {
        GenerateCommand cmd = new GenerateCommand();
        CommandLine cli = new CommandLine(cmd);

        cli.execute(
            "-m", "m9",
            "-t", "lab",
            "mgmt",
            "--azure"
        );

        assertThat(cmd.getTools()).contains("azure_cli");
    }
}
```

**Update Integration Test**: `src/test/java/com/k8s/generator/cli/GenerateCommandSmokeTest.java`

```java
@Test
void shouldGenerateWithAzureIntegration() throws IOException {
    Path tempDir = Files.createTempDirectory("k8s-gen-test");

    int exitCode = new CommandLine(new GenerateCommand())
        .execute(
            "-m", "m9",
            "-t", "lab",
            "mgmt",
            "--azure",
            "-o", tempDir.toString()
        );

    assertThat(exitCode).isEqualTo(0);
    assertThat(tempDir.resolve("Vagrantfile")).exists();
    assertThat(tempDir.resolve("scripts/install_azure_cli.sh")).exists();

    // Cleanup
    Files.walk(tempDir)
        .sorted(Comparator.reverseOrder())
        .forEach(path -> path.toFile().delete());
}
```

---

### ⚠️ P8: OutputWriter Missing scaffoldHooks() (HIGH)

**Priority**: HIGH
**Estimated Effort**: 2-3 hours
**Risk Level**: Medium - Contract violation

#### Issue Description

The `OutputWriter` interface is missing the `scaffoldHooks(Path destination)` method that is expected by the Renderer and Orchestrator bricks. This creates a contract violation where consumers expect hook directory scaffolding capability but the I/O brick doesn't provide it.

Hook directories allow users to customize bootstrap scripts without modifying generated files—a critical feature for extensibility.

#### Affected Files

1. **`src/main/java/com/k8s/generator/fs/OutputWriter.java`** - Missing method signature
2. **`src/main/java/com/k8s/generator/fs/AtomicOutputWriter.java`** - Missing implementation

#### Current State

**OutputWriter Interface**:
```java
public interface OutputWriter {
    void writeVagrantfile(Path destination, String content) throws IOException;
    void writeBootstrapScript(Path destination, String content) throws IOException;
    void writeInstallerScript(Path destination, String content) throws IOException;

    // MISSING: void scaffoldHooks(Path destination) throws IOException;
}
```

#### Expected Behavior

The `scaffoldHooks()` method should create:

**Directory Structure**:
```
{destination}/scripts/
├── bootstrap.pre.d/
│   ├── common/
│   └── README.md
├── bootstrap.post.d/
│   ├── common/
│   └── README.md
├── bootstrap.env.local
├── bootstrap.pre.local.sh
└── bootstrap.post.local.sh
```

**Hook Types**:
1. **Pre-bootstrap hooks** (`.pre.d/`): Run before main bootstrap
2. **Post-bootstrap hooks** (`.post.d/`): Run after main bootstrap
3. **Local overrides** (`.env.local`, `.pre.local.sh`, `.post.local.sh`): User customizations

#### Detailed Fix Instructions

##### Step 1: Add Method Signature to Interface

**File**: `src/main/java/com/k8s/generator/fs/OutputWriter.java`

**Add Method**:
```java
public interface OutputWriter {
    void writeVagrantfile(Path destination, String content) throws IOException;
    void writeBootstrapScript(Path destination, String content) throws IOException;
    void writeInstallerScript(Path destination, String content) throws IOException;

    /**
     * Scaffolds hook directories and stub files for user customization.
     *
     * Creates the following structure:
     * <pre>
     * scripts/
     * ├── bootstrap.pre.d/    # Pre-bootstrap hooks
     * ├── bootstrap.post.d/   # Post-bootstrap hooks
     * ├── bootstrap.env.local # Local environment overrides
     * ├── bootstrap.pre.local.sh  # Local pre-hook
     * └── bootstrap.post.local.sh # Local post-hook
     * </pre>
     *
     * @param destination The output directory where scripts/ should be created
     * @throws IOException If directory/file creation fails
     */
    void scaffoldHooks(Path destination) throws IOException;
}
```

##### Step 2: Implement in AtomicOutputWriter

**File**: `src/main/java/com/k8s/generator/fs/AtomicOutputWriter.java`

**Add Implementation**:
```java
@Override
public void scaffoldHooks(Path destination) throws IOException {
    Path scriptsDir = destination.resolve("scripts");

    // Create hook directories
    createHookDirectories(scriptsDir);

    // Create stub files
    createHookStubs(scriptsDir);

    // Create README files
    createHookReadmes(scriptsDir);
}

private void createHookDirectories(Path scriptsDir) throws IOException {
    // Pre-bootstrap hooks
    Files.createDirectories(scriptsDir.resolve("bootstrap.pre.d/common"));

    // Post-bootstrap hooks
    Files.createDirectories(scriptsDir.resolve("bootstrap.post.d/common"));
}

private void createHookStubs(Path scriptsDir) throws IOException {
    // Local environment overrides
    createEnvLocalStub(scriptsDir.resolve("bootstrap.env.local"));

    // Pre-bootstrap local hook
    createPreLocalStub(scriptsDir.resolve("bootstrap.pre.local.sh"));

    // Post-bootstrap local hook
    createPostLocalStub(scriptsDir.resolve("bootstrap.post.local.sh"));
}

private void createEnvLocalStub(Path envLocalFile) throws IOException {
    if (Files.exists(envLocalFile)) {
        return; // Don't overwrite user's local config
    }

    String template = """
        # Local environment overrides (never committed)
        # This file is sourced by bootstrap.sh before provisioning

        # Example: Override cluster name
        # export CLUSTER_NAME="my-custom-cluster"

        # Example: Azure configuration
        # export AZ_SUBSCRIPTION_ID="your-subscription-id"
        # export AZ_RESOURCE_GROUP="rg-k8s-test"
        # export AZ_LOCATION="westeurope"

        # Example: Skip tool installation if already present
        # export SKIP_TOOLS="docker,kubectl"

        # Add your custom environment variables below:
        """;

    Files.writeString(envLocalFile, template);
}

private void createPreLocalStub(Path preLocalFile) throws IOException {
    if (Files.exists(preLocalFile)) {
        return; // Don't overwrite user's hook
    }

    String template = """
        #!/usr/bin/env bash
        # Local pre-bootstrap hook (never committed)
        # This script runs BEFORE the main bootstrap.sh

        set -Eeuo pipefail
        IFS=$'\\n\\t'

        echo "[bootstrap.pre.local] Running local pre-bootstrap customizations..."

        # Add your custom pre-bootstrap logic here
        # Example: Install additional packages
        # apt-get update && apt-get install -y vim htop

        echo "[bootstrap.pre.local] Pre-bootstrap customizations complete"
        """;

    Files.writeString(preLocalFile, template);
    setExecutable(preLocalFile);
}

private void createPostLocalStub(Path postLocalFile) throws IOException {
    if (Files.exists(postLocalFile)) {
        return; // Don't overwrite user's hook
    }

    String template = """
        #!/usr/bin/env bash
        # Local post-bootstrap hook (never committed)
        # This script runs AFTER the main bootstrap.sh

        set -Eeuo pipefail
        IFS=$'\\n\\t'

        echo "[bootstrap.post.local] Running local post-bootstrap customizations..."

        # Add your custom post-bootstrap logic here
        # Example: Configure kubectl aliases
        # kubectl config set-context --current --namespace=default

        echo "[bootstrap.post.local] Post-bootstrap customizations complete"
        """;

    Files.writeString(postLocalFile, template);
    setExecutable(postLocalFile);
}

private void createHookReadmes(Path scriptsDir) throws IOException {
    createPreDirReadme(scriptsDir.resolve("bootstrap.pre.d/README.md"));
    createPostDirReadme(scriptsDir.resolve("bootstrap.post.d/README.md"));
}

private void createPreDirReadme(Path readmeFile) throws IOException {
    if (Files.exists(readmeFile)) {
        return;
    }

    String content = """
        # Pre-Bootstrap Hooks

        Scripts in this directory run **before** the main bootstrap.sh.

        ## Execution Order

        1. `../bootstrap.pre.local.sh` (if exists)
        2. All `*.sh` files in `bootstrap.pre.d/` (lexicographic order)
        3. All `*.sh` files in `bootstrap.pre.d/common/` (lexicographic order)
        4. Main `bootstrap.sh`

        ## Naming Convention

        Use numeric prefixes to control execution order:
        - `10-setup-repos.sh`
        - `20-install-packages.sh`
        - `30-configure-network.sh`

        ## Example

        ```bash
        #!/usr/bin/env bash
        # bootstrap.pre.d/10-install-vim.sh

        set -Eeuo pipefail

        echo "Installing vim..."
        apt-get update && apt-get install -y vim
        ```

        ## Notes

        - Scripts must be executable: `chmod +x script.sh`
        - Scripts should be idempotent (safe to run multiple times)
        - Scripts inherit environment from bootstrap.sh
        """;

    Files.writeString(readmeFile, content);
}

private void createPostDirReadme(Path readmeFile) throws IOException {
    if (Files.exists(readmeFile)) {
        return;
    }

    String content = """
        # Post-Bootstrap Hooks

        Scripts in this directory run **after** the main bootstrap.sh.

        ## Execution Order

        1. Main `bootstrap.sh` completes
        2. All `*.sh` files in `bootstrap.post.d/` (lexicographic order)
        3. All `*.sh` files in `bootstrap.post.d/common/` (lexicographic order)
        4. `../bootstrap.post.local.sh` (if exists)

        ## Naming Convention

        Use numeric prefixes to control execution order:
        - `10-install-helm.sh`
        - `20-configure-kubectl.sh`
        - `30-verify-cluster.sh`

        ## Example

        ```bash
        #!/usr/bin/env bash
        # bootstrap.post.d/10-install-helm.sh

        set -Eeuo pipefail

        if command -v helm &>/dev/null; then
            echo "Helm already installed"
            exit 0
        fi

        echo "Installing Helm..."
        curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
        ```

        ## Notes

        - Scripts must be executable: `chmod +x script.sh`
        - Scripts should be idempotent (safe to run multiple times)
        - Use `command -v tool` to check if tools already exist
        """;

    Files.writeString(readmeFile, content);
}

private void setExecutable(Path file) throws IOException {
    if (System.getProperty("os.name").toLowerCase().contains("win")) {
        // Windows - can't set POSIX permissions
        return;
    }

    try {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(file, perms);
    } catch (UnsupportedOperationException e) {
        // Filesystem doesn't support POSIX permissions
        // This is OK - Windows, FAT32, etc.
    }
}
```

##### Step 3: Call from ScaffoldService

**File**: `src/main/java/com/k8s/generator/app/ScaffoldService.java`

**Add Call After File Writing**:
```java
public int scaffold(GenerateCommand cmd) {
    // ... existing conversion, validation, plan building, rendering ...

    // Write files
    Path outDir = determineOutDir(cmd, spec);
    OutputWriter writer = new AtomicOutputWriter();
    writer.writeFiles(files, outDir);

    // Copy scripts
    new ResourceCopier().copyScripts(plan, outDir);

    // NEW: Scaffold hooks
    try {
        writer.scaffoldHooks(outDir);
    } catch (IOException e) {
        System.err.println("Failed to scaffold hooks: " + e.getMessage());
        return 1;
    }

    return 0; // Success
}
```

##### Step 4: Add Tests

**New Test File**: `src/test/java/com/k8s/generator/fs/HookScaffoldingTest.java`

```java
package com.k8s.generator.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class HookScaffoldingTest {

    @Test
    void shouldCreateHookDirectories(@TempDir Path tempDir) throws IOException {
        OutputWriter writer = new AtomicOutputWriter();
        writer.scaffoldHooks(tempDir);

        assertThat(tempDir.resolve("scripts/bootstrap.pre.d")).exists().isDirectory();
        assertThat(tempDir.resolve("scripts/bootstrap.post.d")).exists().isDirectory();
        assertThat(tempDir.resolve("scripts/bootstrap.pre.d/common")).exists().isDirectory();
        assertThat(tempDir.resolve("scripts/bootstrap.post.d/common")).exists().isDirectory();
    }

    @Test
    void shouldCreateStubFiles(@TempDir Path tempDir) throws IOException {
        OutputWriter writer = new AtomicOutputWriter();
        writer.scaffoldHooks(tempDir);

        Path envLocal = tempDir.resolve("scripts/bootstrap.env.local");
        Path preLocal = tempDir.resolve("scripts/bootstrap.pre.local.sh");
        Path postLocal = tempDir.resolve("scripts/bootstrap.post.local.sh");

        assertThat(envLocal).exists().isRegularFile();
        assertThat(preLocal).exists().isRegularFile();
        assertThat(postLocal).exists().isRegularFile();
    }

    @Test
    void shouldCreateReadmeFiles(@TempDir Path tempDir) throws IOException {
        OutputWriter writer = new AtomicOutputWriter();
        writer.scaffoldHooks(tempDir);

        Path preReadme = tempDir.resolve("scripts/bootstrap.pre.d/README.md");
        Path postReadme = tempDir.resolve("scripts/bootstrap.post.d/README.md");

        assertThat(preReadme).exists().isRegularFile();
        assertThat(postReadme).exists().isRegularFile();

        String preContent = Files.readString(preReadme);
        assertThat(preContent).contains("Pre-Bootstrap Hooks");
        assertThat(preContent).contains("Execution Order");
    }

    @Test
    void shouldNotOverwriteExistingHooks(@TempDir Path tempDir) throws IOException {
        OutputWriter writer = new AtomicOutputWriter();

        // Create custom hook first
        Path preLocal = tempDir.resolve("scripts/bootstrap.pre.local.sh");
        Files.createDirectories(preLocal.getParent());
        Files.writeString(preLocal, "# My custom hook");

        // Scaffold should not overwrite
        writer.scaffoldHooks(tempDir);

        String content = Files.readString(preLocal);
        assertThat(content).isEqualTo("# My custom hook");
    }

    @Test
    void shouldSetExecutablePermissionsOnScripts(@TempDir Path tempDir) throws IOException {
        OutputWriter writer = new AtomicOutputWriter();
        writer.scaffoldHooks(tempDir);

        Path preLocal = tempDir.resolve("scripts/bootstrap.pre.local.sh");
        Path postLocal = tempDir.resolve("scripts/bootstrap.post.local.sh");

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            assertThat(Files.isExecutable(preLocal)).isTrue();
            assertThat(Files.isExecutable(postLocal)).isTrue();
        }
    }
}
```

---

## Anti-Patterns Found

### 1. Feature Envy in ScaffoldService (MEDIUM)

**Priority**: MEDIUM
**Estimated Effort**: 3-4 hours
**Risk Level**: Low - Technical debt, not blocking

#### Issue Description

The `ScaffoldService.scaffold()` method exhibits **feature envy**—it constantly reaches into domain objects to extract data rather than delegating behavior to those objects. This violates the principle of "tell, don't ask" and makes the service class do work that belongs in the domain model.

#### Location

**File**: `src/main/java/com/k8s/generator/app/ScaffoldService.java` (lines 45-85)

#### Problem Code

```java
// Lines 60-75 - Excessive data extraction
for (ClusterSpec cluster : spec.clusters()) {
    for (VmSpec vm : cluster.vms()) {
        String ip = vm.ip();       // Data extraction
        String name = vm.name();   // Data extraction
        VmRole role = vm.role();   // Data extraction

        // Business logic using extracted data
        if (role == VmRole.MASTER) {
            String bootstrapContent = String.format(
                "#!/bin/bash\nIP=%s\nNAME=%s\n# Master setup...",
                ip, name
            );
            // ... more formatting logic
        } else if (role == VmRole.WORKER) {
            String bootstrapContent = String.format(
                "#!/bin/bash\nIP=%s\nNAME=%s\n# Worker setup...",
                ip, name
            );
            // ... more formatting logic
        }
    }
}
```

**Why This Is a Problem**:
- Service is doing **presentation logic** (formatting strings)
- Service is doing **domain logic** (deciding master vs worker behavior)
- Hard to test formatting in isolation
- Hard to reuse formatting in other contexts
- Violates Single Responsibility Principle

#### Refactoring Solution

**Move behavior to domain objects**—records can have methods!

##### Step 1: Add Behavior Methods to VmSpec

**File**: `src/main/java/com/k8s/generator/model/VmSpec.java`

**Add Methods**:
```java
public record VmSpec(
    String name,
    String ip,
    VmRole role,
    int cpus,
    int memoryMb,
    String boxImage
) {
    // Existing compact constructor...

    /**
     * Check if this VM requires master node setup.
     */
    public boolean isMaster() {
        return role == VmRole.MASTER;
    }

    /**
     * Check if this VM requires worker node setup.
     */
    public boolean isWorker() {
        return role == VmRole.WORKER;
    }

    /**
     * Check if this VM is a management node.
     */
    public boolean isManagement() {
        return role == VmRole.MANAGEMENT;
    }

    /**
     * Render bootstrap context for templates.
     * Encapsulates how a VM presents itself to the renderer.
     */
    public String renderBootstrapContext() {
        return String.format("VM[name=%s, ip=%s, role=%s]", name, ip, role);
    }

    /**
     * Get display name for logs and output.
     */
    public String displayName() {
        return String.format("%s (%s)", name, role.name().toLowerCase());
    }

    /**
     * Get resource summary for display.
     */
    public String resourceSummary() {
        return String.format("%d CPUs, %d MB RAM", cpus, memoryMb);
    }
}
```

##### Step 2: Simplify ScaffoldService

**File**: `src/main/java/com/k8s/generator/app/ScaffoldService.java`

**Before**:
```java
for (ClusterSpec cluster : spec.clusters()) {
    for (VmSpec vm : cluster.vms()) {
        String ip = vm.ip();
        String name = vm.name();
        VmRole role = vm.role();

        if (role == VmRole.MASTER) {
            String content = String.format("#!/bin/bash\nIP=%s\nNAME=%s\n...", ip, name);
            writer.writeBootstrapScript(path, content);
        } else if (role == VmRole.WORKER) {
            String content = String.format("#!/bin/bash\nIP=%s\nNAME=%s\n...", ip, name);
            writer.writeBootstrapScript(path, content);
        }
    }
}
```

**After**:
```java
for (ClusterSpec cluster : spec.clusters()) {
    for (VmSpec vm : cluster.vms()) {
        // Delegate to renderer - much cleaner!
        if (vm.isMaster()) {
            String content = renderer.renderMasterBootstrap(vm);
            writer.writeBootstrapScript(resolvePath(vm), content);
        } else if (vm.isWorker()) {
            String content = renderer.renderWorkerBootstrap(vm);
            writer.writeBootstrapScript(resolvePath(vm), content);
        } else if (vm.isManagement()) {
            String content = renderer.renderManagementBootstrap(vm);
            writer.writeBootstrapScript(resolvePath(vm), content);
        }
    }
}

private Path resolvePath(VmSpec vm) {
    return outputDir.resolve("scripts").resolve(vm.name() + "-bootstrap.sh");
}
```

##### Step 3: Add Behavior Methods to ClusterSpec

**File**: `src/main/java/com/k8s/generator/model/ClusterSpec.java`

**Add Methods**:
```java
public record ClusterSpec(
    String name,
    ClusterType type,
    EngineType engine,
    String baseIp,
    List<VmSpec> vms,
    List<String> tools,
    Optional<AzureSpec> azure,
    String podNetwork,
    String svcNetwork
) {
    // Existing compact constructor...

    /**
     * Get all master VMs in this cluster.
     */
    public List<VmSpec> masters() {
        return vms.stream()
            .filter(VmSpec::isMaster)
            .toList();
    }

    /**
     * Get all worker VMs in this cluster.
     */
    public List<VmSpec> workers() {
        return vms.stream()
            .filter(VmSpec::isWorker)
            .toList();
    }

    /**
     * Get total VM count.
     */
    public int totalVms() {
        return vms.size();
    }

    /**
     * Check if this cluster uses Azure integration.
     */
    public boolean usesAzure() {
        return azure.isPresent();
    }

    /**
     * Get display summary for logs.
     */
    public String displaySummary() {
        return String.format(
            "%s (%s): %d masters, %d workers",
            name,
            engine,
            masters().size(),
            workers().size()
        );
    }
}
```

##### Step 4: Update Tests

**New Test File**: `src/test/java/com/k8s/generator/model/VmSpecBehaviorTest.java`

```java
package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class VmSpecBehaviorTest {

    @Test
    void shouldIdentifyMasterRole() {
        VmSpec vm = createVm(VmRole.MASTER);

        assertThat(vm.isMaster()).isTrue();
        assertThat(vm.isWorker()).isFalse();
        assertThat(vm.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyWorkerRole() {
        VmSpec vm = createVm(VmRole.WORKER);

        assertThat(vm.isMaster()).isFalse();
        assertThat(vm.isWorker()).isTrue();
        assertThat(vm.isManagement()).isFalse();
    }

    @Test
    void shouldRenderBootstrapContext() {
        VmSpec vm = new VmSpec(
            "master-1",
            "192.168.56.10",
            VmRole.MASTER,
            4,
            8192,
            "ubuntu/jammy64"
        );

        String context = vm.renderBootstrapContext();

        assertThat(context).isEqualTo("VM[name=master-1, ip=192.168.56.10, role=MASTER]");
    }

    @Test
    void shouldDisplayResourceSummary() {
        VmSpec vm = createVm(VmRole.MASTER);

        String summary = vm.resourceSummary();

        assertThat(summary).isEqualTo("4 CPUs, 8192 MB RAM");
    }

    private VmSpec createVm(VmRole role) {
        return new VmSpec(
            "test-vm",
            "192.168.56.10",
            role,
            4,
            8192,
            "ubuntu/jammy64"
        );
    }
}
```

**Benefit**: Service code becomes **declarative** ("what to do") rather than **imperative** ("how to do it").

---

### 2. Primitive Obsession (MEDIUM)

**Priority**: MEDIUM
**Estimated Effort**: 4-6 hours
**Risk Level**: Medium - Prevents compile-time safety

#### Issue Description

The codebase uses raw `String` types for structured data that should be value objects. This leads to:
- Validation scattered across multiple locations
- Easy to pass invalid data
- Loss of type safety
- Harder to refactor

#### Affected Locations

1. **IP Addresses**: `ClusterSpec.baseIp` (String instead of IPAddressString)
2. **CIDR Notation**: `ClusterSpec.podNetwork`, `svcNetwork` (String instead of NetworkCIDR)
3. **Module Numbers**: `GeneratorSpec.module` (String instead of ModuleId value object)

#### Refactoring Solution

Create **value objects** with validation embedded.

##### Step 1: Create NetworkCIDR Value Object

**New File**: `src/main/java/com/k8s/generator/model/NetworkCIDR.java`

```java
package com.k8s.generator.model;

import inet.ipaddr.IPAddressString;
import java.util.Objects;

/**
 * Value object representing a network CIDR (Classless Inter-Domain Routing) notation.
 *
 * Examples: "10.244.0.0/16", "192.168.0.0/24"
 *
 * Validates:
 * - IPv4 format
 * - CIDR notation with prefix length
 * - Prefix length in practical range (8-30)
 */
public record NetworkCIDR(String value) {

    public NetworkCIDR {
        Objects.requireNonNull(value, "CIDR cannot be null");

        // Validate format: X.X.X.X/N
        if (!value.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+$")) {
            throw new IllegalArgumentException(
                "Invalid CIDR format: " + value + ". Expected format: X.X.X.X/N"
            );
        }

        // Validate prefix length
        int prefixLen = extractPrefixLength(value);
        if (prefixLen < 8 || prefixLen > 30) {
            throw new IllegalArgumentException(
                "CIDR prefix must be 8-30 for practical networks, got: " + prefixLen
            );
        }

        // Validate IP address is valid
        IPAddressString ipAddr = new IPAddressString(value);
        if (!ipAddr.isValid()) {
            throw new IllegalArgumentException(
                "Invalid IP address in CIDR: " + value
            );
        }
    }

    /**
     * Extract prefix length from CIDR notation.
     */
    public int prefixLength() {
        return extractPrefixLength(value);
    }

    /**
     * Convert to IPAddressString for IP manipulation.
     */
    public IPAddressString toIPAddressString() {
        return new IPAddressString(value);
    }

    /**
     * Get network address (first IP in range).
     */
    public String networkAddress() {
        return toIPAddressString().getAddress().toZeroHost().toString();
    }

    /**
     * Get broadcast address (last IP in range).
     */
    public String broadcastAddress() {
        return toIPAddressString().getAddress().toMaxHost().toString();
    }

    /**
     * Check if this CIDR overlaps with another.
     */
    public boolean overlapsWith(NetworkCIDR other) {
        return toIPAddressString().getAddress()
            .overlaps(other.toIPAddressString().getAddress());
    }

    @Override
    public String toString() {
        return value;
    }

    private static int extractPrefixLength(String cidr) {
        return Integer.parseInt(cidr.substring(cidr.indexOf('/') + 1));
    }

    // Common defaults
    public static NetworkCIDR defaultPodNetwork() {
        return new NetworkCIDR("10.244.0.0/16");
    }

    public static NetworkCIDR defaultServiceNetwork() {
        return new NetworkCIDR("10.96.0.0/12");
    }
}
```

##### Step 2: Create ModuleId Value Object

**New File**: `src/main/java/com/k8s/generator/model/ModuleId.java`

```java
package com.k8s.generator.model;

import java.util.Objects;

/**
 * Value object representing a module identifier.
 *
 * Format: "mN" where N is a positive integer
 * Examples: "m1", "m7", "m12"
 */
public record ModuleId(String value) {

    private static final String PATTERN = "m\\d+";

    public ModuleId {
        Objects.requireNonNull(value, "Module ID cannot be null");

        if (!value.matches(PATTERN)) {
            throw new IllegalArgumentException(
                "Invalid module ID format: " + value + ". Expected: mN (e.g., m1, m7)"
            );
        }
    }

    /**
     * Extract numeric part of module ID.
     */
    public int number() {
        return Integer.parseInt(value.substring(1));
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * Create from number.
     */
    public static ModuleId of(int number) {
        if (number < 1) {
            throw new IllegalArgumentException("Module number must be positive, got: " + number);
        }
        return new ModuleId("m" + number);
    }
}
```

##### Step 3: Update ClusterSpec to Use Value Objects

**File**: `src/main/java/com/k8s/generator/model/ClusterSpec.java`

**Before**:
```java
public record ClusterSpec(
    String name,
    ClusterType type,
    EngineType engine,
    String baseIp,  // Primitive obsession
    List<VmSpec> vms,
    List<String> tools,
    Optional<AzureSpec> azure,
    String podNetwork,  // Primitive obsession
    String svcNetwork   // Primitive obsession
)
```

**After**:
```java
import inet.ipaddr.IPAddressString;

public record ClusterSpec(
    String name,
    ClusterType type,
    EngineType engine,
    IPAddressString baseIp,    // Value object
    List<VmSpec> vms,
    List<String> tools,
    Optional<AzureSpec> azure,
    NetworkCIDR podNetwork,    // Value object
    NetworkCIDR svcNetwork     // Value object
) {
    // Compact constructor validation
    public ClusterSpec {
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(engine, "engine cannot be null");
        Objects.requireNonNull(baseIp, "baseIp cannot be null");
        Objects.requireNonNull(vms, "vms cannot be null");
        Objects.requireNonNull(tools, "tools cannot be null");
        Objects.requireNonNull(azure, "azure cannot be null");
        Objects.requireNonNull(podNetwork, "podNetwork cannot be null");
        Objects.requireNonNull(svcNetwork, "svcNetwork cannot be null");

        // No need for format validation - value objects handle it!

        // Check for overlap
        if (podNetwork.overlapsWith(svcNetwork)) {
            throw new IllegalArgumentException(
                "Pod network " + podNetwork + " overlaps with service network " + svcNetwork
            );
        }

        // Defensive copies
        vms = List.copyOf(vms);
        tools = List.copyOf(tools);
        azure = azure == null ? Optional.empty() : azure;
    }
}
```

**Benefits**:
- Validation happens at construction time
- Can't create invalid ClusterSpec
- Overlap checking is a method call, not scattered logic
- Type system prevents passing wrong data

##### Step 4: Update Parser to Create Value Objects

**File**: `src/main/java/com/k8s/generator/parser/CliToSpec.java`

**Before**:
```java
ClusterSpec cluster = new ClusterSpec(
    name,
    type,
    engine,
    "192.168.56.10",    // String
    vms,
    tools,
    azure,
    "10.244.0.0/16",   // String
    "10.96.0.0/12"     // String
);
```

**After**:
```java
import inet.ipaddr.IPAddressString;

ClusterSpec cluster = new ClusterSpec(
    name,
    type,
    engine,
    new IPAddressString("192.168.56.10"),  // Value object
    vms,
    tools,
    azure,
    NetworkCIDR.defaultPodNetwork(),       // Factory method
    NetworkCIDR.defaultServiceNetwork()    // Factory method
);
```

##### Step 5: Add Tests

**New Test File**: `src/test/java/com/k8s/generator/model/NetworkCIDRTest.java`

```java
package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NetworkCIDRTest {

    @Test
    void shouldAcceptValidCIDR() {
        NetworkCIDR cidr = new NetworkCIDR("10.244.0.0/16");

        assertThat(cidr.value()).isEqualTo("10.244.0.0/16");
        assertThat(cidr.prefixLength()).isEqualTo(16);
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThatThrownBy(() -> new NetworkCIDR("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid CIDR format");
    }

    @Test
    void shouldRejectInvalidPrefix() {
        assertThatThrownBy(() -> new NetworkCIDR("10.0.0.0/7"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("prefix must be 8-30");

        assertThatThrownBy(() -> new NetworkCIDR("10.0.0.0/31"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("prefix must be 8-30");
    }

    @Test
    void shouldDetectOverlap() {
        NetworkCIDR cidr1 = new NetworkCIDR("10.244.0.0/16");
        NetworkCIDR cidr2 = new NetworkCIDR("10.244.128.0/17");

        assertThat(cidr1.overlapsWith(cidr2)).isTrue();
    }

    @Test
    void shouldDetectNoOverlap() {
        NetworkCIDR cidr1 = new NetworkCIDR("10.244.0.0/16");
        NetworkCIDR cidr2 = new NetworkCIDR("10.96.0.0/12");

        assertThat(cidr1.overlapsWith(cidr2)).isFalse();
    }

    @Test
    void shouldProvideDefaultPodNetwork() {
        NetworkCIDR cidr = NetworkCIDR.defaultPodNetwork();

        assertThat(cidr.value()).isEqualTo("10.244.0.0/16");
    }

    @Test
    void shouldProvideDefaultServiceNetwork() {
        NetworkCIDR cidr = NetworkCIDR.defaultServiceNetwork();

        assertThat(cidr.value()).isEqualTo("10.96.0.0/12");
    }
}
```

---

### 3. God Object Risk: ScaffoldService (LOW-MEDIUM)

**Priority**: MEDIUM
**Estimated Effort**: 6-8 hours
**Risk Level**: Low - Preventative refactoring

#### Issue Description

`ScaffoldService` is trending toward becoming a **God Object**—a class with too many responsibilities. Currently at ~250 lines with 7+ concerns:

1. Orchestration (coordinate bricks)
2. VM plan creation
3. Bootstrap plan creation
4. Installer plan creation
5. Rendering coordination
6. File I/O coordination
7. Error aggregation

As Phase 3 adds more engines (k3s, microk8s, AKS), this class will grow rapidly and become unmaintainable.

#### Current State

**File**: `src/main/java/com/k8s/generator/app/ScaffoldService.java` (~250 lines)

```java
public class ScaffoldService {

    public Result<Path, List<ValidationError>> scaffold(GeneratorSpec spec) {
        // 1. Validation
        ValidationResult validation = validator.validate(spec);
        if (!validation.isValid()) {
            return Result.failure(validation.errors());
        }

        // 2. Create VM plans
        List<VmPlan> vmPlans = new ArrayList<>();
        for (ClusterSpec cluster : spec.clusters()) {
            // Complex VM creation logic (30+ lines)
        }

        // 3. Create bootstrap plans
        List<BootstrapPlan> bootstrapPlans = new ArrayList<>();
        for (VmPlan vm : vmPlans) {
            // Complex bootstrap creation logic (40+ lines)
        }

        // 4. Create installer plans
        List<InstallerPlan> installerPlans = new ArrayList<>();
        for (ClusterSpec cluster : spec.clusters()) {
            // Complex installer logic (25+ lines)
        }

        // 5. Build scaffold plan
        ScaffoldPlan plan = new ScaffoldPlan(
            outputDir, vmPlans, bootstrapPlans, installerPlans, providers
        );

        // 6. Render templates
        Map<String, String> files = renderer.render(plan);

        // 7. Write files
        writer.writeFiles(files, outputDir);

        // 8. Copy resources
        copier.copyResources(plan, outputDir);

        return Result.success(outputDir);
    }
}
```

**Problem**: As new engines are added, steps 2-4 will need engine-specific branching, causing **combinatorial explosion** of complexity.

#### Refactoring Solution

Apply **Facade + Strategy** pattern:
- Keep `ScaffoldService` as a **thin orchestrator**
- Extract **plan creation** into `PlanFactory` (Strategy pattern)
- Extract **rendering coordination** into `RenderingOrchestrator`

##### Step 1: Extract PlanFactory Interface

**New File**: `src/main/java/com/k8s/generator/app/PlanFactory.java`

```java
package com.k8s.generator.app;

import com.k8s.generator.model.GeneratorSpec;
import com.k8s.generator.model.ScaffoldPlan;
import com.k8s.generator.model.Result;
import com.k8s.generator.model.ValidationError;
import java.util.List;

/**
 * Factory for creating ScaffoldPlan from GeneratorSpec.
 *
 * Encapsulates all plan creation logic (VM plans, bootstrap plans, installer plans).
 */
public interface PlanFactory {

    /**
     * Create a scaffold plan from a validated spec.
     *
     * @param spec The generator specification (must be validated)
     * @return Success with ScaffoldPlan, or Failure with validation errors
     */
    Result<ScaffoldPlan, List<ValidationError>> createPlan(GeneratorSpec spec);
}
```

##### Step 2: Implement DefaultPlanFactory

**New File**: `src/main/java/com/k8s/generator/app/DefaultPlanFactory.java`

```java
package com.k8s.generator.app;

import com.k8s.generator.model.*;
import com.k8s.generator.ip.IpAllocator;
import com.k8s.generator.orchestrator.VmGenerator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DefaultPlanFactory implements PlanFactory {

    private final IpAllocator ipAllocator;
    private final VmGenerator vmGenerator;

    public DefaultPlanFactory(IpAllocator ipAllocator, VmGenerator vmGenerator) {
        this.ipAllocator = ipAllocator;
        this.vmGenerator = vmGenerator;
    }

    @Override
    public Result<ScaffoldPlan, List<ValidationError>> createPlan(GeneratorSpec spec) {
        try {
            // 1. Create VM plans
            List<VmPlan> vmPlans = createVmPlans(spec);

            // 2. Create bootstrap plans
            List<BootstrapPlan> bootstrapPlans = createBootstrapPlans(vmPlans);

            // 3. Create installer plans
            List<InstallerPlan> installerPlans = createInstallerPlans(spec);

            // 4. Extract providers
            Set<String> providers = extractProviders(spec);

            // 5. Build plan
            ScaffoldPlan plan = new ScaffoldPlan(
                spec.outputPath(),
                vmPlans,
                bootstrapPlans,
                installerPlans,
                providers
            );

            return Result.success(plan);
        } catch (Exception e) {
            ValidationError error = new ValidationError(
                "plan_creation",
                ValidationLevel.ERROR,
                "Failed to create scaffold plan: " + e.getMessage(),
                "Check spec validity and system resources"
            );
            return Result.failure(List.of(error));
        }
    }

    private List<VmPlan> createVmPlans(GeneratorSpec spec) {
        List<VmPlan> plans = new ArrayList<>();

        for (ClusterSpec cluster : spec.clusters()) {
            List<VmSpec> vms = vmGenerator.generate(cluster);
            for (VmSpec vm : vms) {
                plans.add(new VmPlan(vm, cluster));
            }
        }

        return plans;
    }

    private List<BootstrapPlan> createBootstrapPlans(List<VmPlan> vmPlans) {
        List<BootstrapPlan> plans = new ArrayList<>();

        for (VmPlan vmPlan : vmPlans) {
            plans.add(new BootstrapPlan(
                vmPlan.vm(),
                determineBootstrapTemplate(vmPlan)
            ));
        }

        return plans;
    }

    private String determineBootstrapTemplate(VmPlan vmPlan) {
        VmSpec vm = vmPlan.vm();

        if (vm.isMaster()) {
            return "bootstrap-master.sh.jte";
        } else if (vm.isWorker()) {
            return "bootstrap-worker.sh.jte";
        } else if (vm.isManagement()) {
            return "bootstrap-mgmt.sh.jte";
        }

        return "bootstrap-default.sh.jte";
    }

    private List<InstallerPlan> createInstallerPlans(GeneratorSpec spec) {
        List<InstallerPlan> plans = new ArrayList<>();
        Set<String> allTools = new HashSet<>();

        for (ClusterSpec cluster : spec.clusters()) {
            allTools.addAll(cluster.tools());
        }

        for (String tool : allTools) {
            plans.add(new InstallerPlan(tool, "install_" + tool + ".sh"));
        }

        return plans;
    }

    private Set<String> extractProviders(GeneratorSpec spec) {
        Set<String> providers = new HashSet<>();

        spec.management().ifPresent(mgmt ->
            providers.addAll(mgmt.providers())
        );

        return providers;
    }
}
```

##### Step 3: Extract RenderingOrchestrator

**New File**: `src/main/java/com/k8s/generator/app/RenderingOrchestrator.java`

```java
package com.k8s.generator.app;

import com.k8s.generator.model.ScaffoldPlan;
import com.k8s.generator.render.Renderer;
import com.k8s.generator.fs.OutputWriter;
import com.k8s.generator.fs.ResourceCopier;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Orchestrates rendering and file writing.
 */
public class RenderingOrchestrator {

    private final Renderer renderer;
    private final OutputWriter writer;
    private final ResourceCopier copier;

    public RenderingOrchestrator(Renderer renderer, OutputWriter writer, ResourceCopier copier) {
        this.renderer = renderer;
        this.writer = writer;
        this.copier = copier;
    }

    /**
     * Render plan to files and write to disk.
     */
    public void render(ScaffoldPlan plan) throws IOException {
        Path outputDir = plan.outputDir();

        // 1. Render templates
        Map<String, String> files = renderer.render(plan);

        // 2. Write files
        writer.writeFiles(files, outputDir);

        // 3. Copy resources
        copier.copyResources(plan, outputDir);

        // 4. Scaffold hooks
        writer.scaffoldHooks(outputDir);
    }
}
```

##### Step 4: Refactor ScaffoldService

**File**: `src/main/java/com/k8s/generator/app/ScaffoldService.java`

**Before** (~250 lines):
```java
public class ScaffoldService {
    public Result<Path, List<ValidationError>> scaffold(GeneratorSpec spec) {
        // 1. Validation (10 lines)
        // 2. Create VM plans (30 lines)
        // 3. Create bootstrap plans (40 lines)
        // 4. Create installer plans (25 lines)
        // 5. Build scaffold plan (15 lines)
        // 6. Render templates (10 lines)
        // 7. Write files (10 lines)
        // 8. Copy resources (10 lines)
        // ... error handling (20 lines)
    }
}
```

**After** (~50 lines):
```java
public class ScaffoldService {

    private final PlanFactory planFactory;
    private final RenderingOrchestrator orchestrator;

    public ScaffoldService(PlanFactory planFactory, RenderingOrchestrator orchestrator) {
        this.planFactory = planFactory;
        this.orchestrator = orchestrator;
    }

    /**
     * Scaffold a Kubernetes learning environment.
     *
     * @param spec The generator specification
     * @return Success with output directory, or Failure with validation errors
     */
    public Result<Path, List<ValidationError>> scaffold(GeneratorSpec spec) {
        // 1. Create plan
        Result<ScaffoldPlan, List<ValidationError>> planResult = planFactory.createPlan(spec);
        if (planResult.isFailure()) {
            return Result.failure(((Result.Failure<ScaffoldPlan, List<ValidationError>>) planResult).error());
        }

        ScaffoldPlan plan = ((Result.Success<ScaffoldPlan, List<ValidationError>>) planResult).value();

        // 2. Render and write
        try {
            orchestrator.render(plan);
        } catch (IOException e) {
            ValidationError error = new ValidationError(
                "rendering",
                ValidationLevel.ERROR,
                "Failed to render plan: " + e.getMessage(),
                "Check output directory permissions and disk space"
            );
            return Result.failure(List.of(error));
        }

        // 3. Success
        return Result.success(plan.outputDir());
    }
}
```

**Benefits**:
- ScaffoldService reduced from ~250 to ~50 lines
- Clear **Single Responsibility**: orchestration only
- Easy to test each component in isolation
- Easy to add new engines (extend PlanFactory implementations)
- Follows **Open/Closed Principle**: open for extension, closed for modification

##### Step 5: Update Main to Wire Dependencies

**File**: `src/main/java/com/k8s/generator/Main.java`

```java
public class Main {
    public static void main(String[] args) {
        // Create dependencies
        IpAllocator ipAllocator = new SequentialIpAllocator();
        VmGenerator vmGenerator = new DefaultVmGenerator(ipAllocator);
        PlanFactory planFactory = new DefaultPlanFactory(ipAllocator, vmGenerator);

        Renderer renderer = new JteRenderer();
        OutputWriter writer = new AtomicOutputWriter();
        ResourceCopier copier = new ResourceCopier();
        RenderingOrchestrator orchestrator = new RenderingOrchestrator(renderer, writer, copier);

        // Create service
        ScaffoldService service = new ScaffoldService(planFactory, orchestrator);

        // Run CLI
        int exitCode = new CommandLine(new GenerateCommand(service)).execute(args);
        System.exit(exitCode);
    }
}
```

---

## Code Quality Issues by Brick

### CLI Brick (`com.k8s.generator.cli`)

**✅ Strengths**:
- Clean Picocli integration
- Good separation of command parsing and business logic
- Clear validation in `GenerateCommand.validate()`

**⚠️ Issues**:

#### Issue 1: Missing Azure Flag (P7)
**Already covered in P7 section above.**

#### Issue 2: No --dry-run Flag (LOW)

**Priority**: LOW
**Effort**: 2-3 hours

**Add Option**:
```java
@Option(
    names = {"--dry-run"},
    description = {
        "Show what would be generated without writing files",
        "Useful for validating configuration before generation"
    }
)
private boolean dryRun = false;

public boolean isDryRun() {
    return dryRun;
}
```

**Update ScaffoldService**:
```java
public Result<Path, List<ValidationError>> scaffold(GeneratorSpec spec, boolean dryRun) {
    // ... create plan ...

    if (dryRun) {
        System.out.println("DRY RUN: Would generate the following files:");
        plan.vmPlans().forEach(vm ->
            System.out.println("  - VM: " + vm.vm().displayName())
        );
        plan.bootstrapPlans().forEach(bootstrap ->
            System.out.println("  - Bootstrap: " + bootstrap.templateName())
        );
        return Result.success(plan.outputDir());
    }

    // ... actual rendering ...
}
```

#### Issue 3: Tools Validation Incomplete (MEDIUM)

**Priority**: MEDIUM
**Effort**: 1-2 hours

**Add Validation**:
```java
private static final Set<String> VALID_TOOLS = Set.of(
    "docker", "kubectl", "helm", "kustomize",
    "azure_cli", "aws_cli", "gcp_cli",
    "kind", "minikube", "kubeadm",
    "k9s", "stern", "kubectx"
);

private void validateTools() {
    for (String tool : tools) {
        if (!VALID_TOOLS.contains(tool)) {
            throw new IllegalArgumentException(
                "Unknown tool: '" + tool + "'. Valid tools: " + VALID_TOOLS
            );
        }
    }
}
```

---

### Model Brick (`com.k8s.generator.model`)

**✅ Strengths**:
- Excellent use of Java records for immutability
- Compact constructor validation
- Clear domain vocabulary

**⚠️ Issues**:

#### Issue 1: Missing Fields (P5)
**Already covered in P5 section above.**

#### Issue 2: Inconsistent Optional Usage (MEDIUM)

**Priority**: MEDIUM
**Effort**: 2-3 hours

**Current Inconsistency**:
```java
public record ClusterSpec(
    // ...
    Optional<AzureSpec> azure,  // Uses Optional
    List<String> tools          // Uses empty list instead of Optional<List<String>>
)
```

**Recommendation**: Follow Java best practice—**use empty collections, reserve Optional for complex domain objects**:

```java
public record ClusterSpec(
    String name,
    ClusterType type,
    EngineType engine,
    IPAddressString baseIp,
    List<VmSpec> vms,
    List<String> tools,           // Never null, default to List.of()
    Optional<AzureSpec> azure,    // Complex object, nullable
    NetworkCIDR podNetwork,
    NetworkCIDR svcNetwork
) {
    public ClusterSpec {
        // ... null checks ...

        // Defensive copies with null-safe defaults
        vms = vms == null ? List.of() : List.copyOf(vms);
        tools = tools == null ? List.of() : List.copyOf(tools);
        azure = azure == null ? Optional.empty() : azure;
    }
}
```

**Rationale**:
- Collections should never be null (use empty collections)
- `Optional<List<T>>` is an anti-pattern in Java
- Reserve `Optional` for complex domain objects that are truly optional

---

### Parser Brick (`com.k8s.generator.parser`)

**✅ Strengths**:
- Clean conversion from CLI to domain model
- Good error handling with specific exception types

**⚠️ Issues**:

#### Issue 1: No YAML Parser Yet (Phase 4)
**Planned for Phase 4—OK for now.**

#### Issue 2: IP Allocation Coupling (MEDIUM)

**Priority**: MEDIUM
**Effort**: 1-2 hours

**Current Problem**:
```java
// CliArgsParser.java - Tight coupling to SequentialIpAllocator
IpAllocator allocator = new SequentialIpAllocator();
```

**Recommendation**: Inject `IpAllocator` via constructor:

```java
public class CliArgsParser {
    private final IpAllocator ipAllocator;

    public CliArgsParser(IpAllocator ipAllocator) {
        this.ipAllocator = ipAllocator;
    }

    // Or use factory method for convenience
    public static CliArgsParser withSequentialAllocation() {
        return new CliArgsParser(new SequentialIpAllocator());
    }

    public GeneratorSpec parse(GenerateCommand cmd) {
        // Use injected ipAllocator
    }
}
```

**Benefits**:
- Testable with mock IpAllocator
- Can swap allocation strategies
- Follows Dependency Inversion Principle

---

### Validation Brick (`com.k8s.generator.validate`)

**✅ Strengths**:
- Excellent 3-layer validation (Structural/Semantic/Policy)
- Clear separation of concerns
- Good use of ValidationError records

**⚠️ Issues**:

#### Issue 1: ValidationError Missing Severity Level (LOW)

**Priority**: LOW
**Effort**: 2 hours

**Current**:
```java
public record ValidationError(String field, String message, String suggestion)
```

**Better**:
```java
public record ValidationError(
    String field,
    ValidationLevel level,  // ERROR, WARNING, INFO
    String message,
    String suggestion
)

enum ValidationLevel { ERROR, WARNING, INFO }
```

**Usage**:
```java
// Error - blocks generation
new ValidationError(
    "podNetwork",
    ValidationLevel.ERROR,
    "Pod network overlaps with service network",
    "Use non-overlapping CIDRs"
)

// Warning - allows generation but warns user
new ValidationError(
    "boxImage",
    ValidationLevel.WARNING,
    "Box image is not ubuntu/* - may have compatibility issues",
    "Consider using ubuntu/jammy64"
)
```

#### Issue 2: Semantic Validator Could Use Builder Pattern (LOW)

**Priority**: LOW
**Effort**: 1-2 hours

**Current**:
```java
SemanticValidator validator = new SemanticValidator();
ValidationResult result = validator.validate(spec);
```

**Better (fluent builder)**:
```java
ValidationResult result = SemanticValidator.builder()
    .withSpec(spec)
    .checkClusterNames()
    .checkIpRanges()
    .checkTools()
    .checkNetworkOverlaps()
    .validate();
```

**Benefits**:
- More discoverable (IDE autocomplete shows available checks)
- Easy to enable/disable specific checks
- Clearer intent

---

### Rendering Brick (`com.k8s.generator.render`)

**✅ Strengths**:
- Clean JTE integration
- Type-safe template contexts
- Good separation of rendering concerns

**⚠️ Issues**:

#### Issue 1: Template Context Classes Could Use Records (MEDIUM)

**Priority**: MEDIUM
**Effort**: 2-3 hours

**Current - Mutable POJO**:
```java
public class VagrantfileContext {
    private String clusterName;
    private List<VmContext> vms;

    // Getters/setters...
    public void setClusterName(String name) { this.clusterName = name; }
    public void addVm(VmContext vm) { this.vms.add(vm); }
}
```

**Better - Immutable Record**:
```java
public record VagrantfileContext(
    String clusterName,
    List<VmContext> vms,
    Map<String, String> metadata
) {
    public VagrantfileContext {
        vms = List.copyOf(vms);  // Defensive copy
        metadata = Map.copyOf(metadata);
    }
}
```

**Benefits**:
- Immutable by default
- Can't accidentally mutate context during rendering
- Cleaner code (no boilerplate)

#### Issue 2: No Template Caching (LOW)

**Priority**: LOW (optimize later)
**Effort**: 2-3 hours

**Future Optimization**:
```java
public class JteRenderer implements Renderer {
    private final Map<String, Template> templateCache = new ConcurrentHashMap<>();

    private Template getTemplate(String name) {
        return templateCache.computeIfAbsent(name, this::loadTemplate);
    }

    private Template loadTemplate(String name) {
        // Load and compile template
    }
}
```

**Note**: Only needed if template compilation is a performance bottleneck. Profile first.

---

### I/O Brick (`com.k8s.generator.fs`)

**✅ Strengths**:
- Excellent atomic write implementation
- Good error handling with rollback
- Clean separation from rendering

**⚠️ Issues**:

#### Issue 1: Missing scaffoldHooks() (P8)
**Already covered in P8 section above.**

#### Issue 2: No File Permission Setting (LOW)

**Priority**: LOW
**Effort**: 30 minutes

**Current**:
```java
// AtomicOutputWriter.writeBootstrapScript()
Files.writeString(destination, content);
```

**Better**:
```java
Files.writeString(destination, content);

// Set executable permissions (Unix-like systems only)
if (!System.getProperty("os.name").toLowerCase().contains("win")) {
    try {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxr-xr-x");
        Files.setPosixFilePermissions(destination, perms);
    } catch (UnsupportedOperationException e) {
        // Filesystem doesn't support POSIX permissions (OK on Windows/FAT32)
    }
}
```

#### Issue 3: Missing Output Directory Validation (MEDIUM)

**Priority**: MEDIUM
**Effort**: 1 hour

**Add Validation**:
```java
private void validateOutputDirectory(Path outputDir) throws IOException {
    if (Files.exists(outputDir)) {
        if (!Files.isDirectory(outputDir)) {
            throw new IllegalArgumentException(
                "Output path exists but is not a directory: " + outputDir
            );
        }

        if (!Files.isWritable(outputDir)) {
            throw new IllegalArgumentException(
                "Output directory is not writable: " + outputDir
            );
        }

        // Check if directory is empty (optional - warn user)
        try (var stream = Files.list(outputDir)) {
            if (stream.findAny().isPresent()) {
                System.err.println("WARNING: Output directory is not empty: " + outputDir);
                System.err.println("Existing files may be overwritten.");
            }
        }
    } else {
        // Check parent directory is writable
        Path parent = outputDir.getParent();
        if (parent != null && !Files.isWritable(parent)) {
            throw new IllegalArgumentException(
                "Cannot create output directory (parent not writable): " + parent
            );
        }
    }
}
```

---

### IP Brick (`com.k8s.generator.ip`)

**✅ Strengths**:
- Excellent use of IPAddress library
- Clean sequential allocation algorithm
- Good subnet handling

**⚠️ Issues**:

#### Issue 1: Redundant Result Type (P6)
**Already covered in P6 section above.**

#### Issue 2: No IP Range Validation (MEDIUM)

**Priority**: MEDIUM
**Effort**: 1-2 hours

**Add Range Check**:
```java
public class SequentialIpAllocator implements IpAllocator {

    @Override
    public Result<IPAddress, String> allocate(IPAddressString subnet, int vmIndex) {
        // Validate subnet
        if (!subnet.isValid()) {
            return new Result.Failure<>("Invalid subnet: " + subnet);
        }

        IPAddress subnetAddr = subnet.getAddress();

        // Calculate max hosts in subnet
        int prefixLength = subnetAddr.getPrefixLength();
        if (prefixLength >= 31) {
            // /31 or /32 - point-to-point or single host
            int maxHosts = prefixLength == 31 ? 2 : 1;
            if (vmIndex >= maxHosts) {
                return new Result.Failure<>(
                    "VM index " + vmIndex + " exceeds subnet capacity " + maxHosts + " for /" + prefixLength
                );
            }
        } else {
            // Normal subnet - subtract network and broadcast addresses
            int maxHosts = (1 << (32 - prefixLength)) - 2;
            if (vmIndex >= maxHosts) {
                return new Result.Failure<>(
                    "VM index " + vmIndex + " exceeds subnet capacity " + maxHosts + " (excluding network/broadcast)"
                );
            }
        }

        // Allocate IP
        IPAddress allocated = subnetAddr.increment(vmIndex);

        // Check reserved IPs
        if (isReserved(allocated)) {
            return new Result.Failure<>("IP is reserved: " + allocated);
        }

        return new Result.Success<>(allocated);
    }

    private boolean isReserved(IPAddress ip) {
        String ipStr = ip.toString();
        return ipStr.endsWith(".1") ||
               ipStr.endsWith(".2") ||
               ipStr.endsWith(".5");
    }
}
```

---

## Prioritized Recommendations

### CRITICAL (Must fix before Phase 3)

| Priority | Item | Effort | Impact | Files |
|----------|------|--------|--------|-------|
| P5 | Add missing Model fields | 2-4 hours | High - Breaks contract | GeneratorSpec.java, ClusterSpec.java, ScaffoldPlan.java, CliToSpec.java, SpecToPlan.java |
| P6 | Remove redundant Result type | 1 hour | Medium - Type confusion | IpAllocator.java, SequentialIpAllocator.java |

**Total CRITICAL Effort**: 3-5 hours

---

### HIGH (Impacts maintainability/functionality)

| Priority | Item | Effort | Impact | Files |
|----------|------|--------|--------|-------|
| P7 | Add Azure CLI flag | 1-2 hours | Medium - Feature gap | GenerateCommand.java, CliToSpec.java |
| P8 | Add scaffoldHooks() | 2-3 hours | Medium - Contract violation | OutputWriter.java, AtomicOutputWriter.java |
| 3 | Fix Feature Envy | 3-4 hours | Medium - Maintainability | ScaffoldService.java, VmSpec.java, ClusterSpec.java |
| 4 | Replace primitives with value objects | 4-6 hours | High - Type safety | NetworkCIDR.java (new), ModuleId.java (new), ClusterSpec.java, parser/* |

**Total HIGH Effort**: 10-15 hours

---

### MEDIUM (Nice-to-have refactorings)

| Priority | Item | Effort | Impact | Files |
|----------|------|--------|--------|-------|
| 5 | Extract PlanFactory | 6-8 hours | Medium - Prevent God Object | ScaffoldService.java, PlanFactory.java (new), RenderingOrchestrator.java (new) |
| 6 | Standardize Optional usage | 2-3 hours | Low - Consistency | All model records |
| 7 | Add ValidationLevel | 2 hours | Low - Better UX | ValidationError.java, validators/* |
| 8 | Add IP range validation | 1-2 hours | Medium - Prevent errors | SequentialIpAllocator.java |

**Total MEDIUM Effort**: 11-15 hours

---

### LOW (Style/convention improvements)

| Priority | Item | Effort | Impact | Files |
|----------|------|--------|--------|-------|
| 9 | Add --dry-run flag | 2-3 hours | Low - Nice feature | GenerateCommand.java, ScaffoldService.java |
| 10 | Set file permissions | 30 min | Low - Convenience | AtomicOutputWriter.java |
| 11 | Add output directory validation | 1 hour | Low - Better errors | AtomicOutputWriter.java |
| 12 | Convert contexts to records | 2-3 hours | Low - Immutability | VagrantfileContext.java, BootstrapContext.java |
| 13 | Add tools validation | 1-2 hours | Low - Better errors | GenerateCommand.java |
| 14 | Inject IP allocator | 1-2 hours | Low - Testability | CliArgsParser.java |

**Total LOW Effort**: 7.5-12.5 hours

---

### Summary by Priority

| Priority | Item Count | Total Effort | Must Fix? |
|----------|------------|--------------|-----------|
| CRITICAL | 2 | 3-5 hours | Yes |
| HIGH | 4 | 10-15 hours | Recommended |
| MEDIUM | 4 | 11-15 hours | Optional |
| LOW | 6 | 7.5-12.5 hours | Nice-to-have |
| **TOTAL** | **16** | **32-47.5 hours** | |

**Recommended Immediate Action**: Fix CRITICAL + HIGH items = **13-20 hours total**

---

## Detailed Implementation Guide

This section provides step-by-step instructions for implementing each fix.

### Implementation Order

For **maximum efficiency**, fix issues in this order:

1. **P6** (Remove redundant Result) - 1 hour
   - Clean up type system first
   - Makes P5 implementation cleaner

2. **P5** (Add missing Model fields) - 2-4 hours
   - Critical contract fixes
   - Enables P7 and P8

3. **P7** (Add Azure flag) - 1-2 hours
   - Depends on P5 (Management field)

4. **P8** (Add scaffoldHooks) - 2-3 hours
   - Independent of other fixes

5. **Feature Envy fix** - 3-4 hours
   - Improves code quality
   - Makes future changes easier

6. **Primitive Obsession** - 4-6 hours
   - Long-term type safety
   - Can be done incrementally

**Total Sequential Time**: 13-20 hours (CRITICAL + HIGH)

### Testing After Each Fix

After implementing each fix:

1. **Run all tests**:
   ```bash
   mvn clean test
   ```
   - All 296 tests should pass
   - Fix any broken tests before proceeding

2. **Run integration test**:
   ```bash
   mvn test -Dtest=GenerateCommandSmokeTest
   ```

3. **Manual smoke test**:
   ```bash
   mvn clean package
   java -jar target/k8s-generator-1.0.0-SNAPSHOT.jar \
     -m m1 -t pt kind -o /tmp/test-output

   # Verify output
   ls -la /tmp/test-output/
   ```

4. **Commit after each fix**:
   ```bash
   git add .
   git commit -m "fix(P6): Remove redundant Result type in IpAllocator"
   ```

---

## Philosophy Compliance Assessment

### Current Score: 7.5/10

**Breakdown**:

| Principle | Score | Justification |
|-----------|-------|---------------|
| **Ruthless Simplicity** | 9/10 | Excellent - no unnecessary complexity, minimal abstractions, clear implementations |
| **Clear Over Clever** | 8/10 | Very good - explicit naming, readable code; minor issue with feature envy in ScaffoldService |
| **Bricks Regeneratable** | 7/10 | Good structure but missing contracts (P5-P8) suggest incomplete validation |
| **No Future-Proofing** | 8/10 | Good - solves current problems, no speculative abstractions |

### What's Working (Strengths)

1. **Ruthless Simplicity** ✅
   - No over-engineering
   - Minimal dependencies (7 libraries)
   - Each brick < 500 lines (largest: 356 lines)
   - Example: `AtomicOutputWriter` is ~100 lines, does one thing well

2. **Clear Over Clever** ✅
   - Excellent naming conventions
   - Explicit contracts with records
   - Readable, self-documenting code
   - No clever tricks or hidden magic

3. **Bricks Regeneratable** ⚠️
   - **Interfaces are clean and stable** ✅
   - Test coverage excellent (296 tests) ✅
   - Can rebuild Parser, Validation, Rendering bricks independently ✅
   - **BUT**: Missing fields (P5-P8) suggest incomplete contract validation ⚠️

4. **No Future-Proofing** ✅
   - Code solves current problems
   - No speculative abstractions
   - Clean YAGNI compliance

### What Needs Improvement

1. **Contract Validation** (-2 points)
   - Missing fields indicate incomplete interface contracts
   - **Root cause**: No automated contract validation in CI/CD
   - **Fix**: Add contract tests (see recommendations below)

2. **Type System Consistency** (-0.5 points)
   - Duplicate Result types violate DRY (P6)
   - Primitive obsession for structured data (IPs, CIDRs)
   - Inconsistent Optional usage

### Recommendations for Improving Compliance

#### 1. Add Contract Tests (HIGH PRIORITY)

**New Test File**: `src/test/java/com/k8s/generator/ContractTest.java`

```java
package com.k8s.generator;

import com.k8s.generator.model.*;
import com.k8s.generator.ip.IpAllocator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests verify that brick interfaces match documented specifications.
 * These tests catch breaking changes when bricks are regenerated.
 */
class ContractTest {

    @Test
    void generatorSpecContainsRequiredFields() {
        // Verify GeneratorSpec has all expected fields per architecture
        Set<String> fieldNames = getFieldNames(GeneratorSpec.class);

        assertThat(fieldNames)
            .as("GeneratorSpec must contain all contract fields")
            .contains("outputPath", "clusters", "management");
    }

    @Test
    void clusterSpecContainsRequiredFields() {
        Set<String> fieldNames = getFieldNames(ClusterSpec.class);

        assertThat(fieldNames)
            .as("ClusterSpec must contain all contract fields")
            .contains(
                "name", "type", "engine", "baseIp", "vms", "tools", "azure",
                "podNetwork", "svcNetwork"  // Added in P5
            );
    }

    @Test
    void scaffoldPlanContainsRequiredFields() {
        Set<String> fieldNames = getFieldNames(ScaffoldPlan.class);

        assertThat(fieldNames)
            .as("ScaffoldPlan must contain all contract fields")
            .contains(
                "outputDir", "vmPlans", "bootstrapPlans", "installerPlans",
                "providers"  // Added in P5
            );
    }

    @Test
    void ipAllocatorUsesGlobalResultType() throws NoSuchMethodException {
        // Verify IpAllocator returns model.Result, not nested type
        Method allocateMethod = IpAllocator.class
            .getDeclaredMethod("allocate", inet.ipaddr.IPAddressString.class, int.class);

        Class<?> returnType = allocateMethod.getReturnType();

        assertThat(returnType.getName())
            .as("IpAllocator must use global Result type, not nested")
            .isEqualTo("com.k8s.generator.model.Result");
    }

    @Test
    void outputWriterContainsScaffoldHooksMethod() throws NoSuchMethodException {
        // Verify OutputWriter has scaffoldHooks() method
        Method scaffoldHooks = com.k8s.generator.fs.OutputWriter.class
            .getDeclaredMethod("scaffoldHooks", java.nio.file.Path.class);

        assertThat(scaffoldHooks).isNotNull();
        assertThat(scaffoldHooks.getReturnType()).isEqualTo(void.class);
    }

    private Set<String> getFieldNames(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());
    }
}
```

**Add to CI/CD**:
```bash
# In .github/workflows/test.yml or similar
- name: Run Contract Tests
  run: mvn test -Dtest=ContractTest
```

#### 2. Document Brick Contracts (MEDIUM PRIORITY)

**New File**: `doc/BRICK_CONTRACTS.md`

```markdown
---
status: Normative specification
version: 1.0.0
scope: Interface contracts for k8s-generator modular bricks
---

# Brick Contracts

This document defines the public interfaces ("studs") for each brick.
When regenerating a brick, preserve these contracts to avoid breaking downstream consumers.

## Model Brick Contract

### GeneratorSpec
**Purpose**: Top-level configuration for generation

**Required Fields**:
- `String outputPath` - Output directory path
- `List<ClusterSpec> clusters` - Cluster configurations (at least one)
- `Optional<Management> management` - Optional management VM configuration

**Invariants**:
- `clusters` must contain at least one element
- `outputPath` must be a valid filesystem path

### ClusterSpec
**Purpose**: Cluster configuration

**Required Fields**:
- `String name` - Cluster name (unique within spec)
- `ClusterType type` - Cluster type enum
- `EngineType engine` - Engine enum (KIND, MINIKUBE, KUBEADM, NONE)
- `IPAddressString baseIp` - Base IP for sequential allocation
- `List<VmSpec> vms` - VM specifications
- `List<String> tools` - Required tools
- `Optional<AzureSpec> azure` - Optional Azure configuration
- `NetworkCIDR podNetwork` - Kubernetes pod network CIDR
- `NetworkCIDR svcNetwork` - Kubernetes service network CIDR

**Invariants**:
- `podNetwork` and `svcNetwork` must not overlap
- All VMs must have unique IPs

### ScaffoldPlan
**Purpose**: Template-ready plan with allocated resources

**Required Fields**:
- `Path outputDir` - Output directory
- `List<VmPlan> vmPlans` - VM generation plans
- `List<BootstrapPlan> bootstrapPlans` - Bootstrap script plans
- `List<InstallerPlan> installerPlans` - Installer script plans
- `Set<String> providers` - Cloud providers (empty set if none)

## IP Brick Contract

### IpAllocator
**Purpose**: Allocate IP addresses sequentially within a subnet

**Method Signature**:
```java
Result<IPAddress, String> allocate(IPAddressString subnet, int vmIndex);
```

**Contract**:
- Returns `Success<IPAddress>` if allocation succeeds
- Returns `Failure<String>` with error message if allocation fails
- Must skip reserved IPs (.1, .2, .5)
- Must validate subnet boundaries
- Must be deterministic (same inputs → same output)

## I/O Brick Contract

### OutputWriter
**Purpose**: Write files to disk atomically

**Methods**:
```java
void writeVagrantfile(Path destination, String content) throws IOException;
void writeBootstrapScript(Path destination, String content) throws IOException;
void writeInstallerScript(Path destination, String content) throws IOException;
void scaffoldHooks(Path destination) throws IOException;
```

**Contract**:
- All writes must be atomic (all-or-nothing)
- Must rollback on failure
- Must create parent directories if needed
- `scaffoldHooks()` must create hook directory structure

## Parser Brick Contract

### SpecConverter
**Purpose**: Convert CLI arguments to GeneratorSpec

**Method Signature**:
```java
GeneratorSpec convert(GenerateCommand cmd);
```

**Contract**:
- Pure transformation (no side effects)
- No validation (Picocli handles structure, Validator handles business rules)
- Must apply convention-over-configuration defaults
- Must be deterministic

### PlanBuilder
**Purpose**: Convert validated GeneratorSpec to ScaffoldPlan

**Method Signature**:
```java
ScaffoldPlan build(GeneratorSpec spec);
```

**Contract**:
- Assumes spec is valid (validation done separately)
- Must allocate IPs using IpAllocator
- Must create VM configurations
- Must extract providers from management spec
- Must be deterministic

## Validation Brick Contract

### Validator
**Purpose**: Validate GeneratorSpec against all rules

**Method Signature**:
```java
List<ValidationError> validate(GeneratorSpec spec);
```

**Contract**:
- Returns empty list if valid
- Returns all errors (don't short-circuit)
- Must validate structural, semantic, and policy rules
- Must provide actionable error messages with suggestions

## Rendering Brick Contract

### Renderer
**Purpose**: Render JTE templates to file contents

**Method Signature**:
```java
Map<String, String> render(ScaffoldPlan plan);
```

**Contract**:
- Pure transformation (no side effects)
- Returns map of filename → content
- Must use JTE for type-safe templates
- Must be deterministic
```

#### 3. Enable Parallel Brick Regeneration (LOW PRIORITY)

Once contracts are solid and contract tests pass, prove regeneratability:

**Test Procedure**:
1. Delete entire `Validation` brick
   ```bash
   rm -rf src/main/java/com/k8s/generator/validate/
   ```

2. Regenerate from spec + tests
   - Use contract in `doc/BRICK_CONTRACTS.md`
   - Use existing tests as requirements
   - Implement to make tests pass

3. Verify all tests pass
   ```bash
   mvn clean test
   # Should still have 296 passing tests
   ```

This validates the "bricks and studs" philosophy in practice.

---

## Testing Strategy

### Test Coverage Goals

| Test Type | Target | Current | Status |
|-----------|--------|---------|--------|
| Unit Tests | 60% | ~60% | ✅ Good |
| Integration Tests | 30% | ~30% | ✅ Good |
| E2E Tests | 10% | ~10% | ✅ Good |
| **Total Tests** | **296+** | **296** | ✅ Excellent |

### Test Suite Structure

1. **Unit Tests** (60%)
   - Model validation tests
   - Parser conversion tests
   - IP allocation tests
   - Validation logic tests

2. **Integration Tests** (30%)
   - Parser → Model → Validation flow
   - Model → Renderer → I/O flow
   - End-to-end brick integration

3. **E2E Tests** (10%)
   - CLI smoke tests
   - Full generation + Vagrant validation
   - Golden file comparison

### Testing Each Fix

**After P5 (Model fields)**:
```bash
# Run model tests
mvn test -Dtest=GeneratorSpecTest,ClusterSpecTest,ScaffoldPlanTest

# Run parser tests (uses new fields)
mvn test -Dtest=CliToSpecTest,SpecToPlanTest

# Run all tests
mvn test
```

**After P6 (Result consolidation)**:
```bash
# Run IP tests
mvn test -Dtest=SequentialIpAllocatorTest

# Run all tests
mvn test
```

**After P7 (Azure flag)**:
```bash
# Run CLI tests
mvn test -Dtest=GenerateCommandTest,AzureFlagTest

# Run integration test
mvn test -Dtest=GenerateCommandSmokeTest
```

**After P8 (scaffoldHooks)**:
```bash
# Run I/O tests
mvn test -Dtest=HookScaffoldingTest,AtomicOutputWriterTest

# Run all tests
mvn test
```

### Manual Validation Checklist

After all CRITICAL + HIGH fixes:

- [ ] Generate kind cluster
  ```bash
  java -jar target/k8s-generator.jar -m m1 -t pt kind -o /tmp/kind-test
  cd /tmp/kind-test
  vagrant up
  vagrant ssh -c 'kubectl get nodes'
  ```

- [ ] Generate kubeadm cluster
  ```bash
  java -jar target/k8s-generator.jar -m m7 -t hw kubeadm --nodes 1m,2w -o /tmp/kubeadm-test
  cd /tmp/kubeadm-test
  vagrant up
  ```

- [ ] Generate with Azure flag
  ```bash
  java -jar target/k8s-generator.jar -m m9 -t lab mgmt --azure -o /tmp/azure-test
  cd /tmp/azure-test
  ls scripts/install_azure_cli.sh  # Should exist
  ```

- [ ] Verify hook scaffolding
  ```bash
  cd /tmp/kind-test
  ls -la scripts/bootstrap.pre.d/
  ls -la scripts/bootstrap.post.d/
  cat scripts/bootstrap.env.local  # Should have template content
  ```

---

## References

### Internal Documentation

- [GENERATOR-ARCHITECTURE.md](GENERATOR-ARCHITECTURE.md) - Normative specification (v1.16.2)
- [ARCHITECTURE-REVIEW-2025-11-04.md](ARCHITECTURE-REVIEW-2025-11-04.md) - Gap analysis (v1.4.0)
- [GENERATOR_CODE_PLAN.md](GENERATOR_CODE_PLAN.md) - Implementation plan (v1.4.0)
- [CODEBASE-STRUCTURE-ANALYSIS-2025-11-04.md](CODEBASE-STRUCTURE-ANALYSIS-2025-11-04.md) - Current state analysis (v1.0.0)

### External References

- [Picocli Documentation](https://picocli.info/)
- [JTE Template Engine](https://jte.gg/)
- [IPAddress Library](https://seancfoley.github.io/IPAddress/)
- [Jackson YAML](https://github.com/FasterXML/jackson-dataformats-text/tree/master/yaml)
- [Effective Java (3rd Edition)](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/) - Best practices for Java development

### Code Quality Resources

- [Refactoring Guru: Design Patterns](https://refactoring.guru/design-patterns)
- [Refactoring Guru: Code Smells](https://refactoring.guru/refactoring/smells)
- [Martin Fowler: Refactoring Catalog](https://refactoring.com/catalog/)

---

## Document History

| Version | Date       | Author      | Changes |
|---------|------------|-------------|---------|
| 1.0.0   | 2025-11-04 | technical-software-architect | Initial comprehensive code quality review with detailed remediation plan |
