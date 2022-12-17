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
package nycu.sdnfv.vrouter;

import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_ADDED;
import static org.onosproject.net.config.NetworkConfigEvent.Type.CONFIG_UPDATED;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.EthType.EtherType;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.IPProtocolCriterion;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.routeservice.ResolvedRoute;
import org.onosproject.routeservice.RouteInfo;
import org.onosproject.routeservice.RouteService;
import org.onosproject.routeservice.RouteTableId;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final RouterConfigListener cfgListener = new RouterConfigListener();
    private final ConfigFactory<ApplicationId, RouterConfig> factory = new ConfigFactory<ApplicationId, RouterConfig>(
            APP_SUBJECT_FACTORY, RouterConfig.class, "vrouter") {
        @Override
        public RouterConfig createConfig() {
            return new RouterConfig();
        }
    };

    private ApplicationId appId;
    private ReactivePacketProcessor processor;

    private Boolean createBgpIntentFlag = false;
    private Boolean createExternlToExternalIntentFlag = false;

    private String quaggaLocation = null;
    private String quaggaMac = null;
    private String virtualIp = null;
    private String virtualMac = null;

    /** Some configurable property. */

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected InterfaceService interfaceService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected RouteService routeService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected HostService hostService;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication("nycu.sdnfv.vrouter");

        processor = new ReactivePacketProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(6));

        requestIntercepts();

        cfgService.addListener(cfgListener);
        cfgService.registerConfigFactory(factory);
        log.info("Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        cfgService.unregisterConfigFactory(factory);

        withdrawIntercepts();
        deleteAllIntent();

        log.info("Stopped");
    }

    private void requestIntercepts() {

        packetService.requestPackets(
                DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .build(),
                PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private void withdrawIntercepts() {

        packetService.cancelPackets(
                DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .build(),
                PacketPriority.REACTIVE, appId, Optional.empty());

    }

    private void deleteAllIntent() {
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

            Ip4Address dstIpAddress = Ip4Address.valueOf(ipPkt.getDestinationAddress());

            ResolvedRoute externalRoute = findExternalRoute(dstIpAddress);
            Host internalHost = findInternalHost(dstIpAddress);

            // Find Internal

            if (externalRoute != null) {
                Ip4Address nextHopIpAddress = externalRoute.nextHop().getIp4Address();
                MacAddress nextHopMacAddress = externalRoute.nextHopMac();

                Interface dstInterface = interfaceService.getMatchingInterface(nextHopIpAddress);
                ConnectPoint pktInPoint = pkt.receivedFrom();
                ConnectPoint dstPoint = dstInterface.connectPoint();

                log.info("nexthopMac {}", nextHopMacAddress);

                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(EtherType.IPV4.ethType().toShort())
                        .matchIPDst(dstIpAddress.toIpPrefix())
                        .build();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setEthSrc(MacAddress.valueOf(quaggaMac))
                        .setEthDst(nextHopMacAddress)
                        .build();
                PointToPointIntent intent = PointToPointIntent.builder()
                        .appId(appId)
                        .selector(selector)
                        .filteredIngressPoint(new FilteredConnectPoint(pktInPoint))
                        .filteredEgressPoint(new FilteredConnectPoint(dstPoint))
                        .treatment(treatment)
                        .priority(50000)
                        .build();

                intentService.submit(intent);

            } else if (internalHost != null) {
                Ip4Address hostIpAddress = internalHost.ipAddresses().iterator().next().getIp4Address();
                MacAddress hostMacAddress = internalHost.mac();

                ConnectPoint pktInPoint = pkt.receivedFrom();
                ConnectPoint dstPoint = ConnectPoint.fromString(String.format("%s/%s",
                        internalHost.location().deviceId(), internalHost.location().port().toString()));

                log.info("dstPoint {}", dstPoint.toString());

                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(EtherType.IPV4.ethType().toShort())
                        .matchIPDst(hostIpAddress.toIpPrefix())
                        .build();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setEthSrc(MacAddress.valueOf(virtualMac))
                        .setEthDst(hostMacAddress)
                        .build();
                PointToPointIntent intent = PointToPointIntent.builder()
                        .appId(appId)
                        .selector(selector)
                        .filteredIngressPoint(new FilteredConnectPoint(pktInPoint))
                        .filteredEgressPoint(new FilteredConnectPoint(dstPoint))
                        .treatment(treatment)
                        .priority(50000)
                        .build();

                intentService.submit(intent);
            }

        }

    }

    private ResolvedRoute findExternalRoute(Ip4Address dstIpAddress) {
        Collection<RouteTableId> routeTable = routeService.getRouteTables();
        Collection<RouteInfo> routes = null;

        if (routeTable == null) {
            return null;
        }

        for (RouteTableId routeTableId : routeTable) {
            if (routeTableId.toString() == "ipv4") {
                routes = routeService.getRoutes(routeTableId);
            }
        }

        if (routes == null) {
            return null;
        }

        for (RouteInfo route : routes) {
            if (route.prefix().contains(dstIpAddress)) {
                log.info("Find route to {}, prefix={}", dstIpAddress.toString(), route.prefix().toString());
                return route.bestRoute().get();
            }
        }

        return null;
    }

    private Host findInternalHost(Ip4Address dstIpAddress) {
        Set<Host> hostSet = hostService.getHostsByIp(dstIpAddress);
        if (hostSet.isEmpty()) {
            return null;
        }

        Host host = hostSet.iterator().next();

        return host;
    }

    private boolean isControlPacket(Ethernet eth) {
        short type = eth.getEtherType();
        return type == Ethernet.TYPE_LLDP || type == Ethernet.TYPE_BSN;
    }

    private class RouterConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if ((event.type() == CONFIG_ADDED || event.type() == CONFIG_UPDATED)
                    && event.configClass().equals(RouterConfig.class)) {
                RouterConfig config = cfgService.getConfig(appId, RouterConfig.class);
                if (config != null) {

                    quaggaLocation = config.quaggaLocation();
                    quaggaMac = config.quaggaMac();
                    virtualIp = config.virtualIp();
                    virtualMac = config.virtualMac();
                    List<String> peers = config.peers();

                    if (quaggaLocation == null) {
                        return;
                    }
                    if (quaggaMac == null) {
                        return;
                    }
                    if (virtualIp == null) {
                        return;
                    }
                    if (virtualMac == null) {
                        return;
                    }
                    if (peers == null) {
                        return;
                    }

                    if (!createBgpIntentFlag) {
                        ConnectPoint quaggaPoint = ConnectPoint.deviceConnectPoint(quaggaLocation);
                        log.info("Creating BGP Intents");
                        for (String peerIp : peers) {

                            Ip4Address peerIp4Address = Ip4Address.valueOf(peerIp);
                            Interface peerInterface = interfaceService.getMatchingInterface(peerIp4Address);
                            Ip4Address quaggaIp4Address = peerInterface.ipAddressesList().get(0).ipAddress()
                                    .getIp4Address();
                            ConnectPoint peerPoint = peerInterface.connectPoint();

                            TrafficSelector outSelector = DefaultTrafficSelector.builder()
                                    .matchEthType(EtherType.IPV4.ethType().toShort())
                                    .matchIPDst(peerIp4Address.toIpPrefix())
                                    .build();
                            TrafficSelector inSelector = DefaultTrafficSelector.builder()
                                    .matchEthType(EtherType.IPV4.ethType().toShort())
                                    .matchIPDst(quaggaIp4Address.toIpPrefix())
                                    .build();

                            PointToPointIntent outBgpIntent = PointToPointIntent.builder()
                                    .appId(appId)
                                    .selector(outSelector)
                                    .filteredIngressPoint(new FilteredConnectPoint(quaggaPoint))
                                    .filteredEgressPoint(new FilteredConnectPoint(peerPoint))
                                    .priority(50000)
                                    .build();

                            PointToPointIntent inBgpIntent = PointToPointIntent.builder()
                                    .appId(appId)
                                    .selector(inSelector)
                                    .filteredIngressPoint(new FilteredConnectPoint(peerPoint))
                                    .filteredEgressPoint(new FilteredConnectPoint(quaggaPoint))
                                    .priority(50000)
                                    .build();

                            intentService.submit(outBgpIntent);
                            intentService.submit(inBgpIntent);

                            log.info("peerPoint: {}, quaggaPoint: {}", peerPoint.toString(), quaggaPoint.toString());
                            log.info("peerIp: {}, quaggaIp: {}", peerIp4Address.toString(),
                                    quaggaIp4Address.toString());

                        }
                        createBgpIntentFlag = true;
                    }

                    if (!createExternlToExternalIntentFlag) {
                        Collection<RouteTableId> routeTable = routeService.getRouteTables();
                        Collection<RouteInfo> routes = null;

                        for (RouteTableId routeTableId : routeTable) {
                            if (routeTableId.toString() == "ipv4") {
                                routes = routeService.getRoutes(routeTableId);
                            }
                        }

                        if (routes != null) {
                            Set<FilteredConnectPoint> ingressPoints = new HashSet<FilteredConnectPoint>();
                            for (RouteInfo route : routes) {
                                Ip4Address nextHopIpAddress = route.bestRoute().get().nextHop().getIp4Address();
                                Interface dstInterface = interfaceService.getMatchingInterface(nextHopIpAddress);
                                ingressPoints.add(new FilteredConnectPoint(dstInterface.connectPoint()));
                            }

                            for (RouteInfo route : routes) {

                                Ip4Address nextHopIpAddress = route.bestRoute().get().nextHop().getIp4Address();
                                MacAddress nextHopMacAddress = route.bestRoute().get().nextHopMac();
                                Interface dstInterface = interfaceService.getMatchingInterface(nextHopIpAddress);

                                ConnectPoint dstPoint = dstInterface.connectPoint();

                                TrafficSelector selector = DefaultTrafficSelector.builder()
                                        .matchEthType(EtherType.IPV4.ethType().toShort())
                                        .matchIPDst(route.prefix())
                                        .build();

                                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                        .setEthSrc(MacAddress.valueOf(quaggaMac))
                                        .setEthDst(nextHopMacAddress)
                                        .build();

                                MultiPointToSinglePointIntent intent = MultiPointToSinglePointIntent.builder()
                                        .appId(appId)
                                        .selector(selector)
                                        .filteredIngressPoints(ingressPoints)
                                        .filteredEgressPoint(new FilteredConnectPoint(dstPoint))
                                        .treatment(treatment)
                                        .priority(50000)
                                        .build();

                                intentService.submit(intent);

                            }

                            createExternlToExternalIntentFlag = true;

                        }

                    }
                }
            }
        }
    }

}
