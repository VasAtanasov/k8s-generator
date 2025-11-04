package com.k8s.generator.parser;

import com.k8s.generator.ip.IpAllocator;
import com.k8s.generator.ip.SequentialIpAllocator;
import com.k8s.generator.model.*;

import java.util.*;

/**
 * Converts validated GeneratorSpec to template-ready ScaffoldPlan.
 *
 * <p>This implementation performs the following transformations:
 * <ul>
 *   <li><b>VM Generation</b>: Creates VMs based on cluster type and node counts</li>
 *   <li><b>IP Allocation</b>: Assigns IP addresses to VMs using IpAllocator</li>
 *   <li><b>Role Assignment</b>: Maps cluster type to appropriate NodeRole</li>
 *   <li><b>Environment Variables</b>: Builds map for bootstrap scripts</li>
 * </ul>
 *
 * <p>Phase 2 Behavior:
 * <ul>
 *   <li><b>Dynamic IP allocation</b>: Uses IpAllocator for sequential IP assignment</li>
 *   <li><b>Multi-node support</b>: KUBEADM generates (masters + workers) VMs</li>
 *   <li><b>Role-based generation</b>:
 *       <ul>
 *         <li>KIND/MINIKUBE: 1 VM with NodeRole.CLUSTER</li>
 *         <li>KUBEADM: (masters) VMs with NodeRole.MASTER + (workers) VMs with NodeRole.WORKER</li>
 *         <li>NONE: 1 VM with NodeRole.MANAGEMENT</li>
 *       </ul>
 *   </li>
 *   <li><b>Environment variables</b>:
 *       <ul>
 *         <li>CLUSTER_NAME: cluster name from spec</li>
 *         <li>NAMESPACE_DEFAULT: derived from module (ns-{num}-{type})</li>
 *         <li>CLUSTER_TYPE: engine name (kind, minikube, kubeadm)</li>
 *         <li>CNI_TYPE: CNI plugin for kubeadm (calico, flannel, etc.)</li>
 *       </ul>
 *   </li>
 * </ul>
 *
 * <p>VM Generation Rules (Phase 2):
 * <pre>
 * ClusterType.KIND      → 1 VM with NodeRole.CLUSTER, IP from IpAllocator
 * ClusterType.MINIKUBE  → 1 VM with NodeRole.CLUSTER, IP from IpAllocator
 * ClusterType.KUBEADM   → (masters + workers) VMs, IPs from IpAllocator
 * ClusterType.NONE      → 1 VM with NodeRole.MANAGEMENT, IP from IpAllocator
 * </pre>
 *
 * <p>Future Phases:
 * <ul>
 *   <li><b>Phase 3</b>: Multi-cluster support, IP overlap detection</li>
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

    private final IpAllocator ipAllocator;

    /**
     * Creates SpecToPlan with default IpAllocator.
     */
    public SpecToPlan() {
        this(new SequentialIpAllocator());
    }

    /**
     * Creates SpecToPlan with custom IpAllocator (for testing).
     *
     * @param ipAllocator IP allocator to use
     */
    public SpecToPlan(IpAllocator ipAllocator) {
        this.ipAllocator = Objects.requireNonNull(ipAllocator, "ipAllocator is required");
    }

    /**
     * Builds a ScaffoldPlan from a validated GeneratorSpec.
     *
     * @param spec validated GeneratorSpec (must not be null)
     * @return ScaffoldPlan ready for template rendering
     * @throws NullPointerException  if spec is null
     * @throws IllegalStateException if spec contains unsupported cluster types or IP allocation fails
     */
    @Override
    public ScaffoldPlan build(GeneratorSpec spec) {
        Objects.requireNonNull(spec, "spec is required");

        // Phase 2: Only single-cluster mode supported (multi-cluster is Phase 3)
        if (!spec.isSingleCluster()) {
            throw new IllegalStateException(
                    "Multi-cluster mode not yet supported in Phase 2. " +
                            "Spec contains " + spec.clusterCount() + " clusters."
            );
        }

        ClusterSpec cluster = spec.primaryCluster();

        // 1. Allocate IPs for cluster VMs
        var ipResult = ipAllocator.allocate(cluster);
        if (ipResult.isFailure()) {
            throw new IllegalStateException(
                    "IP allocation failed: " + ipResult.getError()
            );
        }
        List<String> allocatedIps = ipResult.orElseThrow();

        // 2. Generate VMs with allocated IPs
        List<VmConfig> vms = generateVms(cluster, allocatedIps);

        // 3. Build environment variables
        Map<String, String> envVars = buildEnvVars(spec.module(), cluster);

        // 4. Create ScaffoldPlan
        return new ScaffoldPlan(spec.module(), vms, envVars);
    }

    /**
     * Generates VMs for a cluster based on its type.
     *
     * <p>Phase 2 Implementation:
     * <ul>
     *   <li>KIND/MINIKUBE: 1 VM with NodeRole.CLUSTER</li>
     *   <li>KUBEADM: (masters + workers) VMs with appropriate roles</li>
     *   <li>NONE: 1 VM with NodeRole.MANAGEMENT</li>
     * </ul>
     *
     * @param cluster      cluster specification
     * @param allocatedIps list of IPs allocated by IpAllocator
     * @return list of VmConfig with assigned IPs
     * @throws IllegalStateException if IP count doesn't match expected VM count
     */
    private List<VmConfig> generateVms(ClusterSpec cluster, List<String> allocatedIps) {
        return switch (cluster.type()) {
            case KIND, MINIKUBE -> {
                if (allocatedIps.size() != 1) {
                    throw new IllegalStateException(
                            String.format("Expected 1 IP for %s cluster, got %d",
                                    cluster.type(), allocatedIps.size())
                    );
                }
                yield List.of(createSingleNodeVm(cluster, allocatedIps.get(0)));
            }
            case KUBEADM -> createKubeadmVms(cluster, allocatedIps);
            case NONE -> {
                if (allocatedIps.size() != 1) {
                    throw new IllegalStateException(
                            String.format("Expected 1 IP for NONE (management) cluster, got %d",
                                    allocatedIps.size())
                    );
                }
                yield List.of(createManagementVm(cluster, allocatedIps.get(0)));
            }
        };
    }

    /**
     * Creates a single-node VM for kind or minikube cluster.
     *
     * <p>VM Configuration:
     * <ul>
     *   <li>Name: cluster name (e.g., "clu-m1-pt-kind")</li>
     *   <li>Role: NodeRole.CLUSTER</li>
     *   <li>IP: From IpAllocator</li>
     *   <li>Size: From cluster.sizeProfile()</li>
     *   <li>Overrides: None (use size profile defaults)</li>
     * </ul>
     *
     * @param cluster cluster specification
     * @param ip      allocated IP address
     * @return VmConfig for single-node cluster
     */
    private VmConfig createSingleNodeVm(ClusterSpec cluster, String ip) {
        return new VmConfig(
                cluster.name(),
                NodeRole.CLUSTER,
                ip,
                cluster.sizeProfile(),
                Optional.empty(),           // No CPU override
                Optional.empty()            // No memory override
        );
    }

    /**
     * Creates a management VM (ClusterType.NONE).
     *
     * <p>VM Configuration:
     * <ul>
     *   <li>Name: cluster name (e.g., "mgmt-m7-hw")</li>
     *   <li>Role: NodeRole.MANAGEMENT</li>
     *   <li>IP: From IpAllocator</li>
     *   <li>Size: From cluster.sizeProfile()</li>
     * </ul>
     *
     * @param cluster cluster specification
     * @param ip      allocated IP address
     * @return VmConfig for management VM
     */
    private VmConfig createManagementVm(ClusterSpec cluster, String ip) {
        return new VmConfig(
                cluster.name(),
                NodeRole.MANAGEMENT,
                ip,
                cluster.sizeProfile(),
                Optional.empty(),
                Optional.empty()
        );
    }

    /**
     * Creates VMs for kubeadm multi-node cluster.
     *
     * <p>VM Naming Convention:
     * <ul>
     *   <li>Masters: {cluster-name}-master-1, {cluster-name}-master-2, ...</li>
     *   <li>Workers: {cluster-name}-worker-1, {cluster-name}-worker-2, ...</li>
     * </ul>
     *
     * <p>IP Assignment:
     * <ul>
     *   <li>Masters get first (masters) IPs</li>
     *   <li>Workers get next (workers) IPs</li>
     * </ul>
     *
     * @param cluster      cluster specification
     * @param allocatedIps list of allocated IPs (size = masters + workers)
     * @return list of VmConfig (masters first, then workers)
     * @throws IllegalStateException if IP count doesn't match masters + workers
     */
    private List<VmConfig> createKubeadmVms(ClusterSpec cluster, List<String> allocatedIps) {
        int expectedVmCount = cluster.masters() + cluster.workers();
        if (allocatedIps.size() != expectedVmCount) {
            throw new IllegalStateException(
                    String.format(
                            "IP count mismatch for kubeadm cluster '%s': expected %d (masters=%d, workers=%d), got %d IPs",
                            cluster.name(), expectedVmCount, cluster.masters(), cluster.workers(), allocatedIps.size()
                    )
            );
        }

        var vms = new ArrayList<VmConfig>();
        int ipIndex = 0;

        // Create master VMs
        for (int i = 1; i <= cluster.masters(); i++) {
            String vmName = String.format("%s-master-%d", cluster.name(), i);
            vms.add(new VmConfig(
                    vmName,
                    NodeRole.MASTER,
                    allocatedIps.get(ipIndex++),
                    cluster.sizeProfile(),
                    Optional.empty(),
                    Optional.empty()
            ));
        }

        // Create worker VMs
        for (int i = 1; i <= cluster.workers(); i++) {
            String vmName = String.format("%s-worker-%d", cluster.name(), i);
            vms.add(new VmConfig(
                    vmName,
                    NodeRole.WORKER,
                    allocatedIps.get(ipIndex++),
                    cluster.sizeProfile(),
                    Optional.empty(),
                    Optional.empty()
            ));
        }

        return vms;
    }

    /**
     * Builds environment variables map for bootstrap scripts.
     *
     * <p>Phase 2 Environment Variables:
     * <ul>
     *   <li><b>CLUSTER_NAME</b>: cluster.name() (e.g., "clu-m1-pt-kind")</li>
     *   <li><b>NAMESPACE_DEFAULT</b>: module.defaultNamespace() (e.g., "ns-m1-pt")</li>
     *   <li><b>CLUSTER_TYPE</b>: cluster.type() lowercase (e.g., "kind", "minikube", "kubeadm")</li>
     *   <li><b>CNI_TYPE</b>: CNI plugin for kubeadm (e.g., "calico", "flannel") - only if CNI present</li>
     * </ul>
     *
     * <p>Future phases may add:
     * <ul>
     *   <li>POD_NETWORK_CIDR (kubeadm)</li>
     *   <li>SERVICE_CIDR (kubeadm)</li>
     *   <li>CONTROL_PLANE_ENDPOINT (kubeadm HA)</li>
     * </ul>
     *
     * @param module  module metadata
     * @param cluster cluster specification
     * @return environment variables map (LinkedHashMap for stable ordering)
     */
    private Map<String, String> buildEnvVars(ModuleInfo module, ClusterSpec cluster) {
        var envVars = new LinkedHashMap<String, String>();

        // Core environment variables
        envVars.put("CLUSTER_NAME", cluster.name());
        envVars.put("NAMESPACE_DEFAULT", module.defaultNamespace());
        envVars.put("CLUSTER_TYPE", cluster.type().name().toLowerCase(Locale.ROOT));

        // CNI type (only for kubeadm clusters)
        cluster.cni().ifPresent(cniType ->
                envVars.put("CNI_TYPE", cniType.name().toLowerCase(Locale.ROOT))
        );

        return envVars;
    }
}
