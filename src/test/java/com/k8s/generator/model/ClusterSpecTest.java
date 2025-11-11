package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClusterSpec record.
 */
class ClusterSpecTest {

    @Test
    void shouldCreateValidKubeadmCluster() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10")
                .masters(3)
                .workers(5)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThat(spec.name().toString()).isEqualTo("staging");
        assertThat(spec.type()).isEqualTo(Kubeadm.INSTANCE);
        assertThat(spec.firstIp()).isNotNull();
        assertThat(spec.firstIp().toCanonicalString()).isEqualTo("192.168.56.10");
        assertThat(spec.masters()).isEqualTo(3);
        assertThat(spec.workers()).isEqualTo(5);
        assertThat(spec.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
        assertThat(spec.vms()).isEmpty();
    }

    @Test
    void shouldCreateValidKindCluster() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(Kind.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

        assertThat(spec.name().toString()).isEqualTo("dev");
        assertThat(spec.type()).isEqualTo(Kind.INSTANCE);
        assertThat(spec.firstIp()).isNull();
        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldCreateClusterWithExplicitVms() {
        var vm1 = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var vm2 = VmConfig.builder()
                .name("worker-1")
                .role(NodeRole.WORKER)
                .ip("192.168.56.11")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm1, vm2))
                .cni(CniType.CALICO)
                .build();

        assertThat(spec.vms()).hasSize(2);
        assertThat(spec.hasExplicitVms()).isTrue();
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name((ClusterName) null)
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(null)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void shouldAllowNullFirstIp() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();
        assertThat(spec.firstIp()).isNull();
    }

    @Test
    void shouldRejectNullSizeProfile() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(1)
                .sizeProfile(null)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sizeProfile is required");
    }

    @Test
    void shouldRejectNullVmsList() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(null)
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("vms list is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankName(String blank) {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name(ClusterName.of(blank))
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be blank");
    }

    @Test
    void shouldRejectNegativeMasters() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(-1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("masters must be >= 0");
    }

    @Test
    void shouldRejectNegativeWorkers() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(-1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workers must be >= 0");
    }

    @Test
    void shouldRejectKubeadmClusterWithZeroNodes() {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KUBEADM cluster requires at least one node");
    }

    @Test
    void shouldAllowKindClusterWithZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(Kind.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldAllowMinikubeClusterWithZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(Minikube.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldAllowNoneClusterWithZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("mgmt")
                .type(NoneCluster.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.SMALL)
                .vms(List.of())
                .build();

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldRejectVmsListContainingNulls() {
        var vm1 = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        // Use ArrayList to avoid List.of() throwing NPE before our validation
        var listWithNull = new java.util.ArrayList<VmConfig>();
        listWithNull.add(vm1);
        listWithNull.add(null);

        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(listWithNull)
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vms list contains null elements");
    }

    @Test
    void shouldCalculateTotalNodes() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(3)
                .workers(5)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThat(spec.totalNodes()).isEqualTo(8);
    }

    @Test
    void shouldReturnZeroTotalNodesForKind() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(Kind.INSTANCE)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        assertThat(spec.totalNodes()).isZero();
    }

    @Test
    void shouldDetectHighAvailability() {
        var haCluster = ClusterSpec.builder()
                .name("prod")
                .type(Kubeadm.INSTANCE)
                .masters(3)
                .workers(5)
                .sizeProfile(SizeProfile.LARGE)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        var singleMaster = ClusterSpec.builder()
                .name("dev")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(2)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThat(haCluster.isHighAvailability()).isTrue();
        assertThat(singleMaster.isHighAvailability()).isFalse();
    }

    @Test
    void shouldDetectExplicitVms() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var withVms = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm))
                .cni(CniType.CALICO)
                .build();

        var withoutVms = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThat(withVms.hasExplicitVms()).isTrue();
        assertThat(withoutVms.hasExplicitVms()).isFalse();
    }

    @Test
    void shouldCreateNewInstanceWithUpdatedVms() {
        var original = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var updated = original.withVms(List.of(vm));

        // Original unchanged
        assertThat(original.vms()).isEmpty();
        // New instance has VMs
        assertThat(updated.vms()).hasSize(1);
        assertThat(updated.vms().getFirst()).isEqualTo(vm);
    }

    @Test
    void shouldRejectNullInWithVms() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        assertThatThrownBy(() -> spec.withVms(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("newVms cannot be null");
    }

    @Test
    void shouldMakeDefensiveCopyOfVmsList() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();
        var mutableList = new java.util.ArrayList<>(List.of(vm));

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(mutableList)
                .cni(CniType.CALICO)
                .build();

        // Modify original list
        mutableList.clear();

        // Spec's list should be unchanged
        assertThat(spec.vms()).hasSize(1);
    }

    @Test
    void shouldReturnImmutableVmsList() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm))
                .cni(CniType.CALICO)
                .build();

        // Attempt to modify should fail
        assertThatThrownBy(() -> spec.vms().add(vm))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    //    @Test
    void modelBrickContainsRequiredFields() {
        assertThat(GeneratorSpec.class.getDeclaredFields())
                .extracting(Field::getName)
                .contains("outputPath", "clusters", "management");
    }
}
