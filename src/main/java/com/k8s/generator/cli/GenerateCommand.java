package com.k8s.generator.cli;

import com.k8s.generator.app.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Picocli command definition for the k8s-generator CLI.
 */
@Command(name = "k8s-gen",
        mixinStandardHelpOptions = true,
        description = "Generate Kubernetes learning environments (Phase 1: kind|minikube)")
public final class GenerateCommand implements Callable<Integer> {

    @Option(names = {"--module"}, required = true, description = "Module number (e.g., m1, m7)")
    public String module;

    @Option(names = {"--type"}, required = true, description = "Type (e.g., pt, hw, exam, exam-prep)")
    public String type;

    @Parameters(index = "0", description = "Cluster type: kind|minikube|kubeadm|mgmt")
    public String clusterType;

    @Option(names = {"--out"}, description = "Output directory; default is <type>-<module>/")
    public String outDir;

    @Override
    public Integer call() {
        return new ScaffoldService().scaffold(this);
    }
}

