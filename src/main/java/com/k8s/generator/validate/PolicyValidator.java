package com.k8s.generator.validate;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.ValidationError;
import com.k8s.generator.model.ValidationLevel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Policy validator: enforces cross-cutting constraints and global topology rules.
 *
 * <p>Responsibility:
 * Validates constraints that span multiple clusters or require global context.
 * Operates on the entire topology rather than individual cluster specifications.
 *
 * <p>Validation Rules (Phase 1 - Basic):
 * <ul>
 *   <li><b>Unique Cluster Names</b>: No two clusters can have the same name</li>
 *   <li><b>Total VM Limit</b>: Total VMs across all clusters should not exceed reasonable limits</li>
 *   <li><b>Naming Conflicts</b>: Detect potential hostname conflicts across clusters</li>
 * </ul>
 *
 * <p>Future Enhancements (Phase 2+):
 * <ul>
 *   <li><b>IP Overlap Detection</b>: Validate no IP ranges overlap between clusters</li>
 *   <li><b>Resource Limits</b>: Validate total CPU/memory allocation is reasonable</li>
 *   <li><b>Network Topology</b>: Validate CIDR ranges and subnet allocations</li>
 * </ul>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Side-effect free</b>: Does not modify input</li>
 *   <li><b>Error collection</b>: Reports all errors, does not short-circuit</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var validator = new PolicyValidator();
 * var clusters = List.of(cluster1, cluster2, cluster3);
 * var result = validator.validate(clusters);
 *
 * if (result.hasErrors()) {
 *     System.err.println("Policy validation failed:");
 *     result.errors().forEach(e -> System.err.println(e.format()));
 * }
 * }</pre>
 *
 * @see ClusterSpec
 * @see ValidationResult
 * @see StructuralValidator
 * @see SemanticValidator
 * @since 1.0.0
 */
public class PolicyValidator {

    /**
     * Default maximum total VMs allowed across all clusters.
     * Conservative limit for local development environments.
     */
    private static final int DEFAULT_MAX_TOTAL_VMS = 50;

    /**
     * Maximum reasonable VMs per cluster.
     */
    private static final int MAX_VMS_PER_CLUSTER = 20;

    /**
     * Validates policy constraints across multiple cluster specifications.
     *
     * @param clusters list of cluster specifications to validate (must not be null)
     * @return ValidationResult with policy errors (empty if valid)
     * @throws NullPointerException if clusters is null
     */
    public ValidationResult validate(List<ClusterSpec> clusters) {
        Objects.requireNonNull(clusters, "clusters cannot be null");

        var errors = new ArrayList<ValidationError>();

        if (clusters.isEmpty()) {
            // No clusters to validate - this is valid
            return ValidationResult.valid();
        }

        validateUniqueClusterNames(clusters, errors);
        validateTotalVmLimit(clusters, errors);
        validatePerClusterVmLimit(clusters, errors);
        validateGlobalNamingConflicts(clusters, errors);

        return ValidationResult.of(errors);
    }

    /**
     * Validates that all cluster names are unique.
     */
    private void validateUniqueClusterNames(List<ClusterSpec> clusters, List<ValidationError> errors) {
        var names = new HashSet<String>();
        var duplicates = new HashSet<String>();

        for (ClusterSpec cluster : clusters) {
            if (!names.add(cluster.name())) {
                duplicates.add(cluster.name());
            }
        }

        for (String duplicate : duplicates) {
            errors.add(new ValidationError(
                "clusters[].name",
                ValidationLevel.POLICY,
                String.format("Duplicate cluster name: '%s'", duplicate),
                "Ensure all cluster names are unique across the topology"
            ));
        }
    }

