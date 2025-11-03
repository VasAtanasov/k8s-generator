package com.k8s.generator.render;

import com.k8s.generator.model.VmConfig;

import java.util.List;
import java.util.Map;

/**
 * Renders files from a simple plan (Phase 1 text-based renderer).
 */
public interface Renderer {

    /**
     * Render core files for Phase 1.
     *
     * @param module module id (e.g., m1)
     * @param type type (e.g., pt, exam-prep)
     * @param vms list with exactly one VM (single-node engine)
     * @param env module-level environment vars
     * @return map of filename → content
     */
    Map<String, String> render(String module, String type, List<VmConfig> vms, Map<String, String> env);
}

