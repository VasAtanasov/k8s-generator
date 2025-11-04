package com.k8s.generator.ip;

import com.k8s.generator.model.ClusterSpec;

import java.util.List;
import java.util.Map;

/**
 * Allocates IP addresses for cluster VMs using sequential allocation strategy.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Sequential allocation</b>: IPs assigned incrementally from base address</li>
 *   <li><b>Reserved IPs skipped</b>: .1 (gateway), .2 (DNS), .5 (management) not assigned</li>
 *   <li><b>Subnet boundary checks</b>: Validates IPs don't exceed .254 (broadcast reserved)</li>
 *   <li><b>Single-cluster default</b>: Uses 192.168.56.10 as base if firstIp not specified</li>
 *   <li><b>Multi-cluster requirement</b>: Requires explicit firstIp for each cluster</li>
 *   <li><b>Overlap detection</b>: Multi-cluster mode validates no IP ranges overlap</li>
 * </ul>
 *
 * <p>Allocation Strategy:
 * <ol>
 *   <li>Determine base IP (firstIp or default 192.168.56.10)</li>
 *   <li>Calculate VM count based on cluster type:
 *       <ul>
 *         <li>KIND/MINIKUBE/NONE: 1 VM</li>
 *         <li>KUBEADM: masters + workers VMs</li>
 *       </ul>
 *   </li>
 *   <li>Allocate sequential IPs starting from base, skipping reserved addresses</li>
 *   <li>Validate all IPs within subnet boundary (≤ .254)</li>
 * </ol>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Single-cluster allocation (default IP)
 * var kindCluster = new ClusterSpec(
 *     "dev", ClusterType.KIND,
 *     Optional.empty(),  // Uses default 192.168.56.10
 *     0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
 * );
 * var allocator = new SequentialIpAllocator();
 * var result = allocator.allocate(kindCluster);
 * // result = Success(["192.168.56.10"])
 *
 * // Multi-node kubeadm cluster
 * var kubeadmCluster = new ClusterSpec(
 *     "staging", ClusterType.KUBEADM,
 *     Optional.of("192.168.56.20"),
 *     1, 2,  // 1 master, 2 workers
 *     SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO)
 * );
 * result = allocator.allocate(kubeadmCluster);
 * // result = Success(["192.168.56.20", "192.168.56.21", "192.168.56.22"])
 *
 * // Multi-cluster allocation with overlap detection
 * var clusters = List.of(cluster1, cluster2);
 * var multiResult = allocator.allocateMulti(clusters);
 * // multiResult = Success({
 * //     "cluster1": ["192.168.56.10"],
 * //     "cluster2": ["192.168.56.30"]
 * // })
 * // Or Failure("IP range overlap detected: cluster1 and cluster2")
 * }</pre>
 *
 * @see SequentialIpAllocator
 * @see ClusterSpec
 * @since 1.0.0 (Phase 2)
 */
public interface IpAllocator {

    /**
     * Allocates IP addresses for a single cluster.
     *
     * <p>Allocation Rules:
     * <ul>
     *   <li>If firstIp is present: use as base address</li>
     *   <li>If firstIp is empty: use default 192.168.56.10</li>
     *   <li>Allocate sequential IPs based on cluster type</li>
     *   <li>Skip reserved IPs: .1, .2, .5</li>
     *   <li>Validate all IPs ≤ .254 (subnet boundary)</li>
     * </ul>
     *
     * @param spec cluster specification (must not be null)
     * @return Result containing list of allocated IPs, or error message if allocation fails
     * @throws NullPointerException if spec is null
     * @see ClusterSpec
     */
    Result<List<String>, String> allocate(ClusterSpec spec);

    /**
     * Allocates IP addresses for multiple clusters with overlap detection.
     *
     * <p>Multi-Cluster Rules:
     * <ul>
     *   <li>Each cluster MUST have explicit firstIp (no defaults in multi-cluster mode)</li>
     *   <li>Validates no IP ranges overlap between clusters</li>
     *   <li>Returns map of cluster name → allocated IPs</li>
     *   <li>Fails if any validation error occurs (fail-fast)</li>
     * </ul>
     *
     * <p>Overlap Detection:
     * <pre>
     * cluster1: firstIp=192.168.56.10, 3 VMs → [.10, .11, .12]
     * cluster2: firstIp=192.168.56.12, 2 VMs → [.12, .13]
     * Result: Failure("IP overlap: 192.168.56.12 used by cluster1 and cluster2")
     * </pre>
     *
     * @param clusters list of cluster specifications (must not be null or empty)
     * @return Result containing map of cluster name to IP list, or error message
     * @throws NullPointerException if clusters is null
     */
    Result<Map<String, List<String>>, String> allocateMulti(List<ClusterSpec> clusters);

    /**
     * Result type for operations that can succeed or fail.
     *
     * <p>This is a sealed interface with two implementations:
     * <ul>
     *   <li><b>Success</b>: Contains the successfully allocated value</li>
     *   <li><b>Failure</b>: Contains an error message explaining the failure</li>
     * </ul>
     *
     * @param <T> success value type
     * @param <E> error value type
     */
    sealed interface Result<T, E> permits Result.Success, Result.Failure {

        /**
         * Success result containing allocated IPs.
         *
         * @param value the successfully allocated IPs
         */
        record Success<T, E>(T value) implements Result<T, E> {}

        /**
         * Failure result containing error message.
         *
         * @param error the error message
         */
        record Failure<T, E>(E error) implements Result<T, E> {}

        /**
         * Creates a success result.
         *
         * @param value the success value
         * @return Success result
         */
        static <T, E> Result<T, E> success(T value) {
            return new Success<>(value);
        }

        /**
         * Creates a failure result.
         *
         * @param error the error message
         * @return Failure result
         */
        static <T, E> Result<T, E> failure(E error) {
            return new Failure<>(error);
        }

        /**
         * Checks if this result is a success.
         *
         * @return true if Success, false if Failure
         */
        default boolean isSuccess() {
            return this instanceof Success;
        }

        /**
         * Checks if this result is a failure.
         *
         * @return true if Failure, false if Success
         */
        default boolean isFailure() {
            return this instanceof Failure;
        }

        /**
         * Returns the value if success, or throws exception if failure.
         *
         * @return the success value
         * @throws RuntimeException if this is a Failure
         */
        default T orElseThrow() {
            return switch (this) {
                case Success<T, E> s -> s.value();
                case Failure<T, E> f -> throw new RuntimeException("Operation failed: " + f.error());
            };
        }

        /**
         * Returns the value if success, or the default value if failure.
         *
         * @param defaultValue value to return on failure
         * @return the success value or default
         */
        default T orElse(T defaultValue) {
            return switch (this) {
                case Success<T, E> s -> s.value();
                case Failure<T, E> f -> defaultValue;
            };
        }

        /**
         * Returns the error if failure, or null if success.
         *
         * @return the error value or null
         */
        default E getError() {
            return switch (this) {
                case Success<T, E> s -> null;
                case Failure<T, E> f -> f.error();
            };
        }
    }
}
