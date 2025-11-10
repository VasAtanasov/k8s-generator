package com.k8s.generator.model;

import inet.ipaddr.IPAddress;

import java.util.Objects;

/**
 * Immutable VM configuration record.
 *
 * <p>Represents a single virtual machine in a Kubernetes cluster topology.
 * Each VM has a name, role, IP address, and resource allocation (size profile).
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: All fields final, no setters</li>
 *   <li><b>Structural Validation</b>: Non-null fields, positive resource values</li>
 *   <li><b>Role-based Naming</b>: VM names follow pattern: {role}-{index} (e.g., master-1, worker-2)</li>
 *   <li><b>Resource Defaults</b>: Size profile determines CPU/memory if not explicitly set</li>
 * </ul>
 *
 * <p>VM Roles (NodeRole enum):
 * <ul>
 *   <li><b>MASTER</b>: Kubernetes control plane node (API server, etcd, scheduler, controller)</li>
 *   <li><b>WORKER</b>: Kubernetes worker node (runs application workloads)</li>
 *   <li><b>CLUSTER</b>: Single-node cluster (kind/minikube, hosts entire cluster)</li>
 *   <li><b>MANAGEMENT</b>: Management machine (no k8s, just kubectl/helm/tools)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Kubeadm master node (using convenience constructor)
 * var master = new VmConfig(
 *     VmName.of("master-1"),
 *     NodeRole.MASTER,
 *     "192.168.56.10",
 *     SizeProfile.MEDIUM   // Use MEDIUM profile defaults (2 CPU, 4096 MB)
 * );
 *
 * // Worker node with custom resources
 * var worker = new VmConfig(
 *     VmName.of("worker-1"),
 *     NodeRole.WORKER,
 *     "192.168.56.11",
 *     SizeProfile.LARGE,
 *     4,      // Override: 4 CPUs
 *     8192    // Override: 8GB RAM
 * );
 * }</pre>
 *
 * @param name             VM hostname (e.g., "master-1", "worker-2", "cluster-1")
 * @param role             VM role (MASTER, WORKER, CLUSTER, MANAGEMENT)
 * @param ip               IP address for this VM (e.g., "192.168.56.10")
 * @param sizeProfile      Base size profile (determines defaults for CPU/memory)
 * @param cpuOverride      CPU count override (null uses sizeProfile default)
 * @param memoryMbOverride Memory override in MB (null uses sizeProfile default)
 * @see NodeRole
 * @see SizeProfile
 * @see ClusterSpec
 * @since 1.0.0
 */
