
all:
	make -C vrouter

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

zip:
	rm -rf final_project_311511034
	rm -rf final_project_311511034.zip

	mkdir final_project_311511034

	cp -r vrouter/* final_project_311511034
	rm -rf final_project_311511034/target

	zip -r final_project_311511034.zip final_project_311511034
	rm -rf final_project_311511034


h6-dhcp:
	-docker exec -it h06 dhclient -v vethh06ovs1

h1-ping:
	docker exec -it h01 ping 192.168.50.2 -c 3
	docker exec -it h01 ping 192.168.51.2 -c 3
	docker exec -it h01 ping 192.168.52.2 -c 3
	docker exec -it h01 ping 192.168.53.2 -c 3

h2-ping:
	docker exec -it h02 ping 192.168.50.1 -c 3
	docker exec -it h02 ping 192.168.51.2 -c 3
	docker exec -it h02 ping 192.168.52.2 -c 3
	docker exec -it h02 ping 192.168.53.2 -c 3

h3-ping:
	docker exec -it h03 ping 192.168.50.1 -c 3
	docker exec -it h03 ping 192.168.50.2 -c 3
	docker exec -it h03 ping 192.168.52.2 -c 3
	docker exec -it h03 ping 192.168.53.2 -c 3

h4-ping:
	docker exec -it h04 ping 192.168.50.1 -c 3
	docker exec -it h04 ping 192.168.50.2 -c 3
	docker exec -it h04 ping 192.168.51.2 -c 3
	docker exec -it h04 ping 192.168.53.2 -c 3

h5-ping:
	docker exec -it h05 ping 192.168.50.1 -c 3
	docker exec -it h05 ping 192.168.50.2 -c 3
	docker exec -it h05 ping 192.168.51.2 -c 3
	docker exec -it h05 ping 192.168.52.2 -c 3

h6-ping: h6-dhcp
	docker exec -it h06 ping 192.168.50.1 -c 3
	docker exec -it h06 ping 192.168.50.2 -c 3
	docker exec -it h06 ping 192.168.51.2 -c 3
	docker exec -it h06 ping 192.168.52.2 -c 3
	docker exec -it h06 ping 192.168.53.2 -c 3

pingall: h1-ping h2-ping h3-ping h4-ping h5-ping h6-ping