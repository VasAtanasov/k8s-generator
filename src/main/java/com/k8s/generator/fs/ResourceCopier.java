package com.k8s.generator.fs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Copies resource scripts from classpath to a target directory.
 */
public final class ResourceCopier {
    public void copyScripts(List<String> scriptNames, Path targetDir) {
        try {
            Files.createDirectories(targetDir);
            for (String name : scriptNames) {
                String resourcePath = "/scripts/" + name;
                try (InputStream in = ResourceCopier.class.getResourceAsStream(resourcePath)) {
                    if (in == null) {
                        // Skip missing optional scripts gracefully in Phase 1
                        continue;
                    }
                    Path out = targetDir.resolve(name);
                    Files.copy(in, out);
                    try {
                        Files.setPosixFilePermissions(out, java.util.Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                            java.nio.file.attribute.PosixFilePermission.OTHERS_READ
                        ));
                    } catch (UnsupportedOperationException ignored) {
                        // Windows/NTFS: ignore POSIX permission setting
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy resource scripts: " + e.getMessage(), e);
        }
    }
}

