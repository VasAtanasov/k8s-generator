package com.k8s.generator.validate;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.ClusterType;
import com.k8s.generator.model.ValidationError;
import com.k8s.generator.model.ValidationLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Semantic validator: enforces business rules and cross-field constraints.
 *
 * <p>Responsibility:
 * Validates that cluster specifications conform to business rules and
 * domain constraints beyond basic structural validation.
 *
 * <p>Validation Rules:
 * <ul>
 *   <li><b>Cluster Name Format</b>: Must match [a-z][a-z0-9-]* (lowercase, alphanumeric, hyphens)</li>
 *   <li><b>Engine-Specific Constraints</b>:
 *       <ul>
 *         <li>KIND/MINIKUBE/NONE: masters=0, workers=0 (single VM with special role)</li>
 *         <li>KUBEADM: masters≥1, workers≥0 (multi-node cluster)</li>
 *       </ul>
 *   </li>
 *   <li><b>IP Address Format</b>: firstIp must be valid IPv4 (basic format check)</li>
 *   <li><b>Multi-Cluster IP Requirement</b>: firstIp required for each cluster in multi-cluster setups</li>
 *   <li><b>High Availability</b>: HA clusters (masters>1) should have odd number of masters</li>
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
 * var validator = new SemanticValidator();
 * var spec = new ClusterSpec("staging", ClusterType.KUBEADM, ...);
 * var result = validator.validate(spec, true);  // isMultiCluster=true
 *
 * if (result.hasErrors()) {
 *     System.err.println("Semantic validation failed:");
 *     result.errors().forEach(e -> System.err.println(e.format()));
 * }
 * }</pre>
 *
 * @see ClusterSpec
 * @see ValidationResult
 * @see StructuralValidator
 * @see PolicyValidator
 * @since 1.0.0
 */
public class SemanticValidator implements ClusterSpecValidator {

    /**
     * Regex pattern for valid cluster names (follows Kubernetes DNS-1123 label convention).
     * Format: [a-z]([a-z0-9-]*[a-z0-9])?
     * Rules:
     * - Must start with lowercase letter
     * - Must end with lowercase letter or digit (not hyphen)
     * - May contain lowercase letters, digits, and hyphens in between
     * - Single character names are allowed (e.g., "a")
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z]([a-z0-9-]*[a-z0-9])?$");

    /**
     * Regex pattern for basic IPv4 validation.
     * Format: xxx.xxx.xxx.xxx where xxx is 0-255
     */
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    @Override
    public ValidationResult validate(ClusterSpec spec) {
        Objects.requireNonNull(spec, "spec cannot be null");

        var errors = new ArrayList<ValidationError>();

        validateClusterName(spec, errors);
        validateEngineSpecificConstraints(spec, errors);
        validateIpAddressFormat(spec, errors);
        validateHighAvailabilityConfiguration(spec, errors);

        return ValidationResult.of(errors);
    }

    @Override
    public ValidationResult validate(List<ClusterSpec> specs) {
        var errors = new ArrayList<ValidationError>();
        boolean isMultiCluster = specs.size() > 1;
        for (ClusterSpec spec : specs) {
            var result = validate(spec, isMultiCluster);
            errors.addAll(result.errors());
        }

        return ValidationResult.of(errors);
    }

    /**
     * Validates semantic constraints of a cluster specification.
     *
     * @param spec           cluster specification to validate (must not be null)
     * @param isMultiCluster true if this cluster is part of a multi-cluster topology
     * @return ValidationResult with semantic errors (empty if valid)
     * @throws NullPointerException if spec is null
     */
    private ValidationResult validate(ClusterSpec spec, boolean isMultiCluster) {
        Objects.requireNonNull(spec, "spec cannot be null");

        var errors = new ArrayList<ValidationError>();

        validateClusterName(spec, errors);
        validateEngineSpecificConstraints(spec, errors);
        validateIpAddressFormat(spec, errors);
        validateMultiClusterIpRequirement(spec, isMultiCluster, errors);
        validateHighAvailabilityConfiguration(spec, errors);

        return ValidationResult.of(errors);
    }

    /**
     * Validates cluster name format.
     * Must match pattern: [a-z][a-z0-9-]*
     */
    private void validateClusterName(ClusterSpec spec, List<ValidationError> errors) {
        if (!NAME_PATTERN.matcher(spec.name()).matches()) {
            errors.add(new ValidationError(
                    "clusters[].name",
                    ValidationLevel.SEMANTIC,
                    String.format("Invalid cluster name: '%s'", spec.name()),
                    "Name must match pattern [a-z][a-z0-9-]* (e.g., 'dev', 'staging', 'prod-1')"
            ));
        }

        // Additional constraint: name should not be too long (practical limit)
        if (spec.name().length() > 63) {
            errors.add(new ValidationError(
                    "clusters[].name",
                    ValidationLevel.SEMANTIC,
                    String.format("Cluster name too long: '%s' (%d characters)", spec.name(), spec.name().length()),
                    "Cluster name should be 63 characters or less (Kubernetes label limit)"
            ));
        }
    }

