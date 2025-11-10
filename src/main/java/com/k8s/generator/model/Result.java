package com.k8s.generator.model;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Type-safe result container for operations that can succeed or fail.
 *
 * <p>Design Pattern: Railway-oriented programming for explicit error handling.
 * Replaces exception-based control flow with explicit success/failure types.
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Type Safety</b>: Sealed interface prevents external implementations</li>
 *   <li><b>Exhaustive Matching</b>: Switch expressions cover all cases at compile-time</li>
 *   <li><b>Immutability</b>: Success and Failure records are immutable</li>
 *   <li><b>Non-null</b>: Values validated in record constructors</li>
 * </ul>
 *
 * <p>Usage Patterns:
 * <pre>{@code
 * // 1. Creating results
 * Result<String, String> success = Result.success("data");
 * Result<String, String> failure = Result.failure("error message");
 *
 * // 2. Pattern matching (preferred)
 * String output = switch (result) {
 *     case Result.Success<String, String>(var value) -> "Got: " + value;
 *     case Result.Failure<String, String>(var error) -> "Error: " + error;
 * };
 *
 * // 3. Conditional processing
 * if (result.isSuccess()) {
 *     String value = result.orElseThrow(e -> new RuntimeException(e));
 *     // process value...
 * }
 *
 * // 4. Mapping transformations
 * Result<Integer, String> length = result.map(String::length);
 *
 * // 5. Flat mapping for chaining
 * Result<User, String> user = parseId(input)
 *     .flatMap(id -> findUser(id))
 *     .flatMap(user -> validateUser(user));
 * }</pre>
 *
 * @param <T> Success value type
 * @param <E> Error value type
 * @since 1.0.0
 */
public sealed interface Result<T, E> {

    /**
     * Success case containing a valid result value.
     *
     * @param value the success value (must be non-null)
     * @param <T>   success type
     * @param <E>   error type (unused in success case)
     */
    record Success<T, E>(T value) implements Result<T, E> {
        public Success {
            Objects.requireNonNull(value, "success value cannot be null");
        }
    }

    /**
     * Failure case containing an error value.
     *
     * @param error the error value (must be non-null)
     * @param <T>   success type (unused in failure case)
     * @param <E>   error type
     */
    record Failure<T, E>(E error) implements Result<T, E> {
        public Failure {
            Objects.requireNonNull(error, "error value cannot be null");
        }
    }

    /**
     * Creates a successful result.
     *
     * @param value success value (must be non-null)
     * @param <T>   success type
     * @param <E>   error type
* @return Success instance containing value
     * @throws NullPointerException if value is null
     */
    static <T, E> Result<T, E> success(T value) {
        return new Success<>(value);
    }

    /**
     * Creates a failed result.
     *
     * @param error error value (must be non-null)
     * @param <T>   success type
     * @param <E>   error type
     * @return Failure instance containing error
     * @throws NullPointerException if error is null
     */
    static <T, E> Result<T, E> failure(E error) {
        return new Failure<>(error);
    }

    /**
     * Checks if this result is successful.
     *
     * @return true if Success, false if Failure
     */
    default boolean isSuccess() {
        return this instanceof Success<T, E>;
    }

    /**
     * Checks if this result is a failure.
     *
     * @return true if Failure, false if Success
     */
    default boolean isFailure() {
        return this instanceof Failure<T, E>;
    }

    /**
     * Returns the success value or throws an exception.
     *
     * @param exceptionMapper function to convert error to exception
     * @return success value if present
     * @throws RuntimeException (or subclass) if this is a failure
     */
    default T orElseThrow(Function<E, ? extends RuntimeException> exceptionMapper) {
        Objects.requireNonNull(exceptionMapper, "exceptionMapper cannot be null");
        return switch (this) {
            case Success<T, E> s -> s.value();
            case Failure<T, E> f -> throw exceptionMapper.apply(f.error());
        };
    }

    /**
     * Returns the value if success, or throws exception if failure.
     *
     * @return the success value
     * @throws RuntimeException if this is a Failure
     */
    default T orElseThrow() {
        return switch (this) {
            case Success<T, E> s -> s.value();
            case Failure<T, E> f -> throw new RuntimeException("Operation failed: " + f.error());
        };
    }

    /**
     * Returns the success value if present, otherwise returns the default value.
     *
     * @param defaultValue value to return if this is a failure
     * @return success value or defaultValue
     */
    default T orElse(T defaultValue) {
        return switch (this) {
            case Success<T, E> s -> s.value();
            case Failure<T, E> f -> defaultValue;
        };
    }

    /**
     * Returns the success value as Optional.
     *
     * @return Optional containing value if success, empty if failure
     */
    default Optional<T> toOptional() {
        return switch (this) {
            case Success<T, E> s -> Optional.of(s.value());
            case Failure<T, E> f -> Optional.empty();
        };
    }

    /**
     * Transforms the success value using the provided mapper.
     * Failures pass through unchanged.
     *
     * @param mapper transformation function
     * @param <U>    new success type
     * @return Result with transformed value or original failure
     */
    default <U> Result<U, E> map(Function<T, U> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        return switch (this) {
            case Success<T, E> s -> Result.success(mapper.apply(s.value()));
            case Failure<T, E> f -> Result.<U, E>failure(f.error());
        };
    }

    /**
     * Transforms the success value using a mapper that returns a Result.
     * Used for chaining operations that can fail.
     *
     * @param mapper function that returns a Result
     * @param <U>    new success type
     * @return Result from mapper if success, original failure otherwise
     */
    default <U> Result<U, E> flatMap(Function<T, Result<U, E>> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        return switch (this) {
            case Success<T, E> s -> mapper.apply(s.value());
            case Failure<T, E> f -> Result.<U, E>failure(f.error());
        };
    }

    /**
     * Transforms the error value using the provided mapper.
     * Successes pass through unchanged.
     *
     * @param mapper transformation function for errors
     * @param <F>    new error type
     * @return Result with original value or transformed error
     */
    default <F> Result<T, F> mapError(Function<? super E, ? extends F> mapper) {
        Objects.requireNonNull(mapper, "mapper cannot be null");
        return switch (this) {
            case Success<T, E> s -> Result.success(s.value());
            case Failure<T, E> f -> Result.failure(mapper.apply(f.error()));
        };
    }

    /**
     * Returns the error if failure, or null if success.
     *
     * @return the error value or null
     */
    default E getError() {
        return switch (this) {
            case Success<T, E> s -> null;
            case Failure<T, E> f -> f.error();
        };
    }
}