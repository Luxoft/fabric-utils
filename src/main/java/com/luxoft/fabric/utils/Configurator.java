package com.luxoft.fabric.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.io.*;
import java.util.*;
import java.util.logging.Level;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class Configurator {

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

    private void configNetwork(final FabricNetwork network, final FabricConfig fabricConfig) throws IOException {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        if (!channels.hasNext())
            throw new RuntimeException("Need at least one channel to do some work");

        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();
            String channelName = channelObject.getKey();
            JsonNode channelParameters = channelObject.getValue();

            HFClient hfc = network.getClient(channelName);
            try {
                Orderer orderer  = network.getOrderer(channelName);
                List<Peer> peers = network.getPeers(channelName);

                Iterator<JsonNode> eventhubs = channelParameters.get("eventhubs").iterator();
                List<EventHub> eventhubList = new ArrayList<>();
                while (eventhubs.hasNext()) {
                    String eventhubKey = eventhubs.next().asText();
                    EventHub eventhub = fabricConfig.getNewEventhub(hfc , eventhubKey);
                    eventhubList.add(eventhub);
                }

                String txFile = channelParameters.get("txFile").asText();
                ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                byte[] channelConfigurationSignature = hfc.getChannelConfigurationSignature(channelConfiguration, hfc.getUserContext());
                Channel channel = hfc.newChannel(channelName, orderer, channelConfiguration, channelConfigurationSignature);

                for (Peer peer : peers) {
                    channel.joinPeer(peer);
                }
                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();

                Iterator<JsonNode> chaincodes = channelParameters.get("chaincodes").iterator();
                while (chaincodes.hasNext()) {
                    String chaincodeKey = chaincodes.next().asText();
                    fabricConfig.instantiateChaincode(hfc, channel, new ArrayList(channel.getPeers()), chaincodeKey);
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Failed to process channel:" + channelName);
            }
        }
    }

    private void deployChancodes(final FabricNetwork network, final FabricConfig fabricConfig, Set<String> names)
            throws IOException, ChaincodeEndorsementPolicyParseException, ProposalException, InvalidArgumentException {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while(channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();
            Iterator<JsonNode> chaincodes = channelObject.getValue().get("chaincodes").iterator();

            while(chaincodes.hasNext()) {
                String chaincodeName = chaincodes.next().asText();
                if(!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                HFClient hfc = network.getClient(channelName);
                Channel channel  = hfc.getChannel(channelName);
                List<Peer> peers = network.getPeers(channelName);

                fabricConfig.instantiateChaincode(hfc, channel, peers, chaincodeName);
            }
        }
    }

    private void upgradeChancodes(final FabricNetwork network, final FabricConfig fabricConfig, Set<String> names)
            throws IOException, ChaincodeEndorsementPolicyParseException, ProposalException, InvalidArgumentException {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        while(channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();

            String channelName = channelObject.getKey();
            Iterator<JsonNode> chaincodes = channelObject.getValue().get("chaincodes").iterator();

            while(chaincodes.hasNext()) {
                String chaincodeName = chaincodes.next().asText();
                if(!names.isEmpty()) {
                    if (!names.contains(chaincodeName)) continue;
                }

                HFClient hfc = network.getClient(channelName);
                Channel channel  = hfc.getChannel(channelName);
                List<Peer> peers = network.getPeers(channelName);

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
                ? options.valueOf(config) : "config.yaml"));

        FabricNetwork network = new FabricNetwork(fabricConfig);

        if(!options.has(type) || mode.equals(Arguments.CONFIG))
            cfg.configNetwork(network, fabricConfig);
        else {
            Set<String> names = options.has(name)
                    ? new HashSet<>(options.valuesOf(name))
                    : Collections.EMPTY_SET;

            if (mode.equals(Arguments.DEPLOY))
                cfg.deployChancodes(network, fabricConfig, names);
            else if (mode.equals(Arguments.UPGRADE))
                cfg.upgradeChancodes(network, fabricConfig, names);
        }
    }
}
