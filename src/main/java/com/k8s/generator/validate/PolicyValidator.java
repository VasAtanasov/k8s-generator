package com.k8s.generator.validate;

import com.k8s.generator.model.*;

import java.util.*;

/**
 * Policy validator: enforces cross-cutting constraints and global topology rules.
 *
 * <p>Responsibility:
 * Validates constraints that span multiple clusters or require global context.
 * Operates on the entire topology rather than individual cluster specifications.
 *
 * <p>Validation Rules (Phase 2):
 * <ul>
 *   <li><b>CNI Requirements</b>: KUBEADM clusters MUST have CNI specified</li>
 *   <li><b>CNI Restrictions</b>: KIND/MINIKUBE/NONE clusters MUST NOT have CNI</li>
 *   <li><b>Master Node Requirements</b>: KUBEADM clusters MUST have masters >= 1</li>
 *   <li><b>Unique Cluster Names</b>: No two clusters can have the same name</li>
 *   <li><b>Total VM Limit</b>: Total VMs across all clusters should not exceed reasonable limits</li>
 *   <li><b>Naming Conflicts</b>: Detect potential hostname conflicts across clusters</li>
 * </ul>
 *
 * <p>Future Enhancements (Phase 3+):
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
public class PolicyValidator implements ClusterSpecValidator {

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
     * Validates policy constraints for a single cluster specification.
     *
     * <p>Single-Cluster Policy Rules (Phase 2):
     * <ul>
     *   <li>KUBEADM clusters MUST have CNI specified</li>
     *   <li>KIND/MINIKUBE clusters MUST NOT have CNI (they bundle their own)</li>
     *   <li>NONE (management) clusters MUST NOT have CNI (no Kubernetes)</li>
     *   <li>KUBEADM clusters MUST have masters >= 1</li>
     * </ul>
     *
     * @param cluster cluster specification to validate (must not be null)
     * @return ValidationResult with policy errors (empty if valid)
     * @throws NullPointerException if cluster is null
     */
    public ValidationResult validate(ClusterSpec cluster) {
        Objects.requireNonNull(cluster, "cluster cannot be null");

        var errors = new ArrayList<ValidationError>();

        validateCniRequirements(cluster, errors);
        validateKubeadmMasterRequirement(cluster, errors);

        return ValidationResult.of(errors);
    }

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
     * Validates CNI requirements based on cluster type.
     *
     * <p>Rules:
     * <ul>
     *   <li>KUBEADM: CNI MUST be specified (required for networking)</li>
     *   <li>KIND/MINIKUBE: CNI MUST NOT be specified (they bundle CNI)</li>
     *   <li>NONE: CNI MUST NOT be specified (no Kubernetes cluster)</li>
     * </ul>
     */
    private void validateCniRequirements(ClusterSpec cluster, List<ValidationError> errors) {
        switch (cluster.type()) {
            case KUBEADM -> {
                if (cluster.cni().isEmpty()) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].cni", cluster.name()),
                            ValidationLevel.POLICY,
                            "KUBEADM cluster requires CNI to be specified",
                            String.format(
                                    "Add CNI specification for cluster '%s'. " +
                                            "Supported CNI types: CALICO, FLANNEL, WEAVE, CILIUM, ANTREA. " +
                                            "Example: --cni calico",
                                    cluster.name()
                            )
                    ));
                }
            }
            case KIND, MINIKUBE -> {
                if (cluster.cni().isPresent()) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].cni", cluster.name()),
                            ValidationLevel.POLICY,
                            String.format(
                                    "%s cluster should not have CNI specified (uses bundled CNI)",
                                    cluster.type()
                            ),
                            String.format(
                                    "Remove CNI specification for %s cluster '%s'. " +
                                            "%s bundles its own CNI plugin.",
                                    cluster.type(), cluster.name(), cluster.type()
                            )
                    ));
                }
            }
            case NONE -> {
                if (cluster.cni().isPresent()) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].cni", cluster.name()),
                            ValidationLevel.POLICY,
                            "Management (NONE) cluster cannot have CNI (no Kubernetes cluster)",
                            String.format(
                                    "Remove CNI specification for management cluster '%s'. " +
                                            "Management VMs don't run Kubernetes clusters.",
                                    cluster.name()
                            )
                    ));
                }
            }
        }
    }

    /**
     * Validates that KUBEADM clusters have at least one master node.
     */
    private void validateKubeadmMasterRequirement(ClusterSpec cluster, List<ValidationError> errors) {
        if (cluster.type() == com.k8s.generator.model.ClusterType.KUBEADM && cluster.masters() < 1) {
            errors.add(new ValidationError(
                    String.format("clusters[name='%s'].masters", cluster.name()),
                    ValidationLevel.POLICY,
                    "KUBEADM cluster must have at least one master node",
                    String.format(
                            "Specify master count for kubeadm cluster '%s'. " +
                                    "Example: --nodes 1m,2w (1 master, 2 workers)",
                            cluster.name()
                    )
            ));
        }
    }

    /**
     * Validates that all cluster names are unique.
     */
    private void validateUniqueClusterNames(List<ClusterSpec> clusters, List<ValidationError> errors) {
        var names = new HashSet<ClusterName>();
        var duplicates = new HashSet<ClusterName>();

        for (ClusterSpec cluster : clusters) {
            if (!names.add(cluster.name())) {
                duplicates.add(cluster.name());
            }
        }

        for (ClusterName duplicate : duplicates) {
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

        // Warn if approaching limits (>= 80%)
        if (totalVms >= DEFAULT_MAX_TOTAL_VMS * 0.8) {
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
        var allVmNames = new HashSet<VmName>();
        var conflicts = new HashSet<VmName>();

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
                for (VmName name : predictedNames) {
                    if (!allVmNames.add(name)) {
                        conflicts.add(name);
                    }
                }
            }
        }

        for (VmName conflict : conflicts) {
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
     *
     * <p>Naming Convention:
     * <ul>
     *   <li>KIND/MINIKUBE: "{cluster-name}" (single VM named after cluster)</li>
     *   <li>NONE: "{cluster-name}" (management VM named after cluster)</li>
     *   <li>KUBEADM: "{cluster-name}-master-{n}", "{cluster-name}-worker-{n}"</li>
     * </ul>
     */
    private Set<VmName> predictVmNames(ClusterSpec cluster) {
        var names = new HashSet<VmName>();

        switch (cluster.type()) {
            case KIND, MINIKUBE, NONE -> {
                // Single VM: use cluster name directly
                names.add(VmName.of(cluster.name().value()));
            }
            case KUBEADM -> {
                // Multi-node: prefix with cluster name to avoid conflicts
                for (int i = 1; i <= cluster.masters(); i++) {
                    names.add(VmName.of(cluster.name().toString() + "-master-" + i));
                }
                for (int i = 1; i <= cluster.workers(); i++) {
                    names.add(VmName.of(cluster.name().toString() + "-worker-" + i));
                }
            }
        }

        return names;
    }
}
