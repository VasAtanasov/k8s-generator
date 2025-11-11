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
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1))
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
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1))
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
            "cluster space"
    })
    void shouldRejectInvalidClusterNames(String name) {
        var spec = ClusterSpec.builder()
                .name(name)
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1))
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
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1))
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
    void shouldRejectNoneClusterWithNonZeroNodes() {
        var spec = ClusterSpec.builder()
                .name("mgmt")
                .type(NoneCluster.INSTANCE)
                .nodes(NodeTopology.of(1,0))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Management Machine clusters do not use master nodes"));
    }

    @Test
    void shouldRejectKubeadmClusterWithZeroMasters() {
        var spec = ClusterSpec.builder()
                .name("staging")
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(0,5))
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
                .type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,150))
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
                .type(Kubeadm.INSTANCE)
                .firstIp("192.168.56.10")
                .nodes(NodeTopology.of(1,1))
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
                .type(Kubeadm.INSTANCE)
                .firstIp(invalidIp)
                .nodes(NodeTopology.of(1,1))
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
                .type(Kubeadm.INSTANCE)
                .firstIp(reservedIp)
                .nodes(NodeTopology.of(1,1))
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
        var spec1 = ClusterSpec.builder().name("staging").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        var spec2 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(spec1, spec2));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.field().contains("firstIp")
                        && e.message().contains("Multi-cluster configuration requires explicit firstIp"));
    }

    @Test
    void shouldAllowEmptyFirstIpForSingleCluster() {
        var spec = ClusterSpec.builder().name("dev").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);  // isMultiCluster=false

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutEvenNumberOfMastersInHA() {
        var spec = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(4,5)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

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
        var spec = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(3,5)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void shouldWarnAboutVeryHighMasterCount() {
        var spec = ClusterSpec.builder().name("huge").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(9,5)).sizeProfile(SizeProfile.LARGE).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(spec);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.errors())
                .anyMatch(e -> e.message().contains("Unusually high master count"));
    }

    @Test
    void shouldNotWarnAboutSingleMaster() {
        var spec = ClusterSpec.builder().name("dev").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,2)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

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
                .firstIp("invalid-ip")  // invalid in builder
                .nodes(NodeTopology.of(1,1))
                .sizeProfile(SizeProfile.MEDIUM)
                .vms(List.of())
                .build())
                .isInstanceOf(IllegalArgumentException.class);

        var spec2 = ClusterSpec.builder().name("prod").type(Kubeadm.INSTANCE)
                .nodes(NodeTopology.of(1,1)).sizeProfile(SizeProfile.MEDIUM).vms(List.of()).cni(CniType.CALICO).build();

        ValidationResult result = validator.validate(List.of(spec2));

        assertThat(result.hasErrors()).isFalse();
    }
}
