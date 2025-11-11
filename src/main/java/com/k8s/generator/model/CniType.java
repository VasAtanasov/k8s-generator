package com.k8s.generator.model;

/**
 * Container Network Interface (CNI) plugin types for Kubernetes clusters.
 *
 * <p>CNI plugins provide networking for Kubernetes pods, enabling pod-to-pod
 * communication across nodes. Each CNI has different features and performance
 * characteristics:
 *
 * <ul>
 *   <li><b>CALICO</b>: L3 networking with BGP routing, network policies, eBPF dataplane</li>
 *   <li><b>FLANNEL</b>: Simple overlay network using VXLAN or host-gw, lightweight</li>
 *   <li><b>WEAVE</b>: Mesh network with encryption, automatic network discovery</li>
 *   <li><b>CILIUM</b>: eBPF-based networking, advanced observability, service mesh</li>
 *   <li><b>ANTREA</b>: VMware-developed, Open vSwitch-based, Kubernetes-native</li>
 * </ul>
 *
 * <p>Usage Context:
 * <ul>
 *   <li><b>KUBEADM clusters</b>: Require CNI to be explicitly specified</li>
 *   <li><b>NONE (management)</b>: Should NOT have CNI (no Kubernetes cluster)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Kubeadm cluster with Calico
 * var cluster = new ClusterSpec(
 *     "prod",
 *     ClusterType.KUBEADM,
 *     Optional.of("192.168.56.20"),
 *     3, 5,
 *     SizeProfile.LARGE,
 *     List.of(),
 *     Optional.of(CniType.CALICO)
 * );
 *
 * // Management cluster (no CNI specified)
 * var mgmtCluster = new ClusterSpec(
 *     "dev",
 *     ClusterType.NONE,
 *     Optional.empty(),
 *     0, 0,
 *     SizeProfile.MEDIUM,
 *     List.of(),
 *     Optional.empty()  // No CNI for NONE
 * );
 * }</pre>
 *
 * @see ClusterSpec
 * @see ClusterType
 * @since 1.0.0 (Phase 2)
 */
public enum CniType {
    /**
     * Calico: L3 networking with BGP routing and network policies.
     * Most popular production CNI with eBPF dataplane support.
     */
    CALICO,

    /**
     * Flannel: Simple VXLAN/host-gw overlay network.
     * Lightweight and easy to configure, good for learning environments.
     */
    FLANNEL,

    /**
     * Weave: Mesh network with built-in encryption.
     * Automatic network discovery, suitable for multi-cloud deployments.
     */
    WEAVE,

    /**
     * Cilium: eBPF-based networking and security.
     * Advanced observability, service mesh capabilities, high performance.
     */
    CILIUM,

    /**
     * Antrea: VMware-developed CNI using Open vSwitch.
     * Kubernetes-native with advanced network policy and observability features.
     */
    ANTREA
}
