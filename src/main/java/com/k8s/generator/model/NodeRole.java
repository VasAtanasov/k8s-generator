package com.k8s.generator.model;

/**
 * Node roles in the generated Kubernetes learning environment.
 *
 * <p>Role Semantics:
 * <ul>
 *   <li><b>MANAGEMENT</b>: Management/bastion VM (no cluster, just tools)
 *       <br>- Used with ClusterType.NONE
 *       <br>- Tools: kubectl, helm, kubectx, kubens, etc.
 *       <br>- Can aggregate kubeconfigs from multiple clusters</li>
 *   <li><b>MASTER</b>: Control plane node (kubeadm)
 *       <br>- Used with ClusterType.KUBEADM
 *       <br>- Runs kube-apiserver, etcd, scheduler, controller-manager</li>
 *   <li><b>WORKER</b>: Worker node (kubeadm)
 *       <br>- Used with ClusterType.KUBEADM
 *       <br>- Runs workloads, joins cluster via kubeadm join</li>
 * </ul>
 *
 * <p>Role Assignment Rules:
 * <pre>{@code
 * ClusterType.NONE      → NodeRole.MANAGEMENT (1 VM)
 * ClusterType.KUBEADM   → NodeRole.MASTER (1+ VMs) + NodeRole.WORKER (0+ VMs)
 * }</pre>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Management VM
 * var mgmtVm = new VmConfig(
 *     "mgmt",
 *     "192.168.56.5",
 *     NodeRole.MANAGEMENT,
 *     2,
 *     2048,
 *     "ubuntu/jammy64"
 * );
 *
 * // Kubeadm multi-node cluster
 * var master = new VmConfig("master-1", "192.168.56.20", NodeRole.MASTER, 2, 2048, "ubuntu/jammy64");
 * var worker = new VmConfig("worker-1", "192.168.56.21", NodeRole.WORKER, 2, 2048, "ubuntu/jammy64");
 * }</pre>
 *
 * @see ClusterType
 * @see VmConfig
 * @since 1.0.0
 */
public enum NodeRole {
    /**
     * Management/bastion VM with no cluster, just Kubernetes tools.
     * Used for multi-cluster management, learning exercises, or Azure CLI work.
     */
    MANAGEMENT,

    /**
     * Single-node cluster VM.
     * Entire cluster runs on this one VM.
     */
    CLUSTER,

    /**
     * Kubeadm control plane node.
     * Runs kube-apiserver, etcd, scheduler, controller-manager.
     */
    MASTER,

    /**
     * Kubeadm worker node.
     * Runs workloads, joins cluster via kubeadm join.
     */
    WORKER
}