public record VmConfig(
        VmName name,
        NodeRole role,
        IPAddress ip,
        SizeProfile sizeProfile,
        Integer cpuOverride,
        Integer memoryMbOverride) {

    /**
     * Convenience constructor - no resource overrides (most common case).
     * Uses sizeProfile defaults for CPU and memory.
     *
     * @param name        VM hostname
     * @param role        VM role
     * @param ip          IP address for this VM
     * @param sizeProfile Size profile (determines CPU/memory)
     */
    public VmConfig(VmName name, NodeRole role, IPAddress ip, SizeProfile sizeProfile) {
        this(name, role, ip, sizeProfile, null, null);
    }

    /**
     * Convenience constructor accepting string IP address (no overrides).
     * Parses IP using IPAddressString library and validates format.
     *
     * <p>This constructor is provided for backward compatibility and test convenience.
     * Production code should prefer using IPAddress directly for type safety.
     *
     * @param name        VM hostname
     * @param role        VM role
     * @param ipString    IP address as string (e.g., "192.168.56.10")
     * @param sizeProfile Size profile
     * @throws IllegalArgumentException if IP string format is invalid
     */
    public VmConfig(VmName name, NodeRole role, String ipString, SizeProfile sizeProfile) {
        this(name, role, parseIpAddress(ipString), sizeProfile, null, null);
    }

    /**
     * Convenience constructor accepting string IP address with overrides.
     * Parses IP using IPAddressString library and validates format.
     *
     * @param name             VM hostname
     * @param role             VM role
     * @param ipString         IP address as string (e.g., "192.168.56.10")
     * @param sizeProfile      Size profile
     * @param cpuOverride      CPU override (null uses sizeProfile default)
     * @param memoryMbOverride Memory override (null uses sizeProfile default)
     * @throws IllegalArgumentException if IP string format is invalid
     */
    public VmConfig(VmName name,
                    NodeRole role,
                    String ipString,
                    SizeProfile sizeProfile,
                    Integer cpuOverride,
                    Integer memoryMbOverride) {
        this(name, role, parseIpAddress(ipString), sizeProfile, cpuOverride, memoryMbOverride);
    }

    /**
     * Helper method to parse and validate IP address strings.
     *
     * @param ipString IP address string
     * @return parsed IPAddress object
     * @throws IllegalArgumentException if IP format is invalid or blank
     */
    private static IPAddress parseIpAddress(String ipString) {
        if (ipString == null || ipString.isBlank()) {
            throw new IllegalArgumentException("ip cannot be blank");
        }

        inet.ipaddr.IPAddressString ipAddr = new inet.ipaddr.IPAddressString(ipString);
        if (!ipAddr.isValid()) {
            throw new IllegalArgumentException("Invalid IP address format: " + ipString);
        }

        return ipAddr.getAddress();
    }

    /**
     * Compact constructor with structural validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>All required fields are non-null</li>
     *   <li>Name, role, and IP are non-blank</li>
     *   <li>CPU override (if present) is positive</li>
     *   <li>Memory override (if present) is positive</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any validation fails
     */
    public VmConfig {
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(ip, "ip is required");
        Objects.requireNonNull(sizeProfile, "sizeProfile is required");
        // cpuOverride and memoryMbOverride are nullable - null means "use sizeProfile default"

        if (name.value().isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }

        // Validate overrides if present (not null)
        if (cpuOverride != null && cpuOverride <= 0) {
            throw new IllegalArgumentException("cpuOverride must be positive, got: " + cpuOverride);
        }
        if (memoryMbOverride != null && memoryMbOverride <= 0) {
            throw new IllegalArgumentException("memoryMbOverride must be positive, got: " + memoryMbOverride);
        }
    }

    /**
     * Returns the effective CPU count for this VM.
     * Uses override if present, otherwise returns sizeProfile default.
     *
     * @return CPU count (1+)
     */
    public int getEffectiveCpus() {
        return cpuOverride != null ? cpuOverride : sizeProfile.getCpus();
    }

    /**
     * Returns the effective memory allocation for this VM.
     * Uses override if present, otherwise returns sizeProfile default.
     *
     * @return memory in megabytes (512+)
     */
    public int getEffectiveMemoryMb() {
        return memoryMbOverride != null ? memoryMbOverride : sizeProfile.getMemoryMb();
    }

    /**
     * Checks if this VM is a master node.
     *
     * @return true if role is NodeRole.MASTER
     */
    public boolean isMaster() {
        return role == NodeRole.MASTER;
    }

    /**
     * Checks if this VM is a worker node.
     *
     * @return true if role is NodeRole.WORKER
     */
    public boolean isWorker() {
        return role == NodeRole.WORKER;
    }

    /**
     * Checks if this VM is a cluster node (kind/minikube single-node).
     *
     * @return true if role is NodeRole.CLUSTER
     */
    public boolean isCluster() {
        return role == NodeRole.CLUSTER;
    }

    /**
     * Checks if this VM is a management machine.
     *
     * @return true if role is NodeRole.MANAGEMENT
     */
    public boolean isManagement() {
        return role == NodeRole.MANAGEMENT;
    }

    /**
     * Returns the role name in lowercase for template rendering.
     * Used by JTE templates for conditional logic and naming.
     *
     * @return role name (e.g., "master", "worker", "cluster", "management")
     */
    public String roleName() {
        return role.name().toLowerCase();
    }
}