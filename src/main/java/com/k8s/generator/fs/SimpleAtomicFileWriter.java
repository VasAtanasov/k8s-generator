package com.k8s.generator.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Simple atomic writer using a temp directory and an atomic move where supported.
 */
final class SimpleAtomicFileWriter implements AtomicFileWriter {

    @Override
    public void writeAll(Path outputDir, Map<Path, String> files) {
        try {
            if (Files.exists(outputDir)) {
                throw new FileAlreadyExistsException("Output directory exists: " + outputDir);
            }

            Path parent = outputDir.toAbsolutePath().getParent();
            if (parent == null) parent = Path.of(".").toAbsolutePath().normalize();
            Path tmp = parent.resolve(".tmp-k8s-gen-" + UUID.randomUUID());
            Files.createDirectories(tmp);

            // write all files under tmp
            for (Map.Entry<Path, String> e : files.entrySet()) {
                Path rel = e.getKey();
                Path dest = tmp.resolve(rel.toString());
                Files.createDirectories(dest.getParent() == null ? tmp : dest.getParent());
                Files.writeString(dest, e.getValue(), StandardCharsets.UTF_8);
            }

            // move tmp → outputDir atomically if possible
            try {
                Files.move(tmp, outputDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception unsupported) {
                // fallback to a two-step move
                Files.move(tmp, outputDir, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Atomic write failed: " + e.getMessage(), e);
        }
    }
}

