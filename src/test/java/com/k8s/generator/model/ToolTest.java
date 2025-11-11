package com.k8s.generator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Tool value object.
 */
@DisplayName("Tool")
class ToolTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionAndValidation {

        @Test
        @DisplayName("should create valid Tool for kubectl")
        void shouldCreateValidToolForKubectl() {
            Tool tool = Tool.of("kubectl");
            assertThat(tool.value()).isEqualTo("kubectl");
            assertThat(tool.toString()).isEqualTo("kubectl");
        }

        @Test
        @DisplayName("should create valid Tool for helm")
        void shouldCreateValidToolForHelm() {
            Tool tool = Tool.of("helm");
            assertThat(tool.value()).isEqualTo("helm");
        }

        @Test
        @DisplayName("should create valid Tool for azure_cli")
        void shouldCreateValidToolForAzureCli() {
            Tool tool = Tool.of("azure_cli");
            assertThat(tool.value()).isEqualTo("azure_cli");
        }

        @Test
        @DisplayName("should create valid Tool for aws_cli")
        void shouldCreateValidToolForAwsCli() {
            Tool tool = Tool.of("aws_cli");
            assertThat(tool.value()).isEqualTo("aws_cli");
        }

        @Test
        @DisplayName("should create valid Tool for gcloud")
        void shouldCreateValidToolForGcloud() {
            Tool tool = Tool.of("gcloud");
            assertThat(tool.value()).isEqualTo("gcloud");
        }

        @Test
        @DisplayName("should create valid Tool for kube_binaries")
        void shouldCreateValidToolForKubeBinaries() {
            Tool tool = Tool.of("kube_binaries");
            assertThat(tool.value()).isEqualTo("kube_binaries");
        }

        @Test
        @DisplayName("should create valid Tool for kind")
        void shouldCreateValidToolForKind() {
            Tool tool = Tool.of("kind");
            assertThat(tool.value()).isEqualTo("kind");
        }

