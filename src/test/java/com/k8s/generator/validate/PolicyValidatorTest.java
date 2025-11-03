package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

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
            "dev",
            ClusterType.KUBEADM,
            Optional.empty(),
            1,
            2,
            SizeProfile.MEDIUM,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptMultipleClustersWithUniqueNames() {
        var cluster1 = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            2,
            SizeProfile.MEDIUM,
            List.of()
        );
        var cluster2 = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.20"),
            3,
            5,
            SizeProfile.LARGE,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicateClusterNames() {
        var cluster1 = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            2,
            SizeProfile.MEDIUM,
            List.of()
        );
        var cluster2 = new ClusterSpec(
            "staging",  // Duplicate name
            ClusterType.KIND,
            Optional.of("192.168.56.20"),
            0,
            0,
            SizeProfile.SMALL,
            List.of()
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
        var cluster1 = new ClusterSpec("dev", ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of());
        var cluster2 = new ClusterSpec("dev", ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of());
        var cluster3 = new ClusterSpec("prod", ClusterType.KUBEADM, Optional.empty(), 1, 1, SizeProfile.MEDIUM, List.of());
        var cluster4 = new ClusterSpec("prod", ClusterType.KUBEADM, Optional.empty(), 1, 1, SizeProfile.MEDIUM, List.of());

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
                "cluster" + i,
                ClusterType.KUBEADM,
                Optional.of("192.168.56." + (10 + i * 10)),
                2,
                4,  // 6 VMs per cluster
                SizeProfile.MEDIUM,
                List.of()
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
        var cluster1 = new ClusterSpec("cluster1", ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of());
        var cluster2 = new ClusterSpec("cluster2", ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of());
        var cluster3 = new ClusterSpec("cluster3", ClusterType.KUBEADM, Optional.empty(), 3, 10, SizeProfile.MEDIUM, List.of());
        var cluster4 = new ClusterSpec("cluster4", ClusterType.KIND, Optional.empty(), 0, 0, SizeProfile.SMALL, List.of());  // +2 = 41 total

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3, cluster4));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.message().contains("approaching limit"));
    }

    @Test
    void shouldRejectClusterExceedingPerClusterLimit() {
        var largeCluster = new ClusterSpec(
            "huge",
            ClusterType.KUBEADM,
            Optional.empty(),
            5,
            20,  // 25 VMs total (exceeds per-cluster limit of 20)
            SizeProfile.LARGE,
            List.of()
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
            "large",
            ClusterType.KUBEADM,
            Optional.empty(),
            5,
            15,  // 20 VMs total (exactly at limit)
            SizeProfile.LARGE,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldDetectVmNameConflictAcrossClusters() {
        var vm1 = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm2 = new VmConfig("master-1", "master", "192.168.56.20",  // Same name
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var cluster1 = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            0,
            SizeProfile.MEDIUM,
            List.of(vm1)
        );
        var cluster2 = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.20"),
            1,
            0,
            SizeProfile.MEDIUM,
            List.of(vm2)
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
        var vm = new VmConfig("master-1", "master", "192.168.56.20",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var cluster1 = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,  // Will generate "master-1"
            0,
            SizeProfile.MEDIUM,
            List.of()  // No explicit VMs, will be generated
        );
        var cluster2 = new ClusterSpec(
            "prod",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.20"),
            1,
            0,
            SizeProfile.MEDIUM,
            List.of(vm)  // Explicit "master-1"
        );

        ValidationResult result = validator.validate(List.of(cluster1, cluster2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.message().contains("VM name conflict"));
    }

    @Test
    void shouldCountKindAsOneVm() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.KIND,
            Optional.empty(),
            0,
            0,
            SizeProfile.SMALL,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // KIND counts as 1 VM, well within limits
    }

    @Test
    void shouldCountMinikubeAsOneVm() {
        var cluster = new ClusterSpec(
            "dev",
            ClusterType.MINIKUBE,
            Optional.empty(),
            0,
            0,
            SizeProfile.SMALL,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // MINIKUBE counts as 1 VM, well within limits
    }

    @Test
    void shouldCountNoneAsOneVm() {
        var cluster = new ClusterSpec(
            "mgmt",
            ClusterType.NONE,
            Optional.empty(),
            0,
            0,
            SizeProfile.SMALL,
            List.of()
        );

        ValidationResult result = validator.validate(List.of(cluster));

        assertThat(result.isValid()).isTrue();
        // NONE counts as 1 VM, well within limits
    }

    @Test
    void shouldRejectNullClusterList() {
        assertThatThrownBy(() -> validator.validate(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("clusters cannot be null");
    }

    @Test
    void shouldCollectAllPolicyErrors() {
        var vm = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var cluster1 = new ClusterSpec("dup", ClusterType.KUBEADM, Optional.empty(),
            10, 15, SizeProfile.LARGE, List.of());  // Exceeds per-cluster limit
        var cluster2 = new ClusterSpec("dup", ClusterType.KUBEADM, Optional.empty(),  // Duplicate name
            10, 15, SizeProfile.LARGE, List.of());
        var cluster3 = new ClusterSpec("other", ClusterType.KUBEADM, Optional.empty(),
            1, 0, SizeProfile.MEDIUM, List.of(vm));

        ValidationResult result = validator.validate(List.of(cluster1, cluster2, cluster3));

        assertThat(result.hasErrors()).isTrue();
        // Should have: duplicate name, total VM limit, 2x per-cluster limit
        assertThat(result.errorCount()).isGreaterThan(2);
    }
}
