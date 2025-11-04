package com.k8s.generator.parser;

import com.k8s.generator.model.GeneratorSpec;
import com.k8s.generator.model.ScaffoldPlan;

/**
 * Converts validated spec to template-ready plan.
 *
 * <p>Architecture Position:
 * <pre>
 * CLI Args → SpecConverter → GeneratorSpec → Validator → PlanBuilder → ScaffoldPlan → Renderer
 * </pre>
 *
 * <p>Responsibility:
 * <ul>
 *   <li>Generate VMs from ClusterSpec counts (masters/workers or single-node cluster/management)</li>
 *   <li>Allocate IP addresses to VMs (Phase 1: hardcoded 192.168.56.10, Phase 2: IpAllocator)</li>
 *   <li>Determine sizing from SizeProfile (effective CPU/memory)</li>
 *   <li>Build environment variables map for bootstrap scripts</li>
 *   <li>Flatten multi-cluster topology to single VM list (Phase 3+)</li>
 * </ul>
 *
 * <p>Contract Guarantees:
 * <ul>
 *   <li><b>Pure transformation</b>: No side effects, no I/O, no external state</li>
 *   <li><b>Deterministic</b>: Same input always produces same output</li>
 *   <li><b>Assumes valid input</b>: Spec must be validated before calling build()</li>
 *   <li><b>No validation</b>: This builder does NOT re-validate the spec</li>
 *   <li><b>Fail-fast on null</b>: Throws NullPointerException if spec is null</li>
 * </ul>
 *
 * <p>Phase 1 MVP Behavior:
 * <ul>
 *   <li>Single VM generation: 1 VM with NodeRole.CLUSTER for kind/minikube</li>
 *   <li>Hardcoded IP: 192.168.56.10 (no IpAllocator yet)</li>
 *   <li>Environment variables:
 *       <ul>
 *         <li>CLUSTER_NAME: clu-{num}-{type}-{engine}</li>
 *         <li>NAMESPACE_DEFAULT: ns-{num}-{type}</li>
 *         <li>CLUSTER_TYPE: kind|minikube</li>
 *       </ul>
 *   </li>
 *   <li>Size profile: Use SizeProfile from ClusterSpec (defaults to SMALL)</li>
 * </ul>
 *
 * <p>Example Usage:
 * <pre>{@code
 * // Given validated spec
 * var spec = new GeneratorSpec(
 *     new ModuleInfo("m1", "pt"),
 *     List.of(
 *         new ClusterSpec("clu-m1-pt-kind", ClusterType.KIND, Optional.empty(), 0, 0,
 *                        SizeProfile.MEDIUM, List.of())
 *     )
 * );
 *
 * var builder = new SpecToPlan();
 * ScaffoldPlan plan = builder.build(spec);
 *
 * // Result:
 * // plan.vms().size() = 1
 * // plan.vms().getFirst().name() = "clu-m1-pt-kind"
 * // plan.vms().getFirst().role() = NodeRole.CLUSTER
 * // plan.vms().getFirst().ip() = "192.168.56.10"
 * // plan.envVars().get("CLUSTER_NAME") = "clu-m1-pt-kind"
 * // plan.envVars().get("CLUSTER_TYPE") = "kind"
 * }</pre>
 *
 * @see GeneratorSpec
 * @see ScaffoldPlan
 * @see SpecToPlan
 * @since 1.0.0
 */
public interface PlanBuilder {
    /**
     * Builds a template-ready plan from a validated specification.
     *
     * <p>This method assumes the input spec has been validated by StructuralValidator
     * and SemanticValidator. It does NOT re-validate the spec.
     *
     * <p>Transformation Steps:
     * <ol>
     *   <li>Generate VMs from cluster specifications (role, sizing)</li>
     *   <li>Allocate IP addresses to VMs (Phase 1: hardcoded, Phase 2: IpAllocator)</li>
     *   <li>Build environment variables map for bootstrap scripts</li>
     *   <li>Flatten topology (Phase 1: already flat, Phase 3: multi-cluster)</li>
     * </ol>
     *
     * @param spec validated GeneratorSpec (must not be null)
     * @return ScaffoldPlan ready for template rendering
     * @throws NullPointerException  if spec is null
     * @throws IllegalStateException if spec is in an unexpected state
     *                               (e.g., unsupported cluster type in Phase 1)
     */
    ScaffoldPlan build(GeneratorSpec spec);
}
