package com.k8s.generator.ip;

import com.k8s.generator.model.Kind;
import com.k8s.generator.model.Minikube;
import com.k8s.generator.model.Kubeadm;
import com.k8s.generator.model.NoneCluster;

import com.k8s.generator.model.ClusterName;
import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.ClusterType;
import com.k8s.generator.model.Result;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.util.*;

/**
 * Sequential IP allocator using ipaddress library for validation and arithmetic.
 *
 * <p>Implementation Details:
 * <ul>
 *   <li><b>Base IP</b>: 192.168.56.10 (if not specified in ClusterSpec)</li>
 *   <li><b>Reserved IPs</b>: .1 (gateway), .2 (DNS), .5 (management) - never assigned</li>
 *   <li><b>Subnet boundary</b>: IPs must be ≤ .254 (255 is broadcast)</li>
 *   <li><b>Validation</b>: Uses ipaddress library for IP parsing and validation</li>
 * </ul>
 *
 * <p>Allocation Algorithm:
 * <pre>
 * 1. Determine base IP from ClusterSpec.firstIp() or use default
 * 2. Calculate VM count:
 *    - KIND/MINIKUBE/NONE: 1 VM
 *    - KUBEADM: masters + workers VMs
 * 3. Generate sequential IPs starting from base:
 *    - Increment IP address for each VM
 *    - Skip reserved IPs (.1, .2, .5)
 *    - Validate each IP ≤ .254
 * 4. Return allocated IPs or error message
 * </pre>
 *
 * <p>Example Allocations:
 * <pre>
 * // Single KIND cluster (default IP)
 * ClusterSpec: KIND, firstIp=empty
 * Result: ["192.168.56.10"]
 *
 * // Kubeadm 1m,2w (explicit IP)
 * ClusterSpec: KUBEADM, firstIp=192.168.56.20, masters=1, workers=2
 * Result: ["192.168.56.20", "192.168.56.21", "192.168.56.22"]
 *
 * // Edge case: Skip reserved .5
 * ClusterSpec: KIND, firstIp=192.168.56.4
 * Result: ["192.168.56.4"]  // .5 would be skipped if needed
 *
 * // Boundary violation
 * ClusterSpec: KUBEADM, firstIp=192.168.56.253, masters=3, workers=0
 * Result: Failure("IP allocation exceeds subnet boundary...")
 * </pre>
 *
 * @see IpAllocator
 * @see ClusterSpec
 * @since 1.0.0 (Phase 2)
 */
public class SequentialIpAllocator implements IpAllocator {

    /**
     * Default base IP for single-cluster mode when firstIp not specified.
     */
    private static final IPAddress DEFAULT_BASE_IP = new IPAddressString("192.168.56.10").getAddress();

    /**
     * Default base IP for management (NONE) clusters when firstIp is not specified.
     */
    private static final IPAddress MGMT_DEFAULT_BASE_IP = new IPAddressString("192.168.56.5").getAddress();

    /**
     * Reserved IP addresses that should never be allocated.
     * These are typically: .1 (gateway), .2 (DNS), .5 (management placeholder).
     */
    private static final Set<Integer> RESERVED_HOST_IDS = Set.of(1, 2, 5);

    /**
     * Maximum host ID in /24 subnet (255 is broadcast, 254 is last usable).
     */
    private static final int MAX_HOST_ID = 254;

    /**
     * Allocates IP addresses for a single cluster.
     *
     * @param spec cluster specification
     * @return Result with allocated IPs or error message
     */
    @Override
    public Result<List<IPAddress>, String> allocate(ClusterSpec spec) {
        Objects.requireNonNull(spec, "spec cannot be null");

        // 1. Determine base IP (mgmt has reserved default .5)
        IPAddress ipAddress = spec.firstIp() != null
                ? spec.firstIp()
                : (spec.type() instanceof NoneCluster ? MGMT_DEFAULT_BASE_IP : DEFAULT_BASE_IP);

        // Guard: allocator supports IPv4-only semantics (last-octet arithmetic, /24 boundary)
        if (!ipAddress.isIPv4()) {
            return Result.failure("Only IPv4 is supported for allocation: " + ipAddress.toCanonicalString());
        }

        // 3. Calculate VM count based on cluster type
        int vmCount = calculateVmCount(spec);

        // 4. Allocate sequential IPs (skip reserved .5 except for mgmt)
        Set<Integer> reserved = (spec.type() instanceof NoneCluster)
                ? Set.of(1, 2)
                : RESERVED_HOST_IDS;
        return allocateSequential(ipAddress, vmCount, spec.name().toString(), reserved);
    }

