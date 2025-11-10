package com.k8s.generator.model;

import lombok.Builder;
import lombok.Singular;

import java.util.List;
import java.util.Objects;

/**
 * Management VM configuration for coordinating multi-cluster setups and cloud providers.
 *
 * <p>This record represents the specification for a management VM that can:
 * <ul>
 *   <li>Aggregate kubeconfigs from multiple clusters</li>
 *   <li>Install cloud provider CLIs (Azure, AWS, GCP)</li>
 *   <li>Serve as a central control point for multi-cluster operations</li>
 *   <li>Install additional tooling (kubectl, helm, etc.)</li>
 * </ul>
 *
 * <p>Architecture Position:
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec(management) → Validator → PlanBuilder → ScaffoldPlan
 * </pre>
 *
 * <p>Phase 2+ Scope:
 * <ul>
 *   <li><b>Phase 2</b>: Basic management VM with Azure support</li>
 *   <li><b>Phase 3+</b>: Multi-cluster aggregation, AWS/GCP support</li>
 * </ul>
 *
 * <p>Provider Names:
 * <ul>
 *   <li>"azure" - Microsoft Azure (requires azure_cli tool)</li>
 *   <li>"aws" - Amazon Web Services (requires aws_cli tool)</li>
 *   <li>"gcp" - Google Cloud Platform (requires gcloud tool)</li>
 * </ul>
 *
 * <p>Tool Names:
 * <ul>
 *   <li>"kubectl" - Kubernetes CLI</li>
 *   <li>"helm" - Kubernetes package manager</li>
 *   <li>"azure_cli" - Azure CLI (az)</li>
 *   <li>"aws_cli" - AWS CLI</li>
 *   <li>"gcloud" - Google Cloud SDK</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Basic Azure management VM
 * var mgmt = new Management(
 *     "mgmt-m7-hw",
 *     List.of("azure"),
 *     true,
 *     List.of("kubectl", "azure_cli")
 * );
 *
 * // Multi-cloud management VM
 * var multiCloud = new Management(
 *     "mgmt-m9-exam",
 *     List.of("azure", "aws", "gcp"),
 *     true,
 *     List.of("kubectl", "helm", "azure_cli", "aws_cli", "gcloud")
 * );
 *
 * // Management VM without aggregation
 * var basic = new Management(
 *     "mgmt-m1-pt",
 *     List.of(),
 *     false,
 *     List.of("kubectl")
 * );
 * }</pre>
 *
 * @param name                 VM name (e.g., "mgmt-m7-hw", must be non-null and non-blank)
 * @param providers            List of cloud providers to configure (e.g., ["azure"], ["aws", "gcp"], never null but can be empty)
 * @param aggregateKubeconfigs Whether to aggregate kubeconfigs from all clusters
 * @param tools                List of tools to install (e.g., ["kubectl", "helm", "azure_cli"], never null but can be empty)
 * @see GeneratorSpec
 * @see ScaffoldPlan
 * @since 1.0.0
 */
@Builder
public record Management(
        VmName name,
        @Singular List<String> providers,
        boolean aggregateKubeconfigs,
        @Singular List<String> tools) {

    public static class ManagementBuilder {

        public ManagementBuilder name(String name) {
            this.name = VmName.of(name);
            return this;
        }


        public ManagementBuilder name(VmName name) {
            this.name = name;
            return this;
        }

        public ManagementBuilder aggregateKubeconfigs() {
            this.aggregateKubeconfigs = true;
            return this;
        }
    }

    /**
     * Compact constructor with structural validation.
     *
     * <p>Validates:
     * <ul>
     *   <li>name is non-null and non-blank</li>
     *   <li>providers list is non-null (but can be empty)</li>
     *   <li>providers list contains no null or blank elements</li>
     *   <li>tools list is non-null (but can be empty)</li>
     *   <li>tools list contains no null or blank elements</li>
     * </ul>
     *
     * <p>Note: This constructor does NOT validate:
     * <ul>
     *   <li>Provider name validity (e.g., "azure" vs "azur") - handled by SemanticValidator</li>
     *   <li>Tool name validity - handled by SemanticValidator</li>
     *   <li>Tool/provider compatibility - handled by SemanticValidator</li>
     * </ul>
     *
     * @throws IllegalArgumentException if any structural validation fails
     */
    public Management {
        // Validate name
        Objects.requireNonNull(name, "name is required");

        // Validate providers list
        Objects.requireNonNull(providers, "providers list is required (use List.of() for empty)");
        for (int i = 0; i < providers.size(); i++) {
            String provider = providers.get(i);
            if (provider == null) {
                throw new IllegalArgumentException("providers list contains null element at index " + i);
            }
            if (provider.isBlank()) {
                throw new IllegalArgumentException("providers list contains blank element at index " + i);
            }
        }

        // Validate tools list
        Objects.requireNonNull(tools, "tools list is required (use List.of() for empty)");
        for (int i = 0; i < tools.size(); i++) {
            String tool = tools.get(i);
            if (tool == null) {
                throw new IllegalArgumentException("tools list contains null element at index " + i);
            }
            if (tool.isBlank()) {
                throw new IllegalArgumentException("tools list contains blank element at index " + i);
            }
        }

        // Make defensive copies to ensure immutability
        providers = List.copyOf(providers);
        tools = List.copyOf(tools);
    }

    /**
     * Checks if this management VM has any cloud providers configured.
     *
     * @return true if providers list is non-empty
     */
    public boolean hasProviders() {
        return !providers.isEmpty();
    }

    /**
     * Checks if this management VM has any tools configured.
     *
     * @return true if tools list is non-empty
     */
    public boolean hasTools() {
        return !tools.isEmpty();
    }

    /**
     * Checks if a specific provider is configured.
     *
     * @param providerName provider name (e.g., "azure", "aws", "gcp")
     * @return true if provider is in providers list
     */
    public boolean hasProvider(String providerName) {
        return providers.contains(providerName);
    }

    /**
     * Checks if a specific tool is configured.
     *
     * @param toolName tool name (e.g., "kubectl", "helm", "azure_cli")
     * @return true if tool is in tools list
     */
    public boolean hasTool(String toolName) {
        return tools.contains(toolName);
    }
}
