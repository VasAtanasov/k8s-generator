package com.k8s.generator.app;

import com.k8s.generator.cli.GenerateCommand;
import com.k8s.generator.fs.OutputWriter;
import com.k8s.generator.fs.ResourceCopier;
import com.k8s.generator.model.GeneratorSpec;
import com.k8s.generator.model.ScaffoldPlan;
import com.k8s.generator.model.Tool;
import com.k8s.generator.model.ValidationError;
import com.k8s.generator.parser.CliToSpec;
import com.k8s.generator.parser.PlanBuilder;
import com.k8s.generator.parser.SpecConverter;
import com.k8s.generator.parser.SpecToPlan;
import com.k8s.generator.render.JteRenderer;
import com.k8s.generator.render.Renderer;
import com.k8s.generator.util.ToolInstallers;
import com.k8s.generator.validate.ClusterSpecValidator;
import com.k8s.generator.validate.CompositeValidator;
import com.k8s.generator.validate.StructuralValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Orchestrates Phase 1 scaffold generation flow using bricks-and-studs architecture.
 *
 * <p>Architecture Flow:
 * <pre>
 * GenerateCommand → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan → Renderer → Files
 * </pre>
 *
 * <p>Brick Responsibilities:
 * <ol>
 *   <li><b>SpecConverter (Parser)</b>: CLI args → GeneratorSpec</li>
 *   <li><b>ClusterSpecValidator (Validation)</b>: GeneratorSpec validation</li>
 *   <li><b>PlanBuilder (Parser)</b>: GeneratorSpec → ScaffoldPlan</li>
 *   <li><b>Renderer (Rendering)</b>: ScaffoldPlan → template files</li>
 *   <li><b>OutputWriter (I/O)</b>: Write files atomically</li>
 *   <li><b>ResourceCopier (I/O)</b>: Copy install scripts</li>
 * </ol>
 *
 * <p>Exit Codes:
 * <ul>
 *   <li><b>0</b>: Success</li>
 *   <li><b>1</b>: Internal error (unexpected exception)</li>
 *   <li><b>2</b>: Validation error (user input issue)</li>
 * </ul>
 *
 * @see GenerateCommand
 * @see SpecConverter
 * @see StructuralValidator
 * @see PlanBuilder
 * @see Renderer
 * @since 1.0.0
 */
public final class ScaffoldService {
    private static final Logger log = LoggerFactory.getLogger(ScaffoldService.class);

    private final SpecConverter specConverter;
    private final ClusterSpecValidator validator;
    private final PlanBuilder planBuilder;
    private final Renderer renderer;
    private final OutputWriter outputWriter;
    private final ResourceCopier resourceCopier;

    /**
     * Creates ScaffoldService with default brick implementations.
     */
    public ScaffoldService() {
        this(
                new CliToSpec(),
                new CompositeValidator(),
                new SpecToPlan(),
                new JteRenderer(),
                new OutputWriter(),
                new ResourceCopier()
        );
    }

    public static ScaffoldService create() {
        return new ScaffoldService();
    }

    /**
     * Creates ScaffoldService with custom brick implementations (for testing).
     *
     * @param specConverter  converts CLI to spec
     * @param validator      validates spec
     * @param planBuilder    builds execution plan
     * @param renderer       renders templates
     * @param outputWriter   writes output files
     * @param resourceCopier copies resource files
     */
    public ScaffoldService(SpecConverter specConverter,
                           ClusterSpecValidator validator,
                           PlanBuilder planBuilder,
                           Renderer renderer,
                           OutputWriter outputWriter,
                           ResourceCopier resourceCopier) {
        this.specConverter = specConverter;
        this.validator = validator;
        this.planBuilder = planBuilder;
        this.renderer = renderer;
        this.outputWriter = outputWriter;
        this.resourceCopier = resourceCopier;
    }

