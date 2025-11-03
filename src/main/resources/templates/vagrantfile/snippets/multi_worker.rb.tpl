  config.vm.define "${NODE_DEFINE}" do |node|
    node.vm.hostname = "${NODE_HOSTNAME}"
    node.vm.network :private_network, ip: "${NODE_IP}"

    node.vm.provider :virtualbox do |vb|
      vb.memory = ${VM_MEMORY}
      vb.cpus = ${VM_CPUS}
      vb.name = "${VM_NAME}"
      vb.customize ['modifyvm', :id, '--ioapic', 'on']
      vb.customize ['modifyvm', :id, '--natdnshostresolver1', 'on']
      vb.customize ['modifyvm', :id, '--natdnsproxy1', 'on']
      vb.customize ['modifyvm', :id, '--nested-hw-virt', 'on']
    end

    node.vm.provision "bootstrap", type: :shell do |shell|
      shell.inline = "bash /vagrant/scripts/bootstrap-${CLUSTER_NAME}-worker.sh"
      shell.env = {
          "NODE_ROLE"         => "worker",
          "CLUSTER_NAME"      => "${CLUSTER_NAME}",
          "K8S_VERSION"       => "${K8S_VERSION}",
          "K8S_POD_CIDR"      => "${K8S_POD_CIDR}",
          "K8S_SVC_CIDR"      => "${K8S_SVC_CIDR}",
          "CNI_TYPE"          => "${CNI_TYPE}",
          "NAMESPACE_DEFAULT" => "${NAMESPACE_DEFAULT}"
      }
    end
  end

