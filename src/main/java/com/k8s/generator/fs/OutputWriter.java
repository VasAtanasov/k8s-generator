package com.k8s.generator.fs;

import java.io.IOException;
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

    /**
     * <pre>
     * scripts/
     * ├── bootstrap.pre.d/
     * ├── bootstrap.post.d/
     * ├── bootstrap.env.local
     * ├── bootstrap.pre.local.sh
     * └── bootstrap.post.local.sh
     * </pre>
     *
     *
     * @param destination output directory containing the {@code scripts/} folder
     * @throws IOException reserved for future concrete implementation
     */
    public void scaffoldHooks(Path destination) throws IOException {
        throw new UnsupportedOperationException("scaffoldHooks API stub; not implemented yet");
    }
}
