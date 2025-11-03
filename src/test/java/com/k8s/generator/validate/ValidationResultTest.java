package com.k8s.generator.validate;

import com.k8s.generator.model.ValidationError;
import com.k8s.generator.model.ValidationLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ValidationResult class.
 */
class ValidationResultTest {

    @Test
    void shouldCreateValidResult() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.errors()).isEmpty();
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void shouldCreateResultWithSingleError() {
        var error = new ValidationError(
            "field",
            ValidationLevel.SEMANTIC,
            "message",
            "suggestion"
        );

        ValidationResult result = ValidationResult.of(error);

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errorCount()).isOne();
        assertThat(result.errors()).containsExactly(error);
    }

    @Test
    void shouldCreateResultWithMultipleErrors() {
        var error1 = new ValidationError(
            "field1", ValidationLevel.SEMANTIC, "msg1", "sugg1"
        );
        var error2 = new ValidationError(
            "field2", ValidationLevel.SEMANTIC, "msg2", "sugg2"
        );

        ValidationResult result = ValidationResult.of(List.of(error1, error2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isEqualTo(2);
        assertThat(result.errors()).containsExactly(error1, error2);
    }

    @Test
    void shouldRejectNullErrorInOf() {
        assertThatThrownBy(() -> ValidationResult.of((ValidationError) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("error cannot be null");
    }

    @Test
    void shouldRejectNullErrorListInOf() {
        assertThatThrownBy(() -> ValidationResult.of((List<ValidationError>) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("errors list cannot be null");
    }

    @Test
    void shouldRejectErrorListContainingNulls() {
        var error = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );

        assertThatThrownBy(() -> ValidationResult.of(List.of(error, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("errors list contains null elements");
    }

    @Test
    void shouldCombineMultipleResults() {
        var error1 = new ValidationError(
            "field1", ValidationLevel.SEMANTIC, "msg1", "sugg1"
        );
        var error2 = new ValidationError(
            "field2", ValidationLevel.SEMANTIC, "msg2", "sugg2"
        );
        var error3 = new ValidationError(
            "field3", ValidationLevel.POLICY, "msg3", "sugg3"
        );

        var result1 = ValidationResult.of(error1);
        var result2 = ValidationResult.of(List.of(error2, error3));
        var result3 = ValidationResult.valid();

        ValidationResult combined = ValidationResult.combine(result1, result2, result3);

        assertThat(combined.hasErrors()).isTrue();
        assertThat(combined.errorCount()).isEqualTo(3);
        assertThat(combined.errors()).containsExactly(error1, error2, error3);
    }

    @Test
    void shouldCombineValidResults() {
        var result1 = ValidationResult.valid();
        var result2 = ValidationResult.valid();

        ValidationResult combined = ValidationResult.combine(result1, result2);

        assertThat(combined.isValid()).isTrue();
        assertThat(combined.errors()).isEmpty();
    }

    @Test
    void shouldRejectNullInCombine() {
        var result = ValidationResult.valid();

        assertThatThrownBy(() -> ValidationResult.combine(result, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("result cannot be null");
    }

    @Test
    void shouldFormatErrorsAsString() {
        var error1 = new ValidationError(
            "field1", ValidationLevel.SEMANTIC, "msg1", "sugg1"
        );
        var error2 = new ValidationError(
            "field2", ValidationLevel.POLICY, "msg2", "sugg2"
        );

        ValidationResult result = ValidationResult.of(List.of(error1, error2));

        String formatted = result.formatErrors();

        assertThat(formatted)
            .contains("[SEMANTIC]")
            .contains("field1")
            .contains("msg1")
            .contains("sugg1")
            .contains("[POLICY]")
            .contains("field2")
            .contains("msg2")
            .contains("sugg2");
    }

    @Test
    void shouldReturnEmptyStringForValidResult() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.formatErrors()).isEmpty();
    }

    @Test
    void shouldAddErrorImmutably() {
        var error1 = new ValidationError(
            "field1", ValidationLevel.SEMANTIC, "msg1", "sugg1"
        );
        var error2 = new ValidationError(
            "field2", ValidationLevel.SEMANTIC, "msg2", "sugg2"
        );

        ValidationResult original = ValidationResult.of(error1);
        ValidationResult updated = original.withError(error2);

        // Original unchanged
        assertThat(original.errorCount()).isOne();
        assertThat(original.errors()).containsExactly(error1);

        // Updated has both errors
        assertThat(updated.errorCount()).isEqualTo(2);
        assertThat(updated.errors()).containsExactly(error1, error2);
    }

    @Test
    void shouldRejectNullInWithError() {
        ValidationResult result = ValidationResult.valid();

        assertThatThrownBy(() -> result.withError(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("error cannot be null");
    }

    @Test
    void shouldMergeResultsImmutably() {
        var error1 = new ValidationError(
            "field1", ValidationLevel.SEMANTIC, "msg1", "sugg1"
        );
        var error2 = new ValidationError(
            "field2", ValidationLevel.SEMANTIC, "msg2", "sugg2"
        );
        var error3 = new ValidationError(
            "field3", ValidationLevel.POLICY, "msg3", "sugg3"
        );

        ValidationResult result1 = ValidationResult.of(List.of(error1, error2));
        ValidationResult result2 = ValidationResult.of(error3);

        ValidationResult merged = result1.merge(result2);

        // Originals unchanged
        assertThat(result1.errorCount()).isEqualTo(2);
        assertThat(result2.errorCount()).isOne();

        // Merged has all errors
        assertThat(merged.errorCount()).isEqualTo(3);
        assertThat(merged.errors()).containsExactly(error1, error2, error3);
    }

    @Test
    void shouldRejectNullInMerge() {
        ValidationResult result = ValidationResult.valid();

        assertThatThrownBy(() -> result.merge(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("other cannot be null");
    }

    @Test
    void shouldImplementEqualsAndHashCode() {
        var error = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );

        ValidationResult result1 = ValidationResult.of(error);
        ValidationResult result2 = ValidationResult.of(error);
        ValidationResult result3 = ValidationResult.valid();

        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        assertThat(result1).isNotEqualTo(result3);
    }

    @Test
    void shouldProvideDescriptiveToString() {
        ValidationResult valid = ValidationResult.valid();
        assertThat(valid.toString()).contains("valid");

        var error = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );
        ValidationResult invalid = ValidationResult.of(error);

        assertThat(invalid.toString())
            .contains("ValidationResult")
            .contains("1 error");
    }

    @Test
    void shouldReturnUnmodifiableErrorsList() {
        var error = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );
        ValidationResult result = ValidationResult.of(error);

        assertThatThrownBy(() -> result.errors().add(error))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldMakeDefensiveCopyOfErrorsList() {
        var error = new ValidationError(
            "field", ValidationLevel.SEMANTIC, "msg", "sugg"
        );
        var mutableList = new ArrayList<ValidationError>();
        mutableList.add(error);

        ValidationResult result = ValidationResult.of(mutableList);

        // Modify original list
        mutableList.clear();

        // Result's list should be unchanged
        assertThat(result.errorCount()).isOne();
    }
}
