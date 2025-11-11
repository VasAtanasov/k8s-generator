package com.k8s.generator.parser;

import com.k8s.generator.model.*;

import java.util.*;

/**
 * Builds environment variables for templates in a minimal, composable way.
 *
 * <p>Produces a global environment map and a per-VM environment map. This keeps
 * the plan template-ready while avoiding hardcoded logic scattered across the codebase.
 *
 * <p>Design Principles:
 * <ul>
 *   <li><b>Pure functions</b>: No side effects, all state flows through parameters</li>
 *   <li><b>Deterministic</b>: Same inputs always produce identical output</li>
 *   <li><b>Composable</b>: Separate builders for base, engine-specific, and provider-specific variables</li>
 *   <li><b>Immutable results</b>: Returns unmodifiable collections</li>
 * </ul>
 *
 * <p>Environment Variable Categories:
 * <ul>
 *   <li><b>Global</b>: Cluster-wide variables (CLUSTER_NAME, CNI_TYPE, provider credentials)</li>
 *   <li><b>Per-VM</b>: Node-specific variables (VM_NAME, ROLE, VM_IP, CONTROL_PLANE flag)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Without providers (local cluster)
 * var envSet = EnvPlanner.build(module, cluster, vms);
 * Map<String, String> globalEnv = envSet.global();  // CLUSTER_NAME, CNI_TYPE
 *
 * // With Azure provider
 * var envSet = EnvPlanner.build(module, cluster, vms, Set.of("azure"));
 * Map<String, String> globalEnv = envSet.global();  // + AZ_SUBSCRIPTION_ID, AZ_RESOURCE_GROUP
 *
 * // Per-VM environment
 * Map<String, String> masterEnv = envSet.perVm().get(masterVmName);  // VM_NAME, ROLE=master, CONTROL_PLANE=1
 * }</pre>
 *
 * @see EnvSet
 * @since 1.0.0
 */
final class EnvPlanner {

    // Constants for magic values
    private static final String FLAG_ENABLED = "1";
    private static final int KUBE_API_PORT_DEFAULT = 6443;

    /**
     * Immutable container for environment variable sets.
     *
     * <p>Contains two maps:
     * <ul>
     *   <li><b>global</b>: Cluster-wide environment variables (CLUSTER_NAME, CNI_TYPE, etc.)</li>
     *   <li><b>perVm</b>: Per-VM environment variables indexed by VM name</li>
     * </ul>
     *
     * <p>Both maps are immutable copies created via {@link Map#copyOf}.
     *
     * @param global cluster-wide environment variables (immutable)
     * @param perVm  per-VM environment variables (immutable, indexed by VmName)
     */
    record EnvSet(Map<String, String> global, Map<VmName, Map<String, String>> perVm) {}

    /**
     * Builds environment variable sets for a cluster with default providers (no cloud providers).
     *
     * <p>This is the standard method for local Kubernetes clusters (KIND, minikube, kubeadm).
     * For Azure/cloud integration, use {@link #build(ModuleInfo, ClusterSpec, List, Set)}.
     *
     * @param module  module metadata (for namespace generation)
     * @param cluster cluster specification
     * @param vms     list of VMs (must not be empty)
     * @return immutable EnvSet with global and per-VM environments
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if vms list is empty
     * @see #build(ModuleInfo, ClusterSpec, List, Set)
     */
    static EnvSet build(ModuleInfo module, ClusterSpec cluster, List<VmConfig> vms) {
        return build(module, cluster, vms, Set.of());
    }

    /**
     * Builds environment variable sets for a cluster with optional cloud providers.
     *
     * <p>This method supports provider-specific environment variables (e.g., Azure credentials).
     * Use this when generating environments that interact with cloud platforms.
     *
     * <p>Supported Providers:
     * <ul>
     *   <li><b>azure</b>: Adds AZ_SUBSCRIPTION_ID, AZ_RESOURCE_GROUP, AZ_LOCATION</li>
     *   <li><b>aws</b>: Reserved for future implementation</li>
     *   <li><b>gcp</b>: Reserved for future implementation</li>
     * </ul>
     *
     * @param module    module metadata (for namespace generation)
     * @param cluster   cluster specification
     * @param vms       list of VMs (must not be empty)
     * @param providers set of provider names (e.g., "azure", "aws") - empty for local-only
     * @return immutable EnvSet with global and per-VM environments
     * @throws NullPointerException     if any parameter is null
     * @throws IllegalArgumentException if vms list is empty
     */
    static EnvSet build(ModuleInfo module, ClusterSpec cluster, List<VmConfig> vms, Set<CloudProvider> providers) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(cluster, "cluster");
        Objects.requireNonNull(vms, "vms");
        Objects.requireNonNull(providers, "providers");

        if (vms.isEmpty()) {
            throw new IllegalArgumentException("vms list cannot be empty");
        }

        // Global env
        var global = new LinkedHashMap<String, String>();
        buildBaseGlobalEnv(global, module, cluster);
        buildEngineGlobalEnv(global, cluster);
        buildProviderGlobalEnv(global, providers);

        // Per-VM envs
        var perVm = new LinkedHashMap<VmName, Map<String, String>>();
        for (VmConfig vm : vms) {
            var env = new LinkedHashMap<String, String>();
            buildBasePerVmEnv(env, vm);
            buildEnginePerVmEnv(env, cluster, vm);
            perVm.put(vm.name(), Map.copyOf(env));
        }

