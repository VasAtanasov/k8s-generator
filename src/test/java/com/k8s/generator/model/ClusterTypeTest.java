package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClusterType sealed hierarchy.
 */
class ClusterTypeTest {

    @Test
    void shouldHaveAllExpectedTypes() {
        var types = ClusterType.values();

        assertThat(types).hasSize(2);
        assertThat(types).containsExactlyInAnyOrder(
                Kubeadm.INSTANCE,
                NoneCluster.INSTANCE
        );
    }

    @Test
    void shouldReturnCorrectIds() {
        assertThat(Kubeadm.INSTANCE.id()).isEqualTo("kubeadm");
        assertThat(NoneCluster.INSTANCE.id()).isEqualTo("none");
    }

    @Test
    void shouldReturnCorrectDisplayNames() {
        assertThat(Kubeadm.INSTANCE.displayName()).isEqualTo("Kubeadm");
        assertThat(NoneCluster.INSTANCE.displayName()).isEqualTo("Management Machine");
    }

    @Test
    void shouldReportMultiNodeSupport() {
        assertThat(Kubeadm.INSTANCE.supportsMultiNode()).isTrue();
        assertThat(NoneCluster.INSTANCE.supportsMultiNode()).isFalse();
    }

    @Test
    void shouldReportRoleSupport() {
        assertThat(Kubeadm.INSTANCE.supportsRoles()).isTrue();
        assertThat(NoneCluster.INSTANCE.supportsRoles()).isFalse();
    }

    @Test
    void shouldReturnCorrectRequiredTools() {
        assertThat(Kubeadm.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl(), Tool.of("containerd"), Tool.kubeBinaries());
        assertThat(NoneCluster.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl());
    }



    @ParameterizedTest
    @ValueSource(strings = {"kubeadm", "KUBEADM", "Kubeadm"})
    void shouldParseCaseInsensitiveKubeadm(String input) {
        assertThat(ClusterType.fromString(input)).isSameAs(Kubeadm.INSTANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "NONE", "None"})
    void shouldParseCaseInsensitiveNone(String input) {
        assertThat(ClusterType.fromString(input)).isSameAs(NoneCluster.INSTANCE);
    }

    @Test
    void shouldLookupById() {
        assertThat(ClusterType.byId("kubeadm")).isSameAs(Kubeadm.INSTANCE);
        assertThat(ClusterType.byId("none")).isSameAs(NoneCluster.INSTANCE);
    }

    @Test
    void shouldRejectNullValueInFromString() {
        assertThatThrownBy(() -> ClusterType.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldRejectNullIdInById() {
        assertThatThrownBy(() -> ClusterType.byId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "k3s", "k8s", "", "  "})
    void shouldRejectInvalidValuesInFromString(String input) {
        assertThatThrownBy(() -> ClusterType.fromString(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cluster type");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "k3s", ""})
    void shouldRejectInvalidIdsInById(String input) {
        assertThatThrownBy(() -> ClusterType.byId(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cluster type id");
    }

    @Test
    void shouldIncludeValidValuesInFromStringErrorMessage() {
        assertThatThrownBy(() -> ClusterType.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kubeadm, none");
    }

    @Test
    void shouldIncludeValidIdsInByIdErrorMessage() {
        assertThatThrownBy(() -> ClusterType.byId("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kubeadm, none");
    }

    @Test
    void shouldUseSingletonInstances() {
        // Verify that parsing returns the same singleton instance
        ClusterType kubeadm1 = ClusterType.fromString("kubeadm");
        ClusterType kubeadm2 = ClusterType.fromString("KUBEADM");
        ClusterType kubeadm3 = ClusterType.byId("kubeadm");

        assertThat(kubeadm1).isSameAs(Kubeadm.INSTANCE);
        assertThat(kubeadm2).isSameAs(Kubeadm.INSTANCE);
        assertThat(kubeadm3).isSameAs(Kubeadm.INSTANCE);
    }

    @Test
    void shouldSupportPatternMatching() {
        // Verify exhaustiveness checking compiles
        ClusterType type = Kubeadm.INSTANCE;

        int vmCount = switch (type) {
            case Kubeadm ku -> 3;  // example value
            case NoneCluster nc -> 1;
        };

        assertThat(vmCount).isEqualTo(3);
    }

    @Test
    void shouldSupportInstanceofChecks() {
        ClusterType kubeadm = Kubeadm.INSTANCE;
        ClusterType none = NoneCluster.INSTANCE;

        assertThat(kubeadm).isInstanceOf(Kubeadm.class);
        assertThat(kubeadm).isNotInstanceOf(NoneCluster.class);
        assertThat(none).isInstanceOf(NoneCluster.class);
        assertThat(none).isNotInstanceOf(Kubeadm.class);
    }
}
