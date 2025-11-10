package com.k8s.generator.model;

/**
 * Value Object representing a quantity of memory.
 *
 * <p>The unit used internally is megabytes to match Vagrant/VirtualBox input,
 * but callers should rely on the type rather than the unit name. Use the
 * provided factories for clarity.
 */
public record MemoryInMB(int megabytes) {

    public MemoryInMB {
        if (megabytes <= 0) {
            throw new IllegalArgumentException("Memory size must be > 0 MB, got: " + megabytes);
        }
    }

    /**
     * Creates a MemoryInMB from megabytes.
     */
    public static MemoryInMB ofMegabytes(int mb) {
        return new MemoryInMB(mb);
    }

    /**
     * Creates a MemoryInMB from gigabytes.
     */
    public static MemoryInMB ofGigabytes(int gb) {
        if (gb <= 0) {
            throw new IllegalArgumentException("Gigabytes must be > 0, got: " + gb);
        }
        return new MemoryInMB(Math.multiplyExact(gb, 1024));
    }

    /**
     * Returns the size in megabytes.
     */
    public int toMegabytes() {
        return megabytes;
    }

    /**
     * Returns the size in gigabytes, rounded down.
     */
    public int toGigabytesFloor() {
        return megabytes / 1024;
    }

    @Override
    public String toString() {
        return megabytes + "MB";
    }
}

