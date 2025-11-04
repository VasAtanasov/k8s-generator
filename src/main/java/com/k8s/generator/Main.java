package com.k8s.generator;

import com.k8s.generator.cli.GenerateCommand;
import picocli.CommandLine;

/**
 * CLI entrypoint for k8s-generator.
 * <p>
 * Delegates to Picocli and exits with orchestrator-provided code:
 * - 0: success
 * - 1: unexpected error
 * - 2: validation failure
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new GenerateCommand()).execute(args);
        System.exit(exitCode);
    }
}