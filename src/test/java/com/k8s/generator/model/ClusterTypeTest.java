package com.k8s.generator.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClusterType enum.
 */
class ClusterTypeTest {

    @Test
    void shouldHaveAllExpectedValues() {
        ClusterType[] types = ClusterType.values();

        assertThat(types).hasSize(4);
        assertThat(types).containsExactlyInAnyOrder(
                ClusterType.KIND,
                ClusterType.MINIKUBE,
                ClusterType.KUBEADM,
                ClusterType.NONE
        );
    }

    @ParameterizedTest
    @CsvSource({
            "KIND, kind",
            "MINIKUBE, minikube",
            "KUBEADM, kubeadm",
            "NONE, none"
    })
    void shouldConvertToLowerCaseString(ClusterType type, String expected) {
        assertThat(type.toLowerCaseString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kind", "KIND", "Kind", "kInD"})
    void shouldParseCaseInsensitiveKind(String input) {
        assertThat(ClusterType.fromString(input)).isEqualTo(ClusterType.KIND);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minikube", "MINIKUBE", "Minikube"})
    void shouldParseCaseInsensitiveMinikube(String input) {
        assertThat(ClusterType.fromString(input)).isEqualTo(ClusterType.MINIKUBE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kubeadm", "KUBEADM", "Kubeadm"})
    void shouldParseCaseInsensitiveKubeadm(String input) {
        assertThat(ClusterType.fromString(input)).isEqualTo(ClusterType.KUBEADM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"none", "NONE", "None"})
    void shouldParseCaseInsensitiveNone(String input) {
        assertThat(ClusterType.fromString(input)).isEqualTo(ClusterType.NONE);
    }

    @Test
    void shouldRejectNullValue() {
        assertThatThrownBy(() -> ClusterType.fromString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "k3s", "k8s", "", "  "})
    void shouldRejectInvalidValues(String input) {
        assertThatThrownBy(() -> ClusterType.fromString(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid cluster type")
                .hasMessageContaining(input);
    }

    @Test
    void shouldIncludeValidValuesInErrorMessage() {
        assertThatThrownBy(() -> ClusterType.fromString("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind, minikube, kubeadm, none");
    }
}
