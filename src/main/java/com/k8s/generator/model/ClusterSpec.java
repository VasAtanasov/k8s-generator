package com.k8s.generator.model;

import com.k8s.generator.model.NodeTopology;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import inet.ipaddr.IPAddressStringParameters;
import lombok.Builder;

import java.util.List;
import java.util.Objects;

/**
 * Immutable cluster specification - the core domain model.
 *
 * <p>Represents a complete Kubernetes cluster configuration including:
 * <ul>
 *   <li>Cluster metadata (name, type/engine)</li>
 *   <li>Node topology (master count, worker count)</li>
 *   <li>Network configuration (IP allocation)</li>
 *   <li>VM configurations (size profile, custom resources)</li>
 * </ul>
 *
 * <p>Validation Strategy (Three-Layer):
 * <ol>
 *   <li><b>Structural</b>: Enforced in this compact constructor
 *       <br>- Non-null fields, positive counts, basic invariants</li>
 *   <li><b>Semantic</b>: Enforced by SemanticValidator
 *       <br>- Cluster name format, engine-specific rules, IP format validation</li>
 *   <li><b>Policy</b>: Enforced by PolicyValidator
 *       <br>- Cross-cluster constraints, IP overlaps, resource limits</li>
 * </ol>
 *
 * <p>Cluster Types and Node Requirements:
 * <ul>
 *   <li><b>KUBEADM</b>: masters≥1, workers≥0 (full multi-node cluster), CNI required</li>
 *   <li><b>NONE</b>: masters=0, workers=0 (VMs generated as "management" role), CNI not allowed</li>
 * </ul>
 *
 * <p>IP Allocation:
 * <ul>
 *   <li><b>Single cluster</b>: firstIp nullable (null uses default 192.168.56.10)</li>
 *   <li><b>Multi-cluster</b>: firstIp should be specified for each cluster (prevents overlaps)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Kubeadm multi-node cluster
 * ClusterSpec kubeadm = ClusterSpec.builder()
 *     .name(ClusterName.of("clu-m1-pt-kubeadm"))
 *     .type(Kubeadm.INSTANCE)
 *     .nodes(NodeTopology.of(1, 2))  // 1 master, 2 workers
 *     .build();
 *
 * // Management-only machine
 * ClusterSpec mgmt = ClusterSpec.builder()
 *     .name(ClusterName.of("mgmt"))
 *     .type(NoneCluster.INSTANCE)
 *     .build();
 * }</pre>
 *
 * @param name        Cluster name (must match [a-z][a-z0-9-]*, validated by SemanticValidator)
 * @param type        Cluster engine type (kubeadm, none)
 * @param firstIp     Starting IP for sequential allocation (null uses default 192.168.56.10)
 * @param nodes       Node topology (master count, worker count)
 * @param sizeProfile Default size profile for all VMs in this cluster
 * @param vms         Explicit VM configurations (empty = orchestrator generates from masters/workers)
 * @param cni         CNI plugin type (null for NONE, required for KUBEADM)
 * @param podNetwork  Pod network CIDR (null uses default 10.244.0.0/16 for kubeadm)
 * @param svcNetwork  Service network CIDR (null uses default 10.96.0.0/12 for kubeadm)
 * @see ClusterType
 * @see VmConfig
 * @see SizeProfile
 * @see CniType
 * @see com.k8s.generator.validate.CompositeValidator
 * @since 1.0.0
 */
