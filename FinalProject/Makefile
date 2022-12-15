
all:
	cd vrouter; mvn clean install -DskipTests 

run: run_topo set_config run_dhcp install_default_apps install_vrouter

clean: uninstall_vrouter uninstall_apps clean_dhcp clean_topo

install_vrouter:
	onos-app localhost deactivate nycu.sdnfv.vrouter
	onos-app localhost uninstall nycu.sdnfv.vrouter
	onos-app localhost install! vrouter/target/vrouter-1.0-SNAPSHOT.oar

install_default_apps:
	onos-app localhost install! target/bridge-1.0-SNAPSHOT.oar
	onos-app localhost install! target/proxyarp-1.0-SNAPSHOT.oar
	onos-app localhost install! target/unicastdhcp-1.0-SNAPSHOT.oar

uninstall_vrouter:
	onos-app localhost deactivate nycu.sdnfv.vrouter
	onos-app localhost uninstall nycu.sdnfv.vrouter

uninstall_apps:
	onos-app localhost deactivate nycu.sdnfv.bridge
	onos-app localhost uninstall nycu.sdnfv.bridge
	onos-app localhost deactivate nycu.sdnfv.proxyarp
	onos-app localhost uninstall nycu.sdnfv.proxyarp
	onos-app localhost deactivate nycu.sdnfv.unicastdhcp
	onos-app localhost uninstall nycu.sdnfv.unicastdhcp

set_config:
	onos-netcfg localhost netcfg.json

run_topo:
	sudo ./build_topo.sh

run_dhcp:
	sudo ./dhcp_start.sh

clean_topo:
	-sudo ./clean_topo.sh

clean_dhcp:
	-sudo killall dhcpd