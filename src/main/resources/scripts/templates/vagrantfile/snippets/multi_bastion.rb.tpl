  config.vm.define "bastion" do |node|
    node.vm.hostname = "bastion"
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
      shell.inline = "bash /vagrant/scripts/bootstrap-bastion.sh"
    end
  end

