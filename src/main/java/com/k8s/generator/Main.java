package com.k8s.generator;

import com.k8s.generator.cli.GenerateCommand;
import picocli.CommandLine;

/**
 * CLI entrypoint for k8s-generator.
 *
 * Delegates to Picocli and exits with orchestrator-provided code:
 *  - 0: success
 *  - 2: validation failure
 *  - 1: unexpected error
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        int exitCode = new CommandLine(new GenerateCommand()).execute(args);
        System.exit(exitCode);
    }
}

