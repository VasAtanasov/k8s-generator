package com.k8s.generator.render;

import com.k8s.generator.model.ScaffoldPlan;

import java.util.Map;

/**
 * Renders files from a scaffold plan.
 *
 * <p>This interface follows the "bricks and studs" philosophy where the Renderer
 * is a self-contained module (brick) with a clear public interface (stud).
 *
 * <p>Architecture Position:
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan → Renderer → Files
 * </pre>
 *
 * <p>The Renderer accepts a complete {@link ScaffoldPlan} containing all necessary
 * information for template rendering: module metadata, VM configurations, and
 * environment variables. This design ensures:
 * <ul>
 *   <li><b>Clear data flow</b>: Plan flows intact through the pipeline</li>
 *   <li><b>Regenerability</b>: Implementation can be regenerated from this interface contract</li>
 *   <li><b>Testability</b>: Mock plans can be easily created for testing</li>
 * </ul>
 *
 * @see ScaffoldPlan
 * @see com.k8s.generator.parser.PlanBuilder
 * @since 1.0.0
 */
public interface Renderer {

    /**
     * Renders all output files from a scaffold plan.
     *
     * <p>Typical output includes:
     * <ul>
     *   <li><b>Vagrantfile</b>: VM topology definition</li>
     *   <li><b>scripts/bootstrap.sh</b>: Provisioning script</li>
     *   <li><b>.gitignore</b>: Git exclusions</li>
     * </ul>
     *
     * <p>The returned map uses relative file paths as keys (e.g., "Vagrantfile",
     * "scripts/bootstrap.sh") and file contents as values. The OutputWriter brick
     * is responsible for writing these files to disk.
     *
     * @param plan complete scaffold plan with VMs, module info, and environment variables
     * @return map of relative file path → file content
     * @throws IllegalArgumentException if plan is null
     * @throws RuntimeException         if template rendering fails
     */
    Map<String, String> render(ScaffoldPlan plan);
}

