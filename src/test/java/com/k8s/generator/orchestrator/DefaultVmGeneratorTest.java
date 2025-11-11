package com.k8s.generator.orchestrator;

import com.k8s.generator.model.*;
import inet.ipaddr.IPAddressString;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DefaultVmGenerator.
 */
class DefaultVmGeneratorTest {
    private static final VmName PROD_MASTER_1 = VmName.of("prod-master-1");
    private static final VmName PROD_MASTER_2 = VmName.of("prod-master-2");
    private static final VmName PROD_WORKER_1 = VmName.of("prod-worker-1");
    private static final VmName PROD_WORKER_2 = VmName.of("prod-worker-2");
    private static final VmName PROD_WORKER_3 = VmName.of("prod-worker-3");

    private final DefaultVmGenerator generator = new DefaultVmGenerator();





    @Test
    void shouldGenerateSingleVmForNoneCluster() {
        var cluster = ClusterSpec.builder()
                .name("mgmt")
                .type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(0,0))
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

        var vms = generator.generate(cluster, List.of(
                new IPAddressString("192.168.56.5").getAddress()
        ));

        assertThat(vms).hasSize(1);
        assertThat(vms.getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("mgmt"));
                    assertThat(vm.role()).isEqualTo(NodeRole.MANAGEMENT);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.5");
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.SMALL);
                });
    }

    @Test
    void shouldGenerateMultipleVmsForKubeadmCluster() {
        var cluster = ClusterSpec.builder()
                .name("prod")
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20")
                .nodes(NodeTopology.of(2,3))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        var ips = List.of(
                new IPAddressString("192.168.56.20").getAddress(), new IPAddressString("192.168.56.21").getAddress(),  // Masters
                new IPAddressString("192.168.56.22").getAddress(), new IPAddressString("192.168.56.23").getAddress(), new IPAddressString("192.168.56.24").getAddress()  // Workers
        );

        var vms = generator.generate(cluster, ips);

        assertThat(vms).hasSize(5);

        // Verify masters
        assertThat(vms.getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_MASTER_1);
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.20");
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.LARGE);
                });

        assertThat(vms.get(1))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_MASTER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.21");
                });

        // Verify workers
        assertThat(vms.get(2))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_1);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.22");
                });

        assertThat(vms.get(3))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.23");
                });

        assertThat(vms.get(4))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_3);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.24");
                });
    }

    @Test
    void shouldGenerateSingleMasterKubeadmCluster() {
        var cluster = ClusterSpec.builder()
                .name("dev")
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.FLANNEL)
                .build();

        var vms = generator.generate(cluster, List.of(
                new IPAddressString("192.168.56.10").getAddress()
        ));

        assertThat(vms).hasSize(1);
        assertThat(vms.getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("dev-master-1"));
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.10");
                });
    }



    @Test
    void shouldCalculateVmCountForNone() {
        var cluster = ClusterSpec.builder()
                .name("mgmt")
                .type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(0,0))
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(1);
    }

    @Test
    void shouldCalculateVmCountForKubeadm() {
        var cluster = ClusterSpec.builder()
                .name("prod")
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(3,10))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThat(generator.calculateVmCount(cluster)).isEqualTo(13);
    }

    @Test
    void shouldRejectNullCluster() {
        assertThatThrownBy(() -> generator.generate(null, List.of(
                new IPAddressString("192.168.56.10").getAddress()
        )))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cluster cannot be null");
    }

    @Test
    void shouldRejectNullIpList() {
        var cluster = ClusterSpec.builder()
                .name("dev").type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(0,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        assertThatThrownBy(() -> generator.generate(cluster, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("allocatedIps cannot be null");
    }

    @Test
    void shouldRejectIpCountMismatch() {
        var cluster = ClusterSpec.builder()
                .name("prod").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(2,3))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // Only provide 3 IPs instead of 5
        assertThatThrownBy(() -> generator.generate(cluster, List.of(
                new IPAddressString("192.168.56.10").getAddress(),
                new IPAddressString("192.168.56.11").getAddress(),
                new IPAddressString("192.168.56.12").getAddress()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IP count mismatch")
                .hasMessageContaining("expected 5 IPs, got 3");
    }

    @Test
    void shouldUseClusterNameForSingleVmClusters() {
        // NONE cluster
        var noneCluster = ClusterSpec.builder().name("my-mgmt").type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();
        var noneVms = generator.generate(noneCluster, List.of(new IPAddressString("192.168.56.30").getAddress()));
        assertThat(noneVms.getFirst().name()).isEqualTo(VmName.of("my-mgmt"));
    }

    @Test
    void shouldUsePrefixedNamesForKubeadmClusters() {
        var cluster = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,2))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        var vms = generator.generate(cluster, List.of(
                new IPAddressString("192.168.56.10").getAddress(),
                new IPAddressString("192.168.56.11").getAddress(),
                new IPAddressString("192.168.56.12").getAddress()
        ));

        assertThat(vms.getFirst().name()).isEqualTo(VmName.of("staging-master-1"));
        assertThat(vms.get(1).name()).isEqualTo(VmName.of("staging-worker-1"));
        assertThat(vms.get(2).name()).isEqualTo(VmName.of("staging-worker-2"));
    }
}
