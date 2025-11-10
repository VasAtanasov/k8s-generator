package com.k8s.generator.cli;

import com.k8s.generator.app.ScaffoldService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Picocli command definition for the k8s-generator CLI.
 */
@Command(
        name = "k8s-gen",
        mixinStandardHelpOptions = true,
        version = "k8s-generator 0.2.0",
        description = {
                "Generate Kubernetes learning environments using convention-over-configuration.",
                "",
                "Examples:",
                "  k8s-gen --module m1 --type pt kind",
                "  k8s-gen --module m1 --type pt minikube --out pt-m1/",
                "  k8s-gen --module m7 --type hw kubeadm --nodes 1m,2w --cni calico --size large",
                "  k8s-gen --module m9 --type lab mgmt",
                "  k8s-gen --module m1 --type pt kind --dry-run"
        }
)
public final class GenerateCommand implements Callable<Integer> {

    @Option(names = {"--module"}, required = true, description = "Module number (e.g., m1, m7)")
    public String module;

    @Option(names = {"--type"}, required = true, description = "Type (e.g., pt, hw, exam, exam-prep)")
    public String type;

    @Parameters(index = "0", description = "Cluster type: kind|minikube|kubeadm|mgmt")
    public String clusterType;

    @Option(names = {"--out"}, description = "Output directory; default is <type>-<module>/")
    public String outDir;

    @Option(names = {"--size"}, description = "Size profile: small|medium|large (default: medium)")
    public String size;

    @Option(names = {"--nodes"}, description = "Kubeadm nodes: N (means 1m,(N-1)w) or Xm,Yw (e.g., 1m,2w)")
    public String nodes;

    @Option(names = {"--cni"}, description = "Kubeadm CNI: calico|flannel|weave|cilium|antrea")
    public String cni;

    @Option(names = {"--dry-run"}, description = "Show planned VMs/files and exit without writing")
    public boolean dryRun;

    @Option(names = {"--azure"}, description = "Enable Azure integration (installs Azure CLI and configures management VM)")
    public boolean azure;

    @Override
    public Integer call() {
        return ScaffoldService.create().scaffold(this);
    }
}
