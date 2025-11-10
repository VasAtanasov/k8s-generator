package com.k8s.generator.model;

import lombok.Builder;
import lombok.Singular;

import java.util.ArrayList;
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
 * // Basic Azure management VM (using builder with strings)
 * var mgmt = Management.builder()
 *     .name("mgmt-m7-hw")
 *     .provider("azure")
 *     .aggregateKubeconfigs()
 *     .tool("kubectl")
 *     .tool("azure_cli")
 *     .build();
 *
 * // Multi-cloud management VM (using VOs directly)
 * var multiCloud = Management.builder()
 *     .name("mgmt-m9-exam")
 *     .provider(CloudProvider.azure())
 *     .provider(CloudProvider.aws())
 *     .provider(CloudProvider.gcp())
 *     .aggregateKubeconfigs()
 *     .tool(Tool.kubectl())
 *     .tool(Tool.helm())
 *     .tool(Tool.azureCli())
 *     .tool(Tool.awsCli())
 *     .tool(Tool.gcloud())
 *     .build();
 *
 * // Management VM without aggregation
 * var basic = Management.builder()
 *     .name("mgmt-m1-pt")
 *     .tool(Tool.kubectl())
 *     .build();
 * }</pre>
 *
 * @param name                 VM name (e.g., "mgmt-m7-hw", must be non-null)
 * @param providers            List of CloudProvider instances to configure (never null but can be empty)
 * @param aggregateKubeconfigs Whether to aggregate kubeconfigs from all clusters
 * @param tools                List of Tool instances to install (never null but can be empty)
 * @see GeneratorSpec
 * @see ScaffoldPlan
 * @since 1.0.0
 */
@Builder
public record Management(
        VmName name,
        @Singular List<CloudProvider> providers,
        boolean aggregateKubeconfigs,
        @Singular List<Tool> tools) {

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
     *   <li>name is non-null</li>
     *   <li>providers list is non-null (but can be empty)</li>
     *   <li>providers list contains no null elements</li>
     *   <li>tools list is non-null (but can be empty)</li>
     *   <li>tools list contains no null elements</li>
     * </ul>
     *
     * <p>Note: This constructor does NOT validate:
     * <ul>
     *   <li>Provider name validity - handled by CloudProvider value object</li>
     *   <li>Tool name validity - handled by Tool value object</li>
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
            if (providers.get(i) == null) {
                throw new IllegalArgumentException("providers list contains null element at index " + i);
            }
        }

        // Validate tools list
        Objects.requireNonNull(tools, "tools list is required (use List.of() for empty)");
        for (int i = 0; i < tools.size(); i++) {
            if (tools.get(i) == null) {
                throw new IllegalArgumentException("tools list contains null element at index " + i);
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
        return hasProvider(CloudProvider.of(providerName));
    }

    /**
     * Checks if a specific provider is configured.
     *
     * @param provider CloudProvider instance
     * @return true if provider is in providers list
     */
    public boolean hasProvider(CloudProvider provider) {
        return providers.contains(provider);
    }

    /**
     * Checks if a specific tool is configured.
     *
     * @param toolName tool name (e.g., "kubectl", "helm", "azure_cli")
     * @return true if tool is in tools list
     */
    public boolean hasTool(String toolName) {
        return hasTool(Tool.of(toolName));
    }

    /**
     * Checks if a specific tool is configured.
     *
     * @param tool Tool instance
     * @return true if tool is in tools list
     */
    public boolean hasTool(Tool tool) {
        return tools.contains(tool);
    }
}
