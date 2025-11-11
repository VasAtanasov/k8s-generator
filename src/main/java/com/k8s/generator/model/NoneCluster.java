package com.k8s.generator.model;

import java.util.List;

/**
 * Management machine - no cluster, kubectl/tools only.
 *
 * <p>Characteristics:
 * <ul>
 *   <li><b>Deployment</b>: Single VM with management tools</li>
 *   <li><b>Cluster</b>: No local Kubernetes cluster installed</li>
 *   <li><b>Setup Time</b>: Very fast (< 1 minute)</li>
 *   <li><b>Resource Usage</b>: Minimal (2 CPU, 4GB RAM default)</li>
 *   <li><b>Use Case</b>: Multi-cluster orchestration, kubectl practice, cloud management</li>
 * </ul>
 *
 * <p>Node Configuration:
 * <ul>
 *   <li>Masters: 0 (no cluster)</li>
 *   <li>Workers: 0 (no cluster)</li>
 *   <li>CNI: Not allowed (no cluster to configure)</li>
 *   <li>Role: NodeRole.MANAGEMENT</li>
 * </ul>
 *
 * <p>Optional Features:
 * <ul>
 *   <li>Azure CLI (with --azure modifier)</li>
 *   <li>Kubeconfig aggregation (multi-cluster scenarios)</li>
 *   <li>Additional tools via bootstrap hooks</li>
 * </ul>
 *
 * <p>Note: Replaces legacy NONE enum value. The id() returns "none" for
 * backward compatibility with CLI parsing.
 *
 * @since 2.0.0
 */
public record NoneCluster() implements ClusterType {

    /**
     * Singleton instance - use this instead of creating new instances.
     */
    public static final NoneCluster INSTANCE = new NoneCluster();

    @Override
    public String id() {
        return "none";  // Backward compatibility with CLI
    }

    @Override
    public String displayName() {
        return "Management Machine";
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
        return List.of(Tool.kubectl());
    }
}
