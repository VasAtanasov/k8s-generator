package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SizeProfile enum.
 */
class SizeProfileTest {

    @Test
    void shouldHaveAllExpectedValues() {
        SizeProfile[] profiles = SizeProfile.values();

        assertThat(profiles).hasSize(3);
        assertThat(profiles).containsExactlyInAnyOrder(
                SizeProfile.SMALL,
                SizeProfile.MEDIUM,
                SizeProfile.LARGE
        );
    }

    @ParameterizedTest
    @CsvSource({
            "SMALL, 2, 4096",
            "MEDIUM, 4, 8192",
            "LARGE, 6, 12288"
    })
    void shouldHaveCorrectResourceAllocations(SizeProfile profile, int expectedCpus, int expectedMemory) {
        assertThat(profile.getCpus()).isEqualTo(expectedCpus);
        assertThat(profile.getMemoryMb()).isEqualTo(expectedMemory);
    }

    @ParameterizedTest
    @CsvSource({
            "SMALL, small",
            "MEDIUM, medium",
            "LARGE, large"
    })
    void shouldConvertToLowerCaseString(SizeProfile profile, String expected) {
        assertThat(profile.toLowerCaseString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"small", "SMALL", "Small", "sMaLl"})
    void shouldParseCaseInsensitiveSmall(String input) {
        assertThat(SizeProfile.fromString(input)).isEqualTo(SizeProfile.SMALL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"medium", "MEDIUM", "Medium"})
    void shouldParseCaseInsensitiveMedium(String input) {
        assertThat(SizeProfile.fromString(input)).isEqualTo(SizeProfile.MEDIUM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"large", "LARGE", "Large"})
    void shouldParseCaseInsensitiveLarge(String input) {
        assertThat(SizeProfile.fromString(input)).isEqualTo(SizeProfile.LARGE);
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> SizeProfile.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "xlarge", "tiny", "", "  "})
    void shouldRejectInvalidValues(String input) {
        assertThatThrownBy(() -> SizeProfile.fromString(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid size profile")
                .hasMessageContaining(input);
    }

    @Test
    void shouldIncludeValidValuesInErrorMessage() {
        assertThatThrownBy(() -> SizeProfile.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("small, medium, large");
    }

    @Test
    void shouldProvideMinimalResourcesForSmall() {
        assertThat(SizeProfile.SMALL.getCpus()).isEqualTo(2);
        assertThat(SizeProfile.SMALL.getMemoryMb()).isEqualTo(4096);
    }

    @Test
    void shouldProvideBalancedResourcesForMedium() {
        assertThat(SizeProfile.MEDIUM.getCpus()).isEqualTo(4);
        assertThat(SizeProfile.MEDIUM.getMemoryMb()).isEqualTo(8192);
    }

    @Test
    void shouldProvideGenerousResourcesForLarge() {
        assertThat(SizeProfile.LARGE.getCpus()).isEqualTo(6);
        assertThat(SizeProfile.LARGE.getMemoryMb()).isEqualTo(12288);
    }
}
