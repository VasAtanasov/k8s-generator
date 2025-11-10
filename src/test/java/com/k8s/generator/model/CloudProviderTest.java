package com.k8s.generator.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.k8s.generator.model.CloudProvider.VALID_PROVIDERS;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for CloudProvider value object.
 */
@DisplayName("CloudProvider")
class CloudProviderTest {

    @Nested
    @DisplayName("Construction and Validation")
    class ConstructionAndValidation {

        @Test
        @DisplayName("should create valid CloudProvider for azure")
        void shouldCreateValidCloudProviderForAzure() {
            CloudProvider provider = CloudProvider.of("azure");

            assertThat(provider.value()).isEqualTo("azure");
            assertThat(provider.toString()).isEqualTo("azure");
        }

        @Test
        @DisplayName("should create valid CloudProvider for aws")
        void shouldCreateValidCloudProviderForAws() {
            CloudProvider provider = CloudProvider.of("aws");

            assertThat(provider.value()).isEqualTo("aws");
            assertThat(provider.toString()).isEqualTo("aws");
        }

        @Test
        @DisplayName("should create valid CloudProvider for gcp")
        void shouldCreateValidCloudProviderForGcp() {
            CloudProvider provider = CloudProvider.of("gcp");

            assertThat(provider.value()).isEqualTo("gcp");
            assertThat(provider.toString()).isEqualTo("gcp");
        }

