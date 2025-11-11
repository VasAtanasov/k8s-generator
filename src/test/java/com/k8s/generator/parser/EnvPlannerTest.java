package com.k8s.generator.parser;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite for EnvPlanner.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>P0 fixes: Empty VMs validation, constants usage, determinism</li>
 *   <li>P1 fixes: Provider support (Azure), kubeadm-specific variables</li>
 *   <li>Engine-specific behavior: KIND, minikube, kubeadm, management-only</li>
 *   <li>Per-VM environment isolation and role-based variables</li>
 * </ul>
 *
 * @since 1.0.0
 */
class EnvPlannerTest {

    // Test fixtures
    private static final ModuleInfo MODULE = new ModuleInfo("m1", "pt");
    private static final ClusterName KIND_CLUSTER_NAME = ClusterName.of("clu-m1-pt-kind");
    private static final ClusterName KUBEADM_CLUSTER_NAME = ClusterName.of("clu-m1-pt-kubeadm");
    private static final VmName KIND_VM_NAME = VmName.of("clu-m1-pt-kind");
    private static final VmName MASTER_VM_NAME = VmName.of("clu-m1-pt-kubeadm-master-1");
    private static final VmName WORKER_VM_NAME = VmName.of("clu-m1-pt-kubeadm-worker-1");

