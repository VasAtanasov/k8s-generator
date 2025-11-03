package com.k8s.generator.model;

/**
 * Supported Kubernetes cluster engine types.
 *
 * <p>Engine Characteristics:
 * <ul>
 *   <li><b>KIND</b>: Kubernetes IN Docker - runs clusters in containers, lightweight, fast setup</li>
 *   <li><b>MINIKUBE</b>: Single-node cluster with addons support, good for local development</li>
 *   <li><b>KUBEADM</b>: Full multi-node cluster using kubeadm, production-like setup</li>
 *   <li><b>NONE</b>: Management machine only, no Kubernetes installation (for kubectl/helm practice)</li>
 * </ul>
 *
 * <p>Node Requirements:
 * <ul>
 *   <li><b>KIND</b>: Requires 1 VM (runs cluster in Docker)</li>
 *   <li><b>MINIKUBE</b>: Requires 1 VM (single-node cluster)</li>
 *   <li><b>KUBEADM</b>: Requires 1+ master VMs, 0+ worker VMs</li>
 *   <li><b>NONE</b>: Requires 1 VM (no cluster installation)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum ClusterType {
    /**
     * Kubernetes IN Docker - containerized cluster nodes.
     * Fastest setup, minimal resource usage, single VM deployment.
     */
    KIND,

    /**
     * Minikube - single-node cluster with rich addon ecosystem.
     * Best for testing Kubernetes features with minimal infrastructure.
     */
    MINIKUBE,

    /**
     * Kubeadm - production-like multi-node cluster.
     * Full control plane, supports HA configurations, realistic topology.
     */
    KUBEADM,

    /**
     * No cluster installation - management machine only.
     * Used for kubectl, helm, and cluster management tool practice.
     */
    NONE;

    /**
     * Returns the lowercase string representation of this cluster type.
     * Used for template selection and user-facing output.
     *
     * @return lowercase type name (e.g., "kind", "minikube", "kubeadm", "none")
     */
    public String toLowerCaseString() {
        return name().toLowerCase();
    }

    /**
     * Parses a string into a ClusterType enum value.
     * Case-insensitive matching.
     *
     * @param value string value to parse (e.g., "KIND", "kind", "Kind")
     * @return matching ClusterType
     * @throws IllegalArgumentException if value doesn't match any cluster type
     */
    public static ClusterType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ClusterType value cannot be null");
        }
        try {
            return ClusterType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                String.format("Invalid cluster type: '%s'. Valid values: kind, minikube, kubeadm, none", value)
            );
        }
    }
}
