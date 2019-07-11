package com.luxoft.fabric.configurator;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.config.ConfigAdapter;
import com.luxoft.fabric.model.ConfigData;
import com.luxoft.fabric.model.ExtendedPeer;
import com.luxoft.fabric.utils.MiscUtils;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.common.Configtx;
import org.hyperledger.fabric.protos.peer.Query;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final Logger logger = LoggerFactory.getLogger(NetworkManager.class);

    public static boolean configNetwork(final FabricConfig fabricConfig, boolean waitChaincodes, int waitChaincodesTimeout) throws Exception {
        configNetwork(fabricConfig);
        if (waitChaincodes)
            return waitChaincodes(FabricConnector.createHFClient(), fabricConfig, Collections.emptySet(), waitChaincodesTimeout);
        return true;
    }

    public static void configNetwork(final FabricConfig fabricConfig) {

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

                final List<Orderer> ordererList = fabricConfig.getOrdererList(hfClient, channelName, channelParameters);
                final List<ExtendedPeer> peerList = fabricConfig.getPeerList(hfClient, channelParameters);
                final List<EventHub> eventhubList = fabricConfig.getEventHubList(hfClient, channelParameters);


                //Looking for channels on peers, to find has already joined
                Set<ExtendedPeer> assumedPeersWithChannel = new HashSet<>();
                List<Peer> ownPeers = peerList.stream().filter(p -> !p.isExternal()).map(p -> p.getPeer()).collect(Collectors.toList());
                boolean channelExistsOnPeers = false;
                for (ExtendedPeer extendedPeer : peerList) {
                    if (!extendedPeer.isExternal()) {
                        Set<String> joinedChannels = hfClient.queryChannels(extendedPeer.getPeer());
                        if (joinedChannels.stream().anyMatch(installedChannelName -> installedChannelName.equalsIgnoreCase(channelName))) {
                            channelExistsOnPeers = true;
                            assumedPeersWithChannel.add(extendedPeer);
                        }
                    } else {
                        // Here we assume that external peer belong to the channel. In case it is not - the ap will fail later.
                        assumedPeersWithChannel.add(extendedPeer);
                    }
                }

                boolean newChannel = false;
                Channel channelObj;
                String txFile = fabricConfig.getFileName(channelParameters.txFile);

                if (!channelExistsOnPeers && txFile != null) {
                    try {
                        ChannelConfiguration channelConfiguration = new ChannelConfiguration(new File(txFile));
                        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, hfClient.getUserContext());
                        channelObj = hfClient.newChannel(channelName, ordererList.get(0), channelConfiguration, channelConfigurationSignature);
                        newChannel = true;
                    } catch (TransactionException ex) {

                        logger.warn("This is OK if the channel already exists in orderer", ex);
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
                for (ExtendedPeer extendedPeer : peerList) {
                    if (assumedPeersWithChannel.contains(extendedPeer))
                        channel.addPeer(extendedPeer.getPeer());
                    else
                        runWithRetries(peerRetryCount, peerRetryDelaySec, () -> channel.joinPeer(extendedPeer.getPeer()));
                }

                for (EventHub eventhub : eventhubList) {
                    channel.addEventHub(eventhub);
                }
                channel.initialize();
                Set<Query.ChaincodeInfo> instantiatedChaincodesInfoSet = new HashSet<>();
                for (ExtendedPeer extendedPeer : assumedPeersWithChannel) {
                    try {
                        List<Query.ChaincodeInfo> chaincodeInfos = channel.queryInstantiatedChaincodes(extendedPeer.getPeer());
                        instantiatedChaincodesInfoSet.addAll(chaincodeInfos);
                    } catch (ProposalException e) {
                        if (extendedPeer.isExternal()) {
                            throw new RuntimeException("Assumption was wrong. External peer does not belong to the channel. Remove it from channel configuration section or join it to the channel", e);
                        } else {
                            throw new RuntimeException("Inknown issue happened with our peer", e);
                        }
                    }
                }

                logger.info("Found instantiated chaincodes: {}", instantiatedChaincodesInfoSet.size());

                final List<ConfigData.ChannelChaincode> chaincodes = getOrDefault(channelParameters.chaincodes, Collections.emptyList());
                for (ConfigData.ChannelChaincode channelChaincode : chaincodes) {

                    final String chaincodeKey = channelChaincode.name;
                    try {
                        final JsonNode channelSpecificConfig = channelChaincode.collectionPolicy;

                        ChaincodeID chaincodeID = fabricConfig.getChaincodeID(chaincodeKey);

                        installChaincodes(hfClient, fabricConfig, ownPeers, chaincodeKey);
                        if (instantiatedChaincodesInfoSet.stream().anyMatch(chaincodeInfo -> MiscUtils.equals(chaincodeID, chaincodeInfo))) {
                            logger.info("Chaincode {} was already instantiated, skipping", chaincodeKey);
                            continue;
                        }

                        fabricConfig.instantiateChaincode(hfClient, channel, chaincodeKey, channelSpecificConfig).get();

                    } catch (Exception e) {
                        logger.error("Exception during chaincode instantiation", e);
                        throw new RuntimeException(String.format("Unable to process chaincode '%s'", chaincodeKey), e);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to process channel:" + channelName, e);
            }
        }
    }

    public static boolean waitChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names, long seconds) throws Exception {

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

            final Set<String> notOurOrgPeerNames = fabricConfig.getPeerList(hfc, channelValue).stream().filter(p -> p.isExternal()).map(p -> p.getPeer().getName()).collect(Collectors.toSet());
            wc.peers.addAll(channel.getPeers().stream().filter(p -> !notOurOrgPeerNames.contains(p.getName())).collect(Collectors.toSet()));

            //TODO: The behaviour is questionable.
            // In case we pass some non-existing chaincode, wait will be finished succesfully.
            // I would expect it to throw an error
            // As a fix I suggest specifying channel name explicitly with this command, and thus we can throw the error if chaincode is not present on this channel
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
        }
        return true;
    }

    public static void deployChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws
            Exception {

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

    public static void upgradeChaincodes(HFClient hfc, final FabricConfig fabricConfig, Set<String> names) throws
            Exception {

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

        FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(fabricConfig).build());
        return fabricConnector.getChannel(channelName);
    }

    public static byte[] getChannelConfig(final FabricConfig fabricConfig, String channelName) throws Exception {
        return getChannel(fabricConfig, channelName).getChannelConfigurationBytes();
    }

    public static String getChannelConfigJson(final FabricConfig fabricConfig, String channelName) throws
            Exception {
        byte[] bytes = getChannelConfig(fabricConfig, channelName);
        return configTxLator.protoToJson("common.Config", bytes);
    }

    public static byte[] signChannelUpdateConfig(HFClient hfc, final FabricConfig fabricConfig,
                                                 byte[] channelConfig, String userKey) throws Exception {
        UpdateChannelConfiguration channelConfigurationUpdate = new UpdateChannelConfiguration(channelConfig);
        User signingUser = fabricConfig.getAdmin(userKey);
        if (hfc.getUserContext() == null)
            hfc.setUserContext(signingUser);
        return hfc.getUpdateChannelConfigurationSignature(channelConfigurationUpdate, signingUser);
    }

    public static void setChannelConfig(final FabricConfig fabricConfig, String channelName,
                                        byte[] channelConfigurationUpdateBytes, byte[]... signatures) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        UpdateChannelConfiguration channelConfigurationUpdate = new UpdateChannelConfiguration(channelConfigurationUpdateBytes);
        channel.updateChannelConfiguration(channelConfigurationUpdate, signatures);
    }

    protected static byte[] generateAddCompanyToChannelUpdate(Channel channel, String companyName,
                                                              byte[] companyConfigGroupBytes) throws Exception {
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

    public static byte[] updateChannel(final FabricConfig fabricConfig, String channelName, String
            targetChannelConfigJson) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        byte[] channelConfigBytes = channel.getChannelConfigurationBytes();
        byte[] targetChannelConfigBytes = configTxLator.jsonToProtoBytes("common.Config", targetChannelConfigJson);
        return configTxLator.queryUpdateConfigurationBytes(channel.getName(), channelConfigBytes, targetChannelConfigBytes);
    }

    public static byte[] addCompanyToChannel(final FabricConfig fabricConfig, String channelName, String
            companyName, String companyConfigGroupJson) throws Exception {
        Channel channel = getChannel(fabricConfig, channelName);
        byte[] companyConfigGroupBytes = configTxLator.jsonToProtoBytes("common.ConfigGroup", companyConfigGroupJson);
        return generateAddCompanyToChannelUpdate(channel, companyName, companyConfigGroupBytes);
    }

    public static byte[] addAnchorToChannel(byte[] anchorProto) throws Exception {
        return Configtx.ConfigUpdate.parseFrom(Configtx.ConfigUpdateEnvelope.parseFrom(Common.Payload.parseFrom(Common.Envelope.parseFrom(anchorProto).getPayload()).getData()).getConfigUpdate()).toByteArray();
    }

    public static byte[] setAppSingleAdmin(final FabricConfig fabricConfig, String channelName, String companyName) throws
            Exception {
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

    private static void installChaincodes(HFClient hfc, FabricConfig fabricConfig, List<Peer> peers, String
            chaincodeKey) throws InvalidArgumentException, ProposalException {
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
