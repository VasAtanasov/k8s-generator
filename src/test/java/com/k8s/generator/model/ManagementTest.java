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
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .tool(Tool.azureCli())
                    .build();

            assertThat(mgmt.name().toString()).isEqualTo("mgmt-m7-hw");
            assertThat(mgmt.providers()).containsExactly(CloudProvider.azure());
            assertThat(mgmt.aggregateKubeconfigs()).isTrue();
            assertThat(mgmt.tools()).containsExactly(Tool.kubectl(), Tool.azureCli());
        }

        @Test
        @DisplayName("should create valid Management with multiple providers")
        void shouldCreateValidManagementWithMultipleProviders() {
            var mgmt = Management.builder()
                    .name("mgmt-m9-exam")
                    .provider(CloudProvider.azure())
                    .provider(CloudProvider.aws())
                    .provider(CloudProvider.gcp())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .tool(Tool.helm())
                    .tool(Tool.azureCli())
                    .tool(Tool.awsCli())
                    .tool(Tool.gcloud())
                    .build();

            assertThat(mgmt.providers()).containsExactly(
                    CloudProvider.azure(),
                    CloudProvider.aws(),
                    CloudProvider.gcp()
            );
            assertThat(mgmt.tools()).containsExactly(
                    Tool.kubectl(),
                    Tool.helm(),
                    Tool.azureCli(),
                    Tool.awsCli(),
                    Tool.gcloud()
            );
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
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject blank name")
        void shouldRejectBlankName() {
            assertThatThrownBy(() -> Management.builder()
                    .name("   ")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
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
                    List.of(Tool.kubectl())
            ))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("providers list is required");
        }

        @Test
        @DisplayName("should reject null provider element")
        void shouldRejectNullProviderElement() {
            assertThatThrownBy(() -> Management.builder()
                    .name("mgmt-m1-pt")
                    .providers(Arrays.asList(CloudProvider.azure(), null, CloudProvider.aws()))
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providers list contains null element at index 1");
        }

        // Provider string parsing tests are covered in CloudProviderTest

        @Test
        @DisplayName("should reject null tools list")
        void shouldRejectNullToolsList() {
            // Use canonical constructor to validate record's null-list check
            assertThatThrownBy(() -> new Management(
                    VmName.of("mgmt-m1-pt"),
                    List.of(CloudProvider.azure()),
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
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tools(Arrays.asList(Tool.kubectl(), null, Tool.helm()))
                    .build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tools list contains null element at index 1");
        }

        // Tool string parsing tests are covered in ToolTest
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("should create defensive copy of providers list")
        void shouldCreateDefensiveCopyOfProvidersList() {
            var originalProviders = new java.util.ArrayList<CloudProvider>();
            originalProviders.add(CloudProvider.azure());

            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .providers(originalProviders)
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            // Modify original list
            originalProviders.add(CloudProvider.aws());

            // Management should be unaffected
            assertThat(mgmt.providers()).containsExactly(CloudProvider.azure());
        }

        @Test
        @DisplayName("should create defensive copy of tools list")
        void shouldCreateDefensiveCopyOfToolsList() {
            var originalTools = new java.util.ArrayList<Tool>();
            originalTools.add(Tool.kubectl());

            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tools(originalTools)
                    .build();

            // Modify original list
            originalTools.add(Tool.helm());

            // Management should be unaffected
            assertThat(mgmt.tools()).containsExactly(Tool.kubectl());
        }

        @Test
        @DisplayName("should return unmodifiable providers list")
        void shouldReturnUnmodifiableProvidersList() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThatThrownBy(() -> mgmt.providers().add(CloudProvider.aws()))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("should return unmodifiable tools list")
        void shouldReturnUnmodifiableToolsList() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThatThrownBy(() -> mgmt.tools().add(Tool.helm()))
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
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasProviders()).isTrue();
        }

        @Test
        @DisplayName("hasProviders() should return false when no providers")
        void hasProvidersShouldReturnFalseWhenNoProviders() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasProviders()).isFalse();
        }

        @Test
        @DisplayName("hasTools() should return true when tools exist")
        void hasToolsShouldReturnTrueWhenToolsExist() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasTools()).isTrue();
        }

        @Test
        @DisplayName("hasTools() should return false when no tools")
        void hasToolsShouldReturnFalseWhenNoTools() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .build();

            assertThat(mgmt.hasTools()).isFalse();
        }

        @Test
        @DisplayName("hasProvider() should return true for existing provider")
        void hasProviderShouldReturnTrueForExistingProvider() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .provider(CloudProvider.aws())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasProvider(CloudProvider.azure())).isTrue();
            assertThat(mgmt.hasProvider(CloudProvider.aws())).isTrue();
        }

        @Test
        @DisplayName("hasProvider() should return false for non-existing provider")
        void hasProviderShouldReturnFalseForNonExistingProvider() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasProvider(CloudProvider.aws())).isFalse();
            assertThat(mgmt.hasProvider(CloudProvider.gcp())).isFalse();
        }

        @Test
        @DisplayName("hasTool() should return true for existing tool")
        void hasToolShouldReturnTrueForExistingTool() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .tool(Tool.helm())
                    .build();

            assertThat(mgmt.hasTool(Tool.kubectl())).isTrue();
            assertThat(mgmt.hasTool(Tool.helm())).isTrue();
        }

        @Test
        @DisplayName("hasTool() should return false for non-existing tool")
        void hasToolShouldReturnFalseForNonExistingTool() {
            var mgmt = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt.hasTool(Tool.helm())).isFalse();
            assertThat(mgmt.hasTool(Tool.azureCli())).isFalse();
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
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt1).isEqualTo(mgmt2);
            assertThat(mgmt1.hashCode()).isEqualTo(mgmt2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when name differs")
        void shouldNotBeEqualWhenNameDiffers() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m2-hw")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when providers differ")
        void shouldNotBeEqualWhenProvidersDiffer() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.aws())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when aggregateKubeconfigs differs")
        void shouldNotBeEqualWhenAggregateKubeconfigsDiffers() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    // do not call aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }

        @Test
        @DisplayName("should not be equal when tools differ")
        void shouldNotBeEqualWhenToolsDiffer() {
            var mgmt1 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.kubectl())
                    .build();
            var mgmt2 = Management.builder()
                    .name("mgmt-m1-pt")
                    .provider(CloudProvider.azure())
                    .aggregateKubeconfigs()
                    .tool(Tool.helm())
                    .build();

            assertThat(mgmt1).isNotEqualTo(mgmt2);
        }
    }
}
