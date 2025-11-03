package com.k8s.generator.model;

public record ClusterName(String value) {
    @Override
    public String toString() {
        return value;
    }
}
