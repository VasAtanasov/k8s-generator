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

        assertThat(types).hasSize(4);
        assertThat(types).containsExactlyInAnyOrder(
                Kind.INSTANCE,
                Minikube.INSTANCE,
                Kubeadm.INSTANCE,
                NoneCluster.INSTANCE
        );
    }

    @Test
    void shouldReturnCorrectIds() {
        assertThat(Kind.INSTANCE.id()).isEqualTo("kind");
        assertThat(Minikube.INSTANCE.id()).isEqualTo("minikube");
        assertThat(Kubeadm.INSTANCE.id()).isEqualTo("kubeadm");
        assertThat(NoneCluster.INSTANCE.id()).isEqualTo("none");
    }

    @Test
    void shouldReturnCorrectDisplayNames() {
        assertThat(Kind.INSTANCE.displayName()).isEqualTo("KIND (Kubernetes IN Docker)");
        assertThat(Minikube.INSTANCE.displayName()).isEqualTo("Minikube");
        assertThat(Kubeadm.INSTANCE.displayName()).isEqualTo("Kubeadm");
        assertThat(NoneCluster.INSTANCE.displayName()).isEqualTo("Management Machine");
    }

    @Test
    void shouldReportMultiNodeSupport() {
        assertThat(Kind.INSTANCE.supportsMultiNode()).isFalse();
        assertThat(Minikube.INSTANCE.supportsMultiNode()).isFalse();
        assertThat(Kubeadm.INSTANCE.supportsMultiNode()).isTrue();
        assertThat(NoneCluster.INSTANCE.supportsMultiNode()).isFalse();
    }

    @Test
    void shouldReportRoleSupport() {
        assertThat(Kind.INSTANCE.supportsRoles()).isFalse();
        assertThat(Minikube.INSTANCE.supportsRoles()).isFalse();
        assertThat(Kubeadm.INSTANCE.supportsRoles()).isTrue();
        assertThat(NoneCluster.INSTANCE.supportsRoles()).isFalse();
    }

    @Test
    void shouldReturnCorrectRequiredTools() {
        assertThat(Kind.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl(), Tool.of("docker"), Tool.kind());
        assertThat(Minikube.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl(), Tool.of("docker"), Tool.of("minikube"));
        assertThat(Kubeadm.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl(), Tool.of("containerd"), Tool.kubeBinaries());
        assertThat(NoneCluster.INSTANCE.requiredTools())
                .containsExactly(Tool.kubectl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "KIND", "Kind", "kInD"})
    void shouldParseCaseInsensitiveKind(String input) {
        assertThat(ClusterType.fromString(input)).isSameAs(Kind.INSTANCE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minikube", "MINIKUBE", "Minikube"})
    void shouldParseCaseInsensitiveMinikube(String input) {
        assertThat(ClusterType.fromString(input)).isSameAs(Minikube.INSTANCE);
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
        assertThat(ClusterType.byId("kind")).isSameAs(Kind.INSTANCE);
        assertThat(ClusterType.byId("minikube")).isSameAs(Minikube.INSTANCE);
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
    @ValueSource(strings = {"invalid", "k3s", "KIND", ""})
    void shouldRejectInvalidIdsInById(String input) {
        assertThatThrownBy(() -> ClusterType.byId(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown cluster type id");
    }

    @Test
    void shouldIncludeValidValuesInFromStringErrorMessage() {
        assertThatThrownBy(() -> ClusterType.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind, minikube, kubeadm, none");
    }

    @Test
    void shouldIncludeValidIdsInByIdErrorMessage() {
        assertThatThrownBy(() -> ClusterType.byId("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind, minikube, kubeadm, none");
    }

    @Test
    void shouldUseSingletonInstances() {
        // Verify that parsing returns the same singleton instance
        ClusterType kind1 = ClusterType.fromString("kind");
        ClusterType kind2 = ClusterType.fromString("KIND");
        ClusterType kind3 = ClusterType.byId("kind");

        assertThat(kind1).isSameAs(Kind.INSTANCE);
        assertThat(kind2).isSameAs(Kind.INSTANCE);
        assertThat(kind3).isSameAs(Kind.INSTANCE);
    }

    @Test
    void shouldSupportPatternMatching() {
        // Verify exhaustiveness checking compiles
        ClusterType type = Kind.INSTANCE;

        int vmCount = switch (type) {
            case Kind k -> 1;
            case Minikube m -> 1;
            case Kubeadm ku -> 3;  // example value
            case NoneCluster nc -> 1;
        };

        assertThat(vmCount).isEqualTo(1);
    }

    @Test
    void shouldSupportInstanceofChecks() {
        ClusterType kind = Kind.INSTANCE;
        ClusterType kubeadm = Kubeadm.INSTANCE;

        assertThat(kind).isInstanceOf(Kind.class);
        assertThat(kind).isNotInstanceOf(Kubeadm.class);
        assertThat(kubeadm).isInstanceOf(Kubeadm.class);
        assertThat(kubeadm).isNotInstanceOf(Kind.class);
    }
}
