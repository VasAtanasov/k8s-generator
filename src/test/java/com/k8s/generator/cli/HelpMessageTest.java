package com.k8s.generator.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class HelpMessageTest {

    @Test
    void printsUsageAndOptions() {
        String usage = new CommandLine(new GenerateCommand()).getUsageMessage();
        assertThat(usage).contains("Usage:");
        assertThat(usage).contains("--module");
        assertThat(usage).contains("--type");
        assertThat(usage).contains("<clusterType>");  // Picocli uses camelCase by default
        assertThat(usage).contains("kubeadm|mgmt");
    }
}

