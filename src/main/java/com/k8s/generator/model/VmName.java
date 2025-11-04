package com.k8s.generator.model;

import java.util.Objects;

public record VmName(String value) {
    public VmName {
        Objects.requireNonNull(value, "name is required");

        if (value.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

    public static VmName of(String value) {
        return new VmName(value);
    }
}
