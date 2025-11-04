package com.k8s.generator.parser;

import com.k8s.generator.cli.GenerateCommand;
import com.k8s.generator.model.GeneratorSpec;

/**
 * Converts CLI arguments to internal spec representation.
 *
 * <p>Architecture Position:
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan
 * </pre>
 *
 * <p>Responsibility:
 * <ul>
 *   <li>Transform Picocli command object to immutable domain model (GeneratorSpec)</li>
 *   <li>Apply convention-over-configuration defaults (e.g., SizeProfile.MEDIUM if not specified)</li>
 *   <li>Create ModuleInfo from module number and type CLI arguments</li>
 *   <li>Build initial ClusterSpec(s) from cluster type and sizing arguments</li>
 * </ul>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Pure transformation</b>: No side effects, no validation, no I/O</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 *   <li><b>No validation</b>: Picocli handles structural validation (required fields),
 *       SemanticValidator handles business rules. This converter only transforms.</li>
 *   <li><b>Fail-fast on null</b>: Throws NullPointerException if cmd is null</li>
 * </ul>
 *
 * <p>Phase 1 MVP Behavior:
 * <ul>
 *   <li>Creates exactly 1 ClusterSpec (single-cluster mode only)</li>
 *   <li>Cluster name derived from ModuleInfo and ClusterType: clu-{num}-{type}-{engine}</li>
 *   <li>Empty VMs list (VMs generated later by PlanBuilder)</li>
 *   <li>No explicit IP (firstIp = Optional.empty(), defaults applied by PlanBuilder)</li>
 *   <li>SizeProfile defaults to MEDIUM (can be overridden in future phases)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * var command = new GenerateCommand();
 * command.module = "m1";
 * command.type = "pt";
 * command.clusterType = "kind";
 *
 * var converter = new CliToSpec();
 * GeneratorSpec spec = converter.convert(command);
 *
 * // Result:
 * // spec.module() = ModuleInfo("m1", "pt")
 * // spec.clusters().getFirst().name() = "clu-m1-pt-kind"
 * // spec.clusters().getFirst().type() = ClusterType.KIND
 * // spec.clusters().getFirst().sizeProfile() = SizeProfile.MEDIUM
 * }</pre>
 *
 * @see GenerateCommand
 * @see GeneratorSpec
 * @see CliToSpec
 * @since 1.0.0
 */
public interface SpecConverter {
    /**
     * Converts CLI arguments to GeneratorSpec.
     *
     * <p>This is a pure transformation function. It does NOT:
     * <ul>
     *   <li>Validate business rules (handled by SemanticValidator)</li>
     *   <li>Allocate IP addresses (handled by PlanBuilder)</li>
     *   <li>Generate VMs (handled by PlanBuilder)</li>
     *   <li>Perform I/O operations</li>
     * </ul>
     *
     * @param cmd validated Picocli command object (must not be null)
     * @return GeneratorSpec with convention-over-configuration defaults applied
     * @throws NullPointerException     if cmd is null
     * @throws IllegalArgumentException if cmd contains structurally invalid data
     *                                  (e.g., unsupported cluster type in Phase 1)
     */
    GeneratorSpec convert(GenerateCommand cmd);
}
