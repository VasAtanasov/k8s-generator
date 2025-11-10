package com.k8s.generator.ip;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SequentialIpAllocator.
 *
 * <p>Tests IP allocation logic including:
 * <ul>
 *   <li>Default IP allocation (192.168.56.10)</li>
 *   <li>Explicit firstIp allocation</li>
 *   <li>Reserved IP skipping (.1, .2, .5)</li>
 *   <li>Subnet boundary validation</li>
 *   <li>Multi-cluster allocation with overlap detection</li>
 * </ul>
 */
class SequentialIpAllocatorTest {

    private SequentialIpAllocator allocator;

    @BeforeEach
    void setUp() {
        allocator = new SequentialIpAllocator();
    }

    // ==================== Single Cluster Allocation ====================

    @Test
    void shouldAllocateDefaultIpForKindCluster() {
        // Given: KIND cluster without explicit firstIp
        var cluster = ClusterSpec.builder()
                .name("dev")
                .type(ClusterType.KIND)
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: default IP 192.168.56.10
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.10");
    }

    @Test
    void shouldAllocateExplicitIpForMinikubeCluster() {
        // Given: MINIKUBE cluster with explicit firstIp
        var cluster = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.MINIKUBE,
                Optional.of("192.168.56.20"),
                0, 0,
                SizeProfile.LARGE,
                List.of(),
                Optional.empty()
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: explicit IP
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.20");
    }

