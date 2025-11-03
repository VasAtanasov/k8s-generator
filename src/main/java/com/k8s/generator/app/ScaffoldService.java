package com.k8s.generator.app;

import com.k8s.generator.cli.GenerateCommand;
import com.k8s.generator.fs.OutputWriter;
import com.k8s.generator.fs.ResourceCopier;
import com.k8s.generator.model.ClusterType;
import com.k8s.generator.model.SizeProfile;
import com.k8s.generator.model.VmConfig;
import com.k8s.generator.render.Renderer;
import com.k8s.generator.render.TextRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orchestrates Phase 1 flow: CLI → plan → render → write → copy resources.
 */
public final class ScaffoldService {

    public int scaffold(GenerateCommand cmd) {
        try {
            // 1) Parse and validate CLI inputs (structural checks only)
            var module = cmd.module;
            var type = cmd.type;
            if (!module.matches("m\\d+")) {
                System.err.printf("[Structural] Invalid module '%s'%n  → Expected pattern: mN (e.g., m1, m7)\n", module);
                return 2;
            }
            if (!type.matches("[a-z][a-z0-9-]*")) {
                System.err.printf("[Structural] Invalid type '%s'%n  → Pattern: [a-z][a-z0-9-]* (e.g., pt, exam-prep)\n", type);
                return 2;
            }

            var engine = parseEngine(cmd.clusterType);
            if (engine == null) {
                System.err.printf("[Structural] Unsupported cluster-type '%s'%n  → Phase 1 supports: kind|minikube\n", cmd.clusterType);
                return 2;
            }

            // 2) Determine output directory (fail on collision if not overridden)
            Path out = determineOutDir(cmd.outDir, module, type);
            if (Files.exists(out)) {
                System.err.printf("[Structural] Output directory already exists: %s%n  → Use --out to target a different path\n", out);
                return 2;
            }

            // 3) Build minimal plan (single VM, default IP 192.168.56.10)
            String clusterName = String.format("clu-%s-%s-%s", module, type, engine.name().toLowerCase(Locale.ROOT));
            String namespace = String.format("ns-%s-%s", module, type);
            String ip = "192.168.56.10";
            SizeProfile profile = SizeProfile.MEDIUM;

            // Use role "cluster" for single-node engines
            VmConfig vm = new VmConfig(sanitizeVmName(module + "-" + type), "cluster", ip, profile, java.util.Optional.empty(), java.util.Optional.empty());
            List<VmConfig> vms = List.of(vm);

            Map<String, String> env = new HashMap<>();
            env.put("CLUSTER_NAME", clusterName);
            env.put("NAMESPACE_DEFAULT", namespace);
            env.put("CLUSTER_TYPE", engine.name().toLowerCase(Locale.ROOT));

            // 4) Render files
            Renderer renderer = new TextRenderer();
            Map<String, String> files = renderer.render(module, type, vms, env);

            // 5) Write files atomically
            new OutputWriter().writeFiles(files, out);

            // 6) Copy install scripts (resources) and make executable
            new ResourceCopier().copyScripts(List.of(
                "install_kubectl.sh",
                "install_docker.sh",
                "install_kind.sh"
            ), out.resolve("scripts"));

            System.out.printf("✓ Generated %s (engine=%s) → %s%n", clusterName, engine.name().toLowerCase(Locale.ROOT), out);
            return 0;
        } catch (Exception e) {
            System.err.printf("[Internal] Unexpected error: %s%n", e.getMessage());
            return 1;
        }
    }

    private static ClusterType parseEngine(String clusterType) {
        String v = clusterType == null ? "" : clusterType.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "kind" -> ClusterType.KIND;
            case "minikube" -> ClusterType.MINIKUBE;
            default -> null; // Phase 1 limits
        };
    }

    private static Path determineOutDir(String outOpt, String module, String type) {
        if (outOpt != null && !outOpt.isBlank()) {
            return Path.of(outOpt);
        }
        return Path.of(String.format("%s-%s", type, module));
    }

    private static String sanitizeVmName(String base) {
        return base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }
}

