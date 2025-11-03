package com.k8s.generator.model;

/**
 * Three-layer validation hierarchy for error classification.
 *
 * <p>Validation Strategy:
 * <ol>
 *   <li><b>STRUCTURAL</b>: Basic type safety, non-null constraints, range checks
 *       <br>Enforced in: Record compact constructors, primitive validation
 *       <br>Examples: non-null fields, positive integers, enum validity</li>
 *   <li><b>SEMANTIC</b>: Business rules, cross-field validation, naming conventions
 *       <br>Enforced in: SemanticValidator
 *       <br>Examples: cluster name format, master count for kubeadm, IP format</li>
 *   <li><b>POLICY</b>: Cross-cutting constraints, global topology rules
 *       <br>Enforced in: PolicyValidator
 *       <br>Examples: IP overlap detection, total VM limits, naming conflicts</li>
 * </ol>
 *
 * <p>Error Severity (implicit):
 * <ul>
 *   <li><b>STRUCTURAL</b>: Critical - prevents object creation (throws immediately)</li>
 *   <li><b>SEMANTIC</b>: High - invalid configuration (collected, fails validation)</li>
 *   <li><b>POLICY</b>: High - cross-cluster conflicts (collected, fails validation)</li>
 * </ul>
 *
 * <p>This hierarchy ensures validation errors are:
 * <ul>
 *   <li>Categorized for clear error messages</li>
 *   <li>Enforced at appropriate layers (fail-fast for structural, collection for semantic/policy)</li>
 *   <li>Traceable to specific validation rules</li>
 * </ul>
 *
 * @see ValidationError
 * @see com.k8s.generator.validate.StructuralValidator
 * @see com.k8s.generator.validate.SemanticValidator
 * @see com.k8s.generator.validate.PolicyValidator
 * @since 1.0.0
 */
public enum ValidationLevel {
    /**
     * Structural validation: type safety, non-null, basic constraints.
     * Enforced in record constructors, throws IllegalArgumentException immediately.
     */
    STRUCTURAL,

    /**
     * Semantic validation: business rules, cross-field logic, format validation.
     * Enforced in SemanticValidator, errors collected and reported together.
     */
    SEMANTIC,

    /**
     * Policy validation: cross-cutting rules, topology constraints, global limits.
     * Enforced in PolicyValidator, errors collected and reported together.
     */
    POLICY
}