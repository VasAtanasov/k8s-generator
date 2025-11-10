package com.k8s.generator.model;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

import java.util.Objects;

/**
 * Immutable network CIDR value object.
 *
 * <p>Represents a network address in CIDR notation (e.g., "10.244.0.0/16").
 * Encapsulates validation and ensures the address represents a network
 * (prefix block with zero host bits) rather than an individual IP address.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: Value is final and cannot be modified</li>
 *   <li><b>Validation</b>: Must have prefix length (e.g., /16, /12)</li>
 *   <li><b>Network Semantics</b>: Automatically converts to prefix block (zeros host bits)</li>
 *   <li><b>Type Safety</b>: Cannot accidentally use individual IP where network is expected</li>
 * </ul>
 *
 * <p>Use Cases:
 * <ul>
 *   <li><b>Pod Network CIDR</b>: Kubernetes pod network (default: 10.244.0.0/16)</li>
 *   <li><b>Service Network CIDR</b>: Kubernetes service network (default: 10.96.0.0/12)</li>
 *   <li><b>Overlap Detection</b>: Check if two networks overlap</li>
 *   <li><b>Containment Checks</b>: Check if IP is within network</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Parse CIDR notation
 * var podNetwork = NetworkCIDR.of("10.244.0.0/16");
 * System.out.println(podNetwork.toCIDRString());  // "10.244.0.0/16"
 * System.out.println(podNetwork.prefixLength());   // 16
 *
 * // Non-zero host is automatically converted to network
 * var network = NetworkCIDR.of("10.244.3.4/16");
 * System.out.println(network.toCIDRString());  // "10.244.0.0/16" (host bits zeroed)
 *
 * // Default networks
 * var defaultPod = NetworkCIDR.defaultPodNetwork();  // 10.244.0.0/16
 * var defaultSvc = NetworkCIDR.defaultServiceNetwork();  // 10.96.0.0/12
 *
 * // Overlap detection
 * var net1 = NetworkCIDR.of("10.244.0.0/16");
 * var net2 = NetworkCIDR.of("10.245.0.0/16");
 * boolean overlaps = net1.overlaps(net2);  // false
 * }</pre>
 *
 * @param value the IPAddress representing the network (with prefix length)
 * @see IPAddress
 * @see ClusterSpec
 * @since 1.0.0
 */
public record NetworkCIDR(IPAddress value) {

    /**
     * Default pod network CIDR for Kubernetes clusters.
     * Standard default used by kubeadm and most CNI plugins.
     */
    public static final String DEFAULT_POD_NETWORK = "10.244.0.0/16";

    /**
     * Default service network CIDR for Kubernetes clusters.
     * Standard default used by kubeadm.
     */
    public static final String DEFAULT_SERVICE_NETWORK = "10.96.0.0/12";

    /**
     * Compact constructor with validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>Value is non-null</li>
     *   <li>Value has prefix length (is a CIDR, not just an IP)</li>
     *   <li>Converts to prefix block (ensures host bits are zero)</li>
     * </ul>
     *
     * @throws IllegalArgumentException if value is null or lacks prefix length
     */
    public NetworkCIDR {
        Objects.requireNonNull(value, "network value is required");

        if (!value.isPrefixed()) {
            throw new IllegalArgumentException(
                    "Network must have prefix length (e.g., 10.244.0.0/16), got: " + value.toCanonicalString()
            );
        }

        // Ensure it's a network (zero host bits) by converting to prefix block
        // e.g., "10.244.3.4/16" becomes "10.244.0.0/16"
        value = value.toPrefixBlock();
    }

    /**
     * Creates a NetworkCIDR from a CIDR string.
     *
     * @param cidr CIDR notation string (e.g., "10.244.0.0/16")
     * @return NetworkCIDR instance
     * @throws IllegalArgumentException if CIDR format is invalid or lacks prefix
     */
    public static NetworkCIDR of(String cidr) {
        if (cidr == null || cidr.isBlank()) {
            throw new IllegalArgumentException("CIDR cannot be blank");
        }

        IPAddressString ipAddrString = new IPAddressString(cidr);
        if (!ipAddrString.isValid()) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr);
        }

        IPAddress address = ipAddrString.getAddress();
        if (!address.isPrefixed()) {
            throw new IllegalArgumentException(
                    "CIDR must include prefix length (e.g., /16, /12), got: " + cidr
            );
        }

        return new NetworkCIDR(address);
    }

    /**
     * Returns the default pod network for Kubernetes clusters.
     *
     * @return NetworkCIDR representing 10.244.0.0/16
     */
    public static NetworkCIDR defaultPodNetwork() {
        return of(DEFAULT_POD_NETWORK);
    }

    /**
     * Returns the default service network for Kubernetes clusters.
     *
     * @return NetworkCIDR representing 10.96.0.0/12
     */
    public static NetworkCIDR defaultServiceNetwork() {
        return of(DEFAULT_SERVICE_NETWORK);
    }

    /**
     * Returns the prefix length (number of network bits).
     *
     * @return prefix length (e.g., 16 for /16, 12 for /12)
     */
    public int prefixLength() {
        // isPrefixed() check in constructor ensures this is never null
        return value.getNetworkPrefixLength();
    }

    /**
     * Returns the CIDR notation string (e.g., "10.244.0.0/16").
     *
     * @return CIDR string representation
     */
    public String toCIDRString() {
        return value.toCanonicalString();
    }

    /**
     * Returns the network mask for this CIDR.
     *
     * @return network mask (e.g., "255.255.0.0" for /16)
     */
    public IPAddress getNetworkMask() {
        return value.getNetworkMask();
    }

    /**
     * Checks if this network overlaps (intersects) with another network.
     *
     * <p>For CIDR prefix blocks, intersection means either containment or equality.
     * If two blocks intersect, they are either the same or one contains the other.
     *
     * @param other the other network to check
     * @return true if networks overlap
     */
    public boolean overlaps(NetworkCIDR other) {
        Objects.requireNonNull(other, "other network cannot be null");
        return value.intersect(other.value) != null;
    }

    /**
     * Checks if this network contains the given IP address.
     *
     * @param ip the IP address to check
     * @return true if IP is within this network
     */
    public boolean contains(IPAddress ip) {
        Objects.requireNonNull(ip, "ip cannot be null");
        return value.contains(ip);
    }

    /**
     * Checks if this network is IPv4.
     *
     * @return true if IPv4 network
     */
    public boolean isIPv4() {
        return value.isIPv4();
    }

    /**
     * Checks if this network is IPv6.
     *
     * @return true if IPv6 network
     */
    public boolean isIPv6() {
        return value.isIPv6();
    }

    /**
     * Returns the total number of addresses in this network.
     *
     * @return address count (e.g., 65536 for /16)
     */
    public long getAddressCount() {
        return value.getCount().longValue();
    }

    @Override
    public String toString() {
        return toCIDRString();
    }
}
