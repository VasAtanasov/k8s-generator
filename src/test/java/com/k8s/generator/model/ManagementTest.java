package com.k8s.generator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Management record.
 */
@DisplayName("Management")
class ManagementTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionAndValidation {

        @Test
        @DisplayName("should create valid Management with Azure provider")
        void shouldCreateValidManagementWithAzure() {
            var mgmt = Management.builder()
                    .name("mgmt-m7-hw")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .tool("azure_cli")
                    .build();

            assertThat(mgmt.name().toString()).isEqualTo("mgmt-m7-hw");
            assertThat(mgmt.providers()).containsExactly("azure");
            assertThat(mgmt.aggregateKubeconfigs()).isTrue();
            assertThat(mgmt.tools()).containsExactly("kubectl", "azure_cli");
        }

        @Test
        @DisplayName("should create valid Management with multiple providers")
        void shouldCreateValidManagementWithMultipleProviders() {
            var mgmt = Management.builder()
                    .name("mgmt-m9-exam")
                    .provider("azure")
                    .provider("aws")
                    .provider("gcp")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .tool("helm")
                    .tool("azure_cli")
                    .tool("aws_cli")
                    .tool("gcloud")
                    .build();

            assertThat(mgmt.providers()).containsExactly("azure", "aws", "gcp");
            assertThat(mgmt.tools()).containsExactly("kubectl", "helm", "azure_cli", "aws_cli", "gcloud");
        }

        @Test
        @DisplayName("should create valid Management with empty providers and tools")
        void shouldCreateValidManagementWithEmptyLists() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .build();

            assertThat(mgmt.providers()).isEmpty();
            assertThat(mgmt.tools()).isEmpty();
            assertThat(mgmt.aggregateKubeconfigs()).isFalse();
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            assertThatThrownBy(() -> Management.builder()
                    .name((String) null)
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> Management.builder()
                    .name("   ")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("name cannot be blank");
        }

        @Test
        @DisplayName("should reject null providers list")
        void shouldRejectNullProvidersList() {
            // Use canonical constructor to validate record's null-list check
            assertThatThrownBy(() -> new Management(
                    VmName.of("mgmt-m1-pt"),
                    null,
                    true,
                    List.of("kubectl")
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("providers list is required");
        }

        @Test
        @DisplayName("should reject null provider element")
        void shouldRejectNullProviderElement() {
            assertThatThrownBy(() -> Management.builder()
                    .name("mgmt-m1-pt")
                    .providers(Arrays.asList("azure", null, "aws"))
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providers list contains null element at index 1");
        }

        @Test
        @DisplayName("should reject blank provider element")
        void shouldRejectBlankProviderElement() {
            assertThatThrownBy(() -> Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .provider("  ")
                    .provider("aws")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providers list contains blank element at index 1");
        }

        @Test
        @DisplayName("should reject null tools list")
        void shouldRejectNullToolsList() {
            // Use canonical constructor to validate record's null-list check
            assertThatThrownBy(() -> new Management(
                    VmName.of("mgmt-m1-pt"),
                    List.of("azure"),
                    true,
                    null
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tools list is required");
        }

        @Test
        @DisplayName("should reject null tool element")
        void shouldRejectNullToolElement() {
            assertThatThrownBy(() -> Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tools(Arrays.asList("kubectl", null, "helm"))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tools list contains null element at index 1");
        }

        @Test
        @DisplayName("should reject blank tool element")
        void shouldRejectBlankToolElement() {
            assertThatThrownBy(() -> Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .tool("")
                    .tool("helm")
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tools list contains blank element at index 1");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should create defensive copy of providers list")
        void shouldCreateDefensiveCopyOfProvidersList() {
            var originalProviders = new java.util.ArrayList<String>();
            originalProviders.add("azure");

            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .providers(originalProviders)
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            // Modify original list
            originalProviders.add("aws");

            // Management should be unaffected
            assertThat(mgmt.providers()).containsExactly("azure");
        }

        @Test
        @DisplayName("should create defensive copy of tools list")
        void shouldCreateDefensiveCopyOfToolsList() {
            var originalTools = new java.util.ArrayList<String>();
            originalTools.add("kubectl");

            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tools(originalTools)
                    .build();

            // Modify original list
            originalTools.add("helm");

            // Management should be unaffected
            assertThat(mgmt.tools()).containsExactly("kubectl");
        }

        @Test
        @DisplayName("should return unmodifiable providers list")
        void shouldReturnUnmodifiableProvidersList() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThatThrownBy(() -> mgmt.providers().add("aws"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should return unmodifiable tools list")
        void shouldReturnUnmodifiableToolsList() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThatThrownBy(() -> mgmt.tools().add("helm"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("Helper Methods")
    class HelperMethods {

        @Test
        @DisplayName("hasProviders() should return true when providers exist")
        void hasProvidersShouldReturnTrueWhenProvidersExist() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasProviders()).isTrue();
        }

        @Test
        @DisplayName("hasProviders() should return false when no providers")
        void hasProvidersShouldReturnFalseWhenNoProviders() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasProviders()).isFalse();
        }

        @Test
        @DisplayName("hasTools() should return true when tools exist")
        void hasToolsShouldReturnTrueWhenToolsExist() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasTools()).isTrue();
        }

        @Test
        @DisplayName("hasTools() should return false when no tools")
        void hasToolsShouldReturnFalseWhenNoTools() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .build();

            assertThat(mgmt.hasTools()).isFalse();
        }

        @Test
        @DisplayName("hasProvider() should return true for existing provider")
        void hasProviderShouldReturnTrueForExistingProvider() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .provider("aws")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasProvider("azure")).isTrue();
            assertThat(mgmt.hasProvider("aws")).isTrue();
        }

        @Test
        @DisplayName("hasProvider() should return false for non-existing provider")
        void hasProviderShouldReturnFalseForNonExistingProvider() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasProvider("aws")).isFalse();
            assertThat(mgmt.hasProvider("gcp")).isFalse();
        }

        @Test
        @DisplayName("hasTool() should return true for existing tool")
        void hasToolShouldReturnTrueForExistingTool() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .tool("helm")
                    .build();

            assertThat(mgmt.hasTool("kubectl")).isTrue();
            assertThat(mgmt.hasTool("helm")).isTrue();
        }

        @Test
        @DisplayName("hasTool() should return false for non-existing tool")
        void hasToolShouldReturnFalseForNonExistingTool() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt.hasTool("helm")).isFalse();
            assertThat(mgmt.hasTool("azure_cli")).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality and Hashcode")
    class EqualityAndHashcode {

        @Test
        @DisplayName("should be equal when all fields match")
        void shouldBeEqualWhenAllFieldsMatch() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt1).isEqualTo(mgmt2);
            assertThat(mgmt1.hashCode()).isEqualTo(mgmt2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when name differs")
        void shouldNotBeEqualWhenNameDiffers() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m2-hw")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when providers differ")
        void shouldNotBeEqualWhenProvidersDiffer() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("aws")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when aggregateKubeconfigs differs")
        void shouldNotBeEqualWhenAggregateKubeconfigsDiffers() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    // do not call aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when tools differ")
        void shouldNotBeEqualWhenToolsDiffer() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("kubectl")
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider("azure")
                    .aggregateKubeconfigs()
                    .tool("helm")
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }
    }
}

