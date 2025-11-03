package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SemanticValidator.
 */
class SemanticValidatorTest {


    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void shouldAcceptValidClusterName() {
        var spec = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dev", "staging", "prod", "test123", "my-cluster", "cluster-1",
            "a", "a-b-c", "test-123-xyz"
    })
    void shouldAcceptValidClusterNames(String name) {
        var spec = new ClusterSpec(
                name,
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Staging",
            "PROD",
            "Test-123",
            "1cluster",
            "-cluster",
            "cluster-",
            "my_cluster",
            "cluster.test",
            "cluster space"
    })
    void shouldRejectInvalidClusterNames(String name) {
        var spec = new ClusterSpec(
                name,
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.field()).contains("name");
            assertThat(e.level()).isEqualTo(ValidationLevel.SEMANTIC);
            assertThat(e.message()).contains("Invalid cluster name");
        });
    }

    @Test
    void shouldRejectClusterNameTooLong() {
        String longName = "a".repeat(64);  // 64 chars, exceeds 63 limit

        var spec = new ClusterSpec(
                longName,
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors()).anySatisfy(e -> {
            assertThat(e.message()).contains("too long");
            assertThat(e.message()).contains("64 characters");  // Message shows actual length
            assertThat(e.suggestion()).contains("63 characters");  // Suggestion mentions limit
        });
    }

    @Test
    void shouldAcceptKindClusterWithZeroNodes() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.KIND,
                Optional.empty(),
                0,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectKindClusterWithNonZeroMasters() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.KIND,
                Optional.empty(),
                1,  // KIND should have 0 masters
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("masters")
                        && e.message().contains("KIND clusters do not use master nodes"));
    }

    @Test
    void shouldRejectKindClusterWithNonZeroWorkers() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.KIND,
                Optional.empty(),
                0,
                1,  // KIND should have 0 workers
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("workers")
                        && e.message().contains("KIND clusters do not use worker nodes"));
    }

    @Test
    void shouldRejectMinikubeClusterWithNonZeroNodes() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.MINIKUBE,
                Optional.empty(),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isEqualTo(2);  // Both masters and workers wrong
    }

    @Test
    void shouldRejectNoneClusterWithNonZeroNodes() {
        var spec = new ClusterSpec(
                "mgmt",
                ClusterType.NONE,
                Optional.empty(),
                1,
                0,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("NONE clusters do not use master nodes"));
    }

    @Test
    void shouldRejectKubeadmClusterWithZeroMasters() {
        var spec = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.empty(),
                0,  // KUBEADM requires at least 1 master
                5,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("masters")
                        && e.message().contains("KUBEADM clusters require at least 1 master"));
    }

    @Test
    void shouldWarnAboutHighWorkerCount() {
        var spec = new ClusterSpec(
                "huge",
                ClusterType.KUBEADM,
                Optional.empty(),
                1,
                150,  // Unusually high
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Unusually high worker count"));
    }

    @Test
    void shouldAcceptValidIpAddress() {
        var spec = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.of("192.168.56.10"),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid", "192.168.56", "192.168.56.256", "300.168.56.10",
            "192.168.56.10.11", "192.168.-1.10"
    })
    void shouldRejectInvalidIpFormat(String invalidIp) {
        var spec = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.of(invalidIp),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("firstIp")
                        && e.message().contains("Invalid IPv4 address format"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0", "127.0.0.1", "127.1.2.3", "255.255.255.255"
    })
    void shouldRejectReservedIpRanges(String reservedIp) {
        var spec = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.of(reservedIp),
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Invalid IP address range"));
    }

    @Test
    void shouldRequireFirstIpForMultiCluster() {
        var spec1 = new ClusterSpec(
                "staging",
                ClusterType.KUBEADM,
                Optional.empty(),  // Missing firstIp in multi-cluster
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        var spec2 = new ClusterSpec(
                "prod",
                ClusterType.KUBEADM,
                Optional.empty(),  // Missing firstIp in multi-cluster
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(List.of(spec1, spec2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("firstIp")
                        && e.message().contains("Multi-cluster configuration requires explicit firstIp"));
    }

    @Test
    void shouldAllowEmptyFirstIpForSingleCluster() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.KUBEADM,
                Optional.empty(),  // OK for single cluster
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);  // isMultiCluster=false

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutEvenNumberOfMastersInHA() {
        var spec = new ClusterSpec(
                "prod",
                ClusterType.KUBEADM,
                Optional.empty(),
                4,  // Even number - bad for etcd quorum
                5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anySatisfy(e -> {
                    assertThat(e.field()).contains("masters");
                    assertThat(e.message()).contains("even number of masters");
                });
    }

    @Test
    void shouldAcceptOddNumberOfMastersInHA() {
        var spec = new ClusterSpec(
                "prod",
                ClusterType.KUBEADM,
                Optional.empty(),
                3,  // Odd number - good for etcd
                5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutVeryHighMasterCount() {
        var spec = new ClusterSpec(
                "huge",
                ClusterType.KUBEADM,
                Optional.empty(),
                9,  // More than 7 masters
                5,
                SizeProfile.LARGE,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Unusually high master count"));
    }

    @Test
    void shouldNotWarnAboutSingleMaster() {
        var spec = new ClusterSpec(
                "dev",
                ClusterType.KUBEADM,
                Optional.empty(),
                1,  // Single master - no HA warnings
                2,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectNullSpec() {
        ClusterSpec spec = null;
        assertThatThrownBy(() -> validator.validate(spec))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("spec cannot be null");
    }

    @Test
    void shouldCollectAllErrors() {
        var spec1 = new ClusterSpec(
                "Invalid-Name",  // Invalid name
                ClusterType.KIND,
                Optional.of("invalid-ip"),  // Invalid IP
                1,  // KIND should have 0 masters
                1,  // KIND should have 0 workers
                SizeProfile.MEDIUM,
                List.of(),
                Optional.empty());

        var spec2 = new ClusterSpec(
                "prod",
                ClusterType.KUBEADM,
                Optional.empty(),  // Missing firstIp in multi-cluster
                1,
                1,
                SizeProfile.MEDIUM,
                List.of(),
                Optional.of(CniType.CALICO));

        ValidationResult result = validator.validate(List.of(spec1, spec2));  // Multi-cluster without firstIp

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isGreaterThan(1);  // Multiple errors collected
    }
}
