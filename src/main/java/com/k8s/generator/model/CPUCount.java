package com.k8s.generator.model;

import java.util.Objects;

/**
 * Value Object representing a count of virtual CPUs.
 *
 * <p>Contract:
 * - Immutable, validated in the compact constructor
 * - Must be >= 1
 * - Provides convenience factory and accessors
 */
public record CPUCount(int value) {

    public CPUCount {
        if (value < 1) {
            throw new IllegalArgumentException("CPU count must be >= 1, got: " + value);
        }
    }

    /**
     * Creates a CPUCount from an integer value.
     *
     * @param value number of CPUs (>=1)
     * @return CPUCount instance
     */
    public static CPUCount of(int value) {
        return new CPUCount(value);
    }

    /**
     * Returns the CPU count as primitive int.
     */
    public int toInt() {
        return value;
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}

