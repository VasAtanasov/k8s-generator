package com.k8s.generator.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Template-ready scaffold plan - represents the complete output model for rendering.
 *
 * <p>This is the final, executable plan after validation, IP allocation, and VM generation.
 * It contains everything needed to render templates (Vagrantfile, bootstrap scripts, etc.).
 *
 * <p>Architecture Position:
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan → Renderer
 * </pre>
 *
 * <p>Key Differences from GeneratorSpec:
 * <ul>
 *   <li><b>Flat VM list</b>: All VMs from all clusters, with allocated IPs</li>
 *   <li><b>Environment variables</b>: Ready for shell scripts (CLUSTER_NAME, NAMESPACE_DEFAULT, etc.)</li>
 *   <li><b>Resolved sizing</b>: All VMs have effective CPU/memory values</li>
 *   <li><b>Post-validation</b>: Guaranteed to be structurally and semantically valid</li>
 * </ul>
 *
 * <p>Phase 1 MVP Scope:
 * <ul>
 *   <li><b>Single VM</b>: vms list always contains exactly 1 VM (kind or minikube)</li>
 *   <li><b>Hardcoded IP</b>: 192.168.56.10 (no IP allocator yet)</li>
 *   <li><b>Simple environment</b>: CLUSTER_NAME, NAMESPACE_DEFAULT, CLUSTER_TYPE</li>
 * </ul>
 *
 * <p>Template Usage:
 * <pre>{@code
 * // JTE template (Vagrantfile.jte)
 * @param com.k8s.generator.model.ScaffoldPlan plan
 *
 * Vagrant.configure("2") do |config|
 *   @for(vm : plan.vms())
 *     config.vm.define "${vm.name()}" do |node|
 *       node.vm.network "private_network", ip: "${vm.ip()}"
 *       node.vm.provider "virtualbox" do |vb|
 *         vb.cpus = ${vm.getEffectiveCpus()}
 *         vb.memory = ${vm.getEffectiveMemoryMb()}
 *       end
 *     end
 *   @endfor
 * end
 * }</pre>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Phase 1 MVP: Single kind cluster
 * var plan = new ScaffoldPlan(
 *     new ModuleInfo("m1", "pt"),
 *     List.of(
 *         new VmConfig(
 *             "clu-m1-pt-kind",
 *             NodeRole.CLUSTER,
 *             "192.168.56.10",
 *             SizeProfile.MEDIUM,
 *             Optional.empty(),
 *             Optional.empty()
 *         )
 *     ),
 *     Map.of(
 *         "CLUSTER_NAME", "clu-m1-pt-kind",
 *         "NAMESPACE_DEFAULT", "ns-m1-pt",
 *         "CLUSTER_TYPE", "kind"
 *     )
 * );
 * }</pre>
 *
 * @param module  Module metadata (module number + type)
 * @param vms     List of VMs to provision (1+ required, with allocated IPs)
 * @param envVars Environment variables for bootstrap scripts
 * @see ModuleInfo
 * @see VmConfig
 * @see NodeRole
 * @see GeneratorSpec
 * @see com.k8s.generator.parser.PlanBuilder
 * @since 1.0.0
 */
