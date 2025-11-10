@import com.k8s.generator.model.VmConfig
@import java.util.List
@import java.util.Map

@param String moduleName
@param List<VmConfig> vms
@param Map<String, Map<String, String>> envByVm
# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
    config.ssh.insert_key = false
    config.ssh.forward_agent = true

    config.vm.synced_folder ".", "/vagrant"

    config.vm.box = "shekeriev/debian-12.11"

    config.vm.provider "virtualbox" do |vb|
        vb.customize ['modifyvm', :id, '--ioapic', 'on']
        vb.customize ['modifyvm', :id, '--natdnshostresolver1', 'on']
        vb.customize ['modifyvm', :id, '--natdnsproxy1', 'on']
        vb.customize ['modifyvm', :id, '--nested-hw-virt', 'on']
        vb.customize ["modifyvm", :id, "--audio", "none"]
        # consider adding default context globals values for:
        # vb.memory =
        # vb.cpus =
    end

    @for(VmConfig vm : vms)config.vm.define "${vm.name().value()}" do |node|
        node.vm.hostname = "${vm.name().value()}"
        node.vm.network "private_network", ip: "${vm.ip().toCanonicalString()}"

        node.vm.provider "virtualbox" do |vb|
            vb.memory = ${vm.getEffectiveMemoryMb()}
            vb.cpus = ${vm.getEffectiveCpus()}
            vb.name = "${vm.name().value()}"
        end

        node.vm.provision "bootstrap", type: :shell do |shell|
            shell.inline = "bash /vagrant/scripts/bootstrap.sh"
            shell.env = {
            @for(Map.Entry<String, String> entry : envByVm.get(vm.name().value()).entrySet())  "${entry.getKey()}" => "${entry.getValue()}",
            @endfor
            }
        end
    end

    @endfor
end

if File.exist?("Vagrantfile.local.rb")
    load "Vagrantfile.local.rb"
end
