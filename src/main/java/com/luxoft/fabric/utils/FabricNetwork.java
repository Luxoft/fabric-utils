package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.util.*;

/**
 * Created by akopnin on 16.08.17.
 */
public class FabricNetwork {

    private HashMap<String, HFClient> channelClient  = new HashMap<>();

    public FabricNetwork(FabricConfig fabricConfig) throws CryptoException, InvalidArgumentException {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        if (!channels.hasNext())
            throw new RuntimeException("Need at least one channel to do some work");

        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        while(channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();
            String channelName = channelObject.getKey();
            JsonNode channelParameters = channelObject.getValue();
            try {
                String adminKey = channelParameters.get("admin").asText();
                final User fabricUser = fabricConfig.getAdmin(adminKey);

                HFClient hfClient = HFClient.createNewInstance();
                hfClient.setCryptoSuite(cryptoSuite);
                hfClient.setUserContext(fabricUser);

                // init channel
                fabricConfig.getChannel(hfClient, channelName);

                channelClient.put(channelName, hfClient);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to process channel:" + channelName);
            }
        }
    }

    public HFClient getClient(String channel) {
        return channelClient.get(channel);
    }
}
