sudo mn --topo=tree,depth=5 \
        --controller=remote,ip=127.0.0.1:6653 \
        --switch=ovs,protocols=OpenFlow14