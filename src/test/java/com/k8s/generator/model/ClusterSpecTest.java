package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClusterSpec record.
 */
class ClusterSpecTest {

    @Test
    void shouldCreateValidKubeadmCluster() {
        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                3,
                5,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        assertThat(spec.name().toString()).isEqualTo("staging");
        assertThat(spec.type()).isEqualTo(ClusterType.KUBEADM);
        assertThat(spec.firstIp()).contains("192.168.56.10");
        assertThat(spec.masters()).isEqualTo(3);
        assertThat(spec.workers()).isEqualTo(5);
        assertThat(spec.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
        assertThat(spec.vms()).isEmpty();
    }

    @Test
    void shouldCreateValidKindCluster() {
        var spec = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.empty(),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        assertThat(spec.name().toString()).isEqualTo("dev");
        assertThat(spec.type()).isEqualTo(ClusterType.KIND);
        assertThat(spec.firstIp()).isEmpty();
        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldCreateClusterWithExplicitVms() {
        var vm1 = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());
        var vm2 = new VmConfig(
                VmName.of("worker-1"),
                NodeRole.WORKER,
                "192.168.56.11",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(vm1, vm2),
                Optional.of(CniType.CALICO)
        );

        assertThat(spec.vms()).hasSize(2);
        assertThat(spec.hasExplicitVms()).isTrue();
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new ClusterSpec(
                null,
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                null,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type is required");
    }

    @Test
    void shouldRejectNullFirstIp() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                null,
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("firstIp must be present");
    }

    @Test
    void shouldRejectNullSizeProfile() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                null,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sizeProfile is required");
    }

    @Test
    void shouldRejectNullVmsList() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                null,
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("vms list is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankName(String blank) {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of(blank),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be blank");
    }

    @Test
    void shouldRejectNegativeMasters() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                -1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("masters must be >= 0");
    }

    @Test
    void shouldRejectNegativeWorkers() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                -1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workers must be >= 0");
    }

    @Test
    void shouldRejectKubeadmClusterWithZeroNodes() {
        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                0,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KUBEADM cluster requires at least one node");
    }

    @Test
    void shouldAllowKindClusterWithZeroNodes() {
        var spec = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.empty(),
                0,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldAllowMinikubeClusterWithZeroNodes() {
        var spec = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.MINIKUBE,
                Optional.empty(),
                0,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldAllowNoneClusterWithZeroNodes() {
        var spec = new ClusterSpec(
                ClusterName.of("mgmt"),
                ClusterType.NONE,
                Optional.empty(),
                0,
                0,
                SizeProfile.SMALL,
                List.of(),
                Optional.empty()
        );

        assertThat(spec.masters()).isZero();
        assertThat(spec.workers()).isZero();
    }

    @Test
    void shouldRejectVmsListContainingNulls() {
        var vm1 = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        // Use ArrayList to avoid List.of() throwing NPE before our validation
        var listWithNull = new java.util.ArrayList<VmConfig>();
        listWithNull.add(vm1);
        listWithNull.add(null);

        assertThatThrownBy(() -> new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                listWithNull,
                Optional.of(CniType.CALICO)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vms list contains null elements");
    }

    @Test
    void shouldCalculateTotalNodes() {
        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                3,
                5,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        assertThat(spec.totalNodes()).isEqualTo(8);
    }

    @Test
    void shouldReturnZeroTotalNodesForKind() {
        var spec = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KIND,
                Optional.empty(),
                0,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty()
        );

        assertThat(spec.totalNodes()).isZero();
    }

    @Test
    void shouldDetectHighAvailability() {
        var haCluster = new ClusterSpec(
                ClusterName.of("prod"),
                ClusterType.KUBEADM,
                Optional.empty(),
                3,
                5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        var singleMaster = new ClusterSpec(
                ClusterName.of("dev"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        assertThat(haCluster.isHighAvailability()).isTrue();
        assertThat(singleMaster.isHighAvailability()).isFalse();
    }

    @Test
    void shouldDetectExplicitVms() {
        var vm = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var withVms = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(vm),
                Optional.of(CniType.CALICO)
        );

        var withoutVms = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        assertThat(withVms.hasExplicitVms()).isTrue();
        assertThat(withoutVms.hasExplicitVms()).isFalse();
    }

    @Test
    void shouldCreateNewInstanceWithUpdatedVms() {
        var original = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        var vm = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());
        var updated = original.withVms(List.of(vm));

        // Original unchanged
        assertThat(original.vms()).isEmpty();
        // New instance has VMs
        assertThat(updated.vms()).hasSize(1);
        assertThat(updated.vms().getFirst()).isEqualTo(vm);
    }

    @Test
    void shouldRejectNullInWithVms() {
        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO)
        );

        assertThatThrownBy(() -> spec.withVms(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("newVms cannot be null");
    }

    @Test
    void shouldMakeDefensiveCopyOfVmsList() {
        var vm = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());
        var mutableList = new java.util.ArrayList<>(List.of(vm));

        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                mutableList,
                Optional.of(CniType.CALICO)
        );

        // Modify original list
        mutableList.clear();

        // Spec's list should be unchanged
        assertThat(spec.vms()).hasSize(1);
    }

    @Test
    void shouldReturnImmutableVmsList() {
        var vm = new VmConfig(
                VmName.of("master-1"),
                NodeRole.MASTER,
                "192.168.56.10",
                SizeProfile.MEDIUM,
                Optional.empty(),
                Optional.empty());

        var spec = new ClusterSpec(
                ClusterName.of("staging"),
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(vm),
                Optional.of(CniType.CALICO)
        );

        // Attempt to modify should fail
        assertThatThrownBy(() -> spec.vms().add(vm))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
