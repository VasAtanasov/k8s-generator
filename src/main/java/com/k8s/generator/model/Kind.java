package com.k8s.generator.model;

import java.util.List;

/**
 * Kubernetes IN Docker - containerized cluster nodes.
 *
 * <p>Characteristics:
 * <ul>
 *   <li><b>Deployment</b>: Single VM running Docker + KIND CLI</li>
 *   <li><b>Cluster Nodes</b>: Run as Docker containers inside the VM</li>
 *   <li><b>Setup Time</b>: Fastest (< 2 minutes)</li>
 *   <li><b>Resource Usage</b>: Minimal (containers vs full VMs)</li>
 *   <li><b>Use Case</b>: Learning, quick experimentation, CI/CD</li>
 * </ul>
 *
 * <p>Node Configuration:
 * <ul>
 *   <li>Masters: 0 (uses NodeRole.CLUSTER instead)</li>
 *   <li>Workers: 0 (single-VM deployment)</li>
 *   <li>CNI: Not required (KIND includes bundled CNI)</li>
 * </ul>
 *
 * @since 2.0.0
 */
public record Kind() implements ClusterType {

    /**
     * Singleton instance - use this instead of creating new instances.
     */
    public static final Kind INSTANCE = new Kind();

    @Override
    public String id() {
        return "kind";
    }

    @Override
    public String displayName() {
        return "KIND (Kubernetes IN Docker)";
    }

    @Override
    public boolean supportsMultiNode() {
        return false;
    }

    @Override
    public boolean supportsRoles() {
        return false;
    }

    @Override
    public List<Tool> requiredTools() {
        return List.of(Tool.kubectl(), Tool.docker(), Tool.kind());
    }
}
