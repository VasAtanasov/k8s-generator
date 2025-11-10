package com.k8s.generator.orchestrator;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.NodeRole;
import com.k8s.generator.model.VmConfig;
import com.k8s.generator.model.VmName;
import inet.ipaddr.IPAddress;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Default implementation of VmGenerator.
 *
 * <p>Generates VM configurations following these naming conventions:
 * <ul>
 *   <li>KIND/MINIKUBE: Single VM named after cluster (role: CLUSTER)</li>
 *   <li>NONE: Single VM named after cluster (role: MANAGEMENT)</li>
 *   <li>KUBEADM: Multi-node with prefix pattern "{cluster-name}-{role}-{n}"</li>
 * </ul>
 *
 * <p>Implementation Details:
 * <ul>
 *   <li>Masters allocated first, workers second (preserves master IP priority)</li>
 *   <li>All VMs use cluster's default sizeProfile</li>
 *   <li>Custom memory/CPU left empty (use size profile defaults)</li>
 * </ul>
 *
 * @see VmGenerator
 * @since 1.0.0 (Phase 2)
 */
public class DefaultVmGenerator implements VmGenerator {

    @Override
    public List<VmConfig> generate(ClusterSpec cluster, List<IPAddress> allocatedIps) {
        Objects.requireNonNull(cluster, "cluster cannot be null");
        Objects.requireNonNull(allocatedIps, "allocatedIps cannot be null");

        int expectedCount = calculateVmCount(cluster);
        if (allocatedIps.size() != expectedCount) {
            throw new IllegalArgumentException(
                    String.format(
                            "IP count mismatch for cluster '%s': expected %d IPs, got %d",
                            cluster.name(), expectedCount, allocatedIps.size()
                    )
            );
        }

        return switch (cluster.type()) {
            case KIND -> generateSingleVm(cluster, allocatedIps.getFirst(), NodeRole.CLUSTER);
            case MINIKUBE -> generateSingleVm(cluster, allocatedIps.getFirst(), NodeRole.CLUSTER);
            case NONE -> generateSingleVm(cluster, allocatedIps.getFirst(), NodeRole.MANAGEMENT);
            case KUBEADM -> generateKubeadmVms(cluster, allocatedIps);
        };
    }

    @Override
    public int calculateVmCount(ClusterSpec cluster) {
        Objects.requireNonNull(cluster, "cluster cannot be null");

        return switch (cluster.type()) {
            case KIND, MINIKUBE, NONE -> 1;
            case KUBEADM -> cluster.masters() + cluster.workers();
        };
    }

    /**
     * Generates a single VM for KIND/MINIKUBE/NONE cluster types.
     * VM name equals cluster name.
     */
    private List<VmConfig> generateSingleVm(ClusterSpec cluster, IPAddress ip, NodeRole role) {
        var vm = new VmConfig(
                VmName.of(cluster.name().value()),           // VM name = cluster name
                role,                     // CLUSTER or MANAGEMENT
                ip,
                cluster.sizeProfile(),
                null,         // Use size profile defaults
                null
        );
        return List.of(vm);
    }

    /**
     * Generates multi-node VMs for KUBEADM clusters.
     * Masters first, then workers, with cluster-prefixed names.
     */
    private List<VmConfig> generateKubeadmVms(ClusterSpec cluster, List<IPAddress> ips) {
        var vms = new ArrayList<VmConfig>();
        int ipIndex = 0;

        // Generate master nodes: {cluster-name}-master-{n}
        for (int i = 1; i <= cluster.masters(); i++) {
            var vm = new VmConfig(
                    VmName.of(String.format("%s-master-%d", cluster.name(), i)),
                    NodeRole.MASTER,
                    ips.get(ipIndex++),
                    cluster.sizeProfile(),
                    null,
                    null
            );
            vms.add(vm);
        }

        // Generate worker nodes: {cluster-name}-worker-{n}
        for (int i = 1; i <= cluster.workers(); i++) {
            var vm = new VmConfig(
                    VmName.of(String.format("%s-worker-%d", cluster.name(), i)),
                    NodeRole.WORKER,
                    ips.get(ipIndex++),
                    cluster.sizeProfile(),
                    null,
                    null
            );
            vms.add(vm);
        }

        return vms;
    }
}
