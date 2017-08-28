package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.*;
import java.util.*;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class Configurator {

    private static final Logger logger = LoggerFactory.getLogger(Configurator.class);

    public static Reader getConfigReader(String configFile) {


        try {
            return new FileReader(configFile);
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public static final class Arguments {

        private final String var;

        public Arguments(final String var) {
            this.var = var;
        }

        public final String toString() { return var; }

        public final boolean equals(Arguments val) {
            return var.equals(val.toString());
        }

        public static final Arguments CONFIG  = new Arguments("config");
        public static final Arguments DEPLOY  = new Arguments("deploy");
        public static final Arguments UPGRADE = new Arguments("upgrade");
    }

    private void configNetwork(final FabricConfig fabricConfig) throws IOException {

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

                Iterator<JsonNode> eventhubs = channelParameters.get("eventhubs").iterator();
                List<EventHub> eventhubList = new ArrayList<>();
                while (eventhubs.hasNext()) {
                    String eventhubKey = eventhubs.next().asText();
                    EventHub eventhub = fabricConfig.getNewEventhub(hfClient, eventhubKey);
                    eventhubList.add(eventhub);
                }

                String txFile = channelParameters.get("txFile").asText();
                ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, fabricUser);
                Channel channel = hfClient.newChannel(channelName, orderer, channelConfiguration, channelConfigurationSignature);
                for (Peer peer : peerList) {
                    channel.joinPeer(peer);
                }
                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();

                for (JsonNode jsonNode : channelParameters.get("chaincodes")) {
                    String chaincodeKey = jsonNode.asText();
                    fabricConfig.instantiateChaincode(hfClient, channel, peerList, chaincodeKey);
                }
            } catch (Exception e) {
               logger.error("Failed to process channel:" + channelName, e);
            }
        }
    }

    private void deployChancodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

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

                fabricConfig.instantiateChaincode(hfc, channel, peers, chaincodeName);
            }
        }
    }

    private void upgradeChancodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

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

                fabricConfig.upgradeChaincode(hfc, channel, peers, chaincodeName);
            }
        }
    }

    public static void main(String[] args) throws Exception {

        OptionParser parser = new OptionParser();
        OptionSpec<Arguments> type = parser.accepts("type").withRequiredArg().ofType(Arguments.class);
        OptionSpec<String> name   = parser.accepts("name").withOptionalArg().ofType(String.class);
        OptionSpec<String> config = parser.accepts("config").withOptionalArg().ofType(String.class);

        OptionSet options = parser.parse(args);
        Arguments mode = options.valueOf(type);

        Configurator cfg = new Configurator();

        FabricConfig fabricConfig = new FabricConfig(getConfigReader(options.has(config)
                ? options.valueOf(config) : "fabric.yaml"));


        if(!options.has(type) || mode.equals(Arguments.CONFIG))
            cfg.configNetwork(fabricConfig);
        else {

            HFClient hfClient = HFClient.createNewInstance();
            hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            Set<String> names = options.has(name)
                    ? new HashSet<>(options.valuesOf(name))
                    : Collections.EMPTY_SET;

            if (mode.equals(Arguments.DEPLOY))
                cfg.deployChancodes(hfClient, fabricConfig, names);
            else if (mode.equals(Arguments.UPGRADE))
                cfg.upgradeChancodes(hfClient, fabricConfig, names);
        }
    }
}
