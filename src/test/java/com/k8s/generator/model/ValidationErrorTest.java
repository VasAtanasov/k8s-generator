package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ValidationError record.
 */
class ValidationErrorTest {

    @Test
    void shouldCreateValidErrorWithAllFields() {
        var error = new ValidationError(
            "clusters[].name",
            ValidationLevel.SEMANTIC,
            "Invalid cluster name",
            "Use lowercase letters only"
        );

        assertThat(error.field()).isEqualTo("clusters[].name");
        assertThat(error.level()).isEqualTo(ValidationLevel.SEMANTIC);
        assertThat(error.message()).isEqualTo("Invalid cluster name");
        assertThat(error.suggestion()).isEqualTo("Use lowercase letters only");
    }

    @Test
    void shouldRejectNullField() {
        assertThatThrownBy(() -> new ValidationError(
            null,
            ValidationLevel.SEMANTIC,
            "message",
            "suggestion"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("field is required");
    }

    @Test
    void shouldRejectNullLevel() {
        assertThatThrownBy(() -> new ValidationError(
            "field",
            null,
            "message",
            "suggestion"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("level is required");
    }

    @Test
    void shouldRejectNullMessage() {
        assertThatThrownBy(() -> new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            null,
            "suggestion"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("message is required");
    }

    @Test
    void shouldRejectNullSuggestion() {
        assertThatThrownBy(() -> new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            "message",
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("suggestion is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankField(String blank) {
        assertThatThrownBy(() -> new ValidationError(
            blank,
            ValidationLevel.SEMANTIC,
            "message",
            "suggestion"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("field cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankMessage(String blank) {
        assertThatThrownBy(() -> new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            blank,
            "suggestion"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("message cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankSuggestion(String blank) {
        assertThatThrownBy(() -> new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            "message",
            blank
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("suggestion cannot be blank");
    }

    @Test
    void shouldFormatErrorMessage() {
        var error = new ValidationError(
            "clusters[name='staging'].firstIp",
            ValidationLevel.SEMANTIC,
            "Multi-cluster requires explicit firstIp",
            "Add: firstIp: 192.168.56.20"
        );

        String formatted = error.format();

        assertThat(formatted)
            .contains("[SEMANTIC]")
            .contains("clusters[name='staging'].firstIp")
            .contains("Multi-cluster requires explicit firstIp")
            .contains("Suggestion:")
            .contains("Add: firstIp: 192.168.56.20");
    }

    @Test
    void shouldIncludeAllValidationLevelsInFormatting() {
        var structural = new ValidationError(
            "field", ValidationLevel.STRUCTURAL, "msg", "sugg"
        );
        var semantic = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );
        var policy = new ValidationError(
            "field", ValidationLevel.POLICY, "msg", "sugg"
        );

        assertThat(structural.format()).contains("[STRUCTURAL]");
        assertThat(semantic.format()).contains("[SEMANTIC]");
        assertThat(policy.format()).contains("[POLICY]");
    }

    @Test
    void shouldUseFormatMethodInToString() {
        var error = new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            "message",
            "suggestion"
        );

        assertThat(error.toString()).isEqualTo(error.format());
    }

    @Test
    void shouldBeImmutable() {
        var error = new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            "message",
            "suggestion"
        );

        // Records are immutable by design - this just verifies the contract
        assertThat(error.field()).isEqualTo("field");
        assertThat(error.level()).isEqualTo(ValidationLevel.SEMANTIC);
        assertThat(error.message()).isEqualTo("message");
        assertThat(error.suggestion()).isEqualTo("suggestion");
    }
}
