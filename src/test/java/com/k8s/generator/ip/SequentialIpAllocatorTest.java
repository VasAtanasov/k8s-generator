package com.k8s.generator.ip;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.k8s.generator.testutil.IpAssertions.assertIps;

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
    void shouldAllocateSingleIpForNoneCluster() {
        // Given: NONE cluster
        var cluster = ClusterSpec.builder()
                .name("mgmt").type(NoneCluster.INSTANCE)
                .firstIp("192.168.56.6")
                .nodes(NodeTopology.of(0,0))
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: single IP
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(), "192.168.56.6");
    }

    @Test
    void shouldAllocateSequentialIpsForKubeadmCluster() {
        // Given: KUBEADM cluster 1 master, 2 workers
        var cluster = ClusterSpec.builder()
                .name("prod").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.30")
                .nodes(NodeTopology.of(1,2))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 3 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(),
                "192.168.56.30",
                "192.168.56.31",
                "192.168.56.32");
    }

    @Test
    void shouldAllocateMultipleMastersAndWorkers() {
        // Given: KUBEADM cluster 3 masters, 5 workers
        var cluster = ClusterSpec.builder()
                .name("ha-cluster").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.50")
                .nodes(NodeTopology.of(3,5))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 8 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(),
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
        var cluster = ClusterSpec.builder()
                .name("test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.0")
                .nodes(NodeTopology.of(2,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .1, use .0 and .3 (skip .1, .2)
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(), "192.168.56.0", "192.168.56.3");
    }

    @Test
    void shouldSkipReservedIp2() {
        // Given: cluster needing IPs around .2
        var cluster = ClusterSpec.builder()
                .name("test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.0")
                .nodes(NodeTopology.of(4,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .1, .2, .5
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(),
                "192.168.56.0",   // OK
                "192.168.56.3",   // Skip .1, .2
                "192.168.56.4",   // OK
                "192.168.56.6"    // Skip .5
        );
    }

    @Test
    void shouldSkipReservedIp5() {
        // Given: cluster starting at .3 needing 4 IPs
        var cluster = ClusterSpec.builder().name("test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.3").nodes(NodeTopology.of(4,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: .3, .4, .6 (skip .5), .7
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(),
                "192.168.56.3",
                "192.168.56.4",
                "192.168.56.6",  // Skipped .5
                "192.168.56.7"
        );
    }

    @Test
    void shouldHandleStartingAtReservedIp() {
        // Given: cluster starting exactly at reserved .5
        var cluster = ClusterSpec.builder().name("test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.5").nodes(NodeTopology.of(2,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: skip .5, use .6 and .7
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(), "192.168.56.6", "192.168.56.7");
    }

    // ==================== Subnet Boundary Tests ====================



    @Test
    void shouldRejectIpExceeding254() {
        // Given: cluster starting at .253 needing 3 IPs (would exceed .254)
        var cluster = ClusterSpec.builder()
                .name("boundary").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.253")
                .nodes(NodeTopology.of(3,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
        var cluster = ClusterSpec.builder()
                .name("large").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.250")
                .nodes(NodeTopology.of(5,5))
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
        var cluster = ClusterSpec.builder().name("bad-ip").type(Kubeadm.INSTANCE)
                .firstIp("fe80::1").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: failure with descriptive error (IPv6 not supported for allocation)
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("Only IPv4 is supported for allocation")
                .contains("fe80::1");
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
        // Given: 2 clusters with non-overlapping IPs
        var cluster1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20").nodes(NodeTopology.of(1,2)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        var cluster2 = ClusterSpec.builder().name("mgmt").type(NoneCluster.INSTANCE)
                .firstIp("192.168.56.30").nodes(NodeTopology.of(0,0)).sizeProfile(SizeProfile.SMALL).vms(List.of()).build();

        var clusters = List.of(cluster1, cluster2);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: success with all allocations
        assertThat(result.isSuccess()).isTrue();

        var allocations = result.orElseThrow();
        assertThat(allocations).hasSize(2);

        assertIps(allocations.get(ClusterName.of("staging")), "192.168.56.20", "192.168.56.21", "192.168.56.22");
        assertIps(allocations.get(ClusterName.of("mgmt")), "192.168.56.30");
    }

    @Test
    void shouldDetectIpOverlapBetweenClusters() {
        // Given: 2 clusters with overlapping IPs
        var cluster1 = ClusterSpec.builder().name("cluster1").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,2)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        var cluster2 = ClusterSpec.builder().name("cluster2").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.12").nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

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
        var cluster1 = ClusterSpec.builder().name("valid1").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build();

        var cluster2 = ClusterSpec.builder().name("invalid").type(Kubeadm.INSTANCE)
                .firstIp("fe80::1").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build();

        var cluster3 = ClusterSpec.builder().name("valid3").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.30").nodes(NodeTopology.of(1,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build();

        var clusters = List.of(cluster1, cluster2, cluster3);

        // When: allocateMulti
        var result = allocator.allocateMulti(clusters);

        // Then: failure from second cluster (IPv6 not supported)
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("Failed to allocate IPs for cluster 'invalid'")
                .contains("Only IPv4 is supported for allocation");
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
        var cluster = ClusterSpec.builder().name("large").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.100").nodes(NodeTopology.of(3,10)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: 13 sequential IPs
        assertThat(result.isSuccess()).isTrue();
        var ips = result.orElseThrow();
        assertThat(ips).hasSize(13);
        assertThat(ips.getFirst().toCanonicalString()).isEqualTo("192.168.56.100");
        assertThat(ips.get(12).toCanonicalString()).isEqualTo("192.168.56.112");
    }



    @Test
    void shouldAllocateMultipleReservedIpSkips() {
        // Given: cluster spanning multiple reserved IPs
        var cluster = ClusterSpec.builder().name("multi-skip").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.0").nodes(NodeTopology.of(7,0)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        // When: allocate
        var result = allocator.allocate(cluster);

        // Then: correct IPs with skips
        assertThat(result.isSuccess()).isTrue();
        assertIps(result.orElseThrow(),
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
