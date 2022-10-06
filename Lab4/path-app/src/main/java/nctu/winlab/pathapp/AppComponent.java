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
package nctu.winlab.pathapp;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyEdge;
import org.onosproject.net.topology.TopologyGraph;
import org.onosproject.net.topology.TopologyService;
import org.onosproject.net.topology.TopologyVertex;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = { SomeInterface.class }, property = {
        "someProperty=Some Default String Value",
})
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    private ApplicationId appId;
    private ReactivePacketProcessor processor;

    private int flowPriority = 10;
    private int flowTimeout = 30;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected TopologyService topologyService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected FlowRuleService flowRuleService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Activate
    protected void activate() {
        cfgService.registerProperties(getClass());

        appId = coreService.getAppId("nctu.winlab.bridge");
        processor = new ReactivePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(2));

        requestIntercepts();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.unregisterProperties(getClass(), false);
        withdrawIntercepts();

        flowRuleService.removeFlowRulesById(appId);
        packetService.removeProcessor(processor);
        processor = null;

        log.info("Stopped");
    }

    @Modified
    public void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context != null ? context.getProperties() : new Properties();
        if (context != null) {
            someProperty = get(properties, "someProperty");
        }
        log.info("Reconfigured");
    }

    @Override
    public void someMethod() {
        log.info("Invoked");
    }

    private void requestIntercepts() {
        packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private void withdrawIntercepts() {
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
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

            if (ethPkt.getEtherType() != Ethernet.TYPE_IPV4) {
                return;
            }

            IPv4 ipPkt = (IPv4) ethPkt.getPayload();

            MacAddress srcMacAddress = ethPkt.getSourceMAC();
            MacAddress dstMacAddress = ethPkt.getDestinationMAC();
            Ip4Address srcIpAddress = Ip4Address.valueOf(ipPkt.getSourceAddress());
            Ip4Address dstIpAddress = Ip4Address.valueOf(ipPkt.getDestinationAddress());
            HostId srcHostId = HostId.hostId(srcMacAddress);
            HostId dstHostId = HostId.hostId(dstMacAddress);
            Host srcHost = hostService.getHost(srcHostId);
            Host dstHost = hostService.getHost(dstHostId);
            PortNumber inPort = pkt.receivedFrom().port();
            PortNumber outPort = null;
            DeviceId deviceId = pkt.receivedFrom().deviceId();

            log.info(String.format("Packet-in from device %s", deviceId.toString()));
            // log.info(String.format("%s to %s", srcIpAddress.toString(),
            // dstIpAddress.toString()));

            // Get Topology from topologyService
            TopologyGraph topologyGraph = topologyService.getGraph(topologyService.currentTopology());

            // Use Dijkstra to find shortest path
            HashMap<DeviceId, PortNumber> path = dijkstra(topologyGraph, deviceId, dstHost.location().deviceId());
            path.put(dstHost.location().deviceId(), dstHost.location().port());
            // log.info(String.format("%s", path.toString()));

            // Check if path exist
            if (path.size() == 1 && srcHost.location().deviceId() != dstHost.location().deviceId()) {
                // log.info(String.format("Cannot find path from %s to %s",
                // dstMacAddress.toString(), srcMacAddress.toString()));
                return;
            }

            // Install flow rule on all switch on path
            log.info(String.format("Start to install path from %s to %s", dstMacAddress.toString(),
                    srcMacAddress.toString()));
            for (Map.Entry<DeviceId, PortNumber> switchPortPair : path.entrySet()) {
                installFlowRule(switchPortPair.getKey(), srcIpAddress, dstIpAddress, switchPortPair.getValue());
            }

        }
    }

    private HashMap<DeviceId, PortNumber> dijkstra(TopologyGraph topologyGraph, DeviceId src, DeviceId dst) {

        ArrayList<TopologyVertex> vertexList = new ArrayList<TopologyVertex>(topologyGraph.getVertexes());
        HashMap<DeviceId, PortNumber> path = new HashMap<DeviceId, PortNumber>();

        // Find the vertex index of src and dst
        int srcIndex = -1;
        int dstIndex = -1;
        for (int i = 0; i < vertexList.size(); i++) {
            if (vertexList.get(i).deviceId().equals(src)) {
                srcIndex = i;
                break;
            }
        }
        for (int i = 0; i < vertexList.size(); i++) {
            if (vertexList.get(i).deviceId().equals(dst)) {
                dstIndex = i;
                break;
            }
        }

        if (srcIndex == -1 || dstIndex == -1) {
            return path;
        }

        // Dijkstra variable initialize
        int nVertices = vertexList.size();
        int[] distanceList = new int[nVertices];
        Boolean[] addedList = new Boolean[nVertices];
        int[] parents = new int[nVertices];
        TopologyEdge[] parentEdges = new TopologyEdge[nVertices];

        for (int i = 0; i < nVertices; i++) {
            distanceList[i] = Integer.MAX_VALUE;
            addedList[i] = false;
            parents[i] = -1;
            parentEdges[i] = null;
        }

        // The distance from src to src is 0
        distanceList[srcIndex] = 0;

        // Iterate through nVertices-1 to add vertices to shortest path tree
        for (int i = 0; i < nVertices - 1; i++) {
            int minVertex = -1;
            int minDistance = Integer.MAX_VALUE;

            // Find the vertex with min distance
            for (int iterVertex = 0; iterVertex < nVertices; iterVertex++) {
                if (!addedList[iterVertex] && distanceList[iterVertex] < minDistance) {
                    minVertex = iterVertex;
                    minDistance = distanceList[iterVertex];
                }
            }

            // Return empty path if path doesn't exist
            if (minVertex == -1) {
                return path;
            }

            addedList[minVertex] = true;

            // Iterate through all edges connect to minVertex to find min distance edge
            ArrayList<TopologyEdge> edgeList = new ArrayList<TopologyEdge>(
                    topologyGraph.getEdgesFrom(vertexList.get(minVertex)));
            for (int iterVertex = 0; iterVertex < nVertices; iterVertex++) {
                int edgeDistance = Integer.MAX_VALUE;
                TopologyEdge edge = null;
                for (int dstVertex = 0; dstVertex < edgeList.size(); dstVertex++) {
                    if (edgeList.get(dstVertex).dst().deviceId() == vertexList.get(iterVertex).deviceId()) {
                        edgeDistance = 1;
                        edge = edgeList.get(dstVertex);
                        break;
                    }
                }

                if (edgeDistance != Integer.MAX_VALUE && ((minDistance + edgeDistance) < distanceList[iterVertex])) {
                    parents[iterVertex] = minVertex;
                    parentEdges[iterVertex] = edge;
                    distanceList[iterVertex] = minDistance + edgeDistance;
                }
            }

        }

        // Add device, port pair to hashmap
        for (int i = dstIndex; parents[i] != -1; i = parents[i]) {
            path.put(vertexList.get(parents[i]).deviceId(), parentEdges[i].link().src().port());
            // log.info(String.format("%s %s %s %s",
            // vertexList.get(parents[i]).deviceId().toString(),
            // parentEdges[i].src().deviceId().toString(),
            // parentEdges[i].dst().deviceId().toString(),
            // parentEdges[i].link().src().port().toString()));
        }

        return path;

    }

    private void installFlowRule(
            DeviceId deviceId, Ip4Address srcIp, Ip4Address dstIp, PortNumber outPort) {
        log.info(String.format("Install flow rule on %s", deviceId.toString()));
        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchIPSrc(srcIp.toIpPrefix())
                        .matchIPDst(dstIp.toIpPrefix())
                        .build())
                .withTreatment(DefaultTrafficTreatment.builder().setOutput(outPort).build())
                .withPriority(flowPriority)
                .fromApp(appId)
                .forDevice(deviceId)
                .makeTemporary(flowTimeout)
                .build();
        flowRuleService.applyFlowRules(flowRule);
    }

    private void packetOut(PacketContext context, PortNumber port) {
        context.treatmentBuilder().setOutput(port);
        context.send();
    }

    private void flood(PacketContext context) {
        packetOut(context, PortNumber.FLOOD);
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

}
