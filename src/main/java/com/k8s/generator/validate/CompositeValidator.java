package com.k8s.generator.validate;

import com.k8s.generator.model.ClusterSpec;
import com.k8s.generator.model.ValidationError;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates all three validators (Structural + Semantic + Policy) into a single validation pipeline.
 *
 * <p>Three-Layer Validation Strategy:
 * <ol>
 *   <li><b>Structural Validation</b>: Non-null fields, basic type constraints
 *       <br>- Performed in record compact constructors
 *       <br>- Validates individual field constraints</li>
 *   <li><b>Semantic Validation</b>: Business rules and cross-field validation
 *       <br>- Cluster name format, engine-specific requirements
 *       <br>- IP format validation, multi-cluster IP requirements</li>
 *   <li><b>Policy Validation</b>: Cross-cutting constraints
 *       <br>- CNI requirements per cluster type
 *       <br>- Resource limits, global topology rules</li>
 * </ol>
 *
 * <p>Validation Flow:
 * <pre>
 * 1. Run Structural validation
 * 2. If structural passes → run Semantic validation
 * 3. Always run Policy validation (independent of previous layers)
 * 4. Aggregate all errors and return ValidationResult
 * </pre>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Side-effect free</b>: Does not modify input</li>
 *   <li><b>Error collection</b>: Reports all errors, does not short-circuit</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 *   <li><b>Fail-fast</b>: Semantic validation skipped if structural fails</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var validator = new CompositeValidator();
 * var cluster = new ClusterSpec(
 *     "staging",
 *     ClusterType.KUBEADM,
 *     Optional.of("192.168.56.20"),
 *     1, 2,
 *     SizeProfile.MEDIUM,
 *     List.of(),
 *     Optional.empty()  // Missing CNI - should fail policy validation
 * );
 *
 * var result = validator.validate(cluster, false);
 * if (result.hasErrors()) {
 *     System.err.println("Validation failed:");
 *     result.errors().forEach(e -> System.err.println(e.format()));
 * }
 * // Output:
 * // [POLICY] clusters[name='staging'].cni: KUBEADM cluster requires CNI to be specified
 * }</pre>
 *
 * @see StructuralValidator
 * @see SemanticValidator
 * @see PolicyValidator
 * @see ValidationResult
 * @since 1.0.0 (Phase 2)
 */
public class CompositeValidator implements ClusterSpecValidator {
    private final StructuralValidator structural;
    private final SemanticValidator semantic;
    private final PolicyValidator policy;

    /**
     * Creates CompositeValidator with default validator implementations.
     */
    public CompositeValidator() {
        this(new StructuralValidator(), new SemanticValidator(), new PolicyValidator());
    }

    /**
     * Creates CompositeValidator with custom validator implementations (for testing).
     *
     * @param structural structural validator
     * @param semantic   semantic validator
     * @param policy     policy validator
     */
    public CompositeValidator(StructuralValidator structural,
                              SemanticValidator semantic,
                              PolicyValidator policy) {
        this.structural = Objects.requireNonNull(structural, "structural validator is required");
        this.semantic = Objects.requireNonNull(semantic, "semantic validator is required");
        this.policy = Objects.requireNonNull(policy, "policy validator is required");
    }

    @Override
    public ValidationResult validate(ClusterSpec spec) {
        Objects.requireNonNull(spec, "spec cannot be null");

        // Layer 1: Structural validation
        var structuralResult = structural.validate(spec);
        var errors = new ArrayList<>(structuralResult.errors());

        // Layer 2: Semantic validation (only if structural passed)
        // This prevents cascading errors from invalid structure
        if (structuralResult.isValid()) {
            var semanticResult = semantic.validate(spec);
            errors.addAll(semanticResult.errors());
        }

        // Layer 3: Policy validation (always run - independent of other layers)
        // Policy rules like CNI requirements can be checked regardless of structural/semantic errors
        var policyResult = policy.validate(spec);
        errors.addAll(policyResult.errors());

        return ValidationResult.of(errors);
    }

    @Override
    public ValidationResult validate(List<ClusterSpec> clusters) {
        Objects.requireNonNull(clusters, "clusters cannot be null");

        var errors = new ArrayList<ValidationError>();

        // Validate each cluster individually
        for (ClusterSpec cluster : clusters) {
            var result = validate(cluster);
            errors.addAll(result.errors());
        }

        // Run topology-level policy validation
        var topologyResult = policy.validate(clusters);
        errors.addAll(topologyResult.errors());

        return ValidationResult.of(errors);
    }
}
