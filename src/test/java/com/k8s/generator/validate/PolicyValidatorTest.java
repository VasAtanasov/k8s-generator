package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

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
        var cluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptMultipleClustersWithUniqueNames() {
        var cluster1 = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,
                2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );
        var cluster2 = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.20"),
                3,
                5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicateClusterNames() {
        var cluster1 = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,
                2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );
        var cluster2 = new ClusterSpec(
                ClusterName.of("staging"),  // Duplicate name
                ClusterType.KIND,
                Optional.of("192.168.56.20"),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

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
        var cluster1 = new ClusterSpec(ClusterName.of("dev"), ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of(), Optional.empty());
        var cluster2 = new ClusterSpec(ClusterName.of("dev"), ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of(), Optional.empty());
        var cluster3 = new ClusterSpec(ClusterName.of("prod"), ClusterType.KUBEADM, Optional.empty(), 1, 1, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO));
        var cluster4 = new ClusterSpec(ClusterName.of("prod"), ClusterType.KUBEADM, Optional.empty(), 1, 1, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO));

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
                .mapToObj(i -> new ClusterSpec(
                        ClusterName.of("cluster" + i),
                        ClusterType.KUBEADM,
                        Optional.of("192.168.56." + (10 + i * 10)),
                        2,
                        4,  // 6 VMs per cluster
                        SizeProfile.MEDIUM,
                        List.of(),
                        Optional.of(CniType.CALICO)
                ))
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
        var cluster1 = new ClusterSpec(ClusterName.of("cluster1"), ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO));
        var cluster2 = new ClusterSpec(ClusterName.of("cluster2"), ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO));
        var cluster3 = new ClusterSpec(ClusterName.of("cluster3"), ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO));
        var cluster4 = new ClusterSpec(ClusterName.of("cluster4"), ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of(), Optional.empty());  // +2 = 41 total

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3, cluster4));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("approaching limit"));
    }

    @Test
    void shouldRejectClusterExceedingPerClusterLimit() {
        var largeCluster = new ClusterSpec(
                ClusterName.of("huge"),
                ClusterType.KUBEADM,
                Optional.empty(),
                5,
                20,  // 25 VMs total (exceeds per-cluster limit of 20)
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        ValidationResult result = validator.validate(List.of(largeCluster));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("clusters[name='huge']")
                        && e.message().contains("exceeds recommended VM limit"));
    }

    @Test
    void shouldAcceptClusterAtPerClusterLimit() {
        var cluster = new ClusterSpec(
                ClusterName.of("large"),
                ClusterType.KUBEADM,
                Optional.empty(),
                5,
                15,  // 20 VMs total (exactly at limit)
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldDetectVmNameConflictAcrossClusters() {
        var vm1 = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());
        var vm2 = new VmConfig(
                VmName.of("master-1"), // Same name
                NodeRole.MASTER,
                "192.168.56.20",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var cluster1 = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(vm1),
                Optional.of(CniType.CALICO)
        );
        var cluster2 = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.20"),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(vm2),
                Optional.of(CniType.CALICO)
        );

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
        var vm = new VmConfig(
                VmName.of("staging-master-1"),
                NodeRole.MASTER,
                "192.168.56.20",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var cluster1 = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,  // Will generate "staging-master-1"
                0,
                SizeProfile.MEDIUM,
                List.of(),  // No explicit VMs, will be generated
                Optional.of(CniType.CALICO)
        );
        var cluster2 = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.20"),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(vm),  // Explicit "staging-master-1" conflicts with cluster1's predicted name
                Optional.of(CniType.CALICO)
        );

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("VM name conflict")
                        && e.message().contains("staging-master-1"));
    }

    @Test
    void shouldCountKindAsOneVm() {
        var cluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.empty(),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // KIND counts as 1 VM, well within limits
    }

    @Test
    void shouldCountMinikubeAsOneVm() {
        var cluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.MINIKUBE,
                Optional.empty(),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // MINIKUBE counts as 1 VM, well within limits
    }

    @Test
    void shouldCountNoneAsOneVm() {
        var cluster = new ClusterSpec(
                ClusterName.of("mgmt"),
                ClusterType.NONE,
                Optional.empty(),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

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
        var vm = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var cluster1 = new ClusterSpec(ClusterName.of("dup"), ClusterType.KUBEADM, Optional.empty(),
                10, 15, SizeProfile.LARGE, List.of(), Optional.of(CniType.CALICO));  // Exceeds per-cluster limit
        var cluster2 = new ClusterSpec(ClusterName.of("dup"), ClusterType.KUBEADM, Optional.empty(),  // Duplicate name
                10, 15, SizeProfile.LARGE, List.of(), Optional.of(CniType.CALICO));
        var cluster3 = new ClusterSpec(ClusterName.of("other"), ClusterType.KUBEADM, Optional.empty(),
                1, 0, SizeProfile.MEDIUM, List.of(vm), Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3));

        assertThat(result.hasErrors()).isTrue();
        // Should have: duplicate name, total VM limit, 2x per-cluster limit
        assertThat(result.errorCount()).isGreaterThan(2);
    }
}
