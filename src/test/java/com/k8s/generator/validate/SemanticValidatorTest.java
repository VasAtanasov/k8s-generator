package com.k8s.generator.validate;

import com.k8s.generator.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SemanticValidator.
 */
class SemanticValidatorTest {


    private final SemanticValidator validator = new SemanticValidator();

    @Test
    void shouldAcceptValidClusterName() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dev", "staging", "prod", "test123", "my-cluster", "cluster-1",
            "a", "a-b-c", "test-123-xyz"
    })
    void shouldAcceptValidClusterNames(String name) {
        var spec = ClusterSpec.builder()
                .name(name)
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
        var spec = ClusterSpec.builder()
                .name(name)
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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

        var spec = ClusterSpec.builder()
                .name(longName)
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

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
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(ClusterType.KIND)
                .masters(0)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldRejectKindClusterWithNonZeroMasters() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(ClusterType.KIND)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("masters")
                        && e.message().contains("KIND clusters do not use master nodes"));
    }

    @Test
    void shouldRejectKindClusterWithNonZeroWorkers() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(ClusterType.KIND)
                .masters(0)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("workers")
                        && e.message().contains("KIND clusters do not use worker nodes"));
    }

    @Test
    void shouldRejectMinikubeClusterWithNonZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("dev")
                .type(ClusterType.MINIKUBE)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errorCount()).isEqualTo(2);  // Both masters and workers wrong
    }

    @Test
    void shouldRejectNoneClusterWithNonZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("mgmt")
                .type(ClusterType.NONE)
                .masters(1)
                .workers(0)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("NONE clusters do not use master nodes"));
    }

    @Test
    void shouldRejectKubeadmClusterWithZeroMasters() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .masters(0)
                .workers(5)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("masters")
                        && e.message().contains("KUBEADM clusters require at least 1 master"));
    }

    @Test
    void shouldWarnAboutHighWorkerCount() {
        var spec = ClusterSpec.builder()
                .name("huge")
                .type(ClusterType.KUBEADM)
                .masters(1)
                .workers(150)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Unusually high worker count"));
    }

    @Test
    void shouldAcceptValidIpAddress() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp("192.168.56.10")
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "192.168.56", "192.168.56.256", "300.168.56.10", "192.168.56.10.11", "999.999.999.999"})
    void shouldRejectInvalidIpFormat(String invalidIp) {
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp(invalidIp)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid IP address format");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "224.0.0.1"})
    void shouldRejectReservedIpRanges(String reservedIp) {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(ClusterType.KUBEADM)
                .firstIp(reservedIp)
                .masters(1)
                .workers(1)
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .cni(CniType.CALICO)
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Reserved or special IP address"));
    }

    @Test
    void shouldRequireFirstIpForMultiCluster() {
        var spec1 = ClusterSpec.builder().name("staging").type(ClusterType.KUBEADM)
                .masters(1).workers(1).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        var spec2 = ClusterSpec.builder().name("prod").type(ClusterType.KUBEADM)
                .masters(1).workers(1).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(spec1, spec2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("firstIp")
                        && e.message().contains("Multi-cluster configuration requires explicit firstIp"));
    }

    @Test
    void shouldAllowEmptyFirstIpForSingleCluster() {
        var spec = ClusterSpec.builder().name("dev").type(ClusterType.KUBEADM)
                .masters(1).workers(1).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);  // isMultiCluster=false

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutEvenNumberOfMastersInHA() {
        var spec = ClusterSpec.builder().name("prod").type(ClusterType.KUBEADM)
                .masters(4).workers(5).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

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
        var spec = ClusterSpec.builder().name("prod").type(ClusterType.KUBEADM)
                .masters(3).workers(5).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutVeryHighMasterCount() {
        var spec = ClusterSpec.builder().name("huge").type(ClusterType.KUBEADM)
                .masters(9).workers(5).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Unusually high master count"));
    }

    @Test
    void shouldNotWarnAboutSingleMaster() {
        var spec = ClusterSpec.builder().name("dev").type(ClusterType.KUBEADM)
                .masters(1).workers(2).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

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
        assertThatThrownBy(() -> ClusterSpec.builder()
                .name("Invalid-Name")  // Invalid name
                .type(ClusterType.KIND)
                .firstIp("invalid-ip")  // invalid in builder
                .masters(1).workers(1)  // invalid roles for KIND
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);

        var spec2 = ClusterSpec.builder().name("prod").type(ClusterType.KUBEADM)
                .masters(1).workers(1).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(spec2));

        assertThat(result.hasErrors()).isFalse();
    }
}