        return new EnvSet(Map.copyOf(global), Map.copyOf(perVm));
    }

    /**
     * Builds base global environment variables (cluster name, namespace, type).
     *
     * <p>Variables added:
     * <ul>
     *   <li><b>CLUSTER_NAME</b>: Cluster name (e.g., "clu-m1-pt-kind")</li>
     *   <li><b>NAMESPACE_DEFAULT</b>: Default namespace (e.g., "ns-m1-pt")</li>
     *   <li><b>CLUSTER_TYPE</b>: Cluster type lowercase (e.g., "kind", "kubeadm")</li>
     * </ul>
     *
     * @param out     output map (mutable)
     * @param module  module metadata
     * @param cluster cluster specification
     */
    private static void buildBaseGlobalEnv(Map<String, String> out, ModuleInfo module, ClusterSpec cluster) {
        out.put("CLUSTER_NAME", cluster.name().toString());
        out.put("NAMESPACE_DEFAULT", module.defaultNamespace());
        out.put("CLUSTER_TYPE", cluster.type().id().toLowerCase(Locale.ROOT));
    }

    /**
     * Builds engine-specific global environment variables.
     *
     * <p>Variables added based on cluster type:
     * <ul>
     *   <li><b>KUBEADM</b>: CNI_TYPE (if specified), KUBE_API_PORT, K8S_POD_CIDR, K8S_SVC_CIDR</li>
     *   <li><b>KIND/MINIKUBE</b>: No additional variables</li>
     * </ul>
     *
     * @param out     output map (mutable)
     * @param cluster cluster specification
     */
    private static void buildEngineGlobalEnv(Map<String, String> out, ClusterSpec cluster) {
        // CNI type only for kubeadm clusters
        if (cluster.type() == Kubeadm.INSTANCE) {
            if (cluster.cni() != null) {
                out.put("CNI_TYPE", cluster.cni().name().toLowerCase(Locale.ROOT));
            }

            // Kubeadm API server port
            out.put("KUBE_API_PORT", String.valueOf(KUBE_API_PORT_DEFAULT));

            // Pod and Service network CIDRs for kubeadm
            if (cluster.podNetwork() != null) {
                out.put("K8S_POD_CIDR", cluster.podNetwork().toCIDRString());
            }
            if (cluster.svcNetwork() != null) {
                out.put("K8S_SVC_CIDR", cluster.svcNetwork().toCIDRString());
            }
        }
    }

    /**
     * Builds provider-specific global environment variables (Azure, AWS, GCP credentials).
     *
     * <p>Provider Variables:
     * <ul>
     *   <li><b>azure</b>: AZ_SUBSCRIPTION_ID, AZ_RESOURCE_GROUP, AZ_LOCATION</li>
     *   <li><b>aws</b>: Reserved for future implementation</li>
     *   <li><b>gcp</b>: Reserved for future implementation</li>
     * </ul>
     *
     * <p>Note: Values are placeholder environment variable references. Actual resolution
     * happens at runtime via bootstrap scripts sourcing /etc/azure-env.
     *
     * @param out       output map (mutable)
     * @param providers set of provider names (e.g., "azure")
     */
    private static void buildProviderGlobalEnv(Map<String, String> out, Set<CloudProvider> providers) {
        if (providers.contains(CloudProvider.azure())) {
            // Placeholder references - resolved at runtime via /etc/azure-env
            out.put("AZ_SUBSCRIPTION_ID", "${AZ_SUBSCRIPTION_ID}");
            out.put("AZ_RESOURCE_GROUP", "${AZ_RESOURCE_GROUP}");
            out.put("AZ_LOCATION", "${AZ_LOCATION}");
        }
        // TODO: Add AWS, GCP providers when needed
    }

    /**
     * Builds base per-VM environment variables (name, role, IP).
     *
     * <p>Variables added:
     * <ul>
     *   <li><b>VM_NAME</b>: VM hostname (e.g., "master-1")</li>
     *   <li><b>ROLE</b>: Node role lowercase (e.g., "master", "worker")</li>
     *   <li><b>VM_IP</b>: VM IP address (e.g., "192.168.56.10")</li>
     * </ul>
     *
     * @param out output map (mutable)
     * @param vm  VM configuration
     */
    private static void buildBasePerVmEnv(Map<String, String> out, VmConfig vm) {
        out.put("VM_NAME", vm.name().value());
        out.put("ROLE", vm.role().name().toLowerCase(Locale.ROOT));
        out.put("VM_IP", vm.ip().toCanonicalString());
    }

    /**
     * Builds engine-specific per-VM environment variables.
     *
     * <p>Variables added based on cluster type and VM role:
     * <ul>
     *   <li><b>KUBEADM master</b>: CONTROL_PLANE=1</li>
     *   <li><b>Other types</b>: No additional variables</li>
     * </ul>
     *
     * @param out     output map (mutable)
     * @param cluster cluster specification
     * @param vm      VM configuration
     */
    private static void buildEnginePerVmEnv(Map<String, String> out, ClusterSpec cluster, VmConfig vm) {
        if (cluster.type() == Kubeadm.INSTANCE && vm.isMaster()) {
            out.put("CONTROL_PLANE", FLAG_ENABLED);
        }
    }
}

