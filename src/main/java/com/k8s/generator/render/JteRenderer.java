package com.k8s.generator.render;

import com.k8s.generator.model.VmConfig;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JTE-based renderer using precompiled templates.
 */
public final class JteRenderer implements Renderer {

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Plain);

    @Override
    public Map<String, String> render(String module, String type, List<VmConfig> vms, Map<String, String> env) {
        var out = new HashMap<String, String>();
        out.put("Vagrantfile", renderVagrantfile(module, vms, env));
        out.put("scripts/bootstrap.sh", renderBootstrap(env));
        out.put(".gitignore", renderDotGitignore());
        return out;
    }

    private String renderVagrantfile(String moduleName, List<VmConfig> vms, Map<String, String> envVars) {
        var output = new StringOutput();
        var params = new HashMap<String, Object>();
        params.put("moduleName", moduleName);
        params.put("vms", vms);
        params.put("envVars", envVars);
        try {
            engine.render("Vagrantfile", params, output);
        } catch (RuntimeException e) {
            // Fallback to same name with extension (precompiled mapping may vary)
            engine.render("Vagrantfile.jte", params, output);
        }
        return output.toString();
    }

    private String renderBootstrap(Map<String, String> envVars) {
        var output = new StringOutput();
        var params = Map.<String, Object>of("envVars", envVars);
        try {
            engine.render("bootstrap.sh", params, output);
        } catch (RuntimeException e) {
            engine.render("bootstrap.sh.jte", params, output);
        }
        return output.toString();
    }

    private String renderDotGitignore() {
        var output = new StringOutput();
        try {
            engine.render(".gitignore", Map.of(), output);
        } catch (RuntimeException e) {
            engine.render(".gitignore.jte", Map.of(), output);
        }
        return output.toString();
    }
}
