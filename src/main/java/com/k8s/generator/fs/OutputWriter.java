package com.k8s.generator.fs;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Facade for writing rendered files (Phase 1).
 */
public final class OutputWriter {

    private final AtomicFileWriter writer = new SimpleAtomicFileWriter();

    /**
     * Writes files into the output directory atomically.
     * Keys in files are relative paths (e.g., "Vagrantfile", "scripts/bootstrap.sh").
     */
    public void writeFiles(Map<String, String> files, Path outDir) {
        Map<Path, String> pathMap = new HashMap<>();
        files.forEach((k, v) -> pathMap.put(Path.of(k), v));
        writer.writeAll(outDir, pathMap);
    }
}

