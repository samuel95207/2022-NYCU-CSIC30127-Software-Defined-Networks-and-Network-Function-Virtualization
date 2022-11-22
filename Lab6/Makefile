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



h1-ping:
	docker exec -it h1 ping 172.18.0.2 -c 3
	docker exec -it h1 ping 172.20.0.3 -c 3
	docker exec -it h1 ping 172.19.0.3 -c 3
	docker exec -it h1 ping 172.20.0.4 -c 3
	docker exec -it h1 ping 172.21.0.3 -c 3
	docker exec -it h1 ping 172.22.0.3 -c 3

h2-ping:
	docker exec -it h2 ping 172.19.0.2 -c 3
	docker exec -it h2 ping 172.20.0.2 -c 3
	docker exec -it h2 ping 172.18.0.3 -c 3
	docker exec -it h2 ping 172.20.0.4 -c 3
	docker exec -it h2 ping 172.21.0.3 -c 3
	docker exec -it h1 ping 172.22.0.3 -c 3

h3-ping:
	docker exec -it h2 ping 172.22.0.2 -c 3
	docker exec -it h2 ping 172.21.0.2 -c 3
	docker exec -it h2 ping 172.20.0.3 -c 3
	docker exec -it h2 ping 172.19.0.3 -c 3
	docker exec -it h2 ping 172.20.0.2 -c 3
	docker exec -it h1 ping 172.18.0.3 -c 3


pingall: h1-ping h2-ping h3-ping


zip:
	rm -rf project6_311511034
	rm -rf project6_311511034.zip
	mkdir project6_311511034
	cp -r config project6_311511034
	cp -r docker-compose.yml project6_311511034
	cp -r host.Dockerfile project6_311511034
	cp -r Makefile project6_311511034
	zip -r project6_311511034.zip project6_311511034
	rm -rf project6_311511034