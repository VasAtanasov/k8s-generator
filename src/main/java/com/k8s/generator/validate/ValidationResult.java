package com.k8s.generator.validate;

import com.k8s.generator.model.ValidationError;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Immutable validation result containing aggregated errors.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: Errors list is unmodifiable</li>
 *   <li><b>Non-null</b>: Errors list never null (empty list for valid results)</li>
 *   <li><b>Error Aggregation</b>: Collects all errors from validation layers</li>
 *   <li><b>Fail-Fast Detection</b>: hasErrors() provides quick validation check</li>
 * </ul>
 *
 * <p>Usage Patterns:
 * <pre>{@code
 * // 1. Single validator
 * ValidationResult result = semanticValidator.validate(spec, false);
 * if (result.hasErrors()) {
 *     result.errors().forEach(error -> System.err.println(error.format()));
 *     return;
 * }
 *
 * // 2. Combining multiple validators
 * ValidationResult combined = ValidationResult.combine(
 *     structuralValidator.validate(spec),
 *     semanticValidator.validate(spec, false),
 *     policyValidator.validate(List.of(spec))
 * );
 *
 * // 3. Conditional execution based on validation
 * var result = validator.validate(spec);
 * if (result.isValid()) {
 *     // proceed with generation
 * } else {
 *     throw new ValidationException(result.formatErrors());
 * }
 * }</pre>
 *
 * @see ValidationError
 * @see StructuralValidator
 * @see SemanticValidator
 * @see PolicyValidator
 * @since 1.0.0
 */
public final class ValidationResult {
    private final List<ValidationError> errors;

    /**
     * Private constructor - use factory methods.
     *
     * @param errors list of validation errors (will be copied)
     */
    private ValidationResult(List<ValidationError> errors) {
        Objects.requireNonNull(errors, "errors list cannot be null");
        if (errors.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("errors list contains null elements");
        }
        this.errors = List.copyOf(errors);  // Defensive copy
    }

    /**
     * Creates a ValidationResult with no errors (valid result).
     *
     * @return ValidationResult with empty error list
     */
    public static ValidationResult valid() {
        return new ValidationResult(List.of());
    }

    /**
     * Creates a ValidationResult with a single error.
     *
     * @param error the validation error
     * @return ValidationResult containing the error
     * @throws NullPointerException if error is null
     */
    public static ValidationResult of(ValidationError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new ValidationResult(List.of(error));
    }

    /**
     * Creates a ValidationResult with multiple errors.
     *
     * @param errors list of validation errors
     * @return ValidationResult containing all errors
     * @throws NullPointerException if errors is null or contains nulls
     */
    public static ValidationResult of(List<ValidationError> errors) {
        return new ValidationResult(errors);
    }

    /**
     * Combines multiple ValidationResults into a single result.
     * All errors from all results are aggregated.
     *
     * @param results variable number of ValidationResults to combine
     * @return combined ValidationResult with all errors
     * @throws NullPointerException if results is null or contains nulls
     */
    public static ValidationResult combine(ValidationResult... results) {
        Objects.requireNonNull(results, "results cannot be null");

        var allErrors = new ArrayList<ValidationError>();
        for (ValidationResult result : results) {
            Objects.requireNonNull(result, "result cannot be null");
            allErrors.addAll(result.errors());
        }

        return new ValidationResult(allErrors);
    }

    /**
     * Checks if this result has any errors.
     *
     * @return true if errors list is not empty
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Checks if this result is valid (no errors).
     *
     * @return true if errors list is empty
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Returns the unmodifiable list of validation errors.
     *
     * @return list of errors (empty if valid)
     */
    public List<ValidationError> errors() {
        return errors;
    }

    /**
     * Returns the number of validation errors.
     *
     * @return error count (0 if valid)
     */
    public int errorCount() {
        return errors.size();
    }

    /**
     * Formats all errors as a single string for display.
     * Each error is formatted on a separate line with its suggestion.
     *
     * @return formatted error messages, empty string if no errors
     */
    public String formatErrors() {
        if (errors.isEmpty()) {
            return "";
        }
        return errors.stream()
            .map(ValidationError::format)
            .collect(Collectors.joining("\n\n"));
    }

    /**
     * Adds a new error to this result, returning a new ValidationResult.
     * This result remains unchanged (immutability).
     *
     * @param error error to add
     * @return new ValidationResult with added error
     * @throws NullPointerException if error is null
     */
    public ValidationResult withError(ValidationError error) {
        Objects.requireNonNull(error, "error cannot be null");
        var newErrors = new ArrayList<>(errors);
        newErrors.add(error);
        return new ValidationResult(newErrors);
    }

    /**
     * Combines this result with another, returning a new ValidationResult.
     * This result remains unchanged (immutability).
     *
     * @param other other ValidationResult to combine with
     * @return new ValidationResult with errors from both results
     * @throws NullPointerException if other is null
     */
    public ValidationResult merge(ValidationResult other) {
        Objects.requireNonNull(other, "other cannot be null");
        var newErrors = new ArrayList<>(errors);
        newErrors.addAll(other.errors());
        return new ValidationResult(newErrors);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return Objects.equals(errors, that.errors);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errors);
    }

    @Override
    public String toString() {
        if (errors.isEmpty()) {
            return "ValidationResult{valid}";
        }
        return String.format("ValidationResult{%d error(s)}:%n%s",
            errors.size(), formatErrors());
    }
}