        @Test
        @DisplayName("should reject null value")
        void shouldRejectNullValue() {
            assertThatThrownBy(() -> CloudProvider.of(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("provider value is required");
        }

        @Test
        @DisplayName("should reject empty string")
        void shouldRejectEmptyString() {
            assertThatThrownBy(() -> CloudProvider.of(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provider value cannot be blank");
        }

        @Test
        @DisplayName("should reject blank string with spaces")
        void shouldRejectBlankStringWithSpaces() {
            assertThatThrownBy(() -> CloudProvider.of("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provider value cannot be blank");
        }

        @Test
        @DisplayName("should reject blank string with tabs")
        void shouldRejectBlankStringWithTabs() {
            assertThatThrownBy(() -> CloudProvider.of("\t\t"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provider value cannot be blank");
        }

        @Test
        @DisplayName("should reject unsupported provider")
        void shouldRejectUnsupportedProvider() {
            assertThatThrownBy(() -> CloudProvider.of("digital-ocean"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cloud provider: 'digital-ocean'")
                    .hasMessageContaining("Supported providers: %s".formatted(String.join(", ", VALID_PROVIDERS)));
        }

        @Test
        @DisplayName("should reject typo in azure")
        void shouldRejectTypoInAzure() {
            assertThatThrownBy(() -> CloudProvider.of("azur"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cloud provider: 'azur'");
        }

        @Test
        @DisplayName("should reject typo in aws")
        void shouldRejectTypoInAws() {
            assertThatThrownBy(() -> CloudProvider.of("amazon"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cloud provider: 'amazon'");
        }

        @Test
        @DisplayName("should reject typo in gcp")
        void shouldRejectTypoInGcp() {
            assertThatThrownBy(() -> CloudProvider.of("google"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid cloud provider: 'google'");
        }
    }

    @Nested
    @DisplayName("Normalization")
    class Normalization {

        @Test
        @DisplayName("should normalize uppercase azure to lowercase")
        void shouldNormalizeUppercaseAzureToLowercase() {
            CloudProvider provider = CloudProvider.of("AZURE");
            assertThat(provider.value()).isEqualTo("azure");
        }

        @Test
        @DisplayName("should normalize mixed case AWS to lowercase")
        void shouldNormalizeMixedCaseAwsToLowercase() {
            CloudProvider provider = CloudProvider.of("Aws");
            assertThat(provider.value()).isEqualTo("aws");
        }

        @Test
        @DisplayName("should normalize uppercase GCP to lowercase")
        void shouldNormalizeUppercaseGcpToLowercase() {
            CloudProvider provider = CloudProvider.of("GCP");
            assertThat(provider.value()).isEqualTo("gcp");
        }

        @Test
        @DisplayName("should trim leading whitespace")
        void shouldTrimLeadingWhitespace() {
            CloudProvider provider = CloudProvider.of("  azure");
            assertThat(provider.value()).isEqualTo("azure");
        }

        @Test
        @DisplayName("should trim trailing whitespace")
        void shouldTrimTrailingWhitespace() {
            CloudProvider provider = CloudProvider.of("aws  ");
            assertThat(provider.value()).isEqualTo("aws");
        }

        @Test
        @DisplayName("should trim leading and trailing whitespace")
        void shouldTrimLeadingAndTrailingWhitespace() {
            CloudProvider provider = CloudProvider.of("  gcp  ");
            assertThat(provider.value()).isEqualTo("gcp");
        }

        @Test
        @DisplayName("should normalize and trim together")
        void shouldNormalizeAndTrimTogether() {
            CloudProvider provider = CloudProvider.of("  AZURE  ");
            assertThat(provider.value()).isEqualTo("azure");
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("azure() should create Azure provider")
        void azureShouldCreateAzureProvider() {
            CloudProvider provider = CloudProvider.azure();

            assertThat(provider.value()).isEqualTo("azure");
            assertThat(provider).isEqualTo(CloudProvider.of("azure"));
        }

        @Test
        @DisplayName("aws() should create AWS provider")
        void awsShouldCreateAwsProvider() {
            CloudProvider provider = CloudProvider.aws();

            assertThat(provider.value()).isEqualTo("aws");
            assertThat(provider).isEqualTo(CloudProvider.of("aws"));
        }

        @Test
        @DisplayName("gcp() should create GCP provider")
        void gcpShouldCreateGcpProvider() {
            CloudProvider provider = CloudProvider.gcp();

            assertThat(provider.value()).isEqualTo("gcp");
            assertThat(provider).isEqualTo(CloudProvider.of("gcp"));
        }
    }

    @Nested
    @DisplayName("Equality and Hashcode")
    class EqualityAndHashcode {

        @Test
        @DisplayName("should be equal when values match")
        void shouldBeEqualWhenValuesMatch() {
            CloudProvider provider1 = CloudProvider.azure();
            CloudProvider provider2 = CloudProvider.of("azure");

            assertThat(provider1).isEqualTo(provider2);
            assertThat(provider1.hashCode()).isEqualTo(provider2.hashCode());
        }

        @Test
        @DisplayName("should be equal after normalization")
        void shouldBeEqualAfterNormalization() {
            CloudProvider provider1 = CloudProvider.of("azure");
            CloudProvider provider2 = CloudProvider.of("AZURE");
            CloudProvider provider3 = CloudProvider.of("  azure  ");

            assertThat(provider1).isEqualTo(provider2);
            assertThat(provider1).isEqualTo(provider3);
            assertThat(provider1.hashCode()).isEqualTo(provider2.hashCode());
            assertThat(provider1.hashCode()).isEqualTo(provider3.hashCode());
        }

        @Test
        @DisplayName("should not be equal when values differ")
        void shouldNotBeEqualWhenValuesDiffer() {
            CloudProvider azure = CloudProvider.azure();
            CloudProvider aws = CloudProvider.aws();
            CloudProvider gcp = CloudProvider.gcp();

            assertThat(azure).isNotEqualTo(aws);
            assertThat(azure).isNotEqualTo(gcp);
            assertThat(aws).isNotEqualTo(gcp);
        }

        @Test
        @DisplayName("should not be equal to null")
        void shouldNotBeEqualToNull() {
            CloudProvider provider = CloudProvider.azure();
            assertThat(provider).isNotEqualTo(null);
        }

        @Test
        @DisplayName("should not be equal to different type")
        void shouldNotBeEqualToDifferentType() {
            CloudProvider provider = CloudProvider.azure();
            assertThat(provider).isNotEqualTo("azure");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return normalized value")
        void shouldReturnNormalizedValue() {
            CloudProvider provider = CloudProvider.of("AZURE");
            assertThat(provider.toString()).isEqualTo("azure");
        }

        @Test
        @DisplayName("should be suitable for template rendering")
        void shouldBeSuitableForTemplateRendering() {
            CloudProvider azure = CloudProvider.azure();
            CloudProvider aws = CloudProvider.aws();
            CloudProvider gcp = CloudProvider.gcp();

            assertThat(azure.toString()).isEqualTo("azure");
            assertThat(aws.toString()).isEqualTo("aws");
            assertThat(gcp.toString()).isEqualTo("gcp");
        }
    }
}
