package com.k8s.generator.render;

import com.k8s.generator.model.ClusterType;
import com.k8s.generator.model.ScaffoldPlan;
import com.k8s.generator.model.VmConfig;
import com.k8s.generator.util.ToolInstallers;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

import java.util.*;

/**
 * JTE-based renderer using precompiled templates.
 *
 * <p>This implementation accepts a complete {@link ScaffoldPlan} containing all necessary
 * information for template rendering, following the "bricks and studs" architecture where
 * the plan flows intact through the pipeline:
 *
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan → Renderer → Files
 * </pre>
 *
 * <p>Templates are precompiled at build time using the JTE Gradle plugin for optimal performance.
 *
 * @see ScaffoldPlan
 * @see Renderer
 * @since 1.0.0
 */
public final class JteRenderer implements Renderer {

    private final TemplateEngine engine = TemplateEngine.createPrecompiled(ContentType.Plain);

    /**
     * Renders all output files from a scaffold plan.
     *
     * <p>Generates three files:
     * <ul>
     *   <li><b>Vagrantfile</b>: VM topology definition with module metadata, VMs, and environment</li>
     *   <li><b>scripts/bootstrap.sh</b>: Provisioning script with environment variables</li>
     *   <li><b>.gitignore</b>: Git exclusions for Vagrant artifacts</li>
     * </ul>
     *
     * @param plan complete scaffold plan with VMs, module info, and environment variables
     * @return map of relative file path → file content
     * @throws IllegalArgumentException if plan is null
     * @throws RuntimeException         if template rendering fails
     */
    @Override
    public Map<String, String> render(ScaffoldPlan plan) {
        Objects.requireNonNull(plan, "plan is required");

        var out = new HashMap<String, String>();
        out.put("Vagrantfile", renderVagrantfile(plan));
        out.put("scripts/bootstrap.sh", renderBootstrap(plan));
        out.put(".gitignore", renderDotGitignore());
        return out;
    }

    /**
     * Renders the Vagrantfile template with VM topology and configuration.
     *
     * @param plan scaffold plan containing VMs, module info, and environment
     * @return rendered Vagrantfile content
     */
    private String renderVagrantfile(ScaffoldPlan plan) {
        var output = new StringOutput();
        // Build merged env per VM: global then per-VM overrides
        var envByVm = new LinkedHashMap<String, Map<String, String>>();
        for (VmConfig vm : plan.vms()) {
            var merged = new LinkedHashMap<>(plan.envVars());
            var perVm = plan.vmEnv().get(vm.name());
            if (perVm != null) merged.putAll(perVm);
            envByVm.put(vm.name().value(), Map.copyOf(merged));
        }
        // Provide template parameters by name
        var params = new HashMap<String, Object>();
        params.put("moduleName", plan.module().num());
        params.put("vms", plan.vms());
        params.put("envByVm", Map.copyOf(envByVm));
        try {
            engine.render("Vagrantfile", params, output);
        } catch (RuntimeException e) {
            engine.render("Vagrantfile.jte", params, output);
        }
        return output.toString();
    }

    /**
     * Renders the bootstrap.sh template with environment variables.
     *
     * @param plan scaffold plan containing environment variables
     * @return rendered bootstrap.sh content
     */
    private String renderBootstrap(ScaffoldPlan plan) {
        var output = new StringOutput();
        var clusterTypeId = plan.getEnv("CLUSTER_TYPE");
        var installScripts = clusterTypeId != null
                ? ToolInstallers.installersForCluster(ClusterType.byId(clusterTypeId))
                : List.<String>of();
        var params = new HashMap<String, Object>();
        params.put("envVars", plan.envVars());
        params.put("installScripts", installScripts);
        try {
            engine.render("bootstrap.sh", params, output);
        } catch (RuntimeException e) {
            engine.render("bootstrap.sh.jte", params, output);
        }
        return output.toString();
    }

    /**
     * Renders the .gitignore template (static content).
     *
     * @return rendered .gitignore content
     */
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
