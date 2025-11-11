package com.k8s.generator.model;

import java.util.List;

/**
 * Sealed hierarchy representing supported Kubernetes cluster types.
 *
 * <p>This sealed interface replaces the legacy ClusterType enum to support
 * the Engine SPI pattern, enabling extensible cluster type implementations
 * while maintaining compile-time exhaustiveness checking via pattern matching.
 *
 * <p>Design Philosophy:
 * <ul>
 *   <li><b>Sealed for Safety</b>: Finite set of cluster types ensures exhaustive
 *       pattern matching at compile time</li>
 *   <li><b>Singleton Records</b>: Each type is a stateless singleton, avoiding
 *       unnecessary object allocation</li>
 *   <li><b>Value Object</b>: Immutable, validated domain concept encapsulating
 *       cluster type semantics</li>
 *   <li><b>Engine Bridge</b>: Each type maps 1:1 to an Engine implementation,
 *       supporting future SPI-based extensibility</li>
 * </ul>
 *
 * <p>Cluster Types and Characteristics:
 * <table>
 *   <tr>
 *     <th>Type</th>
 *     <th>ID</th>
 *     <th>Multi-Node</th>
 *     <th>Roles</th>
 *     <th>Use Case</th>
 *   </tr>
 *   <tr>
 *     <td>KIND</td>
 *     <td>kind</td>
 *     <td>No</td>
 *     <td>No</td>
 *     <td>Fast containerized cluster, learning</td>
 *   </tr>
 *   <tr>
 *     <td>MINIKUBE</td>
 *     <td>minikube</td>
 *     <td>No</td>
 *     <td>No</td>
 *     <td>Single-node with addons, testing</td>
 *   </tr>
 *   <tr>
 *     <td>KUBEADM</td>
 *     <td>kubeadm</td>
 *     <td>Yes</td>
 *     <td>Yes</td>
 *     <td>Production-like multi-node clusters</td>
 *   </tr>
 *   <tr>
 *     <td>MANAGEMENT</td>
 *     <td>none</td>
 *     <td>No</td>
 *     <td>No</td>
 *     <td>Management machine, no cluster</td>
 *   </tr>
 * </table>
 *
 * <p>Migration from ClusterType enum:
 * <ul>
 *   <li>{@code ClusterType.KIND} → {@code Kind.INSTANCE}</li>
 *   <li>{@code ClusterType.MINIKUBE} → {@code Minikube.INSTANCE}</li>
 *   <li>{@code ClusterType.KUBEADM} → {@code Kubeadm.INSTANCE}</li>
 *   <li>{@code ClusterType.NONE} → {@code NoneCluster.INSTANCE}</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Pattern matching with exhaustiveness checking
 * int vmCount = switch (clusterType) {
 *     case Kind k -> 1;
 *     case Minikube m -> 1;
 *     case Kubeadm ku -> ku.masters() + ku.workers();
 *     case NoneCluster nc -> 1;
 * };
 *
 * // String parsing (case-insensitive)
 * ClusterType type = ClusterType.fromString("KIND");  // Returns Kind.INSTANCE
 *
 * // ID-based lookup (lowercase)
 * ClusterType type = ClusterType.byId("kind");  // Returns Kind.INSTANCE
 * }</pre>
 *
 * @see Kind
 * @see Minikube
 * @see Kubeadm
 * @see NoneCluster
 * @since 2.0.0 (refactored from enum in 1.0.0)
 */
public sealed interface ClusterType permits Kind, Minikube, Kubeadm, NoneCluster {

    /**
     * Returns the unique identifier for this cluster type (lowercase).
     * <p>
     * This ID is used for:
     * <ul>
     *   <li>Template selection (e.g., "templates/engines/kind/")</li>
     *   <li>User-facing output and logs</li>
     *   <li>Engine registry lookup</li>
     *   <li>Cluster naming conventions</li>
     * </ul>
     *
     * @return lowercase identifier (e.g., "kind", "minikube", "kubeadm", "none")
     */
    String id();

    /**
     * Returns the display name for this cluster type.
     * Used in help text, error messages, and user-facing documentation.
     *
     * @return human-readable name (e.g., "KIND (Kubernetes IN Docker)")
     */
    String displayName();

    /**
     * Indicates whether this cluster type supports multi-node deployments.
     * <p>
     * Multi-node support means the cluster can be configured with multiple
     * master and worker VMs.
     *
     * @return true if multi-node is supported (only KUBEADM returns true)
     */
    boolean supportsMultiNode();

    /**
     * Indicates whether this cluster type supports role-based VM configuration.
     * <p>
     * Role support means VMs can have distinct roles (master, worker) with
     * different configurations and bootstrap procedures.
     *
     * @return true if roles are supported (only KUBEADM returns true)
     */
    boolean supportsRoles();

    /**
     * Returns the immutable list of required tools for this cluster type.
     * <p>
     * These tools are automatically installed during bootstrap and are
     * essential for cluster operation.
     *
     * @return immutable list of Tool value objects
     */
    List<Tool> requiredTools();

    /**
     * Parses a string into a ClusterType instance (case-insensitive).
     * <p>
     * Accepted values: "kind", "minikube", "kubeadm", "none" (and case variants)
     *
     * @param value string value to parse (e.g., "KIND", "kind", "Kind")
     * @return matching ClusterType singleton instance
     * @throws IllegalArgumentException if value doesn't match any cluster type
     * @throws IllegalArgumentException if value is null
     */
    static ClusterType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ClusterType value cannot be null");
        }

        return switch (value.trim().toLowerCase()) {
            case "kind" -> Kind.INSTANCE;
            case "minikube" -> Minikube.INSTANCE;
            case "kubeadm" -> Kubeadm.INSTANCE;
            case "none" -> NoneCluster.INSTANCE;
            default -> throw new IllegalArgumentException(
                    String.format("Invalid cluster type: '%s'. Valid values: kind, minikube, kubeadm, none", value)
            );
        };
    }

    /**
     * Gets ClusterType by id (exact lowercase match).
     * <p>
     * This method is used when the id is already normalized (e.g., from
     * template paths or internal lookups).
     *
     * @param id lowercase cluster type id (e.g., "kind", "minikube")
     * @return matching ClusterType singleton instance
     * @throws IllegalArgumentException if id doesn't match any cluster type
     * @throws IllegalArgumentException if id is null
     */
    static ClusterType byId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("ClusterType id cannot be null");
        }

        return switch (id) {
            case "kind" -> Kind.INSTANCE;
            case "minikube" -> Minikube.INSTANCE;
            case "kubeadm" -> Kubeadm.INSTANCE;
            case "none" -> NoneCluster.INSTANCE;
            default -> throw new IllegalArgumentException(
                    String.format("Unknown cluster type id: '%s'. Valid ids: kind, minikube, kubeadm, none", id)
            );
        };
    }

    /**
     * Returns all available cluster types.
     * Order: Kind, Minikube, Kubeadm, Management
     *
     * @return immutable list of all cluster types
     */
    static List<ClusterType> values() {
        return List.of(Kind.INSTANCE, Minikube.INSTANCE, Kubeadm.INSTANCE, NoneCluster.INSTANCE);
    }
}
