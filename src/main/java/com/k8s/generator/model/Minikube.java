package com.k8s.generator.model;

import java.util.List;

/**
 * Minikube - single-node cluster with rich addon ecosystem.
 *
 * <p>Characteristics:
 * <ul>
 *   <li><b>Deployment</b>: Single VM running Minikube</li>
 *   <li><b>Addons</b>: Rich ecosystem (dashboard, ingress, metrics-server)</li>
 *   <li><b>Setup Time</b>: Fast (2-3 minutes)</li>
 *   <li><b>Resource Usage</b>: Moderate (single VM)</li>
 *   <li><b>Use Case</b>: Testing Kubernetes features, addon exploration</li>
 * </ul>
 *
 * <p>Node Configuration:
 * <ul>
 *   <li>Masters: 0 (uses NodeRole.CLUSTER instead)</li>
 *   <li>Workers: 0 (single-VM deployment)</li>
 *   <li>CNI: Not required (Minikube uses built-in networking)</li>
 * </ul>
 *
 * @since 2.0.0
 */
public record Minikube() implements ClusterType {

    /**
     * Singleton instance - use this instead of creating new instances.
     */
    public static final Minikube INSTANCE = new Minikube();

    @Override
    public String id() {
        return "minikube";
    }

    @Override
    public String displayName() {
        return "Minikube";
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
        return List.of(Tool.kubectl(), Tool.docker(), Tool.minikube());
    }
}
