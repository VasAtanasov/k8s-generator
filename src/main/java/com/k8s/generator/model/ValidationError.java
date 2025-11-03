package com.k8s.generator.model;

import java.util.Objects;

/**
 * Immutable structured validation error with actionable suggestions.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: All fields final, no setters</li>
 *   <li><b>Non-null</b>: All fields required (enforced in compact constructor)</li>
 *   <li><b>Structured</b>: Field path for precise error location</li>
 *   <li><b>Actionable</b>: Always includes suggestion for fixing the error</li>
 * </ul>
 *
 * <p>Field Path Format:
 * <pre>
 * clusters[].name                          # Cluster name field (single cluster)
 * clusters[name='staging'].firstIp         # Specific cluster's firstIp
 * clusters[name='prod'].masters            # Specific cluster's master count
 * topology.global.baseCidr                 # Global topology setting
 * </pre>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var error = new ValidationError(
 *     "clusters[name='staging'].firstIp",
 *     ValidationLevel.SEMANTIC,
 *     "Multi-cluster configuration requires explicit firstIp for each cluster",
 *     "Add: firstIp: 192.168.56.20 (non-overlapping with other clusters)"
 * );
 *
 * System.err.printf("[%s] %s: %s%n  Suggestion: %s%n",
 *     error.level(), error.field(), error.message(), error.suggestion());
 * }</pre>
 *
 * @param field Field path identifying error location (e.g., "clusters[].name")
 * @param level Validation layer that detected this error
 * @param message Human-readable error description
 * @param suggestion Actionable guidance for fixing the error
 *
 * @see ValidationLevel
 * @see com.k8s.generator.validate.ValidationResult
 * @since 1.0.0
 */
public record ValidationError(
    String field,
    ValidationLevel level,
    String message,
    String suggestion
) {
    /**
     * Compact constructor with structural validation.
     * Ensures all fields are non-null and non-blank.
     *
     * @throws IllegalArgumentException if any field is null or blank
     */
    public ValidationError {
        Objects.requireNonNull(field, "field is required");
        Objects.requireNonNull(level, "level is required");
        Objects.requireNonNull(message, "message is required");
        Objects.requireNonNull(suggestion, "suggestion is required");

        if (field.isBlank()) {
            throw new IllegalArgumentException("field cannot be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
        if (suggestion.isBlank()) {
            throw new IllegalArgumentException("suggestion cannot be blank");
        }
    }

    /**
     * Formats this error as a user-friendly string.
     * Includes field path, level, message, and suggestion.
     *
     * @return formatted error message
     */
    public String format() {
        return String.format("[%s] %s: %s%n  Suggestion: %s",
            level, field, message, suggestion);
    }

    @Override
    public String toString() {
        return format();
    }
}