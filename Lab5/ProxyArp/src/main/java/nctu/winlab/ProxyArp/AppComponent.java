/*
 * Copyright 2022-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.ProxyArp;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.PortNumber;
import org.onosproject.net.edge.EdgePortService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ApplicationId appId;
    private ReactivePacketProcessor processor;

    TrafficSelector.Builder selectorArp;
    TrafficSelector.Builder selectorIpv4;

    private HashMap<Ip4Address, MacAddress> arpTable;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected EdgePortService edgePortService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Activate
    protected void activate() {
        appId = coreService.getAppId("nctu.winlab.ProxyArp");
        processor = new ReactivePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(2));

        selectorArp = DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP);
        requestIntercepts();

        arpTable = new HashMap<Ip4Address, MacAddress>();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        withdrawIntercepts();

        packetService.removeProcessor(processor);
        processor = null;

        log.info("Stopped");
    }

    private void requestIntercepts() {
        packetService.requestPackets(selectorArp.build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private void withdrawIntercepts() {
        packetService.cancelPackets(selectorArp.build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (context.isHandled()) {
                return;
            }

            if (ethPkt == null) {
                return;
            }

            if (isControlPacket(ethPkt)) {
                return;
            }

            if (ethPkt.getEtherType() != Ethernet.TYPE_ARP) {
                return;
            }

            ARP arpPkt = (ARP) ethPkt.getPayload();

            PortNumber inPort = pkt.receivedFrom().port();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            Ip4Address srcIpAddress = Ip4Address.valueOf(arpPkt.getSenderProtocolAddress());
            Ip4Address dstIpAddress = Ip4Address.valueOf(arpPkt.getTargetProtocolAddress());

            MacAddress srcMacAddress = ethPkt.getSourceMAC();
            MacAddress dstMacAddress = ethPkt.getDestinationMAC();

            arpTable.put(srcIpAddress, srcMacAddress);

            if (arpPkt.getOpCode() == ARP.OP_REQUEST) {
                MacAddress entry = arpTable.get(dstIpAddress);
                if (entry == null) {
                    log.info("TABLE MISS. Send request to edge ports");
                    floodEdge(ethPkt);
                } else {
                    log.info("TABLE HIT. Requested MAC = {}", entry.toString());
                    Ethernet replyArpPkt = ARP.buildArpReply(dstIpAddress, entry, ethPkt);
                    packetOut(replyArpPkt, deviceId, inPort);
                }
            } else if (arpPkt.getOpCode() == ARP.OP_REPLY) {
                log.info("RECV REPLY. Requested MAC = {}", srcMacAddress.toString());
                Set<Host> hostSet = hostService.getHostsByMac(dstMacAddress);
                Host hostArray[] = new Host[hostSet.size()];
                hostSet.toArray(hostArray);
                Host host = hostArray[0];
                packetOut(ethPkt, host.location().deviceId(), host.location().port());
            }

        }
    }

    private void floodEdge(Ethernet ethPkt) {
        for (ConnectPoint edgePort : edgePortService.getEdgePoints()) {
            Set<Host> hostSet = hostService.getConnectedHosts(edgePort);

            Boolean sameHost = false;
            for (Host host : hostSet) {
                if (host.mac().equals(ethPkt.getSourceMAC())) {
                    sameHost = true;
                    break;
                }
            }
            if (sameHost) {
                continue;
            }
            packetOut(ethPkt, edgePort.deviceId(), edgePort.port());
        }
    }
    

    private void packetOut(Ethernet ethPkt, DeviceId deviceId, PortNumber port) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder().setOutput(port).build();
        OutboundPacket outboundPacket = new DefaultOutboundPacket(deviceId, treatment,
                ByteBuffer.wrap(ethPkt.serialize()));
        packetService.emit(outboundPacket);
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

}
