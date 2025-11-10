package com.k8s.generator.validate;

import com.k8s.generator.model.ClusterSpec;

import java.util.List;

public interface ClusterSpecValidator {
    /**
     * Validates semantic constraints of a cluster specification.
     *
     * @param cluster cluster specification to validate (must not be null)
     * @return ValidationResult with semantic errors (empty if valid)
     * @throws NullPointerException if spec is null
     */
    ValidationResult validate(ClusterSpec cluster);

    /**
     * Validates semantic constraints of multi cluster specification.
     *
     * @param clusters cluster specification to validate (must not be null)
     * @return ValidationResult with semantic errors (empty if valid)
     * @throws NullPointerException if spec is null
     */
    ValidationResult validate(List<ClusterSpec> clusters);
}
