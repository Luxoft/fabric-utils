package com.luxoft.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class Configurator {

    private static Reader getConfigReader() {
        try {
            return new FileReader("config.yaml");
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        FabricConfig fabricConfig = new FabricConfig(getConfigReader());

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

                String ordererName = channelParameters.get("orderer").asText();
                Orderer orderer = fabricConfig.getNewOrderer(hfClient, ordererName);

                Iterator<JsonNode> peers = channelParameters.get("peers").iterator();
                if (!peers.hasNext())
                    throw new RuntimeException("Peers list can`t be empty");
                List<Peer> peerList = new ArrayList<>();
                while (peers.hasNext()) {
                    String peerKey = peers.next().asText();
                    Peer peer = fabricConfig.getNewPeer(hfClient, peerKey);
                    peerList.add(peer);
                }

                String txFile = channelParameters.get("txFile").asText();
                ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, fabricUser);
                Channel channel = hfClient.newChannel(channelName, orderer, channelConfiguration, channelConfigurationSignature);
                channel.addOrderer(orderer);
                for (Peer peer : peerList) {
                    channel.joinPeer(peer);
                }
                channel.initialize();

                Iterator<JsonNode> chaincodes = channelParameters.get("chaincodes").iterator();
                while (chaincodes.hasNext()) {
                    String chaincodeKey = chaincodes.next().asText();
                    fabricConfig.deployChaincode(hfClient, channel, peerList, chaincodeKey);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to process channel:" + channelName);
            }
        }
    }

}