    /**
     * Allocates IPs for multiple clusters with overlap detection.
     *
     * @param clusters list of cluster specifications
     * @return Result with map of cluster name to IPs, or error
     */
    @Override
    public Result<Map<ClusterName, List<IPAddress>>, String> allocateMulti(List<ClusterSpec> clusters) {
        Objects.requireNonNull(clusters, "clusters cannot be null");

        if (clusters.isEmpty()) {
            return Result.success(Map.of());
        }

        // Phase 2: Multi-cluster requires explicit firstIp for each cluster
        // This validation is also done by SemanticValidator, but we check here too
        for (ClusterSpec cluster : clusters) {
            if (cluster.firstIp() == null) {
                return Result.failure(
                        String.format(
                                "Multi-cluster configuration requires explicit firstIp for cluster '%s'",
                                cluster.name()
                        )
                );
            }
        }

        // Allocate IPs for each cluster
        var allocations = new LinkedHashMap<ClusterName, List<IPAddress>>();
        var allAllocatedIps = new HashSet<IPAddress>();

        for (ClusterSpec cluster : clusters) {
            var result = allocate(cluster);
            if (result.isFailure()) {
                return Result.failure(
                        String.format("Failed to allocate IPs for cluster '%s': %s",
                                cluster.name(), result.getError())
                );
            }

            List<IPAddress> ips = result.orElseThrow();

            // Check for overlaps
            for (IPAddress ip : ips) {
                if (allAllocatedIps.contains(ip)) {
                    return Result.failure(
                            String.format(
                                    "IP address overlap detected: '%s' is used by multiple clusters",
                                    ip
                            )
                    );
                }
                allAllocatedIps.add(ip);
            }

            allocations.put(cluster.name(), ips);
        }

        return Result.success(allocations);
    }

    /**
     * Calculates expected VM count for a cluster based on its type.
     *
     * @param spec cluster specification
     * @return number of VMs to allocate IPs for
     */
    private int calculateVmCount(ClusterSpec spec) {
        return switch (spec.type()) {
            case Kind k -> 1;
            case Minikube m -> 1;
            case NoneCluster nc -> 1;
            case Kubeadm ku -> spec.masters() + spec.workers();
        };
    }

    /**
     * Allocates sequential IPs starting from base address.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Parse base IP using ipaddress library</li>
     *   <li>Extract host ID (last octet)</li>
     *   <li>Generate count sequential IPs, skipping reserved IDs</li>
     *   <li>Validate each IP ≤ .254 (subnet boundary)</li>
     *   <li>Return list of IP strings or error</li>
     * </ol>
     *
     * @param baseIp      starting IP address (e.g., "192.168.56.10")
     * @param count       number of IPs to allocate
     * @param clusterName cluster name (for error messages)
     * @return Result with IP list or error message
     */
    private Result<List<IPAddress>, String> allocateSequential(final IPAddress baseIp,
                                                               final int count,
                                                               final String clusterName,
                                                               final Set<Integer> reservedHostIds) {
        if (baseIp == null) {
            return Result.failure("Invalid IP address");
        }

        var allocatedIps = new ArrayList<IPAddress>();
        IPAddress current = baseIp;

        int allocated = 0;
        int attempts = 0;
        int maxAttempts = count + reservedHostIds.size() + 10; // Safety limit

        while (allocated < count && attempts < maxAttempts) {
            attempts++;

            // Extract last octet (host ID)
            int hostId = getLastOctet(current);

            // Check subnet boundary
            if (hostId > MAX_HOST_ID) {
                return Result.failure(
                        String.format(
                                "IP allocation for cluster '%s' exceeds subnet boundary (last octet > 254). " +
                                        "Started at %s, needed %d IPs, reached %s",
                                clusterName, baseIp, count, current.toCanonicalString()
                        )
                );
            }

            // Skip reserved IPs (except when caller excluded some, e.g., mgmt .5)
            if (!reservedHostIds.contains(hostId)) {
                allocatedIps.add(current);
                allocated++;
            }

            // Increment to next IP
            current = current.increment(1);
        }

        if (allocated < count) {
            return Result.failure(
                    String.format(
                            "Failed to allocate %d IPs for cluster '%s' (only allocated %d)",
                            count, clusterName, allocated
                    )
            );
        }

        return Result.success(allocatedIps);
    }

    /**
     * Extracts the last octet (host ID) from an IP address.
     *
     * @param ipAddress the IP address
     * @return last octet value (0-255)
     */
    private int getLastOctet(IPAddress ipAddress) {
        var bytes = ipAddress.getBytes();
        // For IPv4, bytes.length is 4, last octet is at index 3
        // Need to convert to unsigned int (byte is signed in Java)
        return bytes[bytes.length - 1] & 0xFF;
    }
}
