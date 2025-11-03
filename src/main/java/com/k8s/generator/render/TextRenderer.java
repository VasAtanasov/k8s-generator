package com.k8s.generator.render;

import com.k8s.generator.model.VmConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal text-based renderer for Phase 1.
 *
 * Generates:
 *  - Vagrantfile (single VM)
 *  - scripts/bootstrap.sh
 *  - .gitignore
 */
public final class TextRenderer implements Renderer {

    @Override
    public Map<String, String> render(String module, String type, List<VmConfig> vms, Map<String, String> env) {
        if (vms == null || vms.size() != 1) {
            throw new IllegalArgumentException("Phase 1 expects exactly one VM");
        }
        VmConfig vm = vms.get(0);

        Map<String, String> files = new HashMap<>();
        files.put("Vagrantfile", vagrantfile(vm, env));
        files.put("scripts/bootstrap.sh", bootstrapSh(env));
        files.put(".gitignore", gitignore());
        return files;
    }

    private static String vagrantfile(VmConfig vm, Map<String, String> env) {
        String envRuby = env.entrySet().stream()
            .map(e -> String.format("\"%s\" => \"%s\"", e.getKey(), e.getValue()))
            .reduce((a, b) -> a + ",\n      " + b)
            .orElse("");

        return """
            Vagrant.configure("2") do |config|
              config.vm.define "%s" do |node|
                node.vm.box = "ubuntu/jammy64"
                node.vm.hostname = "%s"
                node.vm.network "private_network", ip: "%s"

                node.vm.provider "virtualbox" do |vb|
                  vb.memory = %d
                  vb.cpus = %d
                end

                node.vm.provision "shell", path: "scripts/bootstrap.sh", env: {
                  %s
                }
              end
            end
            """.formatted(
            vm.name(),
            vm.name(),
            vm.ip(),
            vm.getEffectiveMemoryMb(),
            vm.getEffectiveCpus(),
            envRuby
        );
    }

    private static String bootstrapSh(Map<String, String> env) {
        return """
            #!/usr/bin/env bash
            set -euo pipefail

            echo "[bootstrap] Starting bootstrap for $(hostname)"

            # Persist environment variables to /etc/k8s-env (idempotent)
            TMP_FILE="$(mktemp)"
            cat >"${TMP_FILE}" <<EOF
            %s
            EOF
            if ! cmp -s "${TMP_FILE}" /etc/k8s-env 2>/dev/null; then
              sudo install -m 0644 "${TMP_FILE}" /etc/k8s-env
            fi
            rm -f "${TMP_FILE}"

            # Auto-source on login shells
            sudo bash -lc 'cat >/etc/profile.d/50-k8s-env.sh <<\'EOS'\n[ -f /etc/k8s-env ] && . /etc/k8s-env\nEOS\nchmod 0644 /etc/profile.d/50-k8s-env.sh'

            # Install minimal toolchain for kind/minikube path
            chmod +x scripts/install_*.sh || true
            if [ -x scripts/install_docker.sh ]; then scripts/install_docker.sh; fi
            if [ -x scripts/install_kubectl.sh ]; then scripts/install_kubectl.sh; fi
            if [ -x scripts/install_kind.sh ]; then scripts/install_kind.sh; fi

            echo "[bootstrap] Completed"
            """.formatted(toExports(env));
    }

    private static String gitignore() {
        return """
            .vagrant/
            *.log
            scripts/bootstrap.env.local
            """;
    }

    private static String toExports(Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        env.forEach((k, v) -> sb.append("export ").append(k).append("=\"").append(v).append("\"\n"));
        return sb.toString();
    }
}

