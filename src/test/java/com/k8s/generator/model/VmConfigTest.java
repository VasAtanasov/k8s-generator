package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for VmConfig record.
 */
class VmConfigTest {

    @Test
    void shouldCreateValidVmConfig() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        assertThat(vm.name()).isEqualTo(VmName.of("master-1"));
        assertThat(vm.role()).isEqualTo(NodeRole.MASTER);
        assertThat(vm.ip().toCanonicalString()).isEqualTo("192.168.56.10");
        assertThat(vm.sizeProfile()).isEqualTo(SizeProfile.MEDIUM);
        assertThat(vm.cpuOverride()).isNull();
        assertThat(vm.memoryMbOverride()).isNull();
    }

    @Test
    void shouldCreateVmConfigWithOverrides() {
        var vm = VmConfig.builder()
                .name("worker-1")
                .role(NodeRole.WORKER)
                .ip("192.168.56.11")
                .sizeProfile(SizeProfile.LARGE)
                .cpuOverride(8)
                .memoryMbOverride(16384)
                .build();

        assertThat(vm.cpuOverride()).isEqualTo(8);
        assertThat(vm.memoryMbOverride()).isEqualTo(16384);
    }

    @Test
    void shouldRejectNullName() {
        assertThatThrownBy(() -> VmConfig.builder()
                .name((VmName) null)
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name is required");
    }

    @Test
    void shouldRejectNullRole() {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(null)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("role is required");
    }

    @Test
    void shouldRejectNullIp() {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                // no ip set -> null
                .sizeProfile(SizeProfile.MEDIUM)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ip is required");
    }

    @Test
    void shouldRejectNullSizeProfile() {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(null)
                .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sizeProfile is required");
    }


    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankName(String blank) {
        assertThatThrownBy(() -> VmConfig.builder()
                .name(VmName.of(blank))
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name cannot be blank");
    }

    // Test removed: With NodeRole enum, blank/invalid roles are prevented at compile time
    // Type safety eliminates the need for runtime validation of role values

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "\t", "\n"})
    void shouldRejectBlankIp(String blank) {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip(blank)
                .sizeProfile(SizeProfile.MEDIUM)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ip cannot be blank");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldRejectNonPositiveCpuOverride(int invalidCpu) {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .cpuOverride(invalidCpu)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpuOverride must be positive")
                .hasMessageContaining(String.valueOf(invalidCpu));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -1024})
    void shouldRejectNonPositiveMemoryOverride(int invalidMemory) {
        assertThatThrownBy(() -> VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .memoryMbOverride(invalidMemory)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryMbOverride must be positive")
                .hasMessageContaining(String.valueOf(invalidMemory));
    }

    @Test
    void shouldReturnEffectiveCpusFromProfile() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.LARGE)
                .build();

        assertThat(vm.getEffectiveCpus()).isEqualTo(SizeProfile.LARGE.getCpus());
    }

    @Test
    void shouldReturnEffectiveCpusFromOverride() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.SMALL)
                .cpuOverride(8)
                .build();

        assertThat(vm.getEffectiveCpus()).isEqualTo(8);
    }

    @Test
    void shouldReturnEffectiveMemoryFromProfile() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        assertThat(vm.getEffectiveMemoryMb()).isEqualTo(SizeProfile.MEDIUM.getMemoryMb());
    }

    @Test
    void shouldReturnEffectiveMemoryFromOverride() {
        var vm = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.SMALL)
                .memoryMbOverride(8192)
                .build();

        assertThat(vm.getEffectiveMemoryMb()).isEqualTo(8192);
    }

    @Test
    void shouldIdentifyMasterRole() {
        var master = VmConfig.builder()
                .name("master-1")
                .role(NodeRole.MASTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        assertThat(master.isMaster()).isTrue();
        assertThat(master.isWorker()).isFalse();
        assertThat(master.isCluster()).isFalse();
        assertThat(master.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyWorkerRole() {
        var worker = VmConfig.builder()
                .name("worker-1")
                .role(NodeRole.WORKER)
                .ip("192.168.56.11")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        assertThat(worker.isMaster()).isFalse();
        assertThat(worker.isWorker()).isTrue();
        assertThat(worker.isCluster()).isFalse();
        assertThat(worker.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyClusterRole() {
        var cluster = VmConfig.builder()
                .name("cluster-1")
                .role(NodeRole.CLUSTER)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.MEDIUM)
                .build();

        assertThat(cluster.isMaster()).isFalse();
        assertThat(cluster.isWorker()).isFalse();
        assertThat(cluster.isCluster()).isTrue();
        assertThat(cluster.isManagement()).isFalse();
    }

    @Test
    void shouldIdentifyManagementRole() {
        var management = VmConfig.builder()
                .name("management-1")
                .role(NodeRole.MANAGEMENT)
                .ip("192.168.56.10")
                .sizeProfile(SizeProfile.SMALL)
                .build();

        assertThat(management.isMaster()).isFalse();
        assertThat(management.isWorker()).isFalse();
        assertThat(management.isCluster()).isFalse();
        assertThat(management.isManagement()).isTrue();
    }
}
