package com.k8s.generator.orchestrator;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for DefaultVmGenerator.
 */
class DefaultVmGeneratorTest {

    private final DefaultVmGenerator generator = new DefaultVmGenerator();

    @Test
    void shouldGenerateSingleVmForKindCluster() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.KIND,
            Optional.empty(),
            0,
            0,
            SizeProfile.MEDIUM,
            List.of(),
            Optional.empty()
        );

        var vms = generator.generate(cluster, List.of("192.168.56.10"));

        assertThat(vms).hasSize(1);
        assertThat(vms.get(0))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("dev");
                assertThat(vm.role()).isEqualTo(NodeRole.CLUSTER);
                assertThat(vm.ip()).isEqualTo("192.168.56.10");
                assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
            });
    }

    @Test
    void shouldGenerateSingleVmForMinikubeCluster() {
        var cluster = new ClusterSpec(
            "staging",
            ClusterType.MINIKUBE,
            Optional.empty(),
            0,
            0,
            SizeProfile.LARGE,
            List.of(),
            Optional.empty()
        );

        var vms = generator.generate(cluster, List.of("192.168.56.20"));

        assertThat(vms).hasSize(1);
        assertThat(vms.get(0))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("staging");
                assertThat(vm.role()).isEqualTo(NodeRole.CLUSTER);
                assertThat(vm.ip()).isEqualTo("192.168.56.20");
                assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.LARGE);
            });
    }

    @Test
    void shouldGenerateSingleVmForNoneCluster() {
        var cluster = new ClusterSpec(
            "mgmt",
            ClusterType.NONE,
            Optional.empty(),
            0,
            0,
            SizeProfile.SMALL,
            List.of(),
            Optional.empty()
        );

        var vms = generator.generate(cluster, List.of("192.168.56.5"));

        assertThat(vms).hasSize(1);
        assertThat(vms.get(0))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("mgmt");
                assertThat(vm.role()).isEqualTo(NodeRole.MANAGEMENT);
                assertThat(vm.ip()).isEqualTo("192.168.56.5");
                assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.SMALL);
            });
    }

    @Test
    void shouldGenerateMultipleVmsForKubeadmCluster() {
        var cluster = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.20"),
            2,  // 2 masters
            3,  // 3 workers
            SizeProfile.LARGE,
            List.of(),
            Optional.of(CniType.CALICO)
        );

        var ips = List.of(
            "192.168.56.20", "192.168.56.21",  // Masters
            "192.168.56.22", "192.168.56.23", "192.168.56.24"  // Workers
        );

        var vms = generator.generate(cluster, ips);

        assertThat(vms).hasSize(5);

        // Verify masters
        assertThat(vms.get(0))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("prod-master-1");
                assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                assertThat(vm.ip()).isEqualTo("192.168.56.20");
                assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.LARGE);
            });

        assertThat(vms.get(1))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("prod-master-2");
                assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                assertThat(vm.ip()).isEqualTo("192.168.56.21");
            });

        // Verify workers
        assertThat(vms.get(2))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("prod-worker-1");
                assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                assertThat(vm.ip()).isEqualTo("192.168.56.22");
            });

        assertThat(vms.get(3))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("prod-worker-2");
                assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                assertThat(vm.ip()).isEqualTo("192.168.56.23");
            });

        assertThat(vms.get(4))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("prod-worker-3");
                assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                assertThat(vm.ip()).isEqualTo("192.168.56.24");
            });
    }

    @Test
    void shouldGenerateSingleMasterKubeadmCluster() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.KUBEADM,
            Optional.empty(),
            1,  // 1 master
            0,  // 0 workers
            SizeProfile.MEDIUM,
            List.of(),
            Optional.of(CniType.FLANNEL)
        );

        var vms = generator.generate(cluster, List.of("192.168.56.10"));

        assertThat(vms).hasSize(1);
        assertThat(vms.get(0))
            .satisfies(vm -> {
                assertThat(vm.name()).isEqualTo("dev-master-1");
                assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                assertThat(vm.ip()).isEqualTo("192.168.56.10");
            });
    }

    @Test
    void shouldCalculateVmCountForKind() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.KIND,
            Optional.empty(),
            0, 0,
            SizeProfile.MEDIUM,
            List.of(),
            Optional.empty()
        );

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(1);
    }

    @Test
    void shouldCalculateVmCountForMinikube() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.MINIKUBE,
            Optional.empty(),
            0, 0,
            SizeProfile.MEDIUM,
            List.of(),
            Optional.empty()
        );

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(1);
    }

    @Test
    void shouldCalculateVmCountForNone() {
        var cluster = new ClusterSpec(
            "mgmt",
            ClusterType.NONE,
            Optional.empty(),
            0, 0,
            SizeProfile.SMALL,
            List.of(),
            Optional.empty()
        );

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(1);
    }

    @Test
    void shouldCalculateVmCountForKubeadm() {
        var cluster = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.empty(),
            3,   // 3 masters
            10,  // 10 workers
            SizeProfile.LARGE,
            List.of(),
            Optional.of(CniType.CALICO)
        );

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(13);
    }

    @Test
    void shouldRejectNullCluster() {
        assertThatThrownBy(() -> generator.generate(null, List.of("192.168.56.10")))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("cluster cannot be null");
    }

    @Test
    void shouldRejectNullIpList() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.KIND,
            Optional.empty(),
            0, 0,
            SizeProfile.MEDIUM,
            List.of(),
            Optional.empty()
        );

        assertThatThrownBy(() -> generator.generate(cluster, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("allocatedIps cannot be null");
    }

    @Test
    void shouldRejectIpCountMismatch() {
        var cluster = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.empty(),
            2,  // Expects 2 masters + 3 workers = 5 VMs
            3,
            SizeProfile.LARGE,
            List.of(),
            Optional.of(CniType.CALICO)
        );

        // Only provide 3 IPs instead of 5
        assertThatThrownBy(() -> generator.generate(cluster, List.of("192.168.56.10", "192.168.56.11", "192.168.56.12")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IP count mismatch")
            .hasMessageContaining("expected 5 IPs, got 3");
    }

    @Test
    void shouldUseClusterNameForSingleVmClusters() {
        // KIND cluster
        var kindCluster = new ClusterSpec("my-kind", ClusterType.KIND, Optional.empty(),
            0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty());
        var kindVms = generator.generate(kindCluster, List.of("192.168.56.10"));
        assertThat(kindVms.get(0).name()).isEqualTo("my-kind");

        // MINIKUBE cluster
        var minikubeCluster = new ClusterSpec("my-minikube", ClusterType.MINIKUBE, Optional.empty(),
            0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty());
        var minikubeVms = generator.generate(minikubeCluster, List.of("192.168.56.20"));
        assertThat(minikubeVms.get(0).name()).isEqualTo("my-minikube");

        // NONE cluster
        var noneCluster = new ClusterSpec("my-mgmt", ClusterType.NONE, Optional.empty(),
            0, 0, SizeProfile.SMALL, List.of(), Optional.empty());
        var noneVms = generator.generate(noneCluster, List.of("192.168.56.30"));
        assertThat(noneVms.get(0).name()).isEqualTo("my-mgmt");
    }

    @Test
    void shouldUsePrefixedNamesForKubeadmClusters() {
        var cluster = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.empty(),
            1, 2,
            SizeProfile.MEDIUM,
            List.of(),
            Optional.of(CniType.CALICO)
        );

        var vms = generator.generate(cluster, List.of("192.168.56.10", "192.168.56.11", "192.168.56.12"));

        assertThat(vms.get(0).name()).isEqualTo("staging-master-1");
        assertThat(vms.get(1).name()).isEqualTo("staging-worker-1");
        assertThat(vms.get(2).name()).isEqualTo("staging-worker-2");
    }
}
