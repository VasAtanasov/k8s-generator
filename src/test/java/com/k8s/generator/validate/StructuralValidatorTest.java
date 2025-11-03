package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for StructuralValidator.
 */
class StructuralValidatorTest {

    private final StructuralValidator validator = new StructuralValidator();

    @Test
    void shouldAcceptValidClusterWithoutVms() {
        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.empty(),
            1,
            2,
            SizeProfile.MEDIUM,
            List.of()
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldAcceptValidClusterWithMatchingVms() {
        var master = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var worker1 = new VmConfig("worker-1", "worker", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var worker2 = new VmConfig("worker-2", "worker", "192.168.56.12",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            2,
            SizeProfile.MEDIUM,
            List.of(master, worker1, worker2)
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectNullSpec() {
        ValidationResult result = validator.validate(null);

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
        var master = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var worker = new VmConfig("worker-1", "worker", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            2,  // Declared 2 masters
            1,
            SizeProfile.MEDIUM,
            List.of(master, worker)  // But only 1 master VM
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.field().contains("vms")
                && e.message().contains("1 master")
                && e.message().contains("masters=2"));
    }

    @Test
    void shouldRejectMismatchedWorkerCount() {
        var master = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var worker = new VmConfig("worker-1", "worker", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            3,  // Declared 3 workers
            SizeProfile.MEDIUM,
            List.of(master, worker)  // But only 1 worker VM
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.field().contains("vms")
                && e.message().contains("1 worker")
                && e.message().contains("workers=3"));
    }

    @Test
    void shouldRejectInvalidVmRole() {
        var vm = new VmConfig("node-1", "invalid-role", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            0,
            0,
            SizeProfile.MEDIUM,
            List.of(vm)
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.field().contains("vms[0].role")
                && e.message().contains("Invalid VM role")
                && e.message().contains("invalid-role"));
    }

    @Test
    void shouldAcceptAllValidRoles() {
        var master = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var worker = new VmConfig("worker-1", "worker", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var cluster = new VmConfig("cluster-1", "cluster", "192.168.56.12",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var mgmt = new VmConfig("mgmt-1", "management", "192.168.56.13",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            1,
            SizeProfile.MEDIUM,
            List.of(master, worker)  // Don't include cluster/mgmt to match counts
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectDuplicateVmNames() {
        var vm1 = new VmConfig("master-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm2 = new VmConfig("master-1", "master", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            2,
            0,
            SizeProfile.MEDIUM,
            List.of(vm1, vm2)
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .anyMatch(e -> e.field().contains("vms")
                && e.message().contains("Duplicate VM name")
                && e.message().contains("master-1"));
    }

    @Test
    void shouldDetectMultipleDuplicates() {
        var vm1 = new VmConfig("node-1", "master", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm2 = new VmConfig("node-1", "worker", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm3 = new VmConfig("node-2", "worker", "192.168.56.12",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm4 = new VmConfig("node-2", "worker", "192.168.56.13",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            1,
            3,
            SizeProfile.MEDIUM,
            List.of(vm1, vm2, vm3, vm4)
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
            .filteredOn(e -> e.message().contains("Duplicate VM name"))
            .hasSize(2);  // Two distinct duplicate names
    }

    @Test
    void shouldCollectAllErrorsNotShortCircuit() {
        var vm1 = new VmConfig("dup", "invalid-role", "192.168.56.10",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());
        var vm2 = new VmConfig("dup", "master", "192.168.56.11",
            SizeProfile.MEDIUM, Optional.empty(), Optional.empty());

        var spec = new ClusterSpec(
            "staging",
            ClusterType.KUBEADM,
            Optional.of("192.168.56.10"),
            3,  // Mismatched count
            0,
            SizeProfile.MEDIUM,
            List.of(vm1, vm2)
        );

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        // Should have multiple errors: count mismatch, invalid role, duplicate name
        assertThat(result.errorCount()).isGreaterThan(1);
    }
}
