# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  config.vm.box = "shekeriev/debian-12.11"
  config.ssh.insert_key = false
  config.vm.synced_folder ".", "/vagrant"
${EXTRA_SYNC_BLOCK}
${NODES_CONFIG}end

if File.exist?("Vagrantfile.local.rb")
  load "Vagrantfile.local.rb"
end

