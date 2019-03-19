package com.luxoft.fabric.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.impl.FabricConnectorImplBasedOnFabricConfig;
import com.luxoft.fabric.model.ConfigData;
import com.luxoft.fabric.utils.MiscUtils;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;

import static com.luxoft.fabric.FabricConfig.getOrDefault;
import static com.luxoft.fabric.FabricConfig.getOrThrow;
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
        configNetwork(fabricConfig, false);
    }

    public boolean configNetwork(final FabricConfig fabricConfig, boolean skipUnauth, boolean waitChaincodes, int waitChaincodesTimeout) throws Exception {
        configNetwork(fabricConfig, skipUnauth);
        if (waitChaincodes)
            return waitChaincodes(FabricConnector.createHFClient(), fabricConfig, Collections.emptySet(), waitChaincodesTimeout, skipUnauth);
        return true;
    }

    public static void configNetwork(final FabricConfig fabricConfig, boolean skipUnauth) {

        Map<String, ConfigData.Channel> channels = fabricConfig.getChannels();
        if (channels.isEmpty())
            throw new RuntimeException("Need at least one channel to do some work");

        for (Map.Entry<String, ConfigData.Channel> channelObject : channels.entrySet()) {
            String channelName = channelObject.getKey();
            ConfigData.Channel channelParameters = channelObject.getValue();
            try {
                String adminKey = getOrThrow(channelParameters.admin, String.format("channel[%s].admin", channelName));
                final User fabricUser = fabricConfig.getAdmin(adminKey);

                HFClient hfClient = FabricConnector.createHFClient();
                hfClient.setUserContext(fabricUser);

                final List<Orderer> ordererList = fabricConfig.getOrdererList(hfClient, channelParameters);
                final List<Peer> peerList = fabricConfig.getPeerList(hfClient, channelParameters);
                final List<EventHub> eventhubList = fabricConfig.getEventHubList(hfClient, channelParameters);

                //Looking for channels on peers, to find has already joined
                Set<Peer> peersWithChannel = new HashSet<>();
                List<Peer> ownPeers = new LinkedList<>(peerList);
                boolean channelExists = false;
                for (Peer peer : peerList) {
                    try {
                        Set<String> joinedChannels = hfClient.queryChannels(peer);
                        if (joinedChannels.stream().anyMatch(installedChannelName -> installedChannelName.equalsIgnoreCase(channelName))) {
                            channelExists = true;
                            peersWithChannel.add(peer);
                        }
                    } catch (ProposalException ex) {
                        if (skipUnauth && ex.getLocalizedMessage().contains("description=access denied")) {
                            System.err.println("Access denied exception happened while querying channels from peer " + peer.getName() + ", this is OK with external peers");
                            peersWithChannel.add(peer);
                            ownPeers.remove(peer);
                            continue;
                        }
                        throw ex;
                    }
                }

                boolean newChannel = false;
                Channel channelObj;
                String txFile = fabricConfig.getFileName(channelParameters.txFile);
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
                for (Peer peer : ownPeers) {
                    Callable action = () -> chaincodeInfoList.addAll(channel.queryInstantiatedChaincodes(peer));
                    if (peersWithChannel.contains(peer))
                        action.call();
                    else
                        runWithRetries(peerRetryCount, peerRetryDelaySec, action);
                }

                final List<ConfigData.ChannelChaincode> chaincodes = getOrDefault(channelParameters.chaincodes, Collections.emptyList());
                for (ConfigData.ChannelChaincode channelChaincode : chaincodes) {

                    final String chaincodeKey = channelChaincode.name;
                    try {
                        final JsonNode channelSpecificConfig = channelChaincode.collectionPolicy;

                    ChaincodeID chaincodeID = fabricConfig.getChaincodeID(chaincodeKey);

                    installChaincodes(hfClient, fabricConfig, ownPeers, chaincodeKey);
                    if (chaincodeInfoList.stream().anyMatch(chaincodeInfo -> MiscUtils.equals(chaincodeID, chaincodeInfo))) {
                        System.out.println("Chaincode(" + chaincodeKey + ") was already instantiated, skipping");
                        continue;
                    }
                    try {
                        fabricConfig.instantiateChaincode(hfClient, channel, chaincodeKey, channelSpecificConfig).get();
                    } catch (Exception e) {
                        e.printStackTrace();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(String.format("Unable to process chaincode '%d'", chaincodeKey), e);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to process channel:" + channelName, e);
            }
        }
    }

    public boolean waitChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names, long seconds, boolean skipUnauth) throws Exception {

        class WaitContext {
            private final Set<String> chaincodes = new HashSet<>();
            private final Set<Peer> peers = new HashSet<>();
        }

        Map<String, WaitContext> waitContext = new HashMap<>();
        Map<String, ConfigData.Channel> channels = getOrDefault(fabricConfig.getChannels(), Collections.emptyMap());

        for (Map.Entry<String, ConfigData.Channel> channelObject : channels.entrySet()) {
            String channelName = channelObject.getKey();
            ConfigData.Channel channelValue = channelObject.getValue();

            String adminKey = getOrThrow(channelValue.admin, String.format("chaincode[%s].admin", channelName));
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);
            final Channel channel = hfc.getChannel(channelName);
            final WaitContext wc = waitContext.computeIfAbsent(channelName, (k) -> new WaitContext());

            wc.peers.addAll(channel.getPeers());

            final List<ConfigData.ChannelChaincode> channelChaincodes = getOrDefault(channelValue.chaincodes, Collections.emptyList());
            for (ConfigData.ChannelChaincode channelChaincode : channelChaincodes) {
                final String chaincodeName = getOrThrow(channelChaincode.name, String.format("channel[%s].chaincode[?].name", channelName));
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName))
                        continue;
                }

                wc.chaincodes.add(fabricConfig.getChaincodeID(chaincodeName).getName());
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
                        if (skipUnauth) {
                            try {
                                hfc.queryChannels(peer);
                            } catch (ProposalException ex) {
                                if (ex.getLocalizedMessage().contains("description=access denied")) {
                                    System.out.println("Access denied exception happened while querying channels from peer " + peer.getName() + ", this is OK with external peers");
                                    peerIterator.remove();
                                    continue;
                                }
                                throw ex;
                            }
                        }
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
                return false;
            }
            Thread.sleep(1000);
            skipUnauth = false;
        }
        return true;
    }

    public static void deployChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Map<String, ConfigData.Channel> channels = fabricConfig.getChannels();
        for (Map.Entry<String, ConfigData.Channel> channelEntry : channels.entrySet()) {
            final String channelName = channelEntry.getKey();
            final ConfigData.Channel channelInfo = channelEntry.getValue();

            String adminKey = getOrThrow(channelInfo.admin, String.format("channels[%s].admin", channelName));
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);
            final Channel channel = hfc.getChannel(channelName);
            final List<Peer> peers = new ArrayList<>(channel.getPeers());

            final List<ConfigData.ChannelChaincode> channelChaincodes = getOrDefault(channelInfo.chaincodes, Collections.emptyList());
            for (ConfigData.ChannelChaincode channelChaincode : channelChaincodes) {
                final String chaincodeName = getOrThrow(channelChaincode.name, String.format("channels[%s].chaincodes[?].name", channelName));
                final JsonNode channelConfig = channelChaincode.collectionPolicy;

                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName))
                        continue;
                }

                installChaincodes(hfc, fabricConfig, peers, chaincodeName);
                fabricConfig.instantiateChaincode(hfc, channel, chaincodeName, null).get();
            }
        }
    }

    public static void upgradeChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws Exception {

        Map<String, ConfigData.Channel> channels = fabricConfig.getChannels();
        for (Map.Entry<String, ConfigData.Channel> channelEntry : channels.entrySet()) {

            String channelName = channelEntry.getKey();
            ConfigData.Channel channelInfo = channelEntry.getValue();

            String adminKey = getOrThrow(channelInfo.admin, String.format("channel[%s].admin", channelName));
            final User fabricUser = fabricConfig.getAdmin(adminKey);
            hfc.setUserContext(fabricUser);

            fabricConfig.getChannel(hfc, channelName, null);

            final List<ConfigData.ChannelChaincode> channelChaincodes = getOrDefault(channelInfo.chaincodes, Collections.emptyList());
            for (ConfigData.ChannelChaincode channelChaincode : channelChaincodes) {
                String chaincodeName = channelChaincode.name;
                if (!names.isEmpty()) {
                    if (!names.contains(chaincodeName))
                        continue;
                }

                Channel channel = hfc.getChannel(channelName);
                List<Peer> peers = new ArrayList<>(channel.getPeers());

                fabricConfig.upgradeChaincode(hfc, channel, peers, chaincodeName).get();
            }
        }
    }

    protected static Channel getChannel(final FabricConfig fabricConfig, String channelName) throws Exception {

        FabricConnector fabricConnector = FabricConnector.getFabricConfigBuilder(fabricConfig).build();

        return fabricConfig.getChannel(fabricConnector.getHfClient(), channelName, null);
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


    @SuppressWarnings("unused")
    public static void deployChaincode(FabricConnectorImplBasedOnFabricConfig fabricConnector, String chaincodeName) throws Exception {
        deployChaincode(fabricConnector, chaincodeName, fabricConnector.getDefaultChannel().getName());
    }

    @SuppressWarnings("unused")
    public static void upgradeChaincode(FabricConnectorImplBasedOnFabricConfig fabricConnector, String chaincodeName) throws Exception {
        upgradeChaincode(fabricConnector, chaincodeName, fabricConnector.getDefaultChannel().getName());
    }


    public static void deployChaincode(FabricConnectorImplBasedOnFabricConfig fabricConnector, String chaincodeName, String channelName) throws Exception {
        HFClient hfClient = fabricConnector.getHfClient();
        Channel channel = hfClient.getChannel(channelName);
        if (channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConnector.getFabricConfig().installChaincode(hfClient, new ArrayList<>(channel.getPeers()), chaincodeName);
        fabricConnector.getFabricConfig().instantiateChaincode(hfClient, channel, chaincodeName, null);
    }

    public static void upgradeChaincode(FabricConnectorImplBasedOnFabricConfig fabricConnector, String chaincodeName, String channelName) throws Exception {
        HFClient hfClient = fabricConnector.getHfClient();
        Channel channel = hfClient.getChannel(channelName);
        if (channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConnector.getFabricConfig().upgradeChaincode(hfClient, channel, new ArrayList<>(channel.getPeers()), chaincodeName);
    }


}
