package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
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

//                Channel channel = fabricConfig.generateChannel(hfClient, channelName, fabricUser, orderer);

                Set<String> installedChannels = hfClient.queryChannels(peerList.get(0));
                boolean alreadyInstalled = false;

                for( String installedChannelName : installedChannels) {
                    if (installedChannelName.equalsIgnoreCase(channelName)) alreadyInstalled = true;
                }

                boolean newChannel = false;
                Channel channel;
                if(!alreadyInstalled) {
                    try {
                        String txFile = fabricConfig.getFileName(channelParameters, "txFile");
                        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, hfClient.getUserContext());
                        channel = hfClient.newChannel(channelName, ordererList.get(0), channelConfiguration, channelConfigurationSignature);
                        newChannel = true;
                    } catch (Exception ex) {
                        channel = hfClient.newChannel(channelName);
                    }
                } else {
                    channel = hfClient.newChannel(channelName);
                }

                for (int i = newChannel?1:0; i < ordererList.size(); i++) {
                    channel.addOrderer(ordererList.get(i));
                }

                if(newChannel) {
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

    public static void deployChancodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Set<String> installedChaincodes = new HashSet<>();
        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while(channels.hasNext()) {
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

                installChaincodes(hfc, fabricConfig, installedChaincodes, peers, chaincodeKey);

                fabricConfig.instantiateChaincode(hfc, channel, chaincodeKey).get();
            }
        }
    }

    public static void upgradeChancodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while(channels.hasNext()) {
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


    private static void installChaincodes(HFClient hfc, FabricConfig fabricConfig, Set<String> installedChaincodes, List<Peer> peers, String chaincodeKey) throws InvalidArgumentException, ProposalException {
        for (Peer peer: peers) {
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
