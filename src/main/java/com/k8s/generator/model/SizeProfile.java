package com.k8s.generator.model;

/**
 * VM sizing presets for cluster nodes.
 *
 * <p>Size profiles define resource allocations (CPU, memory) for VMs.
 * These are conventions that can be overridden per-VM if needed.
 *
 * <p>Resource Allocations:
 * <ul>
 *   <li><b>SMALL</b>: 2 vCPUs, 4096 MB RAM - minimal but practical baseline</li>
 *   <li><b>MEDIUM</b>: 4 vCPUs, 8192 MB RAM - default for most scenarios</li>
 *   <li><b>LARGE</b>: 6 vCPUs, 12288 MB RAM - heavier multi-node/multi-cluster labs</li>
 * </ul>
 *
 * <p>Usage Guidelines:
 * <ul>
 *   <li><b>SMALL</b>: Control plane nodes, worker nodes for simple workloads</li>
 *   <li><b>MEDIUM</b>: Default for most scenarios, balanced resources</li>
 *   <li><b>LARGE</b>: Database nodes, high-traffic services, large deployments</li>
 * </ul>
 *
 * @since 1.0.0
 */
public enum SizeProfile {
    /**
     * Small profile: 2 vCPUs, 4096 MB RAM.
     * Practical minimum for local development.
     */
    SMALL(2, 4096),

    /**
     * Medium profile: 4 vCPUs, 8192 MB RAM.
     * Default profile per normative spec.
     */
    MEDIUM(4, 8192),

    /**
     * Large profile: 6 vCPUs, 12288 MB RAM.
     * For resource-intensive and multi-cluster scenarios.
     */
    LARGE(6, 12288);

    private final int cpus;
    private final int memoryMb;

    /**
     * Constructs a size profile with specified resources.
     *
     * @param cpus     number of virtual CPUs
     * @param memoryMb memory allocation in megabytes
     */
    SizeProfile(int cpus, int memoryMb) {
        this.cpus = cpus;
        this.memoryMb = memoryMb;
    }

    /**
     * Returns the number of vCPUs for this profile.
     *
     * @return CPU count (2 or 4)
     */
    public int getCpus() {
        return cpus;
    }

    /**
     * Returns the memory allocation in megabytes.
     *
     * @return memory in MB (2048, 4096, or 8192)
     */
    public int getMemoryMb() {
        return memoryMb;
    }

    /**
     * Returns the lowercase string representation of this size profile.
     *
     * @return lowercase profile name (e.g., "small", "medium", "large")
     */
    public String toLowerCaseString() {
        return name().toLowerCase();
    }

    /**
     * Parses a string into a SizeProfile enum value.
     * Case-insensitive matching.
     *
     * @param value string value to parse (e.g., "SMALL", "small", "Small")
     * @return matching SizeProfile
     * @throws IllegalArgumentException if value doesn't match any size profile
     */
    public static SizeProfile fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SizeProfile value cannot be null");
        }
        try {
            return SizeProfile.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Invalid size profile: '%s'. Valid values: small, medium, large", value)
            );
        }
    }
}
