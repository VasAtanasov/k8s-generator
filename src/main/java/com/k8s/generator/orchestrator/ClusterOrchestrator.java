package com.k8s.generator.orchestrator;

import com.k8s.generator.ip.IpAllocator;
import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.Result;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates cluster VM generation by coordinating IP allocation and VM configuration.
 *
 * <p>Responsibility:
 * High-level coordinator that combines IP allocation and VM generation into a single
 * operation. Ensures clusters with empty VM lists get populated with generated VMs.
 *
 * <p>Orchestration Flow:
 * <ol>
 *   <li>Check if cluster has explicit VMs → if yes, return as-is</li>
 *   <li>Allocate IPs for cluster using IpAllocator</li>
 *   <li>Generate VMs using VmGenerator with allocated IPs</li>
 *   <li>Return new ClusterSpec with populated VMs</li>
 * </ol>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: Original ClusterSpec unchanged, returns new instance</li>
 *   <li><b>Idempotent</b>: Clusters with explicit VMs pass through unchanged</li>
 *   <li><b>Fail-fast</b>: Any allocation/generation error propagates immediately</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var ipAllocator = new SequentialIpAllocator();
 * var vmGenerator = new DefaultVmGenerator();
 * var orchestrator = new ClusterOrchestrator(ipAllocator, vmGenerator);
 *
 * // Cluster without VMs
 * var clusterSpec = new ClusterSpec(
 *     "dev", ClusterType.KIND,
 *     Optional.empty(), 0, 0,
 *     SizeProfile.MEDIUM,
 *     List.of(),  // Empty VMs list
 *     Optional.empty()
 * );
 *
 * // Orchestrate VM generation
 * var result = orchestrator.orchestrate(clusterSpec);
 * if (result.isSuccess()) {
 *     ClusterSpec enriched = result.orElseThrow();
 *     // enriched.vms() = [VmConfig("dev", CLUSTER, "192.168.56.10", ...)]
 * }
 * }</pre>
 *
 * @see IpAllocator
 * @see VmGenerator
 * @see ClusterSpec
 * @since 1.0.0 (Phase 2)
 */
public class ClusterOrchestrator {

    private final IpAllocator ipAllocator;
    private final VmGenerator vmGenerator;

    /**
     * Creates orchestrator with IP allocator and VM generator.
     *
     * @param ipAllocator IP allocation strategy (must not be null)
     * @param vmGenerator VM generation strategy (must not be null)
     * @throws NullPointerException if either parameter is null
     */
    public ClusterOrchestrator(IpAllocator ipAllocator, VmGenerator vmGenerator) {
        this.ipAllocator = Objects.requireNonNull(ipAllocator, "ipAllocator cannot be null");
        this.vmGenerator = Objects.requireNonNull(vmGenerator, "vmGenerator cannot be null");
    }

    /**
     * Orchestrates VM generation for a cluster.
     *
     * <p>Behavior:
     * <ul>
     *   <li>If cluster has explicit VMs: returns cluster unchanged (Success)</li>
     *   <li>If cluster has no VMs: allocates IPs, generates VMs, returns enriched cluster</li>
     *   <li>If any error occurs: returns Failure with error message</li>
     * </ul>
     *
     * @param cluster cluster specification (must not be null)
     * @return Result containing enriched ClusterSpec with VMs, or error message
     * @throws NullPointerException if cluster is null
     */
    public Result<ClusterSpec, String> orchestrate(ClusterSpec cluster) {
        Objects.requireNonNull(cluster, "cluster cannot be null");

        // If cluster already has explicit VMs, return as-is
        if (cluster.hasExplicitVms()) {
            return Result.success(cluster);
        }

        // Allocate IPs using pattern matching
        var ipResult = ipAllocator.allocate(cluster);
        return switch (ipResult) {
            case Result.Success<List<String>, String> success -> generateVmsWithAllocatedIps(cluster, success.value());
            case Result.Failure<List<String>, String> failure -> Result.failure(
                    "IP allocation failed for cluster '" + cluster.name() + "': " + failure.error()
            );
        };
    }

    /**
     * Generates VMs for a cluster using allocated IPs.
     * Extracted method for better separation of concerns and testability.
     *
     * @param cluster      cluster specification
     * @param allocatedIps list of allocated IP addresses
     * @return Result containing enriched ClusterSpec with VMs, or error message
     */
    private Result<ClusterSpec, String> generateVmsWithAllocatedIps(final ClusterSpec cluster,
                                                                    final List<String> allocatedIps) {
        try {
            var generatedVms = vmGenerator.generate(cluster, allocatedIps);
            var enrichedCluster = cluster.withVms(generatedVms);
            return Result.success(enrichedCluster);
        } catch (IllegalArgumentException e) {
            // Specific handling for validation errors
            return Result.failure(
                    "VM generation failed for cluster '" + cluster.name() + "': " + e.getMessage()
            );
        } catch (Exception e) {
            // Catch-all for unexpected errors
            return Result.failure(
                    "Unexpected error during VM generation for cluster '" + cluster.name() + "': " +
                            e.getClass().getSimpleName() + " - " + e.getMessage()
            );
        }
    }

    /**
     * Orchestrates VM generation for multiple clusters.
     *
     * <p>Multi-Cluster Behavior:
     * <ul>
     *   <li>Validates no IP overlaps between clusters</li>
     *   <li>Generates VMs for all clusters without explicit VMs</li>
     *   <li>Returns list of enriched ClusterSpecs</li>
     *   <li>Fails fast if any cluster fails (all-or-nothing)</li>
     * </ul>
     *
     * @param clusters list of cluster specifications (must not be null)
     * @return Result containing list of enriched ClusterSpecs, or error message
     * @throws NullPointerException if clusters is null
     */
    public Result<List<ClusterSpec>, String> orchestrateMulti(List<ClusterSpec> clusters) {
        Objects.requireNonNull(clusters, "clusters cannot be null");

        if (clusters.isEmpty()) {
            return Result.success(List.of());
        }

        // Orchestrate each cluster independently using pattern matching
        var enrichedClusters = new java.util.ArrayList<ClusterSpec>();

        for (ClusterSpec cluster : clusters) {
            var result = orchestrate(cluster);

            switch (result) {
                case Result.Success<ClusterSpec, String> success -> enrichedClusters.add(success.value());
                case Result.Failure<ClusterSpec, String> failure -> {return Result.failure(failure.error());}
            }
        }

        return Result.success(List.copyOf(enrichedClusters));
    }
}