    /**
     * Validates engine-specific node count constraints.
     */
    private void validateEngineSpecificConstraints(ClusterSpec spec, List<ValidationError> errors) {
        switch (spec.type()) {
            case KIND, MINIKUBE, NONE -> {
                // These engines should have zero masters and workers (they use "cluster" or "management" role)
                if (spec.masters() != 0) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].masters", spec.name()),
                            ValidationLevel.SEMANTIC,
                            String.format("%s clusters do not use master nodes (uses '%s' role instead)",
                                    spec.type(), spec.type() == ClusterType.NONE ? "management" : "cluster"),
                            String.format("Set masters: 0 (not %d)", spec.masters())
                    ));
                }
                if (spec.workers() != 0) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].workers", spec.name()),
                            ValidationLevel.SEMANTIC,
                            String.format("%s clusters do not use worker nodes (uses '%s' role instead)",
                                    spec.type(), spec.type() == ClusterType.NONE ? "management" : "cluster"),
                            String.format("Set workers: 0 (not %d)", spec.workers())
                    ));
                }
            }
            case KUBEADM -> {
                // KUBEADM requires at least 1 master
                if (spec.masters() < 1) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].masters", spec.name()),
                            ValidationLevel.SEMANTIC,
                            "KUBEADM clusters require at least 1 master node",
                            "Set masters: 1 (or more for HA)"
                    ));
                }
                // Workers are optional but should be reasonable
                if (spec.workers() > 100) {
                    errors.add(new ValidationError(
                            String.format("clusters[name='%s'].workers", spec.name()),
                            ValidationLevel.SEMANTIC,
                            String.format("Unusually high worker count: %d", spec.workers()),
                            "Consider reducing worker count for local development (typically 1-10)"
                    ));
                }
            }
        }
    }

    /**
     * Validates IP address format if firstIp is provided.
     */
    private void validateIpAddressFormat(ClusterSpec spec, List<ValidationError> errors) {
        spec.firstIp().ifPresent(ip -> {
            if (!IPV4_PATTERN.matcher(ip).matches()) {
                errors.add(new ValidationError(
                        String.format("clusters[name='%s'].firstIp", spec.name()),
                        ValidationLevel.SEMANTIC,
                        String.format("Invalid IPv4 address format: '%s'", ip),
                        "Use format: xxx.xxx.xxx.xxx (e.g., '192.168.56.10')"
                ));
            }

            // Warn about reserved/special IPs
            if (ip.startsWith("0.") || ip.startsWith("127.") || ip.startsWith("255.")) {
                errors.add(new ValidationError(
                        String.format("clusters[name='%s'].firstIp", spec.name()),
                        ValidationLevel.SEMANTIC,
                        String.format("Invalid IP address range: '%s'", ip),
                        "Use private network ranges: 192.168.x.x, 172.16-31.x.x, or 10.x.x.x"
                ));
            }
        });
    }

    /**
     * Validates that multi-cluster setups have explicit firstIp for each cluster.
     */
    private void validateMultiClusterIpRequirement(ClusterSpec spec, boolean isMultiCluster, List<ValidationError> errors) {
        if (isMultiCluster && spec.firstIp().isEmpty()) {
            errors.add(new ValidationError(
                    String.format("clusters[name='%s'].firstIp", spec.name()),
                    ValidationLevel.SEMANTIC,
                    "Multi-cluster configuration requires explicit firstIp for each cluster",
                    String.format("Add: firstIp: 192.168.56.X (non-overlapping with other clusters)")
            ));
        }
    }

    /**
     * Validates high availability configuration.
     * HA clusters (masters > 1) should have odd number of masters for etcd quorum.
     */
    private void validateHighAvailabilityConfiguration(ClusterSpec spec, List<ValidationError> errors) {
        if (spec.isHighAvailability() && spec.masters() % 2 == 0) {
            errors.add(new ValidationError(
                    String.format("clusters[name='%s'].masters", spec.name()),
                    ValidationLevel.SEMANTIC,
                    String.format("HA cluster has even number of masters: %d", spec.masters()),
                    String.format("Use odd number of masters for etcd quorum (e.g., %d or %d)",
                            spec.masters() - 1, spec.masters() + 1)
            ));
        }

        // Warn if HA cluster has very high master count
        if (spec.masters() > 7) {
            errors.add(new ValidationError(
                    String.format("clusters[name='%s'].masters", spec.name()),
                    ValidationLevel.SEMANTIC,
                    String.format("Unusually high master count for HA: %d", spec.masters()),
                    "Typical HA configurations use 3 or 5 masters. Consider reducing for local development."
            ));
        }
    }
}
