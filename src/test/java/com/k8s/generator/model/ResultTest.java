package com.k8s.generator.model;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Result sealed interface and its implementations.
 */
class ResultTest {

    @Test
    void shouldCreateSuccessResult() {
        Result<String, String> result = Result.success("value");

        assertThat(result).isInstanceOf(Result.Success.class);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFailure()).isFalse();
    }

    @Test
    void shouldCreateFailureResult() {
        Result<String, String> result = Result.failure("error");

        assertThat(result).isInstanceOf(Result.Failure.class);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFailure()).isTrue();
    }

    @Test
    void shouldRejectNullSuccessValue() {
        assertThatThrownBy(() -> Result.success(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("success value cannot be null");
    }

    @Test
    void shouldRejectNullFailureValue() {
        assertThatThrownBy(() -> Result.failure(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("error value cannot be null");
    }

    @Test
    void shouldExtractSuccessValue() {
        Result<String, String> result = Result.success("test-value");

        String value = result.orElseThrow(e -> new RuntimeException(e));

        assertThat(value).isEqualTo("test-value");
    }

    @Test
    void shouldThrowOnFailure() {
        Result<String, String> result = Result.failure("test-error");

        assertThatThrownBy(() -> result.orElseThrow(RuntimeException::new))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("test-error");
    }

    @Test
    void shouldReturnValueWithOrElse() {
        Result<String, String> success = Result.success("value");
        Result<String, String> failure = Result.failure("error");

        assertThat(success.orElse("default")).isEqualTo("value");
        assertThat(failure.orElse("default")).isEqualTo("default");
    }

    @Test
    void shouldConvertToOptional() {
        Result<String, String> success = Result.success("value");
        Result<String, String> failure = Result.failure("error");

        assertThat(success.toOptional()).isEqualTo(Optional.of("value"));
        assertThat(failure.toOptional()).isEqualTo(Optional.empty());
    }

    @Test
    void shouldMapSuccessValue() {
        Result<String, String> result = Result.success("test");

        Result<Integer, String> mapped = result.map(String::length);

        assertThat(mapped.isSuccess()).isTrue();
        assertThat(mapped.orElseThrow(e -> new RuntimeException(e))).isEqualTo(4);
    }

    @Test
    void shouldNotMapFailure() {
        Result<String, String> result = Result.failure("error");

        Result<Integer, String> mapped = result.map(String::length);

        assertThat(mapped.isFailure()).isTrue();
        assertThatThrownBy(() -> mapped.orElseThrow(RuntimeException::new))
            .hasMessage("error");
    }

    @Test
    void shouldFlatMapSuccess() {
        Result<String, String> result = Result.success("123");

        Result<Integer, String> flatMapped = result.flatMap(str -> {
            try {
                return Result.success(Integer.parseInt(str));
            } catch (NumberFormatException e) {
                return Result.failure("Invalid number");
            }
        });

        assertThat(flatMapped.isSuccess()).isTrue();
        assertThat(flatMapped.orElseThrow(e -> new RuntimeException(e))).isEqualTo(123);
    }

    @Test
    void shouldFlatMapFailure() {
        Result<String, String> result = Result.failure("original error");

        Result<Integer, String> flatMapped = result.flatMap(str ->
            Result.success(Integer.parseInt(str))
        );

        assertThat(flatMapped.isFailure()).isTrue();
        assertThatThrownBy(() -> flatMapped.orElseThrow(RuntimeException::new))
            .hasMessage("original error");
    }

    @Test
    void shouldMapError() {
        Result<String, String> result = Result.failure("error");

        Result<String, Integer> mapped = result.mapError(String::length);

        assertThat(mapped.isFailure()).isTrue();
        assertThatThrownBy(() -> mapped.orElseThrow(len -> new RuntimeException("Length: " + len)))
            .hasMessage("Length: 5");
    }

    @Test
    void shouldNotMapErrorOnSuccess() {
        Result<String, String> result = Result.success("value");

        Result<String, Integer> mapped = result.mapError(String::length);

        assertThat(mapped.isSuccess()).isTrue();
        assertThat(mapped.orElseThrow(e -> new RuntimeException())).isEqualTo("value");
    }

    @Test
    void shouldPatternMatchSuccess() {
        Result<String, String> result = Result.success("test");

        String output = switch (result) {
            case Result.Success<String, String>(var value) -> "Got: " + value;
            case Result.Failure<String, String>(var error) -> "Error: " + error;
        };

        assertThat(output).isEqualTo("Got: test");
    }

    @Test
    void shouldPatternMatchFailure() {
        Result<String, String> result = Result.failure("test-error");

        String output = switch (result) {
            case Result.Success<String, String>(var value) -> "Got: " + value;
            case Result.Failure<String, String>(var error) -> "Error: " + error;
        };

        assertThat(output).isEqualTo("Error: test-error");
    }

//    @Test
//    void shouldChainOperations() {
//        Result<String, String> result = Result.success("  test  ")
//            .map(String::trim)
//            .map(String::toUpperCase)
//            .flatMap(str -> Result.success(str + "!"));
//
//        assertThat(result.isSuccess()).isTrue();
//        assertThat(result.orElseThrow(e -> new RuntimeException(e))).isEqualTo("TEST!");
//    }
//
//    @Test
//    void shouldShortCircuitOnFailure() {
//        Result<String, String> result = Result.<String, String>success("test")
//            .flatMap(str -> Result.failure("failed"))
//            .map(String::toUpperCase)  // This should not execute
//            .flatMap(str -> Result.success(str + "!"));  // This should not execute
//
//        assertThat(result.isFailure()).isTrue();
//        assertThatThrownBy(() -> result.orElseThrow(RuntimeException::new))
//            .hasMessage("failed");
//    }

    @Test
    void shouldRejectNullMapper() {
        Result<String, String> result = Result.success("test");

        assertThatThrownBy(() -> result.map(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("mapper cannot be null");
    }

    @Test
    void shouldRejectNullFlatMapper() {
        Result<String, String> result = Result.success("test");

        assertThatThrownBy(() -> result.flatMap(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("mapper cannot be null");
    }

    @Test
    void shouldRejectNullErrorMapper() {
        Result<String, String> result = Result.failure("error");

        assertThatThrownBy(() -> result.mapError(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("mapper cannot be null");
    }

    @Test
    void shouldRejectNullExceptionMapper() {
        Result<String, String> result = Result.failure("error");

        assertThatThrownBy(() -> result.orElseThrow(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("exceptionMapper cannot be null");
    }
}
