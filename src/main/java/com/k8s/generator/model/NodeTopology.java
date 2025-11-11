package com.k8s.generator.model;

import java.util.Objects;

/**
 * Immutable record representing the node topology of a Kubernetes cluster.
 *
 * @param masters Number of master nodes.
 * @param workers Number of worker nodes.
 */
public record NodeTopology(int masters, int workers) {

    public NodeTopology {
        if (masters < 0) {
            throw new IllegalArgumentException("Master node count cannot be negative: " + masters);
        }
        if (workers < 0) {
            throw new IllegalArgumentException("Worker node count cannot be negative: " + workers);
        }
    }

    /**
     * Factory method to create a NodeTopology instance.
     *
     * @param masters Number of master nodes.
     * @param workers Number of worker nodes.
     * @return A new NodeTopology instance.
     */
    public static NodeTopology of(int masters, int workers) {
        return new NodeTopology(masters, workers);
    }

    /**
     * Returns the total number of nodes (masters + workers).
     *
     * @return The total node count.
     */
    public int totalNodes() {
        return masters + workers;
    }
}
