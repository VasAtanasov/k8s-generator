package com.k8s.generator.fs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

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
                        Files.setPosixFilePermissions(out, Set.of(
                                OWNER_EXECUTE,
                                OWNER_READ,
                                GROUP_READ,
                                OTHERS_READ
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