    private static ClusterSpec kindCluster() {
        return ClusterSpec.builder()
                .name(KIND_CLUSTER_NAME)
                .type(Kind.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();
    }

    private static ClusterSpec kubeadmCluster() {
        return ClusterSpec.builder()
                .name(KUBEADM_CLUSTER_NAME)
                .type(Kubeadm.INSTANCE)
                .masters(1).workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();
    }

    private static VmConfig kindVm() {
        return VmConfig.builder()
                .name(KIND_VM_NAME)
                .role(NodeRole.CLUSTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
    }

    private static VmConfig masterVm() {
        return VmConfig.builder()
                .name(MASTER_VM_NAME)
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
    }

    private static VmConfig workerVm() {
        return VmConfig.builder()
                .name(WORKER_VM_NAME)
                .role(NodeRole.WORKER)
                .ip("192.168.56.11")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
    }

    @Test
    @DisplayName("build() with KIND cluster produces correct global environment")
    void build_kindCluster_producesCorrectGlobalEnv() {
        // Given: KIND cluster with single VM
        var cluster = kindCluster();
        var vms = List.of(kindVm());

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global environment has base variables only (no CNI)
        assertThat(envSet.global())
                .containsEntry("CLUSTER_NAME", "clu-m1-pt-kind")
                .containsEntry("NAMESPACE_DEFAULT", "ns-m1-pt")
                .containsEntry("CLUSTER_TYPE", "kind")
                .doesNotContainKey("CNI_TYPE")  // KIND doesn't expose CNI
                .doesNotContainKey("KUBE_API_PORT");  // Only kubeadm
    }

    @Test
    @DisplayName("build() with kubeadm cluster includes CNI type and API port")
    void build_kubeadmCluster_includesCniType() {
        // Given: Kubeadm cluster with Calico CNI
        var cluster = kubeadmCluster();
        var vms = List.of(masterVm(), workerVm());

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global environment includes kubeadm-specific variables
        assertThat(envSet.global())
                .containsEntry("CLUSTER_NAME", "clu-m1-pt-kubeadm")
                .containsEntry("NAMESPACE_DEFAULT", "ns-m1-pt")
                .containsEntry("CLUSTER_TYPE", "kubeadm")
                .containsEntry("CNI_TYPE", "calico")
                .containsEntry("KUBE_API_PORT", "6443");
    }

    @Test
    @DisplayName("build() with kubeadm master sets CONTROL_PLANE flag")
    void build_kubeadmMaster_setsControlPlaneFlag() {
        // Given: Kubeadm master and worker VMs
        var cluster = kubeadmCluster();
        var master = masterVm();
        var worker = workerVm();
        var vms = List.of(master, worker);

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Master has CONTROL_PLANE=1, worker does not
        assertThat(envSet.perVm().get(master.name()))
                .containsEntry("CONTROL_PLANE", "1")
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-master-1")
                .containsEntry("ROLE", "master")
                .containsEntry("VM_IP", "192.168.56.10");

        assertThat(envSet.perVm().get(worker.name()))
                .doesNotContainKey("CONTROL_PLANE")  // Workers don't get flag
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-worker-1")
                .containsEntry("ROLE", "worker")
                .containsEntry("VM_IP", "192.168.56.11");
    }

    @Test
    @DisplayName("build() is deterministic - same inputs produce same output")
    void build_deterministic_sameInputsProduceSameOutput() {
        // Given: Same cluster and VMs
        var cluster = kindCluster();
        var vms = List.of(kindVm());

        // When: Build environment twice
        var envSet1 = EnvPlanner.build(MODULE, cluster, vms);
        var envSet2 = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Results are identical
        assertThat(envSet1.global()).isEqualTo(envSet2.global());
        assertThat(envSet1.perVm()).isEqualTo(envSet2.perVm());
    }

    @Test
    @DisplayName("build() with empty VMs list throws IllegalArgumentException")
    void build_emptyVmsList_throwsIllegalArgumentException() {
        // Given: Empty VMs list
        var cluster = kindCluster();
        var vms = List.<VmConfig>of();

        // When/Then: Build throws exception
        assertThatThrownBy(() -> EnvPlanner.build(MODULE, cluster, vms))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vms list cannot be empty");
    }

    @Test
    @DisplayName("build() with null parameters throws NullPointerException")
    void build_nullParameters_throwsNullPointerException() {
        // Given: Valid inputs
        var cluster = kindCluster();
        var vms = List.of(kindVm());

        // When/Then: Null module throws
        assertThatThrownBy(() -> EnvPlanner.build(null, cluster, vms))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("module");

        // When/Then: Null cluster throws
        assertThatThrownBy(() -> EnvPlanner.build(MODULE, null, vms))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cluster");

        // When/Then: Null vms throws
        assertThatThrownBy(() -> EnvPlanner.build(MODULE, cluster, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("vms");

        // When/Then: Null providers throws (4-arg overload)
        assertThatThrownBy(() -> EnvPlanner.build(MODULE, cluster, vms, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("providers");
    }

    @Test
    @DisplayName("build() with Azure provider includes Azure environment variables")
    void build_withProviders_includesAzureVariables() {
        // Given: Kubeadm cluster with Azure provider
        var cluster = kubeadmCluster();
        var vms = List.of(masterVm());
        var providers = Set.of(CloudProvider.azure());

        // When: Build environment with providers
        var envSet = EnvPlanner.build(MODULE, cluster, vms, providers);

        // Then: Global environment includes Azure placeholders
        assertThat(envSet.global())
                .containsEntry("CLUSTER_NAME", "clu-m1-pt-kubeadm")
                .containsEntry("CNI_TYPE", "calico")
                .containsEntry("AZ_SUBSCRIPTION_ID", "${AZ_SUBSCRIPTION_ID}")
                .containsEntry("AZ_RESOURCE_GROUP", "${AZ_RESOURCE_GROUP}")
                .containsEntry("AZ_LOCATION", "${AZ_LOCATION}");
    }

    @Test
    @DisplayName("build() without providers does not include Azure variables")
    void build_withoutProviders_noAzureVariables() {
        // Given: Kubeadm cluster without providers
        var cluster = kubeadmCluster();
        var vms = List.of(masterVm());

        // When: Build environment (default no providers)
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global environment has no Azure variables
        assertThat(envSet.global())
                .doesNotContainKey("AZ_SUBSCRIPTION_ID")
                .doesNotContainKey("AZ_RESOURCE_GROUP")
                .doesNotContainKey("AZ_LOCATION");
    }

    @Test
    @DisplayName("build() with multiple VMs produces isolated per-VM environments")
    void build_multipleVms_eachHasUniquePerVmEnv() {
        // Given: Kubeadm cluster with 2 masters and 2 workers
        var cluster = kubeadmCluster();
        var master1 = VmConfig.builder()
                .name("clu-m1-pt-kubeadm-master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var master2 = VmConfig.builder()
                .name("clu-m1-pt-kubeadm-master-2")
                .role(NodeRole.MASTER)
                .ip("192.168.56.11")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var worker1 = VmConfig.builder()
                .name("clu-m1-pt-kubeadm-worker-1")
                .role(NodeRole.WORKER)
                .ip("192.168.56.12")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var worker2 = VmConfig.builder()
                .name("clu-m1-pt-kubeadm-worker-2")
                .role(NodeRole.WORKER)
                .ip("192.168.56.13")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var vms = List.of(master1, master2, worker1, worker2);

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Each VM has unique per-VM environment
        assertThat(envSet.perVm()).hasSize(4);

        // Master 1
        assertThat(envSet.perVm().get(master1.name()))
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-master-1")
                .containsEntry("VM_IP", "192.168.56.10")
                .containsEntry("ROLE", "master")
                .containsEntry("CONTROL_PLANE", "1");

        // Master 2
        assertThat(envSet.perVm().get(master2.name()))
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-master-2")
                .containsEntry("VM_IP", "192.168.56.11")
                .containsEntry("ROLE", "master")
                .containsEntry("CONTROL_PLANE", "1");

        // Worker 1
        assertThat(envSet.perVm().get(worker1.name()))
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-worker-1")
                .containsEntry("VM_IP", "192.168.56.12")
                .containsEntry("ROLE", "worker")
                .doesNotContainKey("CONTROL_PLANE");

        // Worker 2
        assertThat(envSet.perVm().get(worker2.name()))
                .containsEntry("VM_NAME", "clu-m1-pt-kubeadm-worker-2")
                .containsEntry("VM_IP", "192.168.56.13")
                .containsEntry("ROLE", "worker")
                .doesNotContainKey("CONTROL_PLANE");
    }

    @Test
    @DisplayName("build() returns immutable EnvSet")
    void build_returnsImmutableEnvSet() {
        // Given: Valid cluster and VMs
        var cluster = kindCluster();
        var vms = List.of(kindVm());

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global map is immutable
        assertThatThrownBy(() -> envSet.global().put("NEW_KEY", "value"))
                .isInstanceOf(UnsupportedOperationException.class);

        // Then: Per-VM map is immutable
        assertThatThrownBy(() -> envSet.perVm().put(KIND_VM_NAME, Map.of("NEW_KEY", "value")))
                .isInstanceOf(UnsupportedOperationException.class);

        // Then: Per-VM inner map is immutable
        Map<String, String> kindEnv = envSet.perVm().get(KIND_VM_NAME);
        assertThatThrownBy(() -> kindEnv.put("NEW_KEY", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("build() with minikube cluster produces correct environment")
    void build_minikubeCluster_producesCorrectEnv() {
        // Given: Minikube cluster
        var cluster = ClusterSpec.builder()
                .name("clu-m1-pt-minikube")
                .type(Minikube.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();
        var vm = VmConfig.builder()
                .name("clu-m1-pt-minikube")
                .role(NodeRole.CLUSTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var vms = List.of(vm);

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global environment has base variables only
        assertThat(envSet.global())
                .containsEntry("CLUSTER_NAME", "clu-m1-pt-minikube")
                .containsEntry("NAMESPACE_DEFAULT", "ns-m1-pt")
                .containsEntry("CLUSTER_TYPE", "minikube")
                .doesNotContainKey("CNI_TYPE")
                .doesNotContainKey("KUBE_API_PORT");

        // Then: Per-VM environment is correct
        assertThat(envSet.perVm().get(vm.name()))
                .containsEntry("VM_NAME", "clu-m1-pt-minikube")
                .containsEntry("ROLE", "cluster")
                .containsEntry("VM_IP", "192.168.56.10")
                .doesNotContainKey("CONTROL_PLANE");
    }

    @Test
    @DisplayName("build() with management-only cluster (NONE type) produces correct environment")
    void build_managementOnlyCluster_producesCorrectEnv() {
        // Given: Management-only cluster (NONE type)
        var cluster = ClusterSpec.builder()
                .name("mgmt-m7-hw")
                .type(NoneCluster.INSTANCE)
                .masters(0).workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();
        var vm = VmConfig.builder()
                .name("mgmt-m7-hw")
                .role(NodeRole.MANAGEMENT)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.SMALL)
                .build();
        var vms = List.of(vm);

        // When: Build environment
        var envSet = EnvPlanner.build(MODULE, cluster, vms);

        // Then: Global environment has base variables
        assertThat(envSet.global())
                .containsEntry("CLUSTER_NAME", "mgmt-m7-hw")
                .containsEntry("CLUSTER_TYPE", "none")
                .doesNotContainKey("CNI_TYPE")
                .doesNotContainKey("KUBE_API_PORT");

        // Then: Per-VM environment reflects management role
        assertThat(envSet.perVm().get(vm.name()))
                .containsEntry("VM_NAME", "mgmt-m7-hw")
                .containsEntry("ROLE", "management")
                .containsEntry("VM_IP", "192.168.56.10")
                .doesNotContainKey("CONTROL_PLANE");
    }
}
