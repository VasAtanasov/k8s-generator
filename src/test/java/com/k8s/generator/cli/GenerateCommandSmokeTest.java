package com.k8s.generator.cli;

import com.k8s.generator.app.ScaffoldService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateCommandSmokeTest {

    @Test
    void generatesKindWorkspaceWithExpectedFiles() throws Exception {
        Path out = Files.createTempDirectory("k8s-gen-kind-" + UUID.randomUUID()).resolve("pt-m1");

        GenerateCommand cmd = new GenerateCommand();
        cmd.module = "m1";
        cmd.type = "pt";
        cmd.clusterType = "kind";
        cmd.outDir = out.toString();

        int code = new ScaffoldService().scaffold(cmd);

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(out.resolve("Vagrantfile"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/bootstrap.sh"))).isTrue();
        assertThat(Files.exists(out.resolve(".gitignore"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/install_kind.sh"))).isTrue();

        String vf = Files.readString(out.resolve("Vagrantfile"), StandardCharsets.UTF_8);
        assertThat(vf).contains("private_network");
        assertThat(vf).contains("192.168.56.10");
    }

    @Test
    void generatesMinikubeWorkspaceWithExpectedFiles() throws Exception {
        Path out = Files.createTempDirectory("k8s-gen-minikube-" + UUID.randomUUID()).resolve("pt-m1");

        GenerateCommand cmd = new GenerateCommand();
        cmd.module = "m1";
        cmd.type = "pt";
        cmd.clusterType = "minikube";
        cmd.outDir = out.toString();

        int code = new ScaffoldService().scaffold(cmd);

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(out.resolve("Vagrantfile"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/bootstrap.sh"))).isTrue();
        assertThat(Files.exists(out.resolve(".gitignore"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/install_kind.sh"))).isTrue();

        String bs = Files.readString(out.resolve("scripts/bootstrap.sh"), StandardCharsets.UTF_8);
        assertThat(bs).contains("install_kubectl.sh");
    }
}

