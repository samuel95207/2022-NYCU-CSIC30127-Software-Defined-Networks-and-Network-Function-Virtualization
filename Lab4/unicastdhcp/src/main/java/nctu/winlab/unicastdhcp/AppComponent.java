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
package nctu.winlab.unicastdhcp;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ServerConfigListener cfgListener = new ServerConfigListener();
    private final ConfigFactory<ApplicationId, ServerConfig> factory = new ConfigFactory<ApplicationId, ServerConfig>(
            APP_SUBJECT_FACTORY, ServerConfig.class, "UnicastDhcpConfig") {
        @Override
        public ServerConfig createConfig() {
            return new ServerConfig();
        }
    };
    private ApplicationId appId;
    private ReactivePacketProcessor processor;

    private ConnectPoint dhcpServer = null;
    TrafficSelector.Builder selectorDhcpServer;
    TrafficSelector.Builder selectorDhcpClient;

    private HashMap<String, ArrayList<PointToPointIntent>> intentTable;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nctu.winlab.unicastdhcp");
        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);

        processor = new ReactivePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(2));

        selectorDhcpServer = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

        selectorDhcpClient = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

        requestIntercepts();

        intentTable = new HashMap<String, ArrayList<PointToPointIntent>>();

        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        withdrawIntercepts();

        packetService.removeProcessor(processor);
        processor = null;

        intentService.getIntentsByAppId(appId).forEach((intent) -> {
            intentService.withdraw(intent);
        });

        while (true) {
            int count = 0;
            for (Intent intent : intentService.getPending()) {
                if (intent.appId() == appId) {
                    count++;
                }
            }
            // log.info("Remaining intent count = {}", count);
            if (count == 0) {
                break;
            }
        }

        intentService.getIntentsByAppId(appId).forEach((intent) -> {
            intentService.purge(intent);
        });

        log.info("Stopped");
    }

    private void requestIntercepts() {

        packetService.requestPackets(selectorDhcpServer.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(selectorDhcpClient.build(), PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private void withdrawIntercepts() {

        packetService.cancelPackets(selectorDhcpServer.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.cancelPackets(selectorDhcpClient.build(), PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private class ReactivePacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            if (dhcpServer == null) {
                return;
            }

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (context.isHandled()) {
                return;
            }

            if (ethPkt == null) {
                return;
            }

            PortNumber inPort = pkt.receivedFrom().port();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            MacAddress srcMacAddress = ethPkt.getSourceMAC();

            ConnectPoint dhcpClient = new ConnectPoint(deviceId, inPort);

            if (dhcpClient.equals(dhcpServer)) {
                // log.info("Intent error! Client same as Server.");
                return;
            }

            if (intentTable.get(dhcpClient.toString()) != null) {
                // log.info("Intent already exist.");
                return;
            }

            TrafficSelector selectorServer = selectorDhcpServer.build();
            TrafficSelector selectorClient = selectorDhcpClient
                    .matchEthDst(srcMacAddress)
                    .build();

            PointToPointIntent dhcpToServerIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(selectorServer)
                    .filteredIngressPoint(new FilteredConnectPoint(dhcpClient))
                    .filteredEgressPoint(new FilteredConnectPoint(dhcpServer))
                    .priority(50000)
                    .build();


            PointToPointIntent dhcpToClientIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .selector(selectorClient)
                    .filteredIngressPoint(new FilteredConnectPoint(dhcpServer))
                    .filteredEgressPoint(new FilteredConnectPoint(dhcpClient))
                    .priority(50000)
                    .build();

            intentService.submit(dhcpToClientIntent);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    dhcpToClientIntent.filteredIngressPoint().connectPoint().deviceId().toString(),
                    dhcpToClientIntent.filteredIngressPoint().connectPoint().port().toString(),
                    dhcpToClientIntent.filteredEgressPoint().connectPoint().deviceId().toString(),
                    dhcpToClientIntent.filteredEgressPoint().connectPoint().port().toString());

            intentService.submit(dhcpToServerIntent);
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    dhcpToServerIntent.filteredIngressPoint().connectPoint().deviceId().toString(),
                    dhcpToServerIntent.filteredIngressPoint().connectPoint().port().toString(),
                    dhcpToServerIntent.filteredEgressPoint().connectPoint().deviceId().toString(),
                    dhcpToServerIntent.filteredEgressPoint().connectPoint().port().toString());

            ArrayList<PointToPointIntent> intentList = new ArrayList<PointToPointIntent>();
            intentList.add(dhcpToServerIntent);
            intentList.add(dhcpToClientIntent);
            intentTable.put(dhcpClient.toString(), intentList);

            return;

        }
    }

    private class ServerConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                    && event.configClass().equals(ServerConfig.class)) {
                ServerConfig config = cfgService.getConfig(appId, ServerConfig.class);
                if (config != null) {
                    String deviceIdStr = config.serverSwitchId();
                    String devicePortStr = config.serverSwitchPort();

                    dhcpServer = new ConnectPoint(DeviceId.deviceId(deviceIdStr), PortNumber.fromString(devicePortStr));

                    log.info("DHCP server is connected to `{}`, port `{}`", deviceIdStr,
                            devicePortStr);
                }
            }
        }
    }

}
