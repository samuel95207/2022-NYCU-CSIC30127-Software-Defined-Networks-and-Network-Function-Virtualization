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
package nctu.winlab.bridge;

import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.Properties;
import java.util.HashMap;
import java.util.Optional;

import static org.onlab.util.Tools.get;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true, service = { SomeInterface.class }, property = {
        "someProperty=Some Default String Value", })
public class AppComponent implements SomeInterface {

    private final Logger log = LoggerFactory.getLogger(getClass());

    /** Some configurable property. */
    private String someProperty;

    private ApplicationId appId;
    private ReactivePacketProcessor processor;
    private HashMap<DeviceId, HashMap<MacAddress, PortNumber>> switchMacIpTable;

    private int flowPriority = 50000;
    private int flowTimeout = 60;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
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

        switchMacIpTable = new HashMap<DeviceId, HashMap<MacAddress, PortNumber>>();
        
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
        // packetService.requestPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
        //         PacketPriority.REACTIVE, appId, Optional.empty());
    }

    private void withdrawIntercepts() {
        packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
                PacketPriority.REACTIVE, appId, Optional.empty());
        // packetService.cancelPackets(DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_ARP).build(),
        //         PacketPriority.REACTIVE, appId, Optional.empty());
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
            
            MacAddress srcMacAddress = ethPkt.getSourceMAC();
            MacAddress dstMacAddress = ethPkt.getDestinationMAC();
            PortNumber inPort = pkt.receivedFrom().port();
            PortNumber outPort = null;
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            
            String logMessage = "";
            logMessage += "\nPacket In\n";
            logMessage += String.format("Switch %s\n%s to %s\n", deviceId.toString(), srcMacAddress.toString(), dstMacAddress.toString());

            
            HashMap<MacAddress, PortNumber> macIpTable = switchMacIpTable.get(deviceId);
            
            if (macIpTable == null) {
                macIpTable = new HashMap<MacAddress, PortNumber>();
                switchMacIpTable.put(deviceId, macIpTable);
            }
            
            macIpTable.put(srcMacAddress, inPort);
            
            outPort = macIpTable.get(dstMacAddress);
            
            if (outPort == null) {
                flood(context);
                logMessage += "flood";
            } else {
                installFlowRule(deviceId, dstMacAddress, outPort);
                packetOut(context, outPort);
                logMessage += "install flow";
            }
            
            log.info(logMessage);
        }
    }

    private void installFlowRule(DeviceId deviceId, MacAddress dstMac, PortNumber outPort) {
        FlowRule flowRule = DefaultFlowRule.builder()
                .withSelector(DefaultTrafficSelector.builder().matchEthDst(dstMac).build())
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