    /**
     * Scaffolds a learning environment from CLI command.
     *
     * @param cmd Picocli command with user inputs
     * @return exit code (0=success, 1=internal error, 2=validation error)
     */
    public int scaffold(GenerateCommand cmd) {
        try {
            // 1. Convert CLI arguments to GeneratorSpec
            log.debug("Converting CLI arguments to GeneratorSpec");
            GeneratorSpec spec = specConverter.convert(cmd);

            // 2. Validate spec (structural validation)
            log.debug("Validating GeneratorSpec: {}", spec);
            // Run full composite validation (structural + semantic + policy)
            var validationResult = validator.validate(spec.clusters());
            if (validationResult.hasErrors()) {
                log.error("[Validation] Specification failed validation ({} error(s)):", validationResult.errorCount());
                for (ValidationError error : validationResult.errors()) {
                    log.error("  {}", error.format());
                }
                return 2;
            }

            // 3. Build ScaffoldPlan (allocate IPs, generate VMs, build env vars)
            log.debug("Building ScaffoldPlan from validated spec");
            ScaffoldPlan plan = planBuilder.build(spec);

            // 4. Determine output directory (fail on collision)
            Path outDir = determineOutDir(cmd, spec);

            // 4a. DRY-RUN: print plan summary and exit success without writing
            if (cmd.dryRun) {
                log.info("DRY-RUN: No files will be written");
                log.info("Module: {}  Type: {}  Engine: {}", spec.module().num(), spec.module().type(), plan.getEnv("CLUSTER_TYPE"));
                log.info("Output directory (planned): {}", outDir);
                log.info("Planned VMs ({}):", plan.vmCount());
                for (var vm : plan.vms()) {
                    log.info("  - {}  role={}  ip={}  cpus={}  mem={}MB",
                            vm.name(), vm.role().name().toLowerCase(), vm.ip(), vm.getEffectiveCpus(), vm.getEffectiveMemoryMb());
                }
                log.info("Planned files:");
                log.info("  - Vagrantfile");
                log.info("  - scripts/bootstrap.sh");
                log.info("  - .gitignore");
                return 0;
            }
            if (Files.exists(outDir)) {
                log.error("[Validation] Output directory already exists: {} -> Use --out to target a different path", outDir);
                return 2;
            }

            // 5. Render templates to files
            log.debug("Rendering templates with ScaffoldPlan");
            Map<String, String> files = renderer.render(plan);

            // 6. Write files atomically
            log.debug("Writing files atomically to {}", outDir);
            outputWriter.writeFiles(files, outDir);

            // 7. Copy install scripts (resources) and make executable
            log.debug("Copying install scripts to {}/scripts", outDir);
            List<String> scriptsToCopy = plan.scripts();
            resourceCopier.copyScripts(scriptsToCopy, outDir.resolve("scripts"));

            log.info("✓ Generated {} (engine={}) → {}",
                    plan.getEnv("CLUSTER_NAME"),
                    plan.getEnv("CLUSTER_TYPE"),
                    outDir
            );
            return 0;

        } catch (IllegalArgumentException e) {
            // User input validation error (e.g., unsupported cluster type in Phase 1)
            log.error("[Validation] {}", e.getMessage());
            return 2;
        } catch (Exception e) {
            // Unexpected internal error
            log.error("[Internal] Unexpected error during scaffold generation", e);
            return 1;
        }
    }


    /**
     * Maps a Tool value object to a concrete installer script name in resources/scripts.
     * Unknown tools return empty and are ignored.
     */
    private static Optional<String> mapToolToInstaller(Tool tool) {
        return ToolInstallers.mapToolToInstaller(tool);
    }

    /**
     * Determines output directory from CLI or spec defaults.
     *
     * @param cmd  CLI command
     * @param spec generator spec
     * @return output directory path
     */
    private static Path determineOutDir(GenerateCommand cmd, GeneratorSpec spec) {
        if (cmd.outDir != null && !cmd.outDir.isBlank()) {
            return Path.of(cmd.outDir);
        }
        return Path.of(spec.module().defaultOutputDir());
    }
}
