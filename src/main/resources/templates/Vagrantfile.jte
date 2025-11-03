@import com.k8s.generator.model.VmConfig
@import java.util.List
@import java.util.Map

@param String moduleName
@param List<VmConfig> vms
@param Map<String, String> envVars
# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
@for(VmConfig vm : vms)
    config.vm.define "${vm.name()}" do |node|
        node.vm.box = "ubuntu/jammy64"
        node.vm.hostname = "${vm.name()}"
        node.vm.network "private_network", ip: "${vm.ip()}"

        node.vm.provider "virtualbox" do |vb|
            vb.memory = ${vm.getEffectiveMemoryMb()}
            vb.cpus = ${vm.getEffectiveCpus()}
        end

        node.vm.provision "bootstrap", type: :shell do |shell|
            shell.inline = "bash /vagrant/scripts/bootstrap.sh"
            shell.env = {
            @for(Map.Entry<String, String> entry : envVars.entrySet())
                "${entry.getKey()}" => "${entry.getValue()}"
            @endfor
            }
        end
    end
@endfor
end

if File.exist?("Vagrantfile.local.rb")
    load "Vagrantfile.local.rb"
end
