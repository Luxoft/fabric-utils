package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class NetworkManager {
    public static void configNetwork(final FabricConfig fabricConfig) throws IOException {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        if (!channels.hasNext())
            throw new RuntimeException("Need at least one channel to do some work");

        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();

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

                Iterator<JsonNode> orderers = channelParameters.get("orderers").iterator();
                if (!orderers.hasNext())
                    throw new RuntimeException("Orderers list can`t be empty");
                List<Orderer> ordererList = new ArrayList<>();
                while (orderers.hasNext()) {
                    String ordererKey = orderers.next().asText();
                    Orderer orderer = fabricConfig.getNewOrderer(hfClient, ordererKey);
                    ordererList.add(orderer);
                }

                Iterator<JsonNode> peers = channelParameters.get("peers").iterator();
                if (!peers.hasNext())
                    throw new RuntimeException("Peers list can`t be empty");
                List<Peer> peerList = new ArrayList<>();
                while (peers.hasNext()) {
                    String peerKey = peers.next().asText();
                    Peer peer = fabricConfig.getNewPeer(hfClient, peerKey);
                    peerList.add(peer);
                }

                Iterator<JsonNode> eventhubs = channelParameters.get("eventhubs").iterator();
                List<EventHub> eventhubList = new ArrayList<>();
                while (eventhubs.hasNext()) {
                    String eventhubKey = eventhubs.next().asText();
                    EventHub eventhub = fabricConfig.getNewEventhub(hfClient, eventhubKey);
                    eventhubList.add(eventhub);
                }

                //Looking for channels on peers, to find has already joined
                Set<Peer> peersWithChannel = new HashSet<>();
                boolean channelExists = false;
                for (Peer peer : peerList) {
                    Set<String> joinedChannels = hfClient.queryChannels(peer);
                    if (joinedChannels.stream().anyMatch(installedChannelName -> installedChannelName.equalsIgnoreCase(channelName))) {
                        channelExists = true;
                        peersWithChannel.add(peer);
                    }
                }

                boolean newChannel = false;
                Channel channel;
                if (!channelExists) {
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
                for (Peer peer : peerList) {
                    if (peersWithChannel.contains(peer))
                        channel.addPeer(peer);
                    else
                        channel.joinPeer(peer);
                }

                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();
                List<Query.ChaincodeInfo> chaincodeInfoList = new ArrayList<>();
                for (Peer peer : peerList) {
                    chaincodeInfoList.addAll(channel.queryInstantiatedChaincodes(peer));
                }

                for (JsonNode jsonNode : channelParameters.get("chaincodes")) {
                    String chaincodeKey = jsonNode.asText();
                    ChaincodeID chaincodeID = fabricConfig.getChaincodeID(chaincodeKey);

                    installChaincodes(hfClient, fabricConfig, peerList, chaincodeKey);
                    if (chaincodeInfoList.stream().anyMatch(chaincodeInfo -> MiscUtils.equals(chaincodeID, chaincodeInfo))) {
                        System.out.println("Chaincode(" + chaincodeKey + ") was already instantiated, skipping");
                        continue;
                    }
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

    public static void deployChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();

            String adminKey = channelObject.getValue().get("admin").asText();
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName);

            for (JsonNode jsonNode : channelObject.getValue().get("chaincodes")) {
                String chaincodeName = jsonNode.asText();
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                Channel channel = hfc.getChannel(channelName);
                List<Peer> peers = new ArrayList<>(channel.getPeers());

                String chaincodeKey = jsonNode.asText();

                installChaincodes(hfc, fabricConfig, peers, chaincodeKey);

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

            fabricConfig.getChannel(hfc, channelName);

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

    public static byte[] getChannelConfig(final FabricConfig fabricConfig, String channelName) throws Exception {
        FabricConnector fabricConnector = new FabricConnector(fabricConfig, false);
        return fabricConfig.getChannel(fabricConnector.getHfClient(), channelName).getChannelConfigurationBytes();
    }

    private static void installChaincodes(HFClient hfc, FabricConfig fabricConfig, List<Peer> peers, String chaincodeKey) throws InvalidArgumentException, ProposalException {
        ChaincodeID chaincodeID = fabricConfig.getChaincodeID(chaincodeKey);
        for (Peer peer : peers) {
            List<Query.ChaincodeInfo> peerInstallerChaincodes = hfc.queryInstalledChaincodes(peer);
            if (peerInstallerChaincodes.stream().noneMatch(installedChaincode -> MiscUtils.equals(chaincodeID, installedChaincode))) {
                try {
                    fabricConfig.installChaincode(hfc, Collections.singletonList(peer), chaincodeKey);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
