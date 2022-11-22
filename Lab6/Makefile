all: docker ovs

docker:
	docker compose up -d

ovs:
	sudo ovs-vsctl add-br ovs
	sudo ovs-vsctl set bridge ovs protocols=OpenFlow14
	sudo ovs-vsctl set-controller ovs tcp:127.0.0.1:6653
	sudo ovs-docker add-port ovs eth1 R1 --ipaddress=172.20.0.2/16
	sudo ovs-docker add-port ovs eth1 R2 --ipaddress=172.20.0.3/16
	sudo ovs-docker add-port ovs eth1 R3 --ipaddress=172.20.0.4/16

clean: clean-docker clean-ovs

clean-docker:
	docker compose down

clean-ovs:
	sudo ovs-vsctl del-br ovs