public record ScaffoldPlan(
        ModuleInfo module,
        List<VmConfig> vms,
        Map<String, String> envVars) {
    /**
     * Compact constructor with structural validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>All required fields are non-null</li>
     *   <li>VMs list is non-empty (at least 1 VM required)</li>
     *   <li>VMs list contains no null elements</li>
     *   <li>Environment variables map contains no null keys or values</li>
     * </ul>
     *
     * <p>Note: This constructor assumes the plan has been validated.
     * It does NOT re-validate:
     * <ul>
     *   <li>IP format or uniqueness</li>
     *   <li>VM naming patterns</li>
     *   <li>Environment variable content</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any structural validation fails
     */
    public ScaffoldPlan {
        // Null checks
        Objects.requireNonNull(module, "module is required");
        Objects.requireNonNull(vms, "vms list is required");
        Objects.requireNonNull(envVars, "envVars map is required (use Map.of() for empty)");

        // Empty check for VMs
        if (vms.isEmpty()) {
            throw new IllegalArgumentException("vms list cannot be empty (at least 1 VM required)");
        }

        // Null element check
        if (vms.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("vms list contains null elements");
        }

        // Environment variables validation
        if (envVars.containsKey(null)) {
            throw new IllegalArgumentException("envVars map contains null key");
        }
        if (envVars.containsValue(null)) {
            throw new IllegalArgumentException("envVars map contains null value");
        }

        // Make defensive copies to ensure immutability
        vms = List.copyOf(vms);
        envVars = Map.copyOf(envVars);
    }

    /**
     * Returns the total number of VMs in this plan.
     *
     * @return VM count (1+)
     */
    public int vmCount() {
        return vms.size();
    }

    /**
     * Returns the number of master VMs in this plan.
     *
     * @return master count (0+ for KUBEADM, always 0 for KIND/MINIKUBE/NONE)
     */
    public long masterCount() {
        return vms.stream().filter(vm -> vm.role() == NodeRole.MASTER).count();
    }

    /**
     * Returns the number of worker VMs in this plan.
     *
     * @return worker count (0+ for KUBEADM, always 0 for KIND/MINIKUBE/NONE)
     */
    public long workerCount() {
        return vms.stream().filter(vm -> vm.role() == NodeRole.WORKER).count();
    }

    /**
     * Returns the number of cluster VMs (single-node kind/minikube).
     *
     * @return cluster VM count (1 for KIND/MINIKUBE, 0 for KUBEADM/NONE)
     */
    public long clusterVmCount() {
        return vms.stream().filter(vm -> vm.role() == NodeRole.CLUSTER).count();
    }

    /**
     * Returns the number of management VMs.
     *
     * @return management VM count (0 for Phase 1, 1+ for Phase 3+)
     */
    public long managementVmCount() {
        return vms.stream().filter(vm -> vm.role() == NodeRole.MANAGEMENT).count();
    }

    /**
     * Checks if this plan has a management VM.
     * Always false for Phase 1 MVP.
     *
     * @return true if managementVmCount() > 0
     */
    public boolean hasManagementVm() {
        return managementVmCount() > 0;
    }

    /**
     * Checks if this is a single-VM plan (kind/minikube).
     *
     * @return true if vms.size() == 1
     */
    public boolean isSingleVm() {
        return vms.size() == 1;
    }

    /**
     * Checks if this is a multi-VM plan (kubeadm or multi-cluster).
     *
     * @return true if vms.size() > 1
     */
    public boolean isMultiVm() {
        return vms.size() > 1;
    }

    /**
     * Returns a specific environment variable value.
     *
     * @param key environment variable name
     * @return value or null if not present
     */
    public String getEnv(String key) {
        return envVars.get(key);
    }

    /**
     * Returns the output directory name based on module metadata.
     * Delegates to ModuleInfo.defaultOutputDir().
     *
     * @return output directory name (e.g., "pt-m1", "exam-prep-m7")
     */
    public String defaultOutputDir() {
        return module.defaultOutputDir();
    }

    /**
     * Returns a new ScaffoldPlan with updated VMs list.
     * Used by plan builders or post-processors.
     *
     * @param newVms new VMs list (must not be null or empty)
     * @return new ScaffoldPlan with updated VMs
     * @throws IllegalArgumentException if newVms is null, empty, or contains nulls
     */
    public ScaffoldPlan withVms(List<VmConfig> newVms) {
        Objects.requireNonNull(newVms, "newVms cannot be null");
        return new ScaffoldPlan(module, newVms, envVars);
    }

    /**
     * Returns a new ScaffoldPlan with updated environment variables.
     * Used by plan builders to add or modify environment variables.
     *
     * @param newEnvVars new environment variables map (must not be null)
     * @return new ScaffoldPlan with updated environment variables
     * @throws IllegalArgumentException if newEnvVars is null or contains null keys/values
     */
    public ScaffoldPlan withEnvVars(Map<String, String> newEnvVars) {
        Objects.requireNonNull(newEnvVars, "newEnvVars cannot be null");
        return new ScaffoldPlan(module, vms, newEnvVars);
    }
}
