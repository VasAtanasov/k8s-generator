package com.k8s.generator.orchestrator;

import com.k8s.generator.ip.SequentialIpAllocator;
import com.k8s.generator.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        var cluster = ClusterSpec.builder()
                .name("dev")
                .type(Kind.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

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
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.10");  // Default IP
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
                });
    }

    @Test
    void shouldOrchestrateMinikubeCluster() {
        // Given: MINIKUBE cluster with explicit IP
        var cluster = ClusterSpec.builder()
                .name("staging")
                .type(Minikube.INSTANCE)
                .firstIp("192.168.56.20")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .build();

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
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.20");
                    assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.LARGE);
                });
    }

    @Test
    void shouldOrchestrateNoneCluster() {
        // Given: NONE (management) cluster
        var cluster = ClusterSpec.builder()
                .name("mgmt")
                .type(NoneCluster.INSTANCE)
                .firstIp("192.168.56.6")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

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
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.6");
                });
    }

    @Test
    void shouldOrchestrateKubeadmClusterSingleMaster() {
        // Given: KUBEADM cluster 1 master, 0 workers
        var cluster = ClusterSpec.builder()
                .name("dev")
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.30")
                .masters(1).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.FLANNEL)
                .build();

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
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.30");
                });
    }

    @Test
    void shouldOrchestrateKubeadmClusterMultiNode() {
        // Given: KUBEADM cluster 2 masters, 3 workers
        var cluster = ClusterSpec.builder()
                .name("prod")
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.40")
                .masters(2).workers(3)
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.40");
                });

        assertThat(enriched.vms().get(1))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_MASTER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.41");
                });

        // Verify workers
        assertThat(enriched.vms().get(2))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_1);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.42");
                });

        assertThat(enriched.vms().get(3))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_2);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.43");
                });

        assertThat(enriched.vms().get(4))
                .satisfies(vm -> {
                    assertThat(vm.name()).isEqualTo(PROD_WORKER_3);
                    assertThat(vm.role()).isEqualTo(NodeRole.WORKER);
                    assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.44");
                });
    }

    @Test
    void shouldSkipOrchestrationForClustersWithExplicitVms() {
        // Given: cluster with explicit VMs already defined
        var explicitVm = VmConfig.builder()
                .name("custom-vm")
                .role(NodeRole.CLUSTER)
                .ip("10.0.0.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .memoryMbOverride(4096)
                .cpuOverride(4)
                .build();

        var cluster = ClusterSpec.builder()
                .name("custom")
                .type(Kind.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(explicitVm))
                .build();

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
        var cluster = ClusterSpec.builder()
                .name("bad-ip")
                .type(Kind.INSTANCE)
                .firstIp("fe80::1")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

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
        var kindCluster = ClusterSpec.builder()
                .name("dev").type(Kind.INSTANCE)
                .firstIp("192.168.56.10")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        var kubeadmCluster = ClusterSpec.builder()
                .name("staging").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.20")
                .masters(1).workers(2)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        var mgmtCluster = ClusterSpec.builder()
                .name("mgmt").type(NoneCluster.INSTANCE)
                .firstIp("192.168.56.30")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

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
        assertThat(enrichedClusters.getFirst().vms().getFirst().ip().toCanonicalString()).isEqualTo("192.168.56.10");

        // Verify staging (KUBEADM 1m,2w)
        assertThat(enrichedClusters.get(1).name()).isEqualTo(STAGING);
        assertThat(enrichedClusters.get(1).vms()).hasSize(3);
        assertThat(enrichedClusters.get(1).vms().getFirst().ip().toCanonicalString()).isEqualTo("192.168.56.20");
        assertThat(enrichedClusters.get(1).vms().get(1).ip().toCanonicalString()).isEqualTo("192.168.56.21");
        assertThat(enrichedClusters.get(1).vms().get(2).ip().toCanonicalString()).isEqualTo("192.168.56.22");

        // Verify mgmt (NONE)
        assertThat(enrichedClusters.get(2).name()).isEqualTo(MGMT);
        assertThat(enrichedClusters.get(2).vms()).hasSize(1);
        assertThat(enrichedClusters.get(2).vms().getFirst().ip().toCanonicalString()).isEqualTo("192.168.56.30");
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
        var cluster1 = ClusterSpec.builder()
                .name("valid1").type(Kind.INSTANCE)
                .firstIp("192.168.56.10")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        var cluster2 = ClusterSpec.builder()
                .name("invalid").type(Kind.INSTANCE)
                .firstIp("fe80::1")  // Invalid for IPv4 allocation
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        var cluster3 = ClusterSpec.builder()
                .name("valid3").type(Kind.INSTANCE)
                .firstIp("192.168.56.30")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

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
        var explicitVm = VmConfig.builder()
                .name("custom")
                .role(NodeRole.MANAGEMENT)
                .ip("10.0.0.5")
                .sizeProfile(SizeProfile.SMALL)
                .build();

        var cluster1 = ClusterSpec.builder()
                .name("explicit").type(NoneCluster.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of(explicitVm))
                .build();

        var cluster2 = ClusterSpec.builder()
                .name("generated").type(Kind.INSTANCE)
                .firstIp("192.168.56.20")
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

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
        assertThat(enrichedClusters.get(1).vms().getFirst().ip().toCanonicalString()).isEqualTo("192.168.56.20");
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
        var cluster = ClusterSpec.builder()
                .name("test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.3")
                .masters(3).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        // When: orchestrate
        var result = orchestrator.orchestrate(cluster);

        // Then: IPs allocated skipping .5
        assertThat(result.isSuccess()).isTrue();

        var enriched = result.orElseThrow();
        assertThat(enriched.vms()).hasSize(3);
        assertThat(enriched.vms().getFirst().ip().toCanonicalString()).isEqualTo("192.168.56.3");
        assertThat(enriched.vms().get(1).ip().toCanonicalString()).isEqualTo("192.168.56.4");
        assertThat(enriched.vms().get(2).ip().toCanonicalString()).isEqualTo("192.168.56.6");  // Skipped .5
    }

    @Test
    void shouldHandleSubnetBoundaryViolation() {
        // Given: cluster starting at .252 needing 5 IPs (would exceed .254)
        var cluster = ClusterSpec.builder()
                .name("boundary-test").type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.252")
                .masters(3).workers(2)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
                ClusterSpec.builder().name("dev").type(Kind.INSTANCE).firstIp("192.168.56.10").masters(0).workers(0).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build(),
                ClusterSpec.builder().name("staging").type(Minikube.INSTANCE).firstIp("192.168.56.20").masters(0).workers(0).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).build(),
                ClusterSpec.builder().name("mgmt").type(NoneCluster.INSTANCE).firstIp("192.168.56.30").masters(0).workers(0).sizeProfile(SizeProfile.SMALL).vms(List.of()).build(),
                ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE).firstIp("192.168.56.40").masters(2).workers(3).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build()
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
