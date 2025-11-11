package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for PolicyValidator.
 */
class PolicyValidatorTest {

    private final PolicyValidator validator = new PolicyValidator();

    @Test
    void shouldAcceptEmptyClusterList() {
        ValidationResult result = validator.validate(List.of());

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptSingleCluster() {
        var cluster = ClusterSpec.builder()
                .name("dev")
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,2))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptMultipleClustersWithUniqueNames() {
        var cluster1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,2)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20").nodes(NodeTopology.of(3,5)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicateClusterNames() {
        var cluster1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,2)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("staging").type(NoneCluster.INSTANCE)
                .firstIp("192.168.56.20").nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("clusters[].name")
                        && e.level() == ValidationLevel.POLICY
                        && e.message().contains("Duplicate cluster name")
                        && e.message().contains("staging"));
    }

    @Test
    void shouldDetectMultipleDuplicateNames() {
        var cluster1 = ClusterSpec.builder().name("dev").type(NoneCluster.INSTANCE).nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();
        var cluster2 = ClusterSpec.builder().name("dev").type(NoneCluster.INSTANCE).nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();
        var cluster3 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE).nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster4 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE).nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3, cluster4));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .filteredOn(e -> e.message().contains("Duplicate cluster name"))
                .hasSize(2);  // Two distinct duplicates
    }

    @Test
    void shouldRejectExcessiveTotalVmCount() {
        // Create 10 clusters with 6 VMs each = 60 total (exceeds limit of 50)
        var clusters = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> ClusterSpec.builder()
                        .name("cluster" + i)
                        .type(Kubeadm.INSTANCE)
                        .firstIp("192.168.56." + (10 + i * 10))
                        .nodes(NodeTopology.of(2,4))
                        .sizeProfile(SizeProfile.MEDIUM)
                        .vms(List.of())
                        .cni(CniType.CALICO)
                        .build())
                .toList();

        ValidationResult result = validator.validate(clusters);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("topology.global")
                        && e.message().contains("Total VM count exceeds recommended limit"));
    }

    @Test
    void shouldWarnWhenApproachingTotalVmLimit() {
        // Create clusters totaling 41 VMs (80% of 50)
        var cluster1 = ClusterSpec.builder().name("cluster1").type(Kubeadm.INSTANCE).nodes(NodeTopology.of(3,10)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("cluster2").type(Kubeadm.INSTANCE).nodes(NodeTopology.of(3,10)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster3 = ClusterSpec.builder().name("cluster3").type(Kubeadm.INSTANCE).nodes(NodeTopology.of(3,10)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster4 = ClusterSpec.builder().name("cluster4").type(NoneCluster.INSTANCE).nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();  // +2 = 41 total

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3, cluster4));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("approaching limit"));
    }

    @Test
    void shouldRejectClusterExceedingPerClusterLimit() {
        var largeCluster = ClusterSpec.builder()
                .name("huge").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(5,20))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(List.of(largeCluster));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("clusters[name='huge']")
                        && e.message().contains("exceeds recommended VM limit"));
    }

    @Test
    void shouldAcceptClusterAtPerClusterLimit() {
        var cluster = ClusterSpec.builder()
                .name("large").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(5,15))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldDetectVmNameConflictAcrossClusters() {
        var vm1 = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var vm2 = VmConfig.builder()
                .name("master-1") // Same name
                .role(NodeRole.MASTER)
                .ip("192.168.56.20")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var cluster1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of(vm1)).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of(vm2)).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("vmNames")
                        && e.message().contains("VM name conflict")
                        && e.message().contains("master-1"));
    }

    @Test
    void shouldDetectConflictBetweenExplicitAndPredictedNames() {
        // Explicit VM with same name that cluster1 will generate
        var vm = VmConfig.builder()
                .name("staging-master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.20")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var cluster1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of(vm)).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("VM name conflict")
                        && e.message().contains("staging-master-1"));
    }



    @Test
    void shouldCountNoneAsOneVm() {
        var cluster = ClusterSpec.builder().name("mgmt").type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // NONE counts as 1 VM, well within limits
    }

    @Test
    void shouldRejectNullClusterList() {
        assertThatThrownBy(() -> validator.validate((List<ClusterSpec>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clusters cannot be null");
    }

    @Test
    void shouldCollectAllPolicyErrors() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var cluster1 = ClusterSpec.builder().name("dup").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(10,15)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();
        var cluster2 = ClusterSpec.builder().name("dup").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(10,15)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();
        var cluster3 = ClusterSpec.builder().name("other").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of(vm)).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3));

        assertThat(result.hasErrors()).isTrue();
        // Should have: duplicate name, total VM limit, 2x per-cluster limit
        assertThat(result.errorCount()).isGreaterThan(2);
    }
}
