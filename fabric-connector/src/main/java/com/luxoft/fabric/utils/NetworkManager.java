package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class NetworkManager {
    public static void configNetwork(final FabricConfig fabricConfig) {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        if (!channels.hasNext())
            throw new RuntimeException("Need at least one channel to do some work");

        CryptoSuite cryptoSuite = FabricConfig.getCryptoSuite();
        Set<String> installedChaincodes = new HashSet<>();

        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();
            String channelName = channelObject.getKey();
            JsonNode channelParameters = channelObject.getValue();
            try {
                String adminKey = channelParameters.get("admin").asText();
                final User fabricUser = fabricConfig.getAdmin(adminKey);

                HFClient hfClient = HFClient.createNewInstance();
                hfClient.setCryptoSuite(cryptoSuite);
                hfClient.setUserContext(fabricUser);

                Iterator<JsonNode> orderers = channelParameters.path("orderers").iterator();
                if (!orderers.hasNext())
                    throw new RuntimeException("Orderers list can`t be empty");
                List<Orderer> ordererList = new ArrayList<>();
                while (orderers.hasNext()) {
                    String ordererKey = orderers.next().asText();
                    Orderer orderer = fabricConfig.getNewOrderer(hfClient, ordererKey);
                    ordererList.add(orderer);
                }

                Iterator<JsonNode> peers = channelParameters.path("peers").iterator();
                if (!peers.hasNext())
                    throw new RuntimeException("Peers list can`t be empty");
                List<Peer> peerList = new ArrayList<>();
                while (peers.hasNext()) {
                    String peerKey = peers.next().asText();
                    Peer peer = fabricConfig.getNewPeer(hfClient, peerKey);
                    peerList.add(peer);
                }

                Iterator<JsonNode> eventhubs = channelParameters.path("eventhubs").iterator();
                List<EventHub> eventhubList = new ArrayList<>();
                while (eventhubs.hasNext()) {
                    String eventhubKey = eventhubs.next().asText();
                    EventHub eventhub = fabricConfig.getNewEventhub(hfClient, eventhubKey);
                    eventhubList.add(eventhub);
                }

                Set<String> installedChannels = hfClient.queryChannels(peerList.get(0));
                boolean alreadyInstalled = false;

                for (String installedChannelName : installedChannels) {
                    if (installedChannelName.equalsIgnoreCase(channelName)) alreadyInstalled = true;
                }

                boolean newChannel = false;
                Channel channel;
                if (!alreadyInstalled) {
                    String txFile = fabricConfig.getFileName(channelParameters, "txFile");
                    ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                    try {
                        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, hfClient.getUserContext());
                        channel = hfClient.newChannel(channelName, ordererList.get(0), channelConfiguration, channelConfigurationSignature);
                        newChannel = true;
                    } catch (Exception ex) {
                        //recreating orderer object as it may be consumed by try channel and will be destroyed on it`s GC
                        Orderer newOrderer = fabricConfig.getNewOrderer(hfClient, ordererList.get(0).getName());
                        ordererList.set(0, newOrderer);
                        channel = hfClient.newChannel(channelName);
                        System.out.println("Exception happened while creating channel, this might not be a problem");
                        ex.printStackTrace();
                    }
                } else {
                    channel = hfClient.newChannel(channelName);
                }

                for (int i = newChannel ? 1 : 0; i < ordererList.size(); i++) {
                    channel.addOrderer(ordererList.get(i));
                }

                if (!alreadyInstalled) {
                    for (Peer peer : peerList) {
                        channel.joinPeer(peer);
                    }
                } else {
                    for (Peer peer : peerList) {
                        channel.addPeer(peer);
                    }
                }


                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();

                for (JsonNode jsonNode : channelParameters.get("chaincodes")) {
                    String chaincodeKey = jsonNode.asText();

                    installChaincodes(hfClient, fabricConfig, installedChaincodes, peerList, chaincodeKey);

                    try {
                        fabricConfig.instantiateChaincode(hfClient, channel, chaincodeKey).get();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to process channel:" + channelName, e);
            }
        }
    }

    public void waitChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names, long seconds) throws Exception {

        class WaitContext {
            private final Set<String> chaincodes = new HashSet<>();
            private final Set<Peer> peers = new HashSet<>();
        }

        Map<String, WaitContext> waitContext = new HashMap<>();
        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();

            String adminKey = channelObject.getValue().get("admin").asText();
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);
            final Channel channel = hfc.getChannel(channelName);
            final WaitContext wc = waitContext.computeIfAbsent(channelName, (k) -> new WaitContext());

            wc.peers.addAll(channel.getPeers());

            for (JsonNode jsonNode : channelObject.getValue().get("chaincodes")) {
                String chaincodeName = jsonNode.asText();
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                String chaincodeKey = jsonNode.asText();
                wc.chaincodes.add(chaincodeKey);
            }
        }

        Instant start = Instant.now();

        while (true) {
            for (Iterator<Map.Entry<String, WaitContext>> iterator = waitContext.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<String, WaitContext> e = iterator.next();
                final String channelName = e.getKey();
                final WaitContext wc = e.getValue();

                if (wc.chaincodes.isEmpty()) {
                    iterator.remove();
                } else if (!wc.peers.isEmpty()) {
                    final Channel channel = hfc.getChannel(channelName);
                    for (Iterator<Peer> peerIterator = wc.peers.iterator(); peerIterator.hasNext(); ) {
                        final Peer peer = peerIterator.next();
                        final List<Query.ChaincodeInfo> chaincodeInfoList = channel.queryInstantiatedChaincodes(peer);
                        final Set<String> s = new HashSet(wc.chaincodes);
                        chaincodeInfoList.forEach((elem) -> s.remove(elem.getName()));
                        if (s.isEmpty()) {
                            peerIterator.remove();

                            System.out.printf("channel: %s, peer %s, chaincodes:\n", channelName, peer.getName());
                            for (Query.ChaincodeInfo ccinfo : chaincodeInfoList) {
                                if (wc.chaincodes.contains(ccinfo.getName()))
                                    System.out.printf("\t%s:%s\n", ccinfo.getName(), ccinfo.getVersion());
                            }
                        }
                    }
                }

                if (wc.peers.isEmpty())
                    iterator.remove();
            }

            if (waitContext.isEmpty())
                break;

            if (Duration.between(start, Instant.now()).compareTo(Duration.of(seconds, ChronoUnit.SECONDS)) >= 0) {
                System.err.println("Unable to wait for chaincodes:");
                for (Map.Entry<String, WaitContext> e : waitContext.entrySet()) {
                    final String channelName = e.getKey();
                    final WaitContext wc = e.getValue();
                    for (Peer peer : wc.peers) {
                        System.out.printf("channel %s, peer %s: ", channelName, peer.getName());

                        for (String s : wc.chaincodes) {
                            System.err.printf(" %s", s);
                        }
                    }

                    System.err.println();
                }
                break;
            }
            Thread.sleep(1000);
        }
    }

    public static void deployChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Set<String> installedChaincodes = new HashSet<>();
        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();

            String adminKey = channelObject.getValue().get("admin").asText();
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);
            final Channel channel = hfc.getChannel(channelName);
            final List<Peer> peers = new ArrayList<>(channel.getPeers());

            for (JsonNode jsonNode : channelObject.getValue().get("chaincodes")) {
                String chaincodeName = jsonNode.asText();
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                String chaincodeKey = jsonNode.asText();

                installChaincodes(hfc, fabricConfig, installedChaincodes, peers, chaincodeKey);

                fabricConfig.instantiateChaincode(hfc, channel, chaincodeKey).get();
            }
        }
    }

    public static void upgradeChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();

            String adminKey = channelObject.getValue().get("admin").asText();
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);

            for (JsonNode jsonNode : channelObject.getValue().get("chaincodes")) {
                String chaincodeName = jsonNode.asText();
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                Channel channel = hfc.getChannel(channelName);
                List<Peer> peers = new ArrayList<>(channel.getPeers());

                fabricConfig.upgradeChaincode(hfc, channel, peers, chaincodeName).get();
            }
        }
    }


    private static void installChaincodes(HFClient hfc, FabricConfig fabricConfig, Set<String> installedChaincodes, List<Peer> peers, String chaincodeKey) {
        for (Peer peer : peers) {
            String chaincodeInstallKey = chaincodeKey + "@" + peer.getName();
            if (!installedChaincodes.contains(chaincodeInstallKey)) {
                try {
                    fabricConfig.installChaincode(hfc, Collections.singletonList(peer), chaincodeKey);
                    installedChaincodes.add(chaincodeInstallKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
