package com.k8s.generator.orchestrator;

import com.k8s.generator.ip.SequentialIpAllocator;
import com.k8s.generator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ClusterOrchestrator.
 *
 * <p>Tests the complete orchestration flow:
 * <ul>
 *   <li>IP allocation via SequentialIpAllocator</li>
 *   <li>VM generation via DefaultVmGenerator</li>
 *   <li>ClusterSpec enrichment with generated VMs</li>
 *   <li>Multi-cluster orchestration</li>
 * </ul>
 */
class ClusterOrchestratorTest {
    private static final ClusterName DEV = ClusterName.of("dev");
    private static final ClusterName STAGING = ClusterName.of("staging");
    private static final ClusterName MGMT = ClusterName.of("mgmt");

    private static final VmName PROD_MASTER_1 = VmName.of("prod-master-1");
    private static final VmName PROD_MASTER_2 = VmName.of("prod-master-2");
    private static final VmName PROD_WORKER_1 = VmName.of("prod-worker-1");
    private static final VmName PROD_WORKER_2 = VmName.of("prod-worker-2");
    private static final VmName PROD_WORKER_3 = VmName.of("prod-worker-3");

    private ClusterOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        var ipAllocator = new SequentialIpAllocator();
        var vmGenerator = new DefaultVmGenerator();
        orchestrator = new ClusterOrchestrator(ipAllocator, vmGenerator);
    }

    // ==================== Single Cluster Orchestration ====================

    @Test
    void shouldOrchestrateKindCluster() {
        // Given: KIND cluster without VMs
        var cluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.empty(),  // Use default IP
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),  // No explicit VMs
                Optional.empty()
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: success with enriched cluster
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(1);
        assertThat(enriched.vms().getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("dev"));
                    assertThat(vm.role()).isEqualTo(NodeRole.CLUSTER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.10");  // Default IP
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
                });
    }

    @Test
    void shouldOrchestrateMinikubeCluster() {
        // Given: MINIKUBE cluster with explicit IP
        var cluster = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.MINIKUBE,
                Optional.of("192.168.56.20"),
                0, 0,
                SizeProfile.LARGE,
                List.of(),
                Optional.empty()
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: success with correct IP
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(1);
        assertThat(enriched.vms().getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("staging"));
                    assertThat(vm.role()).isEqualTo(NodeRole.CLUSTER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.20");
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.LARGE);
                });
    }

    @Test
    void shouldOrchestrateNoneCluster() {
        // Given: NONE (management) cluster
        var cluster = new ClusterSpec(
                ClusterName.of("mgmt"),
                ClusterType.NONE,
                Optional.of("192.168.56.6"),  // Note: .5 is reserved, so use .6
                0, 0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: success with MANAGEMENT role
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(1);
        assertThat(enriched.vms().getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("mgmt"));
                    assertThat(vm.role()).isEqualTo(NodeRole.MANAGEMENT);
                    assertThat(vm.ip()).isEqualTo("192.168.56.6");
                });
    }

    @Test
    void shouldOrchestrateKubeadmClusterSingleMaster() {
        // Given: KUBEADM cluster 1 master, 0 workers
        var cluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.30"),
                1, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.FLANNEL)
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: success with 1 master VM
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(1);
        assertThat(enriched.vms().getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(VmName.of("dev-master-1"));
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.30");
                });
    }

    @Test
    void shouldOrchestrateKubeadmClusterMultiNode() {
        // Given: KUBEADM cluster 2 masters, 3 workers
        var cluster = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.40"),
                2, 3,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: success with 5 VMs (2 masters + 3 workers)
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(5);

        // Verify masters
        assertThat(enriched.vms().getFirst())
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_MASTER_1);
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.40");
                });

        assertThat(enriched.vms().get(1))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_MASTER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.41");
                });

        // Verify workers
        assertThat(enriched.vms().get(2))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_1);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.42");
                });

        assertThat(enriched.vms().get(3))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.43");
                });

        assertThat(enriched.vms().get(4))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_3);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip()).isEqualTo("192.168.56.44");
                });
    }

    @Test
    void shouldSkipOrchestrationForClustersWithExplicitVms() {
        // Given: cluster with explicit VMs already defined
        var explicitVm = new VmConfig(
                VmName.of("custom-vm"),
                NodeRole.CLUSTER,
                "10.0.0.10",
                SizeProfile.MEDIUM,
                Optional.of(4096),
                Optional.of(4)
        );

        var cluster = new ClusterSpec(
                ClusterName.of("custom"),
                ClusterType.KIND,
                Optional.empty(),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(explicitVm),  // Explicit VMs
                Optional.empty()
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: cluster returned unchanged
        assertThat(result.isSuccess()).isTrue();
        var enriched = result.orElseThrow();
        assertThat(enriched).isSameAs(cluster);
        assertThat(enriched.vms()).containsExactly(explicitVm);
    }

    @Test
    void shouldHandleIpAllocationFailure() {
        // Given: cluster with invalid IP format
        var cluster = new ClusterSpec(
                ClusterName.of("bad-ip"),
                ClusterType.KIND,
                Optional.of("invalid-ip-format"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: failure with descriptive error
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("IP allocation failed")
                .contains("bad-ip");
    }

    @Test
    void shouldRejectNullCluster() {
        assertThatThrownBy(() -> orchestrator.orchestrate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cluster cannot be null");
    }

    // ==================== Multi-Cluster Orchestration ====================

    @Test
    void shouldOrchestrateMultipleClustersIndependently() {
        // Given: 3 different clusters
        var kindCluster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.of("192.168.56.10"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        var kubeadmCluster = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.20"),
                1, 2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        var mgmtCluster = new ClusterSpec(
                ClusterName.of("mgmt"),
                ClusterType.NONE,
                Optional.of("192.168.56.30"),
                0, 0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        var clusters = List.of(kindCluster, kubeadmCluster, mgmtCluster);

        // When: orchestrateMulti
        var result = orchestrator.orchestrateMulti(clusters);

        // Then: all clusters enriched
        assertThat(result.isSuccess()).isTrue();

        var enrichedClusters = result.orElseThrow();
        assertThat(enrichedClusters).hasSize(3);

        // Verify dev (KIND)
        assertThat(enrichedClusters.getFirst().name()).isEqualTo(DEV);
        assertThat(enrichedClusters.getFirst().vms()).hasSize(1);
        assertThat(enrichedClusters.getFirst().vms().getFirst().ip()).isEqualTo("192.168.56.10");

        // Verify staging (KUBEADM 1m,2w)
        assertThat(enrichedClusters.get(1).name()).isEqualTo(STAGING);
        assertThat(enrichedClusters.get(1).vms()).hasSize(3);
        assertThat(enrichedClusters.get(1).vms().getFirst().ip()).isEqualTo("192.168.56.20");
        assertThat(enrichedClusters.get(1).vms().get(1).ip()).isEqualTo("192.168.56.21");
        assertThat(enrichedClusters.get(1).vms().get(2).ip()).isEqualTo("192.168.56.22");

        // Verify mgmt (NONE)
        assertThat(enrichedClusters.get(2).name()).isEqualTo(MGMT);
        assertThat(enrichedClusters.get(2).vms()).hasSize(1);
        assertThat(enrichedClusters.get(2).vms().getFirst().ip()).isEqualTo("192.168.56.30");
    }

    @Test
    void shouldHandleEmptyClusterList() {
        // Given: empty list
        var clusters = List.<ClusterSpec>of();

        // When: orchestrateMulti
        var result = orchestrator.orchestrateMulti(clusters);

        // Then: success with empty result
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.orElseThrow()).isEmpty();
    }

    @Test
    void shouldFailFastOnFirstClusterError() {
        // Given: 3 clusters, second one has invalid IP
        var cluster1 = new ClusterSpec(
                ClusterName.of("valid1"),
                ClusterType.KIND,
                Optional.of("192.168.56.10"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("invalid"),
                ClusterType.KIND,
                Optional.of("bad-ip"),  // Invalid IP
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        var cluster3 = new ClusterSpec(
                ClusterName.of("valid3"),
                ClusterType.KIND,
                Optional.of("192.168.56.30"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        var clusters = List.of(cluster1, cluster2, cluster3);

        // When: orchestrateMulti
        var result = orchestrator.orchestrateMulti(clusters);

        // Then: failure with error from second cluster
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("IP allocation failed")
                .contains("invalid");
    }

    @Test
    void shouldHandleMixOfExplicitAndGeneratedVms() {
        // Given: 2 clusters, one with explicit VMs, one without
        var explicitVm = new VmConfig(
                VmName.of("custom"),
                NodeRole.MANAGEMENT,
                "10.0.0.5",
                SizeProfile.SMALL,
                Optional.empty(),
                Optional.empty()
        );

        var cluster1 = new ClusterSpec(
                ClusterName.of("explicit"),
                ClusterType.NONE,
                Optional.empty(),
                0, 0,
                SizeProfile.SMALL,
                List.of(explicitVm),  // Explicit VM
                Optional.empty()
        );

        var cluster2 = new ClusterSpec(
                ClusterName.of("generated"),
                ClusterType.KIND,
                Optional.of("192.168.56.20"),
                0, 0,
                SizeProfile.MEDIUM,
                List.of(),  // Will be generated
                Optional.empty()
        );

        var clusters = List.of(cluster1, cluster2);

        // When: orchestrateMulti
        var result = orchestrator.orchestrateMulti(clusters);

        // Then: success with both clusters
        assertThat(result.isSuccess()).isTrue();

        var enrichedClusters = result.orElseThrow();
        assertThat(enrichedClusters).hasSize(2);

        // Verify first cluster kept explicit VM
        assertThat(enrichedClusters.getFirst().vms()).containsExactly(explicitVm);

        // Verify second cluster has generated VM
        assertThat(enrichedClusters.get(1).vms()).hasSize(1);
        assertThat(enrichedClusters.get(1).vms().getFirst().name()).isEqualTo(VmName.of("generated"));
        assertThat(enrichedClusters.get(1).vms().getFirst().ip()).isEqualTo("192.168.56.20");
    }

    @Test
    void shouldRejectNullClusterList() {
        assertThatThrownBy(() -> orchestrator.orchestrateMulti(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("clusters cannot be null");
    }

    // ==================== IP Allocation Integration Tests ====================

    @Test
    void shouldSkipReservedIpsDuringAllocation() {
        // Given: cluster starting at IP that would normally use reserved .5
        var cluster = new ClusterSpec(
                ClusterName.of("test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.3"),
                3, 0,  // 3 masters: should get .3, .4, .6 (skip .5)
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: IPs allocated skipping .5
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(3);
        assertThat(enriched.vms().getFirst().ip()).isEqualTo("192.168.56.3");
        assertThat(enriched.vms().get(1).ip()).isEqualTo("192.168.56.4");
        assertThat(enriched.vms().get(2).ip()).isEqualTo("192.168.56.6");  // Skipped .5
    }

    @Test
    void shouldHandleSubnetBoundaryViolation() {
        // Given: cluster starting at .252 needing 5 IPs (would exceed .254)
        var cluster = new ClusterSpec(
                ClusterName.of("boundary-test"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.252"),
                3, 2,  // 5 total VMs
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: failure due to subnet boundary
        assertThat(result.isFailure()).isTrue();
        assertThat(result.getError())
                .contains("IP allocation failed")
                .contains("boundary-test");
    }

    // ==================== Naming Convention Tests ====================

    @Test
    void shouldFollowNamingConventionConsistently() {
        // Given: various cluster types
        var clusters = List.of(
                new ClusterSpec(ClusterName.of("dev"), ClusterType.KIND, Optional.of("192.168.56.10"),
                        0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()),
                new ClusterSpec(ClusterName.of("staging"), ClusterType.MINIKUBE, Optional.of("192.168.56.20"),
                        0, 0, SizeProfile.MEDIUM, List.of(), Optional.empty()),
                new ClusterSpec(ClusterName.of("mgmt"), ClusterType.NONE, Optional.of("192.168.56.30"),
                        0, 0, SizeProfile.SMALL, List.of(), Optional.empty()),
                new ClusterSpec(ClusterName.of("prod"), ClusterType.KUBEADM, Optional.of("192.168.56.40"),
                        2, 3, SizeProfile.LARGE, List.of(), Optional.of(CniType.CALICO))
        );

        // When: orchestrateMulti
        var result = orchestrator.orchestrateMulti(clusters);

        // Then: naming conventions followed
        assertThat(result.isSuccess()).isTrue();
        var enriched = result.orElseThrow();

        // KIND: VM name = cluster name
        assertThat(enriched.getFirst().vms().getFirst().name()).isEqualTo(VmName.of("dev"));

        // MINIKUBE: VM name = cluster name
        assertThat(enriched.get(1).vms().getFirst().name()).isEqualTo(VmName.of("staging"));

        // NONE: VM name = cluster name
        assertThat(enriched.get(2).vms().getFirst().name()).isEqualTo(VmName.of("mgmt"));

        // KUBEADM: cluster-prefixed names
        assertThat(enriched.get(3).vms().getFirst().name()).isEqualTo(PROD_MASTER_1);
        assertThat(enriched.get(3).vms().get(1).name()).isEqualTo(PROD_MASTER_2);
        assertThat(enriched.get(3).vms().get(2).name()).isEqualTo(PROD_WORKER_1);
        assertThat(enriched.get(3).vms().get(3).name()).isEqualTo(PROD_WORKER_2);
        assertThat(enriched.get(3).vms().get(4).name()).isEqualTo(PROD_WORKER_3);
    }
}
