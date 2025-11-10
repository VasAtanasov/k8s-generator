package com.k8s.generator.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Value Object representing a development/operations tool to install.
 *
 * <p>This VO encapsulates tool identification with validation to prevent
 * primitive obsession and ensure type safety. It guarantees that only valid,
 * supported tool names are used throughout the system.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: Value is final and cannot be modified</li>
 *   <li><b>Validation</b>: Only supported tools are allowed</li>
 *   <li><b>Normalization</b>: Values are trimmed and lowercased for consistency</li>
 *   <li><b>Type Safety</b>: Cannot accidentally use invalid tool names</li>
 *   <li><b>Domain Logic</b>: Knows which tools require cloud provider configuration</li>
 * </ul>
 *
 * <p>Supported Tools:
 * <ul>
 *   <li><b>kubectl</b>: Kubernetes CLI</li>
 *   <li><b>helm</b>: Kubernetes package manager</li>
 *   <li><b>azure_cli</b>: Azure CLI (az) - requires Azure provider</li>
 *   <li><b>aws_cli</b>: AWS CLI - requires AWS provider</li>
 *   <li><b>gcloud</b>: Google Cloud SDK - requires GCP provider</li>
 *   <li><b>kube_binaries</b>: kubeadm, kubelet, kubectl binaries</li>
 *   <li><b>kind</b>: Kubernetes in Docker</li>
 *   <li><b>k3s</b>: Lightweight Kubernetes</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Factory methods for common tools
 * Tool kubectl = Tool.kubectl();
 * Tool helm = Tool.helm();
 * Tool azCli = Tool.azureCli();
 * Tool awsCli = Tool.awsCli();
 * Tool gcloud = Tool.gcloud();
 *
 * // Parsing from strings
 * Tool tool1 = Tool.of("kubectl");
 * Tool tool2 = Tool.of("HELM");  // Normalized to "helm"
 * Tool tool3 = Tool.of("  azure_cli  ");  // Trimmed and normalized
 *
 * // Invalid tool - throws IllegalArgumentException
 * Tool invalid = Tool.of("kubctl");  // Typo - error!
 *
 * // Domain logic - check if tool requires cloud provider
 * if (tool.requiresCloudProvider()) {
 *     // Ensure matching cloud provider is configured
 * }
 *
 * // In Management builder
 * Management mgmt = Management.builder()
 *     .name("mgmt-m7-hw")
 *     .provider(CloudProvider.azure())
 *     .tool(Tool.kubectl())
 *     .tool(Tool.azureCli())
 *     .build();
 *
 * // Template rendering
 * System.out.println(kubectl.toString());  // "kubectl"
 * }</pre>
 *
 * @param value the normalized tool name (non-null, non-blank, supported tool)
 * @see Management
 * @see CloudProvider
 * @since 1.0.0
 */
public record Tool(String value) {

    /**
     * Set of valid tool names (normalized lowercase).
     */
    private static final Set<String> VALID_TOOLS = Set.of(
            "kubectl",
            "helm",
            "azure_cli",
            "aws_cli",
            "gcloud",
            "kube_binaries",
            "kind",
            "k3s"
    );

    /**
     * Set of tools that require cloud provider configuration.
     */
    private static final Set<String> CLOUD_CLI_TOOLS = Set.of(
            "azure_cli",
            "aws_cli",
            "gcloud"
    );

    /**
     * Compact constructor with validation and normalization.
     *
     * <p>Validates:
     * <ul>
     *   <li>Value is non-null</li>
     *   <li>Value is non-blank after trimming</li>
     *   <li>Value is one of the supported tools (case-insensitive)</li>
     * </ul>
     *
     * <p>Normalization:
     * <ul>
     *   <li>Trims whitespace</li>
     *   <li>Converts to lowercase</li>
     * </ul>
     *
     * @throws IllegalArgumentException if value is blank or not a supported tool
     */
    public Tool {
        Objects.requireNonNull(value, "tool value is required");

        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isBlank()) {
            throw new IllegalArgumentException("tool value cannot be blank");
        }

        if (!VALID_TOOLS.contains(value)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid tool: '%s'. Supported tools: %s",
                            value,
                            String.join(", ", VALID_TOOLS)
                    )
            );
        }
    }

    /**
     * Creates a Tool from a string value.
     *
     * <p>Input is normalized (trimmed, lowercased) and validated against
     * the list of supported tools.
     *
     * @param value tool name (case-insensitive)
     * @return Tool instance
     * @throws IllegalArgumentException if value is blank or unsupported
     */
    public static Tool of(String value) {
        return new Tool(value);
    }

    /**
     * Creates a Tool for kubectl (Kubernetes CLI).
     *
     * @return Tool instance for kubectl
     */
    public static Tool kubectl() {
        return new Tool("kubectl");
    }

    /**
     * Creates a Tool for Helm (Kubernetes package manager).
     *
     * @return Tool instance for Helm
     */
    public static Tool helm() {
        return new Tool("helm");
    }

    /**
     * Creates a Tool for Azure CLI (az).
     *
     * @return Tool instance for Azure CLI
     */
    public static Tool azureCli() {
        return new Tool("azure_cli");
    }

    /**
     * Creates a Tool for AWS CLI.
     *
     * @return Tool instance for AWS CLI
     */
    public static Tool awsCli() {
        return new Tool("aws_cli");
    }

    /**
     * Creates a Tool for Google Cloud SDK (gcloud).
     *
     * @return Tool instance for gcloud
     */
    public static Tool gcloud() {
        return new Tool("gcloud");
    }

    /**
     * Creates a Tool for Kubernetes binaries (kubeadm, kubelet, kubectl).
     *
     * @return Tool instance for kube_binaries
     */
    public static Tool kubeBinaries() {
        return new Tool("kube_binaries");
    }

    /**
     * Creates a Tool for kind (Kubernetes in Docker).
     *
     * @return Tool instance for kind
     */
    public static Tool kind() {
        return new Tool("kind");
    }

    /**
     * Creates a Tool for k3s (Lightweight Kubernetes).
     *
     * @return Tool instance for k3s
     */
    public static Tool k3s() {
        return new Tool("k3s");
    }

    /**
     * Checks if this tool requires a cloud provider to be configured.
     *
     * <p>Cloud CLI tools (azure_cli, aws_cli, gcloud) require their
     * corresponding cloud provider to be configured in the Management VM.
     *
     * @return true if tool is a cloud CLI (azure_cli, aws_cli, gcloud)
     */
    public boolean requiresCloudProvider() {
        return CLOUD_CLI_TOOLS.contains(value);
    }

    /**
     * Returns the tool name for template rendering.
     *
     * @return normalized tool name (lowercase)
     */
    @Override
    public String toString() {
        return value;
    }
}
