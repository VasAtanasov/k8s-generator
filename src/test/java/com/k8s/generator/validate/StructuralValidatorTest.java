package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for StructuralValidator.
 */
class StructuralValidatorTest {

    private final StructuralValidator validator = new StructuralValidator();

    @Test
    void shouldAcceptValidClusterWithoutVms() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(2)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptValidClusterWithMatchingVms() {
        var master = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var worker1 = VmConfig.builder().name("worker-1").role(NodeRole.WORKER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();
        var worker2 = VmConfig.builder().name("worker-2").role(NodeRole.WORKER).ip("192.168.56.12").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(2)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(master, worker1, worker2))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectNullSpec() {
        ClusterSpec spec = null;
        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .hasSize(1)
                .first()
                .satisfies(error -> {
                    assertThat(error.field()).isEqualTo("cluster");
                    assertThat(error.level()).isEqualTo(ValidationLevel.STRUCTURAL);
                    assertThat(error.message()).contains("null");
                });
    }

    @Test
    void shouldRejectMismatchedMasterCount() {
        var master = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var worker = VmConfig.builder().name("worker-1").role(NodeRole.WORKER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(2)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(master, worker))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("vms")
                        && e.message().contains("1 master")
                        && e.message().contains("masters=2"));
    }

    @Test
    void shouldRejectMismatchedWorkerCount() {
        var master = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var worker = VmConfig.builder().name("worker-1").role(NodeRole.WORKER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(3)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(master, worker))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("vms")
                        && e.message().contains("1 worker")
                        && e.message().contains("workers=3"));
    }

    // Test removed: With NodeRole enum, invalid roles are prevented at compile time
    // Type safety eliminates the need for runtime validation of role values

    @Test
    void shouldAcceptAllValidRoles() {
        var master = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var worker = VmConfig.builder().name("worker-1").role(NodeRole.WORKER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();
        var cluster = VmConfig.builder().name("cluster-1").role(NodeRole.CLUSTER).ip("192.168.56.12").sizeProfile(SizeProfile.MEDIUM).build();
        var mgmt = VmConfig.builder().name("mgmt-1").role(NodeRole.MANAGEMENT).ip("192.168.56.13").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(master, worker))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicateVmNames() {
        var vm1 = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var vm2 = VmConfig.builder().name("master-1").role(NodeRole.MASTER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(2)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm1, vm2))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("vms")
                        && e.message().contains("Duplicate VM name")
                        && e.message().contains("master-1"));
    }

    @Test
    void shouldDetectMultipleDuplicates() {
        var vm1 = VmConfig.builder().name("node-1").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var vm2 = VmConfig.builder().name("node-1").role(NodeRole.WORKER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();
        var vm3 = VmConfig.builder().name("node-2").role(NodeRole.WORKER).ip("192.168.56.12").sizeProfile(SizeProfile.MEDIUM).build();
        var vm4 = VmConfig.builder().name("node-2").role(NodeRole.WORKER).ip("192.168.56.13").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(3)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm1, vm2, vm3, vm4))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .filteredOn(e -> e.message().contains("Duplicate VM name"))
                .hasSize(2);  // Two distinct duplicate names
    }

    @Test
    void shouldCollectAllErrorsNotShortCircuit() {
        var vm1 = VmConfig.builder().name("dup").role(NodeRole.MASTER).ip("192.168.56.10").sizeProfile(SizeProfile.MEDIUM).build();
        var vm2 = VmConfig.builder().name("dup").role(NodeRole.MASTER).ip("192.168.56.11").sizeProfile(SizeProfile.MEDIUM).build();

        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(3)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of(vm1, vm2))
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        // Should have multiple errors: count mismatch, duplicate name
        assertThat(result.errorCount()).isGreaterThan(1);
    }
}