@Builder(toBuilder = true)
public record ClusterSpec(
        ClusterName name,
        ClusterType type,
        IPAddress firstIp,
        NodeTopology nodes,
        SizeProfile sizeProfile,
        List<VmConfig> vms,
        CniType cni,
        NetworkCIDR podNetwork,
        NetworkCIDR svcNetwork) {
    /**
     * Compact constructor with structural validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>Required fields (name, type, sizeProfile, vms) are non-null</li>
     *   <li>Name is non-blank</li>
     *   <li>Master and worker counts are non-negative</li>
     *   <li>At least one node exists (masters + workers > 0 OR type allows zero nodes)</li>
     *   <li>VMs list contains no nulls</li>
     *   <li>Optional fields (firstIp, cni, podNetwork, svcNetwork) may be null</li>
     * </ul>
     *
     * <p>Note: This constructor does NOT validate:
     * <ul>
     *   <li>Cluster name format (handled by SemanticValidator)</li>
     *   <li>Engine-specific node requirements (handled by SemanticValidator)</li>
     *   <li>IP format and overlaps (handled by SemanticValidator/PolicyValidator)</li>
     *   <li>CNI requirements per cluster type (handled by PolicyValidator)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any structural validation fails
     */
    public ClusterSpec {
        // Null checks for required fields
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(nodes, "nodes is required");
        Objects.requireNonNull(sizeProfile, "sizeProfile is required");
        Objects.requireNonNull(vms, "vms list is required (use List.of() for empty)");
        // firstIp, cni, podNetwork, svcNetwork are nullable (null means "use defaults")

        // Blank check (redundant if ClusterName already enforces non-blank, kept defensive)
        if (name.value().isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }

        // At least one node required for KUBEADM
        // (NONE allows zero masters+workers, they use "management" role)
        if (type instanceof Kubeadm && nodes.masters() == 0 && nodes.workers() == 0) {
            throw new IllegalArgumentException(
                    "KUBEADM cluster requires at least one node (masters + workers > 0)"
            );
        }

        // VMs list must not contain nulls
        if (vms.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("vms list contains null elements");
        }

        // Make defensive copy to ensure immutability
        vms = List.copyOf(vms);
    }

    public static class ClusterSpecBuilder {
        public ClusterSpecBuilder name(ClusterName name) {
            this.name = name;
            return this;
        }

        public ClusterSpecBuilder name(String name) {
            this.name = ClusterName.of(name);
            return this;
        }

        public ClusterSpecBuilder nodes(NodeTopology nodes) {
            this.nodes = nodes;
            return this;
        }

        public ClusterSpecBuilder nodes(int masters, int workers) {
            this.nodes = NodeTopology.of(masters, workers);
            return this;
        }

        /**
         * Convenience method to set firstIp from string.
         * Uses IPAddressString library for parsing and validation.
         *
         * @param ipString IP address string (e.g., "192.168.56.10"), or null
         * @return this builder
         * @throws IllegalArgumentException if IP format is invalid
         */
        public ClusterSpecBuilder firstIp(String ipString) {
            if (ipString == null || ipString.isBlank()) {
                this.firstIp = null;
                return this;
            }

            // Use strict parsing to avoid inet_aton-style permissive formats
            IPAddressStringParameters params = new IPAddressStringParameters.Builder()
                    .allow_inet_aton(false)
                    .toParams();
            IPAddressString ipAddr = new IPAddressString(ipString, params);
            if (!ipAddr.isValid()) {
                throw new IllegalArgumentException("Invalid IP address format: " + ipString);
            }

            this.firstIp = ipAddr.getAddress();
            return this;
        }

        /**
         * Convenience method to set firstIp from string.
         * Uses IPAddressString library for parsing and validation.
         *
         * @param ip IP address (e.g., "192.168.56.10"), or null
         * @return this builder
         * @throws IllegalArgumentException if IP format is invalid
         */
        public ClusterSpecBuilder firstIp(IPAddress ip) {
            this.firstIp = ip;
            return this;
        }

        /**
         * Convenience method to set podNetwork from CIDR string.
         * Uses NetworkCIDR for parsing and validation.
         *
         * @param cidr CIDR notation string (e.g., "10.244.0.0/16"), or null
         * @return this builder
         * @throws IllegalArgumentException if CIDR format is invalid
         */
        public ClusterSpecBuilder podNetwork(String cidr) {
            if (cidr == null || cidr.isBlank()) {
                this.podNetwork = null;
                return this;
            }

            this.podNetwork = NetworkCIDR.of(cidr);
            return this;
        }

        public ClusterSpecBuilder podNetwork(NetworkCIDR network) {
            this.podNetwork = network;
            return this;
        }

        /**
         * Convenience method to set svcNetwork from CIDR string.
         * Uses NetworkCIDR for parsing and validation.
         *
         * @param cidr CIDR notation string (e.g., "10.96.0.0/12"), or null
         * @return this builder
         * @throws IllegalArgumentException if CIDR format is invalid
         */
        public ClusterSpecBuilder svcNetwork(String cidr) {
            if (cidr == null || cidr.isBlank()) {
                this.svcNetwork = null;
                return this;
            }

            this.svcNetwork = NetworkCIDR.of(cidr);
            return this;
        }

        public ClusterSpecBuilder svcNetwork(NetworkCIDR network) {
            this.svcNetwork = network;
            return this;
        }
    }

    /**
     * Returns total node count (masters + workers).
     *
     * @return total nodes declared
     */
    public int totalNodes() {
        return nodes.masters() + nodes.workers();
    }

    /**
     * Checks if this cluster has explicit VM configurations.
     * Empty list means orchestrator should generate VMs from masters/workers counts.
     *
     * @return true if vms list is not empty
     */
    public boolean hasExplicitVms() {
        return !vms.isEmpty();
    }

    /**
     * Checks if this is a multi-master (HA) cluster.
     *
     * @return true if masters > 1
     */
    public boolean isHighAvailability() {
        return nodes.masters() > 1;
    }

    /**
     * Returns a new ClusterSpec with updated VMs list.
     * Used by orchestrator to set generated VMs.
     *
     * @param newVms new VMs list (must not be null)
     * @return new ClusterSpec with updated VMs
     * @throws IllegalArgumentException if newVms is null or contains nulls
     */
    public ClusterSpec withVms(List<VmConfig> newVms) {
        Objects.requireNonNull(newVms, "newVms cannot be null");
        return this.toBuilder().vms(newVms).build();
    }

    /**
     * Returns a new ClusterSpec with updated CNI type.
     * Used when applying defaults or overrides.
     *
     * @param newCni new CNI type (can be null)
     * @return new ClusterSpec with updated CNI
     */
    public ClusterSpec withCni(CniType newCni) {
        return this.toBuilder().cni(newCni).build();
    }
}
