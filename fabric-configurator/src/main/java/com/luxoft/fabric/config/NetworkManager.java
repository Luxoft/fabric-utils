package com.luxoft.fabric.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.utils.MiscUtils;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;

import static com.luxoft.fabric.utils.MiscUtils.runWithRetries;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class NetworkManager {
    static protected ConfigTxLator configTxLator = new ConfigTxLator();

    //taken from my first network sample
    static final int peerRetryDelaySec = 10;
    static final int peerRetryCount = 5;

    public static void configNetwork(final FabricConfig fabricConfig) {

        Iterator<JsonNode> channels = fabricConfig.getChannels();
        if (!channels.hasNext())
            throw new RuntimeException("Need at least one channel to do some work");

        while (channels.hasNext()) {
            Map.Entry<String, JsonNode> channelObject = channels.next().fields().next();
            String channelName = channelObject.getKey();
            JsonNode channelParameters = channelObject.getValue();
            try {
                String adminKey = channelParameters.get("admin").asText();
                final User fabricUser = fabricConfig.getAdmin(adminKey);

                HFClient hfClient = FabricConfig.createHFClient();
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
                Channel channelObj;
                String txFile = fabricConfig.getFileName(channelParameters, "txFile");
                if (!channelExists && txFile != null) {
                    try {
                        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, hfClient.getUserContext());
                        channelObj = hfClient.newChannel(channelName, ordererList.get(0), channelConfiguration, channelConfigurationSignature);
                        newChannel = true;
                    } catch (Exception ex) {
                        System.err.println("Exception happened while creating channel, this might not be a problem");
                        ex.printStackTrace();
                        //recreating orderer object as it may be consumed by try channel and will be destroyed on it`s GC
                        Orderer newOrderer = fabricConfig.getNewOrderer(hfClient, ordererList.get(0).getName());
                        ordererList.set(0, newOrderer);
                        channelObj = hfClient.newChannel(channelName);
                    }
                } else {
                    channelObj = hfClient.newChannel(channelName);
                }
                final Channel channel = channelObj;

                for (int i = newChannel ? 1 : 0; i < ordererList.size(); i++) {
                    channel.addOrderer(ordererList.get(i));
                }
                for (Peer peer : peerList) {
                    if (peersWithChannel.contains(peer))
                        channel.addPeer(peer);
                    else
                        runWithRetries(peerRetryCount, peerRetryDelaySec, () -> channel.joinPeer(peer));
                }

                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();
                Set<Query.ChaincodeInfo> chaincodeInfoList = new HashSet<>();
                for (Peer peer : peerList) {
                    Callable action = () -> chaincodeInfoList.addAll(channel.queryInstantiatedChaincodes(peer));
                    if (peersWithChannel.contains(peer))
                        action.call();
                    else
                        runWithRetries(peerRetryCount, peerRetryDelaySec, action);
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

    protected static Channel getChannel(final FabricConfig fabricConfig, String channelName) throws Exception {
        FabricConnector fabricConnector = new FabricConnector(fabricConfig, false);
        return fabricConfig.getChannel(fabricConnector.getHfClient(), channelName);
    }

    public static byte[] getChannelConfig(final FabricConfig fabricConfig, String channelName) throws Exception {
        return getChannel(fabricConfig, channelName).getChannelConfigurationBytes();
    }

    public static String getChannelConfigJson(final FabricConfig fabricConfig, String channelName) throws Exception {
        byte[] bytes = getChannelConfig(fabricConfig, channelName);
        return configTxLator.protoToJson("common.Config", bytes);
    }

    public static byte[] signChannelUpdateConfig(HFClient hfc, final FabricConfig fabricConfig, byte[] channelConfig, String userKey) throws Exception {
        UpdateChannelConfiguration channelConfigurationUpdate = new UpdateChannelConfiguration(channelConfig);
        User signingUser = fabricConfig.getAdmin(userKey);
        if (hfc.getUserContext() == null)
            hfc.setUserContext(signingUser);
        return hfc.getUpdateChannelConfigurationSignature(channelConfigurationUpdate, signingUser);
    }

    public static void setChannelConfig(final FabricConfig fabricConfig, String channelName, byte[] channelConfigurationUpdateBytes, byte[]... signatures) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        UpdateChannelConfiguration channelConfigurationUpdate = new UpdateChannelConfiguration(channelConfigurationUpdateBytes);
        channel.updateChannelConfiguration(channelConfigurationUpdate, signatures);
    }

    protected static byte[] generateAddCompanyToChannelUpdate(Channel channel, String companyName, byte[] companyConfigGroupBytes) throws Exception {
        byte[] channelConfigBytes = channel.getChannelConfigurationBytes();
        Configtx.Config.Builder targetChannelConfig = Configtx.Config.parseFrom(channelConfigBytes).toBuilder();
        Configtx.ConfigGroup companyConfigGroup = Configtx.ConfigGroup.parseFrom(companyConfigGroupBytes);

        final String targetGroup = "Application";
        Configtx.ConfigGroup.Builder channelGroup = targetChannelConfig.getChannelGroup().toBuilder();
        Configtx.ConfigGroup.Builder applicationConfigGroup = channelGroup.getGroupsOrThrow(targetGroup).toBuilder();

        applicationConfigGroup.putGroups(companyName, companyConfigGroup);
        channelGroup.putGroups(targetGroup, applicationConfigGroup.build());
        targetChannelConfig.setChannelGroup(channelGroup.build());

        return configTxLator.queryUpdateConfigurationBytes(channel.getName(), channelConfigBytes, targetChannelConfig.build().toByteArray());
    }

    public static byte[] updateChannel(final FabricConfig fabricConfig, String channelName, String targetChannelConfigJson) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        byte[] channelConfigBytes = channel.getChannelConfigurationBytes();
        byte[] targetChannelConfigBytes = configTxLator.jsonToProtoBytes("common.Config", targetChannelConfigJson);
        return configTxLator.queryUpdateConfigurationBytes(channel.getName(), channelConfigBytes, targetChannelConfigBytes);
    }

    public static byte[] addCompanyToChannel(final FabricConfig fabricConfig, String channelName, String companyName, String companyConfigGroupJson) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        byte[] companyConfigGroupBytes = configTxLator.jsonToProtoBytes("common.ConfigGroup", companyConfigGroupJson);
        return generateAddCompanyToChannelUpdate(channel, companyName, companyConfigGroupBytes);
    }

    public static byte[] addAnchorToChannel(byte[] anchorProto) throws Exception {
        return Configtx.ConfigUpdate.parseFrom(Configtx.ConfigUpdateEnvelope.parseFrom(Common.Payload.parseFrom(Common.Envelope.parseFrom(anchorProto).getPayload()).getData()).getConfigUpdate()).toByteArray();
    }

    public static byte[] setAppSingleAdmin(final FabricConfig fabricConfig, String channelName, String companyName) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        byte[] channelConfigBytes = channel.getChannelConfigurationBytes();
        Configtx.Config.Builder targetChannelConfig = Configtx.Config.parseFrom(channelConfigBytes).toBuilder();

        final String channelGroupName = "Application";
        final String policyGroupName = "Admins";
        Configtx.ConfigGroup.Builder channelGroup = targetChannelConfig.getChannelGroup().toBuilder();
        Configtx.ConfigGroup.Builder applicationConfigGroup = channelGroup.getGroupsOrThrow(channelGroupName).toBuilder();
        Configtx.ConfigGroup targetCompanyConfigGroup;
        if (companyName != null)
            targetCompanyConfigGroup = applicationConfigGroup.getGroupsOrThrow(companyName);
        else
            targetCompanyConfigGroup = applicationConfigGroup.getGroupsMap().entrySet().stream().findFirst().get().getValue();
        Configtx.ConfigPolicy targetPolicyConfig = targetCompanyConfigGroup.getPoliciesOrThrow(policyGroupName);

        applicationConfigGroup.putPolicies(policyGroupName, targetPolicyConfig);
        channelGroup.putGroups(channelGroupName, applicationConfigGroup.build());
        targetChannelConfig.setChannelGroup(channelGroup.build());
        return configTxLator.queryUpdateConfigurationBytes(channel.getName(), channelConfigBytes, targetChannelConfig.build().toByteArray());
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
