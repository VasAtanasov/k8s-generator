package com.k8s.generator.orchestrator;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.VmConfig;
import inet.ipaddr.IPAddress;

import java.util.List;

/**
 * Generates VM configurations from cluster specifications.
 *
 * <p>Responsibility:
 * Converts high-level cluster specifications (master count, worker count) into
 * concrete VM configurations with names, roles, IPs, and resource allocations.
 *
 * <p>Generation Rules:
 * <ul>
 *   <li><b>KIND/MINIKUBE</b>: Generates 1 VM with name=cluster-name, role=CLUSTER</li>
 *   <li><b>NONE (management)</b>: Generates 1 VM with name=cluster-name, role=MANAGEMENT</li>
 *   <li><b>KUBEADM</b>: Generates master VMs ({cluster}-master-{n}) and worker VMs ({cluster}-worker-{n})</li>
 * </ul>
 *
 * <p>Naming Convention:
 * <ul>
 *   <li>KIND/MINIKUBE/NONE: VM name = cluster name (e.g., "dev", "staging")</li>
 *   <li>KUBEADM masters: "{cluster-name}-master-{n}" (e.g., "prod-master-1")</li>
 *   <li>KUBEADM workers: "{cluster-name}-worker-{n}" (e.g., "prod-worker-1")</li>
 * </ul>
 *
 * <p>IP Allocation:
 * <ul>
 *   <li>Requires pre-allocated IP list matching expected VM count</li>
 *   <li>Assigns IPs sequentially to generated VMs</li>
 *   <li>Masters receive first IPs, workers receive remaining IPs</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var generator = new DefaultVmGenerator();
 *
 * // KIND cluster: 1 VM
 * var kindCluster = new ClusterSpec(
 *     "dev", ClusterType.KIND,
 *     Optional.empty(), 0, 0,
 *     SizeProfile.MEDIUM, List.of(), Optional.empty()
 * );
 * var kindVms = generator.generate(kindCluster, List.of("192.168.56.10"));
 * // Result: [VmConfig("dev", CLUSTER, "192.168.56.10", MEDIUM, ...)]
 *
 * // KUBEADM cluster: 1 master + 2 workers = 3 VMs
 * var kubeadmCluster = new ClusterSpec(
 *     "prod", ClusterType.KUBEADM,
 *     Optional.of("192.168.56.20"), 1, 2,
 *     SizeProfile.LARGE, List.of(), Optional.of(CniType.CALICO)
 * );
 * var kubeadmVms = generator.generate(kubeadmCluster, List.of(
 *     "192.168.56.20", "192.168.56.21", "192.168.56.22"
 * ));
 * // Result: [
 * //   VmConfig("prod-master-1", MASTER, "192.168.56.20", LARGE, ...),
 * //   VmConfig("prod-worker-1", WORKER, "192.168.56.21", LARGE, ...),
 * //   VmConfig("prod-worker-2", WORKER, "192.168.56.22", LARGE, ...)
 * // ]
 * }</pre>
 *
 * @see ClusterSpec
 * @see VmConfig
 * @see DefaultVmGenerator
 * @since 1.0.0 (Phase 2)
 */
public interface VmGenerator {

    /**
     * Generates VM configurations for a cluster using pre-allocated IPs.
     *
     * <p>Contract:
     * <ul>
     *   <li>IP list size MUST match expected VM count for cluster type</li>
     *   <li>Generated VMs use cluster's sizeProfile as default</li>
     *   <li>VM names follow cluster-prefixed convention</li>
     *   <li>Roles assigned based on cluster type and position</li>
     * </ul>
     *
     * <p>Expected VM Counts:
     * <ul>
     *   <li>KIND/MINIKUBE/NONE: 1 VM</li>
     *   <li>KUBEADM: masters + workers VMs</li>
     * </ul>
     *
     * @param cluster      cluster specification (must not be null)
     * @param allocatedIps pre-allocated IP addresses (size must match VM count)
     * @return list of generated VM configurations
     * @throws NullPointerException     if cluster or allocatedIps is null
     * @throws IllegalArgumentException if IP count doesn't match expected VM count
     * @see ClusterSpec#totalNodes()
     */
    List<VmConfig> generate(ClusterSpec cluster, List<IPAddress> allocatedIps);

    /**
     * Calculates expected VM count for a cluster.
     * Helper method to determine how many IPs to allocate.
     *
     * @param cluster cluster specification (must not be null)
     * @return expected number of VMs
     * @throws NullPointerException if cluster is null
     */
    int calculateVmCount(ClusterSpec cluster);
}