    /**
     * Validates that total VM count across all clusters is reasonable.
     */
    private void validateTotalVmLimit(List<ClusterSpec> clusters, List<ValidationError> errors) {
        int totalVms = clusters.stream()
            .mapToInt(this::calculateExpectedVmCount)
            .sum();

        if (totalVms > DEFAULT_MAX_TOTAL_VMS) {
            errors.add(new ValidationError(
                "topology.global",
                ValidationLevel.POLICY,
                String.format("Total VM count exceeds recommended limit: %d VMs (limit: %d)",
                    totalVms, DEFAULT_MAX_TOTAL_VMS),
                String.format("Consider reducing cluster count or node counts (current: %d clusters, %d total VMs)",
                    clusters.size(), totalVms)
            ));
        }

        // Warn if approaching limits
        if (totalVms > DEFAULT_MAX_TOTAL_VMS * 0.8) {
            errors.add(new ValidationError(
                "topology.global",
                ValidationLevel.POLICY,
                String.format("Total VM count approaching limit: %d VMs (80%% of limit: %d)",
                    totalVms, DEFAULT_MAX_TOTAL_VMS),
                "Monitor system resources. Consider using smaller size profiles or fewer nodes."
            ));
        }
    }

    /**
     * Validates that individual clusters don't exceed per-cluster VM limits.
     */
    private void validatePerClusterVmLimit(List<ClusterSpec> clusters, List<ValidationError> errors) {
        for (ClusterSpec cluster : clusters) {
            int vmCount = calculateExpectedVmCount(cluster);
            if (vmCount > MAX_VMS_PER_CLUSTER) {
                errors.add(new ValidationError(
                    String.format("clusters[name='%s']", cluster.name()),
                    ValidationLevel.POLICY,
                    String.format("Cluster '%s' exceeds recommended VM limit: %d VMs (limit: %d)",
                        cluster.name(), vmCount, MAX_VMS_PER_CLUSTER),
                    "Consider splitting into multiple smaller clusters or reducing node counts"
                ));
            }
        }
    }

    /**
     * Validates that VM names don't conflict across clusters.
     * This is a soft warning since clusters are typically isolated.
     */
    private void validateGlobalNamingConflicts(List<ClusterSpec> clusters, List<ValidationError> errors) {
        var allVmNames = new HashSet<String>();
        var conflicts = new HashSet<String>();

        for (ClusterSpec cluster : clusters) {
            // For clusters with explicit VMs, check those names
            if (cluster.hasExplicitVms()) {
                for (var vm : cluster.vms()) {
                    if (!allVmNames.add(vm.name())) {
                        conflicts.add(vm.name());
                    }
                }
            } else {
                // For clusters without explicit VMs, check predicted names
                // (This is best-effort - actual names generated by orchestrator)
                var predictedNames = predictVmNames(cluster);
                for (String name : predictedNames) {
                    if (!allVmNames.add(name)) {
                        conflicts.add(name);
                    }
                }
            }
        }

        for (String conflict : conflicts) {
            errors.add(new ValidationError(
                "topology.global.vmNames",
                ValidationLevel.POLICY,
                String.format("VM name conflict detected: '%s' used in multiple clusters", conflict),
                "Consider using cluster-specific VM naming (e.g., '{cluster-name}-master-1')"
            ));
        }
    }

    /**
     * Calculates expected VM count for a cluster.
     * Accounts for different cluster types and their VM generation patterns.
     */
    private int calculateExpectedVmCount(ClusterSpec cluster) {
        if (cluster.hasExplicitVms()) {
            return cluster.vms().size();
        }

        return switch (cluster.type()) {
            case KIND, MINIKUBE, NONE -> 1;  // Single VM
            case KUBEADM -> cluster.masters() + cluster.workers();
        };
    }

    /**
     * Predicts VM names that will be generated for a cluster.
     * Used for detecting potential naming conflicts before generation.
     */
    private Set<String> predictVmNames(ClusterSpec cluster) {
        var names = new HashSet<String>();

        switch (cluster.type()) {
            case KIND, MINIKUBE -> names.add("cluster-1");
            case NONE -> names.add("management-1");
            case KUBEADM -> {
                for (int i = 1; i <= cluster.masters(); i++) {
                    names.add("master-" + i);
                }
                for (int i = 1; i <= cluster.workers(); i++) {
                    names.add("worker-" + i);
                }
            }
        }

        return names;
    }
}
