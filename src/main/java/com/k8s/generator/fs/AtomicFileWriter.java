package com.k8s.generator.fs;

import java.nio.file.Path;
import java.util.Map;

/**
 * Writes multiple files atomically to an output directory.
 * <p>
 * Contract:
 * - Atomic: either all files are written or none
 * - Idempotent: same inputs produce identical outputs
 * - Out dir must not already exist
 */
public interface AtomicFileWriter {
    void writeAll(Path outputDir, Map<Path, String> files);
}