    @Test
    void shouldAllocateSingleIpForNoneCluster() {
        // Given: NONE cluster
        var cluster = new ClusterSpec(
                ClusterName.of("mgmt"),
                ClusterType.NONE,
                Optional.of("192.168.56.6"),
                0, 0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: single IP
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.6");
    }

    @Test
    void shouldAllocateSequentialIpsForKubeadmCluster() {
        // Given: KUBEADM cluster 1 master, 2 workers
        var cluster = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.30"),
                1, 2,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 3 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly(
                        "192.168.56.30",
                        "192.168.56.31",
                        "192.168.56.32"
                );
    }

    @Test
    void shouldAllocateMultipleMastersAndWorkers() {
        // Given: KUBEADM cluster 3 masters, 5 workers
        var cluster = new ClusterSpec(
                ClusterName.of("ha-cluster"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.50"),
                3, 5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 8 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).containsExactly(
                "192.168.56.50", "192.168.56.51", "192.168.56.52",  // Masters
                "192.168.56.53", "192.168.56.54", "192.168.56.55",  // Workers
                "192.168.56.56", "192.168.56.57"
        );
    }

    // ==================== Reserved IP Skipping ====================

    @Test
    void shouldSkipReservedIp1() {
        // Given: cluster starting at .254 (wraps to .1, which is reserved)
        // Actually, test cluster starting at .0 which would use .1
        var cluster = new ClusterSpec(
                ClusterName.of("test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.0"),
                2, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .1, use .0 and .3 (skip .1, .2)
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.0", "192.168.56.3");
    }

    @Test
    void shouldSkipReservedIp2() {
        // Given: cluster needing IPs around .2
        var cluster = new ClusterSpec(
                ClusterName.of("test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.0"),
                4, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .1, .2, .5
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly(
                        "192.168.56.0",   // OK
                        "192.168.56.3",   // Skip .1, .2
                        "192.168.56.4",   // OK
                        "192.168.56.6"    // Skip .5
                );
    }

    @Test
    void shouldSkipReservedIp5() {
        // Given: cluster starting at .3 needing 4 IPs
        var cluster = new ClusterSpec(
                ClusterName.of("test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.3"),
                4, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: .3, .4, .6 (skip .5), .7
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly(
                        "192.168.56.3",
                        "192.168.56.4",
                        "192.168.56.6",  // Skipped .5
                        "192.168.56.7"
                );
    }

    @Test
    void shouldHandleStartingAtReservedIp() {
        // Given: cluster starting exactly at reserved .5
        var cluster = new ClusterSpec(
                ClusterName.of("test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.5"),
                2, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .5, use .6 and .7
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.6", "192.168.56.7");
    }

    // ==================== Subnet Boundary Tests ====================

    @Test
    void shouldAllowIpAt254() {
        // Given: cluster requesting exactly .254 (last usable IP)
        var cluster = new ClusterSpec(
                ClusterName.of("boundary"),
                ClusterType.KIND,
                Optional.of("192.168.56.254"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: success with .254
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.254");
    }

    @Test
    void shouldRejectIpExceeding254() {
        // Given: cluster starting at .253 needing 3 IPs (would exceed .254)
        var cluster = new ClusterSpec(
                ClusterName.of("boundary"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.253"),
                3, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: failure
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("exceeds subnet boundary")
                .contains("boundary");
    }

    @Test
    void shouldRejectLargeClusterExceedingSubnet() {
        // Given: cluster starting at .250 needing 10 IPs
        var cluster = new ClusterSpec(
                ClusterName.of("large"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.250"),
                5, 5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: failure
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("exceeds subnet boundary");
    }

    // ==================== Invalid Input Tests ====================

    @Test
    void shouldRejectInvalidIpFormat() {
        // Given: cluster with invalid IP format
        var cluster = new ClusterSpec(
                ClusterName.of("bad-ip"),
                ClusterType.KIND,
                Optional.of("not-an-ip"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: failure with descriptive error
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("Invalid IP address format")
                .contains("not-an-ip");
    }

    @Test
    void shouldRejectNullClusterSpec() {
        assertThatThrownBy(() -> allocator.allocate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("spec cannot be null");
    }

    // ==================== Multi-Cluster Allocation ====================

    @Test
    void shouldAllocateMultipleClustersWithoutOverlap() {
        // Given: 3 clusters with non-overlapping IPs
        var cluster1 = new ClusterSpec(
                ClusterName.of("dev"), ClusterType.KIND,
                Optional.of("192.168.56.10"),
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("staging"), ClusterType.KUBEADM,
                Optional.of("192.168.56.20"),
                1, 2, SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO)
        );

        var cluster3 = new ClusterSpec(
                ClusterName.of("mgmt"), ClusterType.NONE,
                Optional.of("192.168.56.30"),
                0, 0, SizeProfile.SMALL, List.of(), Optional.empty()
        );

        var clusters = List.of(cluster1, cluster2, cluster3);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: success with all allocations
        assertThat(result.isSuccess()).isTrue();

        var allocations = result.orElseThrow();
        assertThat(allocations).hasSize(3);

        assertThat(allocations.get("dev"))
                .containsExactly("192.168.56.10");

        assertThat(allocations.get("staging"))
                .containsExactly("192.168.56.20", "192.168.56.21", "192.168.56.22");

        assertThat(allocations.get("mgmt"))
                .containsExactly("192.168.56.30");
    }

    @Test
    void shouldDetectIpOverlapBetweenClusters() {
        // Given: 2 clusters with overlapping IPs
        var cluster1 = new ClusterSpec(
                ClusterName.of("cluster1"), ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1, 2,  // Uses .10, .11, .12
                SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO)
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("cluster2"), ClusterType.KUBEADM,
                Optional.of("192.168.56.12"),  // Overlaps with cluster1's .12
                1, 1,  // Uses .12, .13
                SizeProfile.MEDIUM, List.of(), Optional.of(CniType.CALICO)
        );

        var clusters = List.of(cluster1, cluster2);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: failure with overlap error
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("IP address overlap detected")
                .contains("192.168.56.12");
    }

    @Test
    void shouldRequireExplicitFirstIpForMultiCluster() {
        // Given: multi-cluster with one missing firstIp
        var cluster1 = new ClusterSpec(
                ClusterName.of("cluster1"), ClusterType.KIND,
                Optional.of("192.168.56.10"),
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("cluster2"), ClusterType.KIND,
                Optional.empty(),  // Missing firstIp
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var clusters = List.of(cluster1, cluster2);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: failure
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("Multi-cluster configuration requires explicit firstIp")
                .contains("cluster2");
    }

    @Test
    void shouldHandleEmptyClusterListInMultiAllocation() {
        // Given: empty cluster list
        var clusters = List.<ClusterSpec>of();

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: success with empty map
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).isEmpty();
    }

    @Test
    void shouldFailFastOnInvalidIpInMultiCluster() {
        // Given: 3 clusters, second has invalid IP
        var cluster1 = new ClusterSpec(
                ClusterName.of("valid1"), ClusterType.KIND,
                Optional.of("192.168.56.10"),
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("invalid"), ClusterType.KIND,
                Optional.of("bad-ip"),  // Invalid
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var cluster3 = new ClusterSpec(
                ClusterName.of("valid3"), ClusterType.KIND,
                Optional.of("192.168.56.30"),
                0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()
        );

        var clusters = List.of(cluster1, cluster2, cluster3);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: failure from second cluster
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("Failed to allocate IPs for cluster 'invalid'")
                .contains("Invalid IP address");
    }

    @Test
    void shouldRejectNullClusterListInMultiAllocation() {
        assertThatThrownBy(() -> allocator.allocateMulti(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clusters cannot be null");
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleLargeKubeadmCluster() {
        // Given: large cluster (3 masters, 10 workers)
        var cluster = new ClusterSpec(
                ClusterName.of("large"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.100"),
                3, 10,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 13 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        var ips = result.orElseThrow();
        assertThat(ips).hasSize(13);
        assertThat(ips.getFirst()).isEqualTo("192.168.56.100");
        assertThat(ips.get(12)).isEqualTo("192.168.56.112");
    }

    @Test
    void shouldHandleClusterStartingAtZero() {
        // Given: cluster starting at .0
        var cluster = new ClusterSpec(
                ClusterName.of("zero-start"),
                ClusterType.KIND,
                Optional.of("192.168.56.0"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: success with .0
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly("192.168.56.0");
    }

    @Test
    void shouldAllocateMultipleReservedIpSkips() {
        // Given: cluster spanning multiple reserved IPs
        var cluster = new ClusterSpec(
                ClusterName.of("multi-skip"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.0"),
                7, 0,  // Needs 7 IPs, will skip .1, .2, .5
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: correct IPs with skips
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly(
                        "192.168.56.0",   // OK
                        "192.168.56.3",   // Skip .1, .2
                        "192.168.56.4",   // OK
                        "192.168.56.6",   // Skip .5
                        "192.168.56.7",   // OK
                        "192.168.56.8",   // OK
                        "192.168.56.9"    // OK
                );
    }
}
