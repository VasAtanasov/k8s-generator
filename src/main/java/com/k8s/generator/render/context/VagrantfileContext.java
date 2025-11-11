package com.k8s.generator.render.context;

import com.k8s.generator.model.VmConfig;

import java.util.List;
import java.util.Map;

/**
 * Typed context object for Vagrantfile/Bootstrap templates.
 */
public record VagrantfileContext(
        String moduleName,
        List<VmConfig> vms,
        Map<String, String> envVars) {}

