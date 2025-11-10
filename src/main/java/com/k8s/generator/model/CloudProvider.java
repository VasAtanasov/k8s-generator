package com.k8s.generator.model;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Value Object representing a cloud provider platform.
 *
 * <p>This VO encapsulates cloud provider identification with validation
 * to prevent primitive obsession and ensure type safety. It guarantees
 * that only valid, supported provider names are used throughout the system.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Immutability</b>: Value is final and cannot be modified</li>
 *   <li><b>Validation</b>: Only supported providers (azure, aws, gcp) are allowed</li>
 *   <li><b>Normalization</b>: Values are trimmed and lowercased for consistency</li>
 *   <li><b>Type Safety</b>: Cannot accidentally use invalid provider names</li>
 * </ul>
 *
 * <p>Supported Providers:
 * <ul>
 *   <li><b>azure</b>: Microsoft Azure (requires azure_cli tool)</li>
 *   <li><b>aws</b>: Amazon Web Services (requires aws_cli tool)</li>
 *   <li><b>gcp</b>: Google Cloud Platform (requires gcloud tool)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Factory methods for common providers
 * CloudProvider azure = CloudProvider.azure();
 * CloudProvider aws = CloudProvider.aws();
 * CloudProvider gcp = CloudProvider.gcp();
 *
 * // Parsing from strings
 * CloudProvider provider1 = CloudProvider.of("azure");
 * CloudProvider provider2 = CloudProvider.of("AWS");  // Normalized to "aws"
 * CloudProvider provider3 = CloudProvider.of("  gcp  ");  // Trimmed and normalized
 *
 * // Invalid provider - throws IllegalArgumentException
 * CloudProvider invalid = CloudProvider.of("digital-ocean");  // Error!
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
 * System.out.println(azure.toString());  // "azure"
 * }</pre>
 *
 * @param value the normalized cloud provider name (non-null, non-blank, one of: azure, aws, gcp)
 * @see Management
 * @see Tool
 * @since 1.0.0
 */
public record CloudProvider(String value) {

    /**
     * Set of valid cloud provider names (normalized lowercase).
     */
    static final Set<String> VALID_PROVIDERS = Set.of("azure", "aws", "gcp");

    /**
     * Compact constructor with validation and normalization.
     *
     * <p>Validates:
     * <ul>
     *   <li>Value is non-null</li>
     *   <li>Value is non-blank after trimming</li>
     *   <li>Value is one of the supported providers (case-insensitive)</li>
     * </ul>
     *
     * <p>Normalization:
     * <ul>
     *   <li>Trims whitespace</li>
     *   <li>Converts to lowercase</li>
     * </ul>
     *
     * @throws IllegalArgumentException if value is blank or not a supported provider
     */
    public CloudProvider {
        Objects.requireNonNull(value, "provider value is required");

        value = value.trim().toLowerCase(Locale.ROOT);

        if (value.isBlank()) {
            throw new IllegalArgumentException("provider value cannot be blank");
        }

        if (!VALID_PROVIDERS.contains(value)) {
            throw new IllegalArgumentException(
                    String.format(
                            "Invalid cloud provider: '%s'. Supported providers: %s",
                            value,
                            String.join(", ", VALID_PROVIDERS)
                    )
            );
        }
    }

    /**
     * Creates a CloudProvider from a string value.
     *
     * <p>Input is normalized (trimmed, lowercased) and validated against
     * the list of supported providers.
     *
     * @param value provider name (azure, aws, or gcp - case-insensitive)
     * @return CloudProvider instance
     * @throws IllegalArgumentException if value is blank or unsupported
     */
    public static CloudProvider of(String value) {
        return new CloudProvider(value);
    }

    /**
     * Creates a CloudProvider for Microsoft Azure.
     *
     * @return CloudProvider instance for Azure
     */
    public static CloudProvider azure() {
        return new CloudProvider("azure");
    }

    /**
     * Creates a CloudProvider for Amazon Web Services.
     *
     * @return CloudProvider instance for AWS
     */
    public static CloudProvider aws() {
        return new CloudProvider("aws");
    }

    /**
     * Creates a CloudProvider for Google Cloud Platform.
     *
     * @return CloudProvider instance for GCP
     */
    public static CloudProvider gcp() {
        return new CloudProvider("gcp");
    }

    /**
     * Returns the provider name for template rendering.
     *
     * @return normalized provider name (lowercase)
     */
    @Override
    public String toString() {
        return value;
    }
}
