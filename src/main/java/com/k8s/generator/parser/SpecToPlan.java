package com.k8s.generator.parser;

import com.k8s.generator.model.*;

import java.util.*;

/**
 * Converts validated GeneratorSpec to template-ready ScaffoldPlan.
 *
 * <p>This implementation performs the following transformations:
 * <ul>
 *   <li><b>VM Generation</b>: Creates VMs based on cluster type and node counts</li>
 *   <li><b>IP Allocation</b>: Assigns IP addresses to VMs (Phase 1: hardcoded 192.168.56.10)</li>
 *   <li><b>Role Assignment</b>: Maps cluster type to appropriate NodeRole</li>
 *   <li><b>Environment Variables</b>: Builds map for bootstrap scripts</li>
 * </ul>
 *
 * <p>Phase 1 MVP Behavior:
 * <ul>
 *   <li><b>Single VM</b>: Always creates exactly 1 VM</li>
 *   <li><b>Hardcoded IP</b>: 192.168.56.10 (no IpAllocator yet)</li>
 *   <li><b>CLUSTER role</b>: kind and minikube use NodeRole.CLUSTER</li>
 *   <li><b>Environment variables</b>:
 *       <ul>
 *         <li>CLUSTER_NAME: cluster name from spec</li>
 *         <li>NAMESPACE_DEFAULT: derived from module (ns-{num}-{type})</li>
 *         <li>CLUSTER_TYPE: engine name (kind, minikube)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>VM Generation Rules (Phase 1):
 * <pre>
 * ClusterType.KIND      → 1 VM with NodeRole.CLUSTER
 * ClusterType.MINIKUBE  → 1 VM with NodeRole.CLUSTER
 * ClusterType.KUBEADM   → Not supported in Phase 1 (throws exception)
 * ClusterType.NONE      → Not supported in Phase 1 (throws exception)
 * </pre>
 *
 * <p>Future Phases:
 * <ul>
 *   <li><b>Phase 2</b>: IpAllocator integration, kubeadm multi-node support</li>
 *   <li><b>Phase 3</b>: Multi-cluster support, management VM, IP overlap detection</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Given validated spec
 * var spec = new GeneratorSpec(
 *     new ModuleInfo("m1", "pt"),
 *     List.of(
 *         new ClusterSpec(
 *             "clu-m1-pt-kind",
 *             ClusterType.KIND,
 *             Optional.empty(),
 *             0, 0,
 *             SizeProfile.MEDIUM,
 *             List.of()
 *         )
 *     )
 * );
 *
 * var builder = new SpecToPlan();
 * ScaffoldPlan plan = builder.build(spec);
 *
 * // Result:
 * // plan.vms().size() = 1
 * // plan.vms().get(0) = VmConfig(
 * //     name="clu-m1-pt-kind",
 * //     role=NodeRole.CLUSTER,
 * //     ip="192.168.56.10",
 * //     sizeProfile=SizeProfile.MEDIUM,
 * //     cpuOverride=Optional.empty(),
 * //     memoryMbOverride=Optional.empty()
 * // )
 * // plan.envVars() = {
 * //     CLUSTER_NAME: "clu-m1-pt-kind",
 * //     NAMESPACE_DEFAULT: "ns-m1-pt",
 * //     CLUSTER_TYPE: "kind"
 * // }
 * }</pre>
 *
 * @see PlanBuilder
 * @see GeneratorSpec
 * @see ScaffoldPlan
 * @since 1.0.0
 */
public final class SpecToPlan implements PlanBuilder {

    /**
     * Phase 1 MVP default IP address.
     * Future phases will use IpAllocator for dynamic allocation.
     */
    private static final String DEFAULT_IP = "192.168.56.10";

    /**
     * Builds a ScaffoldPlan from a validated GeneratorSpec.
     *
     * @param spec validated GeneratorSpec (must not be null)
     * @return ScaffoldPlan ready for template rendering
     * @throws NullPointerException if spec is null
     * @throws IllegalStateException if spec contains unsupported cluster types in Phase 1
     */
    @Override
    public ScaffoldPlan build(GeneratorSpec spec) {
        Objects.requireNonNull(spec, "spec is required");

        // Phase 1 MVP: Only single-cluster mode supported
        if (!spec.isSingleCluster()) {
            throw new IllegalStateException(
                "Multi-cluster mode not yet supported in Phase 1 MVP. " +
                "Spec contains " + spec.clusterCount() + " clusters."
            );
        }

        ClusterSpec cluster = spec.primaryCluster();

        // 1. Generate VMs based on cluster type
        List<VmConfig> vms = generateVms(cluster);

        // 2. Build environment variables
        Map<String, String> envVars = buildEnvVars(spec.module(), cluster);

        // 3. Create ScaffoldPlan
        return new ScaffoldPlan(spec.module(), vms, envVars);
    }

