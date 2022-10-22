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

import java.util.Optional;

import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
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

        requestIntercepts();

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

        intentService.getIntentsByAppId(appId).forEach((intent) -> {
            intentService.purge(intent);
        });

        log.info("Stopped");
    }

    private void requestIntercepts() {
        TrafficSelector.Builder selectorServer = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

        TrafficSelector.Builder selectorClient = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

        packetService.requestPackets(selectorServer.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.requestPackets(selectorClient.build(), PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selectorServer = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_SERVER_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_CLIENT_PORT));

        TrafficSelector.Builder selectorClient = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchUdpDst(TpPort.tpPort(UDP.DHCP_CLIENT_PORT))
                .matchUdpSrc(TpPort.tpPort(UDP.DHCP_SERVER_PORT));

        packetService.cancelPackets(selectorServer.build(), PacketPriority.REACTIVE, appId, Optional.empty());
        packetService.cancelPackets(selectorClient.build(), PacketPriority.REACTIVE, appId, Optional.empty());

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
            ConnectPoint dhcpClient = new ConnectPoint(deviceId, inPort);

            PointToPointIntent dhcpToServerIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .filteredIngressPoint(new FilteredConnectPoint(dhcpClient))
                    .filteredEgressPoint(new FilteredConnectPoint(dhcpServer))
                    .priority(50000)
                    .build();

            PointToPointIntent dhcpToClientIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .filteredIngressPoint(new FilteredConnectPoint(dhcpServer))
                    .filteredEgressPoint(new FilteredConnectPoint(dhcpClient))
                    .priority(50000)
                    .build();
            

            intentService.submit(dhcpToServerIntent);
            intentService.submit(dhcpToClientIntent);

            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    dhcpToServerIntent.filteredIngressPoint().connectPoint().deviceId().toString(),
                    dhcpToServerIntent.filteredIngressPoint().connectPoint().port().toString(),
                    dhcpToServerIntent.filteredEgressPoint().connectPoint().deviceId().toString(),
                    dhcpToServerIntent.filteredEgressPoint().connectPoint().port().toString());
            log.info("Intent `{}`, port `{}` => `{}`, port `{}` is submitted.",
                    dhcpToClientIntent.filteredIngressPoint().connectPoint().deviceId().toString(),
                    dhcpToClientIntent.filteredIngressPoint().connectPoint().port().toString(),
                    dhcpToClientIntent.filteredEgressPoint().connectPoint().deviceId().toString(),
                    dhcpToClientIntent.filteredEgressPoint().connectPoint().port().toString());

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
