package com.k8s.generator.util;

import com.k8s.generator.model.ClusterType;
import com.k8s.generator.model.Tool;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * Utility for mapping Tool value objects to concrete installer script names
 * under resources/scripts and composing ordered installer lists.
 */
public final class ToolInstallers {
    private ToolInstallers() {
    }

    /**
     * Maps a Tool value object to its installer script filename.
     * Unknown tools return empty and are ignored by callers.
     */
    public static Optional<String> mapToolToInstaller(Tool tool) {
        String name = tool.value();
        return switch (name) {
            case "kubectl" -> Optional.of("install_kubectl.sh");
            case "helm" -> Optional.of("install_helm.sh");
            case "azure_cli" -> Optional.of("install_azure_cli.sh");
            case "kube_binaries" -> Optional.of("install_kube_binaries.sh");
            case "kind" -> Optional.of("install_kind.sh");
            case "docker" -> Optional.of("install_docker.sh");
            case "minikube" -> Optional.of("install_minikube.sh");
            case "containerd" -> Optional.of("install_containerd.sh");
            default -> Optional.empty();
        };
    }

    /**
     * Returns an ordered, de-duplicated list of installer scripts required for a cluster type.
     */
    public static List<String> installersForCluster(ClusterType type) {
        var ordered = new LinkedHashSet<String>();
        ordered.add("install_base_packages.sh");
        for (var tool : type.requiredTools()) {
            mapToolToInstaller(tool).ifPresent(ordered::add);
        }
        return List.copyOf(ordered);
    }
}