        @Test
        @DisplayName("should create valid Tool for k3s")
        void shouldCreateValidToolForK3s() {
            Tool tool = Tool.of("k3s");
            assertThat(tool.value()).isEqualTo("k3s");
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            assertThatThrownBy(() -> Tool.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tool value is required");
        }

        @Test
        @DisplayName("should reject empty string")
        void shouldRejectEmptyString() {
            assertThatThrownBy(() -> Tool.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tool value cannot be blank");
        }

        @Test
        @DisplayName("should reject blank string with spaces")
        void shouldRejectBlankStringWithSpaces() {
            assertThatThrownBy(() -> Tool.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tool value cannot be blank");
        }

        @Test
        @DisplayName("should reject unsupported tool")
        void shouldRejectUnsupportedTool() {
            assertThatThrownBy(() -> Tool.of("unsupported_tool"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tool: 'unsupported_tool'")
                    .hasMessageContaining("Supported tools:");
        }

        @Test
        @DisplayName("should reject typo in kubectl")
        void shouldRejectTypoInKubectl() {
            assertThatThrownBy(() -> Tool.of("kubctl"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tool: 'kubctl'");
        }

        @Test
        @DisplayName("should reject typo in helm")
        void shouldRejectTypoInHelm() {
            assertThatThrownBy(() -> Tool.of("helms"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tool: 'helms'");
        }

        @Test
        @DisplayName("should reject invalid azure cli name")
        void shouldRejectInvalidAzureCliName() {
            assertThatThrownBy(() -> Tool.of("az"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tool: 'az'");
        }

        @Test
        @DisplayName("should reject invalid aws cli name")
        void shouldRejectInvalidAwsCliName() {
            assertThatThrownBy(() -> Tool.of("awscli"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid tool: 'awscli'");
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        @DisplayName("should normalize uppercase KUBECTL to lowercase")
        void shouldNormalizeUppercaseKubectlToLowercase() {
            Tool tool = Tool.of("KUBECTL");
            assertThat(tool.value()).isEqualTo("kubectl");
        }

        @Test
        @DisplayName("should normalize mixed case Helm to lowercase")
        void shouldNormalizeMixedCaseHelmToLowercase() {
            Tool tool = Tool.of("Helm");
            assertThat(tool.value()).isEqualTo("helm");
        }

        @Test
        @DisplayName("should normalize uppercase AZURE_CLI to lowercase")
        void shouldNormalizeUppercaseAzureCliToLowercase() {
            Tool tool = Tool.of("AZURE_CLI");
            assertThat(tool.value()).isEqualTo("azure_cli");
        }

        @Test
        @DisplayName("should trim leading whitespace")
        void shouldTrimLeadingWhitespace() {
            Tool tool = Tool.of("  kubectl");
            assertThat(tool.value()).isEqualTo("kubectl");
        }

        @Test
        @DisplayName("should trim trailing whitespace")
        void shouldTrimTrailingWhitespace() {
            Tool tool = Tool.of("helm  ");
            assertThat(tool.value()).isEqualTo("helm");
        }

        @Test
        @DisplayName("should trim leading and trailing whitespace")
        void shouldTrimLeadingAndTrailingWhitespace() {
            Tool tool = Tool.of("  azure_cli  ");
            assertThat(tool.value()).isEqualTo("azure_cli");
        }

        @Test
        @DisplayName("should normalize and trim together")
        void shouldNormalizeAndTrimTogether() {
            Tool tool = Tool.of("  KUBECTL  ");
            assertThat(tool.value()).isEqualTo("kubectl");
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("kubectl() should create kubectl tool")
        void kubectlShouldCreateKubectlTool() {
            Tool tool = Tool.kubectl();
            assertThat(tool.value()).isEqualTo("kubectl");
            assertThat(tool).isEqualTo(Tool.of("kubectl"));
        }

        @Test
        @DisplayName("helm() should create helm tool")
        void helmShouldCreateHelmTool() {
            Tool tool = Tool.helm();
            assertThat(tool.value()).isEqualTo("helm");
            assertThat(tool).isEqualTo(Tool.of("helm"));
        }

        @Test
        @DisplayName("azureCli() should create azure_cli tool")
        void azureCliShouldCreateAzureCliTool() {
            Tool tool = Tool.azureCli();
            assertThat(tool.value()).isEqualTo("azure_cli");
            assertThat(tool).isEqualTo(Tool.of("azure_cli"));
        }

        @Test
        @DisplayName("awsCli() should create aws_cli tool")
        void awsCliShouldCreateAwsCliTool() {
            Tool tool = Tool.awsCli();
            assertThat(tool.value()).isEqualTo("aws_cli");
            assertThat(tool).isEqualTo(Tool.of("aws_cli"));
        }

        @Test
        @DisplayName("gcloud() should create gcloud tool")
        void gcloudShouldCreateGcloudTool() {
            Tool tool = Tool.gcloud();
            assertThat(tool.value()).isEqualTo("gcloud");
            assertThat(tool).isEqualTo(Tool.of("gcloud"));
        }

        @Test
        @DisplayName("kubeBinaries() should create kube_binaries tool")
        void kubeBinariesShouldCreateKubeBinariesTool() {
            Tool tool = Tool.kubeBinaries();
            assertThat(tool.value()).isEqualTo("kube_binaries");
            assertThat(tool).isEqualTo(Tool.of("kube_binaries"));
        }

        @Test
        @DisplayName("kind() should create kind tool")
        void kindShouldCreateKindTool() {
            Tool tool = Tool.kind();
            assertThat(tool.value()).isEqualTo("kind");
            assertThat(tool).isEqualTo(Tool.of("kind"));
        }

        @Test
        @DisplayName("docker() should create docker tool")
        void dockerShouldCreateDockerTool() {
            Tool tool = Tool.docker();
            assertThat(tool.value()).isEqualTo("docker");
            assertThat(tool).isEqualTo(Tool.of("docker"));
        }
    }

    @Nested
    @DisplayName("requiresCloudProvider()")
    class RequiresCloudProvider {

        @Test
        @DisplayName("should return true for azure_cli")
        void shouldReturnTrueForAzureCli() {
            Tool tool = Tool.azureCli();
            assertThat(tool.requiresCloudProvider()).isTrue();
        }

        @Test
        @DisplayName("should return true for aws_cli")
        void shouldReturnTrueForAwsCli() {
            Tool tool = Tool.awsCli();
            assertThat(tool.requiresCloudProvider()).isTrue();
        }

        @Test
        @DisplayName("should return true for gcloud")
        void shouldReturnTrueForGcloud() {
            Tool tool = Tool.gcloud();
            assertThat(tool.requiresCloudProvider()).isTrue();
        }

        @Test
        @DisplayName("should return false for kubectl")
        void shouldReturnFalseForKubectl() {
            Tool tool = Tool.kubectl();
            assertThat(tool.requiresCloudProvider()).isFalse();
        }

        @Test
        @DisplayName("should return false for helm")
        void shouldReturnFalseForHelm() {
            Tool tool = Tool.helm();
            assertThat(tool.requiresCloudProvider()).isFalse();
        }

        @Test
        @DisplayName("should return false for kube_binaries")
        void shouldReturnFalseForKubeBinaries() {
            Tool tool = Tool.kubeBinaries();
            assertThat(tool.requiresCloudProvider()).isFalse();
        }

        @Test
        @DisplayName("should return false for kind")
        void shouldReturnFalseForKind() {
            Tool tool = Tool.kind();
            assertThat(tool.requiresCloudProvider()).isFalse();
        }

        @Test
        @DisplayName("should return false for k3s")
        void shouldReturnFalseForK3s() {
            Tool tool = Tool.docker();
            assertThat(tool.requiresCloudProvider()).isFalse();
        }
    }

    @Nested
    @DisplayName("Equality and Hashcode")
    class EqualityAndHashcode {

        @Test
        @DisplayName("should be equal when values match")
        void shouldBeEqualWhenValuesMatch() {
            Tool tool1 = Tool.kubectl();
            Tool tool2 = Tool.of("kubectl");

            assertThat(tool1).isEqualTo(tool2);
            assertThat(tool1.hashCode()).isEqualTo(tool2.hashCode());
        }

        @Test
        @DisplayName("should be equal after normalization")
        void shouldBeEqualAfterNormalization() {
            Tool tool1 = Tool.of("kubectl");
            Tool tool2 = Tool.of("KUBECTL");
            Tool tool3 = Tool.of("  kubectl  ");

            assertThat(tool1).isEqualTo(tool2);
            assertThat(tool1).isEqualTo(tool3);
            assertThat(tool1.hashCode()).isEqualTo(tool2.hashCode());
            assertThat(tool1.hashCode()).isEqualTo(tool3.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            Tool kubectl = Tool.kubectl();
            Tool helm = Tool.helm();
            Tool azCli = Tool.azureCli();

            assertThat(kubectl).isNotEqualTo(helm);
            assertThat(kubectl).isNotEqualTo(azCli);
            assertThat(helm).isNotEqualTo(azCli);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            Tool tool = Tool.kubectl();
            assertThat(tool).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            Tool tool = Tool.kubectl();
            assertThat(tool).isNotEqualTo("kubectl");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return normalized value")
        void shouldReturnNormalizedValue() {
            Tool tool = Tool.of("KUBECTL");
            assertThat(tool.toString()).isEqualTo("kubectl");
        }

        @Test
        @DisplayName("should be suitable for template rendering")
        void shouldBeSuitableForTemplateRendering() {
            assertThat(Tool.kubectl().toString()).isEqualTo("kubectl");
            assertThat(Tool.helm().toString()).isEqualTo("helm");
            assertThat(Tool.azureCli().toString()).isEqualTo("azure_cli");
            assertThat(Tool.awsCli().toString()).isEqualTo("aws_cli");
            assertThat(Tool.gcloud().toString()).isEqualTo("gcloud");
            assertThat(Tool.kubeBinaries().toString()).isEqualTo("kube_binaries");
            assertThat(Tool.kind().toString()).isEqualTo("kind");
            assertThat(Tool.docker().toString()).isEqualTo("docker");
        }
    }
}
