package com.k8s.generator.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Module metadata - identifies the learning module and type.
 *
 * <p>Used to generate consistent naming across all generated artifacts:
 * <ul>
 *   <li>Output directory: {@code <type>-<num>/} (e.g., "pt-m1/", "exam-prep-m7/")</li>
 *   <li>Cluster names: {@code clu-<num>-<type>-<engine>} (e.g., "clu-m1-pt-kind")</li>
 *   <li>Namespaces: {@code ns-<num>-<type>} (e.g., "ns-m1-pt")</li>
 * </ul>
 *
 * <p>Validation Rules:
 * <ul>
 *   <li><b>num</b>: Must match pattern {@code m\d+} (e.g., "m1", "m7", "m12")</li>
 *   <li><b>type</b>: Must match pattern {@code [a-z][a-z0-9-]*} (lowercase, alphanumeric + hyphens)</li>
 * </ul>
 *
 * <p>Common Types:
 * <ul>
 *   <li>{@code pt} - Practice task</li>
 *   <li>{@code hw} - Homework assignment</li>
 *   <li>{@code exam} - Exam environment</li>
 *   <li>{@code exam-prep} - Exam preparation</li>
 *   <li>{@code demo} - Demonstration/tutorial</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Practice task for module 1
 * var practice = new ModuleInfo("m1", "pt");
 * // → Output dir: "pt-m1/"
 * // → Cluster: "clu-m1-pt-kind"
 * // → Namespace: "ns-m1-pt"
 *
 * // Exam prep for module 7
 * var examPrep = new ModuleInfo("m7", "exam-prep");
 * // → Output dir: "exam-prep-m7/"
 * // → Cluster: "clu-m7-exam-prep-kubeadm"
 * // → Namespace: "ns-m7-exam-prep"
 * }</pre>
 *
 * @param num  Module number (must match "m\d+", e.g., "m1", "m7")
 * @param type Module type (must match "[a-z][a-z0-9-]*", e.g., "pt", "exam-prep")
 * @see GeneratorSpec
 * @since 1.0.0
 */
public record ModuleInfo(String num, String type) {
    /**
     * Pattern for valid module numbers: m1, m7, m12, etc.
     */
    private static final Pattern MODULE_NUM_PATTERN = Pattern.compile("m\\d+");

    /**
     * Pattern for valid types: lowercase start, alphanumeric + hyphens
     */
    private static final Pattern TYPE_PATTERN = Pattern.compile("[a-z][a-z0-9-]*");

    /**
     * Compact constructor with validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>Both fields are non-null and non-blank</li>
     *   <li>num matches "m\d+" pattern</li>
     *   <li>type matches "[a-z][a-z0-9-]*" pattern</li>
     * </ul>
     *
     * @throws IllegalArgumentException if validation fails
     */
    public ModuleInfo {
        // Null and blank checks
        Objects.requireNonNull(num, "num is required");
        Objects.requireNonNull(type, "type is required");

        if (num.isBlank()) {
            throw new IllegalArgumentException("num cannot be blank");
        }
        if (type.isBlank()) {
            throw new IllegalArgumentException("type cannot be blank");
        }

        // Pattern validation
        if (!MODULE_NUM_PATTERN.matcher(num).matches()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid module number '%s' - must match pattern 'm\\d+' (e.g., m1, m7, m12)",
                            num
                    )
            );
        }

        if (!TYPE_PATTERN.matcher(type).matches()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid module type '%s' - must match pattern '[a-z][a-z0-9-]*' (lowercase start, alphanumeric + hyphens)",
                            type
                    )
            );
        }
    }

    public static ModuleInfo of(String num, String type) {
        return new ModuleInfo(num, type);
    }

    /**
     * Returns the default output directory name for this module.
     * Format: {@code <type>-<num>} (e.g., "pt-m1", "exam-prep-m7")
     *
     * @return output directory name
     */
    public String defaultOutputDir() {
        return type + "-" + num;
    }

    /**
     * Returns the default namespace name for this module.
     * Format: {@code ns-<num>-<type>} (e.g., "ns-m1-pt", "ns-m7-exam-prep")
     *
     * @return namespace name
     */
    public String defaultNamespace() {
        return "ns-" + num + "-" + type;
    }

    /**
     * Returns a cluster name for this module and engine.
     * Format: {@code clu-<num>-<type>-<engine>} (e.g., "clu-m1-pt-kind")
     *
     * @param engine cluster engine type
     * @return cluster name
     * @throws NullPointerException if engine is null
     */
    public String clusterName(ClusterType engine) {
        Objects.requireNonNull(engine, "engine is required");
        return String.format("clu-%s-%s-%s", num, type, engine.name().toLowerCase());
    }
}