    /**
     * Generates VMs for a cluster based on its type.
     *
     * <p>Phase 1 MVP Implementation:
     * <ul>
     *   <li>KIND/MINIKUBE: 1 VM with NodeRole.CLUSTER</li>
     *   <li>KUBEADM/NONE: Not supported (throws exception)</li>
     * </ul>
     *
     * @param cluster cluster specification
     * @return list of VmConfig (1 VM for Phase 1)
     * @throws IllegalStateException if cluster type is unsupported in Phase 1
     */
    private List<VmConfig> generateVms(ClusterSpec cluster) {
        return switch (cluster.type()) {
            case KIND, MINIKUBE -> List.of(createSingleNodeVm(cluster));
            case KUBEADM -> throw new IllegalStateException(
                "KUBEADM cluster type not yet supported in Phase 1 MVP. " +
                "Use ClusterType.KIND or ClusterType.MINIKUBE."
            );
            case NONE -> throw new IllegalStateException(
                "Management (NONE) cluster type not yet supported in Phase 1 MVP. " +
                "Use ClusterType.KIND or ClusterType.MINIKUBE."
            );
        };
    }

    /**
     * Creates a single-node VM for kind or minikube cluster.
     *
     * <p>VM Configuration:
     * <ul>
     *   <li>Name: cluster name (e.g., "clu-m1-pt-kind")</li>
     *   <li>Role: NodeRole.CLUSTER</li>
     *   <li>IP: 192.168.56.10 (Phase 1 hardcoded default)</li>
     *   <li>Size: From cluster.sizeProfile()</li>
     *   <li>Overrides: None (use size profile defaults)</li>
     * </ul>
     *
     * @param cluster cluster specification
     * @return VmConfig for single-node cluster
     */
    private VmConfig createSingleNodeVm(ClusterSpec cluster) {
        // Use cluster name as VM name for Phase 1 MVP
        // Future phases may sanitize/transform the name
        String vmName = cluster.name();

        // Determine IP: use firstIp if specified, otherwise default
        String ip = cluster.firstIp().orElse(DEFAULT_IP);

        return new VmConfig(
            vmName,
            NodeRole.CLUSTER,
            ip,
            cluster.sizeProfile(),
            Optional.empty(),           // No CPU override
            Optional.empty()            // No memory override
        );
    }

    /**
     * Builds environment variables map for bootstrap scripts.
     *
     * <p>Phase 1 MVP Environment Variables:
     * <ul>
     *   <li><b>CLUSTER_NAME</b>: cluster.name() (e.g., "clu-m1-pt-kind")</li>
     *   <li><b>NAMESPACE_DEFAULT</b>: module.defaultNamespace() (e.g., "ns-m1-pt")</li>
     *   <li><b>CLUSTER_TYPE</b>: cluster.type() lowercase (e.g., "kind", "minikube")</li>
     * </ul>
     *
     * <p>Future phases may add:
     * <ul>
     *   <li>POD_NETWORK_CIDR (kubeadm)</li>
     *   <li>SERVICE_CIDR (kubeadm)</li>
     *   <li>CONTROL_PLANE_ENDPOINT (kubeadm HA)</li>
     * </ul>
     *
     * @param module module metadata
     * @param cluster cluster specification
     * @return environment variables map (LinkedHashMap for stable ordering)
     */
    private Map<String, String> buildEnvVars(ModuleInfo module, ClusterSpec cluster) {
        var envVars = new LinkedHashMap<String, String>();

        // Core environment variables
        envVars.put("CLUSTER_NAME", cluster.name());
        envVars.put("NAMESPACE_DEFAULT", module.defaultNamespace());
        envVars.put("CLUSTER_TYPE", cluster.type().name().toLowerCase(Locale.ROOT));

        return envVars;
    }
}
