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
    void generatesKubeadmWorkspaceWithExpectedFiles() throws Exception {
        Path out = Files.createTempDirectory("k8s-gen-kubeadm-" + UUID.randomUUID()).resolve("pt-m1");

        GenerateCommand cmd = new GenerateCommand();
        cmd.module = "m1";
        cmd.type = "pt";
        cmd.clusterType = "kubeadm";
        cmd.nodes = "1m,2w";
        cmd.cni = "calico";
        cmd.outDir = out.toString();

        int code = ScaffoldService.create().scaffold(cmd);

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(out.resolve("Vagrantfile"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/bootstrap.sh"))).isTrue();
        assertThat(Files.exists(out.resolve(".gitignore"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/install_kube_binaries.sh"))).isTrue();

        String vf = Files.readString(out.resolve("Vagrantfile"), StandardCharsets.UTF_8);
        assertThat(vf).contains("clu-m1-pt-kubeadm-master-1");
        assertThat(vf).contains("clu-m1-pt-kubeadm-worker-1");
        assertThat(vf).contains("clu-m1-pt-kubeadm-worker-2");
        assertThat(vf).contains("192.168.56.10");
    }

    @Test
    void generatesMgmtWorkspaceWithExpectedFiles() throws Exception {
        Path out = Files.createTempDirectory("k8s-gen-mgmt-" + UUID.randomUUID()).resolve("pt-m1");

        GenerateCommand cmd = new GenerateCommand();
        cmd.module = "m1";
        cmd.type = "pt";
        cmd.clusterType = "mgmt";
        cmd.outDir = out.toString();

        int code = ScaffoldService.create().scaffold(cmd);

        assertThat(code).isEqualTo(0);
        assertThat(Files.exists(out.resolve("Vagrantfile"))).isTrue();
        assertThat(Files.exists(out.resolve("scripts/bootstrap.sh"))).isTrue();
        assertThat(Files.exists(out.resolve(".gitignore"))).isTrue();

        String vf = Files.readString(out.resolve("Vagrantfile"), StandardCharsets.UTF_8);
        assertThat(vf).contains("node.vm.hostname = \"clu-m1-pt-none\"");
        assertThat(vf).contains("vb.name = \"clu-m1-pt-none\"");
        assertThat(vf).contains("\"CLUSTER_NAME\" => \"clu-m1-pt-none\"");
        assertThat(vf).contains("192.168.56.5");
    }
}

