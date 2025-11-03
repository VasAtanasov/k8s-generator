package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for VmConfig record.
 */
class VmConfigTest {

    @Test
    void shouldCreateValidVmConfig() {
        var vm = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(vm.name()).isEqualTo("master-1");
        assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
        assertThat(vm.ip()).isEqualTo("192.168.56.10");
        assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
        assertThat(vm.cpuOverride()).isEmpty();
        assertThat(vm.memoryMbOverride()).isEmpty();
    }

    @Test
    void shouldCreateVmConfigWithOverrides() {
        var vm = new VmConfig(
            "worker-1",
            NodeRole.WORKER,
            "192.168.56.11",
            SizeProfile.LARGE,
            Optional.of(8),
            Optional.of(16384)
        );

        assertThat(vm.cpuOverride()).contains(8);
        assertThat(vm.memoryMbOverride()).contains(16384);
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> new VmConfig(
            null,
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            null,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("role is required");
    }

    @Test
    void shouldRejectNullIp() {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            null,
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ip is required");
    }

    @Test
    void shouldRejectNullSizeProfile() {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            null,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("sizeProfile is required");
    }

    @Test
    void shouldRejectNullCpuOverride() {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            null,
            Optional.empty()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("cpuOverride must be present");
    }

    @Test
    void shouldRejectNullMemoryOverride() {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("memoryMbOverride must be present");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankName(String blank) {
        assertThatThrownBy(() -> new VmConfig(
            blank,
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("name cannot be blank");
    }

    // Test removed: With NodeRole enum, blank/invalid roles are prevented at compile time
    // Type safety eliminates the need for runtime validation of role values

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankIp(String blank) {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            blank,
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ip cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldRejectNonPositiveCpuOverride(int invalidCpu) {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.of(invalidCpu),
            Optional.empty()
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cpuOverride must be positive")
            .hasMessageContaining(String.valueOf(invalidCpu));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -1024})
    void shouldRejectNonPositiveMemoryOverride(int invalidMemory) {
        assertThatThrownBy(() -> new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.of(invalidMemory)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("memoryMbOverride must be positive")
            .hasMessageContaining(String.valueOf(invalidMemory));
    }

    @Test
    void shouldReturnEffectiveCpusFromProfile() {
        var vm = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.LARGE,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(vm.getEffectiveCpus()).isEqualTo(SizeProfile.LARGE.getCpus());
    }

    @Test
    void shouldReturnEffectiveCpusFromOverride() {
        var vm = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.SMALL,
            Optional.of(8),
            Optional.empty()
        );

        assertThat(vm.getEffectiveCpus()).isEqualTo(8);
    }

    @Test
    void shouldReturnEffectiveMemoryFromProfile() {
        var vm = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(vm.getEffectiveMemoryMb()).isEqualTo(SizeProfile.MEDIUM.getMemoryMb());
    }

    @Test
    void shouldReturnEffectiveMemoryFromOverride() {
        var vm = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.SMALL,
            Optional.empty(),
            Optional.of(8192)
        );

        assertThat(vm.getEffectiveMemoryMb()).isEqualTo(8192);
    }

    @Test
    void shouldIdentifyMasterRole() {
        var master = new VmConfig(
            "master-1",
            NodeRole.MASTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(master.isMaster()).isTrue();
        assertThat(master.isWorker()).isFalse();
        assertThat(master.isCluster()).isFalse();
        assertThat(master.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyWorkerRole() {
        var worker = new VmConfig(
            "worker-1",
            NodeRole.WORKER,
            "192.168.56.11",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(worker.isMaster()).isFalse();
        assertThat(worker.isWorker()).isTrue();
        assertThat(worker.isCluster()).isFalse();
        assertThat(worker.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyClusterRole() {
        var cluster = new VmConfig(
            "cluster-1",
            NodeRole.CLUSTER,
            "192.168.56.10",
            SizeProfile.MEDIUM,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(cluster.isMaster()).isFalse();
        assertThat(cluster.isWorker()).isFalse();
        assertThat(cluster.isCluster()).isTrue();
        assertThat(cluster.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyManagementRole() {
        var management = new VmConfig(
            "management-1",
            NodeRole.MANAGEMENT,
            "192.168.56.10",
            SizeProfile.SMALL,
            Optional.empty(),
            Optional.empty()
        );

        assertThat(management.isMaster()).isFalse();
        assertThat(management.isWorker()).isFalse();
        assertThat(management.isCluster()).isFalse();
        assertThat(management.isManagement()).isTrue();
    }
}
