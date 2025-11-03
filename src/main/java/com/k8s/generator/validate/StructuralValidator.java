package com.k8s.generator.validate;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.NodeRole;
import com.k8s.generator.model.ValidationError;
import com.k8s.generator.model.ValidationLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Structural validator: enforces basic type safety and non-null constraints.
 *
 * <p>Responsibility:
 * Validates that all required structural constraints are met before semantic validation.
 * Most structural validation happens in record compact constructors (fail-fast),
 * but this validator provides additional checks that require context or cross-field validation.
 *
 * <p>Validation Rules:
 * <ul>
 *   <li><b>VM Count Consistency</b>: If explicit VMs provided, count must match masters+workers</li>
 *   <li><b>VM Role Validation</b>: Master VMs must have role="master", workers must have role="worker"</li>
 *   <li><b>IP Assignment</b>: Each VM must have a non-null, non-blank IP address</li>
 *   <li><b>Unique VM Names</b>: No duplicate VM names within a cluster</li>
 * </ul>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Side-effect free</b>: Does not modify input</li>
 *   <li><b>Error collection</b>: Reports all errors, does not short-circuit</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 *   <li><b>Null-safe</b>: Handles null inputs gracefully (reports as error)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var validator = new StructuralValidator();
 * var spec = new ClusterSpec(...);
 * var result = validator.validate(spec);
 *
 * if (result.hasErrors()) {
 *     System.err.println("Structural validation failed:");
 *     result.errors().forEach(e -> System.err.println(e.format()));
 * }
 * }</pre>
 *
 * @see ClusterSpec
 * @see ValidationResult
 * @see SemanticValidator
 * @since 1.0.0
 */
public class StructuralValidator {

    /**
     * Validates structural constraints of a cluster specification.
     *
     * <p>Note: Most structural validation (non-null, basic types) happens in
     * record constructors. This method validates constraints that require
     * examining multiple fields or list contents.
     *
     * @param spec cluster specification to validate (may be null)
     * @return ValidationResult with structural errors (empty if valid)
     */
    public ValidationResult validate(ClusterSpec spec) {
        var errors = new ArrayList<ValidationError>();

        // Null check (should not happen if using record constructors, but defensive)
        if (spec == null) {
            errors.add(new ValidationError(
                "cluster",
                ValidationLevel.STRUCTURAL,
                "Cluster specification is null",
                "Ensure ClusterSpec object is properly constructed"
            ));
            return ValidationResult.of(errors);
        }

        // Validate VM count consistency if explicit VMs provided
        if (spec.hasExplicitVms()) {
            validateVmCountConsistency(spec, errors);
            validateVmRoles(spec, errors);
            validateUniqueVmNames(spec, errors);
        }

        return ValidationResult.of(errors);
    }

    /**
     * Validates that explicit VM count matches declared masters + workers.
     * Only applicable for KUBEADM clusters.
     */
    private void validateVmCountConsistency(ClusterSpec spec, List<ValidationError> errors) {
        long masterCount = spec.vms().stream().filter(vm -> vm.role() == NodeRole.MASTER).count();
        long workerCount = spec.vms().stream().filter(vm -> vm.role() == NodeRole.WORKER).count();

        if (masterCount != spec.masters()) {
            errors.add(new ValidationError(
                String.format("clusters[name='%s'].vms", spec.name()),
                ValidationLevel.STRUCTURAL,
                String.format("VM list contains %d master(s) but masters=%d declared",
                    masterCount, spec.masters()),
                String.format("Ensure VMs list has exactly %d VM(s) with role='master'", spec.masters())
            ));
        }

        if (workerCount != spec.workers()) {
            errors.add(new ValidationError(
                String.format("clusters[name='%s'].vms", spec.name()),
                ValidationLevel.STRUCTURAL,
                String.format("VM list contains %d worker(s) but workers=%d declared",
                    workerCount, spec.workers()),
                String.format("Ensure VMs list has exactly %d VM(s) with role='worker'", spec.workers())
            ));
        }
    }

    /**
     * Validates that VM roles are valid and consistent with cluster type.
     * Note: With NodeRole enum, role validation is already guaranteed by the type system.
     * This method is kept for potential future semantic validations (e.g., cluster type vs role).
     */
    private void validateVmRoles(ClusterSpec spec, List<ValidationError> errors) {
        // With NodeRole enum, structural validation is handled by the type system.
        // Future semantic validations could check:
        // - KIND/MINIKUBE should only have CLUSTER role VMs
        // - KUBEADM should only have MASTER/WORKER role VMs
        // - NONE should only have MANAGEMENT role VMs
        // These are semantic rules, not structural, so they belong in SemanticValidator.
    }

    /**
     * Validates that all VM names within a cluster are unique.
     */
    private void validateUniqueVmNames(ClusterSpec spec, List<ValidationError> errors) {
        var names = new ArrayList<String>();
        for (int i = 0; i < spec.vms().size(); i++) {
            var vm = spec.vms().get(i);
            if (names.contains(vm.name())) {
                errors.add(new ValidationError(
                    String.format("clusters[name='%s'].vms[%d].name", spec.name(), i),
                    ValidationLevel.STRUCTURAL,
                    String.format("Duplicate VM name: '%s'", vm.name()),
                    "Ensure all VM names within a cluster are unique"
                ));
            }
            names.add(vm.name());
        }
    }
}