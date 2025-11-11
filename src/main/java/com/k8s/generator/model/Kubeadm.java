package com.k8s.generator.model;

import java.util.List;

/**
 * Kubeadm - production-like multi-node cluster.
 *
 * <p>Characteristics:
 * <ul>
 *   <li><b>Deployment</b>: Multiple VMs (1+ masters, 0+ workers)</li>
 *   <li><b>Control Plane</b>: Full HA support (multiple masters)</li>
 *   <li><b>Setup Time</b>: Moderate (5-10 minutes for multi-node)</li>
 *   <li><b>Resource Usage</b>: High (multiple VMs)</li>
 *   <li><b>Use Case</b>: Production-like scenarios, HA practice, exam prep</li>
 * </ul>
 *
 * <p>Node Configuration:
 * <ul>
 *   <li>Masters: 1+ required (supports HA with 3, 5, 7 masters)</li>
 *   <li>Workers: 0+ (optional, can be masterless cluster)</li>
 *   <li>CNI: Required (Calico, Flannel, Weave, Cilium, Antrea)</li>
 *   <li>Roles: Supported (NodeRole.MASTER, NodeRole.WORKER)</li>
 * </ul>
 *
 * @since 2.0.0
 */
public record Kubeadm() implements ClusterType {

    /**
     * Singleton instance - use this instead of creating new instances.
     */
    public static final Kubeadm INSTANCE = new Kubeadm();

    @Override
    public String id() {
        return "kubeadm";
    }

    @Override
    public String displayName() {
        return "Kubeadm";
    }

    @Override
    public boolean supportsMultiNode() {
        return true;
    }

    @Override
    public boolean supportsRoles() {
        return true;
    }

    @Override
    public List<Tool> requiredTools() {
        return List.of(Tool.kubectl(), Tool.containerd(), Tool.kubeBinaries());
    }
}
