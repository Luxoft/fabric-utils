/*
 * Copyright (C) Luxoft 2017
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Created by ADoroganov on 25.07.2017.
 */


package com.luxoft.fabric;

import com.fasterxml.jackson.databind.*;
import com.luxoft.fabric.model.ConfigData;
import com.luxoft.fabric.model.FileReference;
import com.luxoft.fabric.model.jackson.ConfigModule;
import com.luxoft.fabric.utils.ConfigGenerator;
import com.luxoft.fabric.utils.MiscUtils;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import java.io.*;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class FabricConfig {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ConfigGenerator configGenerator = new ConfigGenerator();
    private final String confDir;
    private final ConfigData.Root config;

    static {
        //loading Fabric security provider to the system
        getCryptoSuite();
    }

    public static CryptoSuite getCryptoSuite() {
        try {
            return CryptoSuite.Factory.getCryptoSuite();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public FabricConfig(Reader configReader) throws IOException {
        this(configReader, null);
    }

    public FabricConfig(Reader configReader, String confDir) throws IOException {
        this.confDir = confDir == null ? "." : confDir;
        if (configReader == null) {
            config = null;
        } else {
            ObjectMapper mapper = new ObjectMapper();
            final YamlConfig yamlConfig = new YamlConfig(configReader);
            final JsonNode root = yamlConfig.getRoot();
            ConfigModule.configure(mapper);
            config = mapper.treeToValue(root, ConfigData.Root.class);
        }
    }

    public ConfigData.Root getRoot() {
        return config;
    }

    public Map<String, ConfigData.Channel> getChannels() {
        final Map<String, ConfigData.Channel> channels = getRoot().channels;
        if (channels == null)
            return Collections.emptyMap();
        else
            return channels;
    }

    public ConfigData.Channel getChannelDetails(String key) {
        return getRoot().channels.get(key);
    }

    public ConfigData.Peer getPeerDetails(String key) {
        return getRoot().peers.get(key);
    }

    protected List<String> getElementKeys(Map<String, ?> collection) {
        if (collection == null)
            return Collections.emptyList();
        return new LinkedList<>(collection.keySet());
    }

    public List<String> getPeersKeys() {
        return getElementKeys(getRoot().peers);
    }

    public List<String> getOrderersKeys() {
        return getElementKeys(getRoot().orderers);
    }

    public List<String> getEventHubsKeys() {
        return getElementKeys(getRoot().eventhubs);
    }

    public List<String> getChaincodesKeys() {
        return getElementKeys(getRoot().chaincodes);
    }

    public List<String> getChannelsKeys() {
        return getElementKeys(getRoot().channels);
    }

    public List<String> getCAsKeys() {
        return getElementKeys(getRoot().cas);
    }

    public List<String> getAdminsKeys() {
        return getElementKeys(getRoot().admins);
    }

    public ConfigData.Eventhub getEventhubDetails(String key) {
        return getRoot().eventhubs.get(key);
    }

    public ConfigData.Orderer getOrdererDetails(String key) {
        return getRoot().orderers.get(key);
    }

    public ConfigData.Chaincode getChaincodeDetails(String key) {
        return getRoot().chaincodes.get(key);
    }

    public ConfigData.Admin getAdminDetails(String key) {
        return getRoot().admins.get(key);
    }

    public ConfigData.CA getCADetails(String key) {
        return getRoot().cas.get(key);
    }

    public ConfigData.Users getUsersDetails() {
        return getRoot().users;
    }

    public String getFileName(FileReference fileReference, String defaultValue) {
        String value;
        if (fileReference == null) {
            if (defaultValue == null || defaultValue.isEmpty())
                return defaultValue;
            value = defaultValue;
        } else
            value = fileReference.asString();
        return MiscUtils.resolveFile(value, confDir);
    }

    public String getFileName(FileReference fileReference) {
        return getFileName(fileReference, null);
    }

    public User getAdmin(String key) throws Exception {
        ConfigData.Admin adminParameters = requireNonNull(getAdminDetails(key));

        String adminName = adminParameters.name;
        String adminMspID = adminParameters.mspID;
        String adminCert = getFileName(adminParameters.cert);
        String adminPrivateKey = getFileName(adminParameters.privateKey);

        Enrollment enrollment = FabricConfig.createEnrollment(new FileInputStream(adminPrivateKey), new FileInputStream(adminCert));
        return new FabricUser(adminName, null, null, enrollment, adminMspID);
    }

    public User getFabricUser(String userName) throws IOException {
        ConfigData.Users usersDetails = getUsersDetails();
        if (usersDetails == null)
            throw new RuntimeException("User details not found");

        String destFilesRootPath = getOrDefault(usersDetails.destFilesPath, "users/");
        String privateKeyFileName = getOrDefault(usersDetails.privateKeyFileName, "pk.pem");
        String certFileName = getOrDefault(usersDetails.certFileName, "cert.pem");

        final FileReference keyFileReference = new FileReference(Paths.get(destFilesRootPath, userName, privateKeyFileName).toString());
        final FileReference certFileReference = new FileReference(Paths.get(destFilesRootPath, userName, certFileName).toString());

        final File keyfile = new File(getFileName(keyFileReference));
        final File certfile = new File(getFileName(certFileReference));

        final String pemPrivateKey = new String(Files.readAllBytes(keyfile.toPath()), StandardCharsets.UTF_8);
        final String pemCertificate = new String(Files.readAllBytes(certfile.toPath()), StandardCharsets.UTF_8);

        return getFabricUser(userName, pemPrivateKey, pemCertificate, null);
    }

    public static User getFabricUser(String name, String pemPrivateKey, String cert, String mspId) throws IOException {
        final Enrollment enrollment = createEnrollment(pemPrivateKey, cert);
        return new FabricUser(name, null, null, enrollment, mspId);
    }

    private void updatePemFile(Properties properties, FileReference pemFileReference) {
        final String PEMFILE_PROP = "pemFile";

        if (properties.getProperty(PEMFILE_PROP) == null && pemFileReference != null) {
            properties.setProperty(PEMFILE_PROP, getFileName(pemFileReference));
        }
    }

    /**
     * Create new orderer using fabric.yaml. Properties for orderer can be defined by corresponding key.
     * List of supported properties:
     *
     * @param hfClient Hyperledger Fabric client
     * @param key      key of the orderer in fabric.yaml
     * @return the orderer
     * @throws InvalidArgumentException throws by SDK in case of exception
     * @see HFClient#newOrderer(String, String, Properties)
     */
    public Orderer getNewOrderer(HFClient hfClient, String key) throws InvalidArgumentException {
        ConfigData.Orderer ordererParameters = requireNonNull(getOrdererDetails(key));

        String ordererUrl = getOrThrow(ordererParameters.url, String.format("orderer[%s].url", key));
        Properties properties = mapToProperties(ordererParameters.properties);
        updatePemFile(properties, ordererParameters.pemFile);

        logger.info("Creating Orderer with props: {}", properties);

        return hfClient.newOrderer(key, ordererUrl, properties);
    }

    /**
     * Create new peer using fabric.yaml. Properties for peer can be defined by corresponding key.
     * List of supported properties:
     *
     * @param hfClient Hyperledger Fabric client
     * @param key      key of the peer in fabric.yaml
     * @return the peer
     * @throws InvalidArgumentException throws by SDK in case of exception
     * @see HFClient#newPeer(String, String, Properties)
     */
    public Peer getNewPeer(HFClient hfClient, String key) throws InvalidArgumentException {
        ConfigData.Peer peerParameters = requireNonNull(getPeerDetails(key));

        String peerUrl = getOrThrow(peerParameters.url, String.format("peer[%s].url", key));
        String peerName = getOrDefault(peerParameters.name, key);

        Properties properties = mapToProperties(peerParameters.properties);
        updatePemFile(properties, peerParameters.pemFile);

        logger.info("Creating Peer with props: {}", properties);

        return hfClient.newPeer(peerName, peerUrl, properties);
    }

    /**
     * Create new eventhub using fabric.yaml. Properties for eventhub can be defined by corresponding key.
     * List of supported properties:
     *
     * @param hfClient Hyperledger Fabric client
     * @param key      key of the eventhub in fabric.yaml
     * @return the eventhub
     * @throws InvalidArgumentException throws by SDK in case of exception
     * @see HFClient#newEventHub(String, String, Properties)
     */
    public EventHub getNewEventhub(HFClient hfClient, String key) throws InvalidArgumentException {
        ConfigData.Eventhub eventhubParameters = requireNonNull(getEventhubDetails(key));

        String eventhubUrl = getOrThrow(eventhubParameters.url, String.format("eventhub[%s].url", key));
        String eventhubName = getOrDefault(eventhubParameters.name, key);

        Properties properties = mapToProperties(eventhubParameters.properties);
        updatePemFile(properties, eventhubParameters.pemFile);

        logger.info("Creating Eventhub with props: {}", properties);

        return hfClient.newEventHub(eventhubName, eventhubUrl, properties);
    }

    public Channel generateChannel(HFClient hfClient, String channelName, User fabricUser, Orderer orderer) throws Exception {
        ChannelConfiguration channelConfiguration = configGenerator.generateChannelConfiguration(channelName);
        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, fabricUser);
        return hfClient.newChannel(channelName, orderer, channelConfiguration, channelConfigurationSignature);
    }

    public void initChannel(HFClient hfClient, String channelName, FabricConnector.Options options) throws Exception {
        getChannel(hfClient, channelName, options);
    }

    public void initChannel(HFClient hfClient, String channelName, User fabricUser, FabricConnector.Options options) throws Exception {
        getChannel(hfClient, channelName, fabricUser, options);
    }

    public Channel getChannel(HFClient hfClient, String channelName, FabricConnector.Options options) throws Exception {
        ConfigData.Channel channelParameters = requireNonNull(getChannelDetails(channelName));
        String adminKey = getOrThrow(channelParameters.admin, String.format("channel[%s].admin", channelName));
        final User fabricUser = getAdmin(adminKey);
        return getChannel(hfClient, channelName, fabricUser, options);
    }

    public Channel getChannel(HFClient hfClient, String channelName, User fabricUser, FabricConnector.Options options) throws Exception {
        ConfigData.Channel channelParameters = requireNonNull(getChannelDetails(channelName));
        requireNonNull(fabricUser);
        hfClient.setUserContext(fabricUser);

        final List<Orderer> ordererList = getOrdererList(hfClient, channelParameters);
        final List<Peer> peerList = getPeerList(hfClient, channelParameters);
        final List<EventHub> eventhubList = getEventHubList(hfClient, channelParameters);

        Channel channel = hfClient.newChannel(channelName);
        final EventTracker eventTracker = options != null ? options.eventTracker : null;
        final Channel.PeerOptions peerOptions = Channel.PeerOptions.createPeerOptions();

        if (eventTracker != null) {
            eventTracker.configureChannel(channel);
            final long startBlock = eventTracker.getStartBlock(channel);

            if (startBlock > 0 && startBlock < Long.MAX_VALUE)
                peerOptions.startEvents(startBlock);
            else
                peerOptions.startEventsNewest();

            if (eventTracker.useFilteredBlocks(channel))
                peerOptions.registerEventsForFilteredBlocks();
            else
                peerOptions.registerEventsForBlocks();
        }

        for (Orderer orderer : ordererList) {
            channel.addOrderer(orderer);
        }

        for (Peer peer : peerList) {
            channel.addPeer(peer, peerOptions);
        }

        for (EventHub eventhub : eventhubList) {
            channel.addEventHub(eventhub);
        }

        channel.initialize();

        if (eventTracker != null)
            eventTracker.connectChannel(channel);

        return channel;
    }

    public List<EventHub> getEventHubList(HFClient hfClient, ConfigData.Channel channelParameters) throws InvalidArgumentException {
        Set<String> eventhubs = channelParameters.eventhubs;
        List<EventHub> eventhubList = new ArrayList<>();
        for (String eventhubKey : eventhubs) {
            EventHub eventhub = getNewEventhub(hfClient, eventhubKey);
            eventhubList.add(eventhub);
        }
        return eventhubList;
    }

    public List<Peer> getPeerList(HFClient hfClient, ConfigData.Channel channelParameters) throws InvalidArgumentException {
        Set<String> peers = channelParameters.peers;
        if (peers == null || peers.isEmpty())
            throw new RuntimeException("Peers list can`t be empty");
        List<Peer> peerList = new ArrayList<>();
        for (String peerKey : peers) {
            Peer peer = getNewPeer(hfClient, peerKey);
            peerList.add(peer);
        }
        return peerList;
    }

    public List<Orderer> getOrdererList(HFClient hfClient, ConfigData.Channel channelParameters) throws InvalidArgumentException {
        Set<String> orderers = channelParameters.orderers;
        if (orderers == null || orderers.isEmpty())
            throw new RuntimeException("Orderers list can`t be empty");
        List<Orderer> ordererList = new ArrayList<>();
        for (String ordererKey : orderers) {
            Orderer orderer = getNewOrderer(hfClient, ordererKey);
            ordererList.add(orderer);
        }
        return ordererList;
    }

    public ChaincodeID getChaincodeID(String chaincodeKey) throws InvalidArgumentException {
        return getChaincodeID(chaincodeKey, getChaincodeDetails(chaincodeKey));
    }

    public ChaincodeID getChaincodeID(String chaincodeKey, ConfigData.Chaincode chaincodeParameters) throws InvalidArgumentException {
        if (chaincodeParameters == null)
            throw new RuntimeException(String.format("Chaincode '%s' is not specified", chaincodeKey));
        final String prefix = String.format("chaincode[%s].", chaincodeKey);
        final String chaincodeIDString = getOrThrow(chaincodeParameters.id, prefix + "id");
        final String chaincodePath = getOrThrow(chaincodeParameters.sourceLocation, prefix + "sourceLocation");
        final String chaincodeVersion = getOrDefault(chaincodeParameters.version, "0");

        return ChaincodeID.newBuilder()
                .setName(chaincodeIDString)
                .setVersion(chaincodeVersion)
                .setPath(chaincodePath)
                .build();
    }

    public void installChaincode(HFClient hfClient, List<Peer> peerList, String key) throws InvalidArgumentException, ProposalException {
        ConfigData.Chaincode chaincodeParameters = getChaincodeDetails(key);

        // String chaincodePathPrefix = chaincodeParameters.path("sourceLocationPrefix").asText("chaincode");
        String chaincodePathPrefix = getFileName(chaincodeParameters.sourceLocationPrefix, "chaincode");
        String chaincodeType = getOrDefault(chaincodeParameters.type, "GO_LANG");

        ChaincodeID chaincodeID = getChaincodeID(key, chaincodeParameters);
        String chaincodeVersion = chaincodeID.getVersion();

        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(chaincodePathPrefix));
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.valueOf(chaincodeType));

        logger.info("install chaincode proposal {}:{}", chaincodeID.getName(), chaincodeID.getVersion());
        Collection<ProposalResponse> installProposalResponse = hfClient.sendInstallProposal(installProposalRequest, peerList);

        checkProposalResponse("install chaincode", installProposalResponse);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> instantiateChaincode(HFClient hfClient, Channel channel, String key) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ChaincodeCollectionConfigurationException {
        return instantiateChaincode(hfClient, channel, key, null, null);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> instantiateChaincode(HFClient hfClient, Channel channel, String key, JsonNode collectionPolicy) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ChaincodeCollectionConfigurationException {
        return instantiateChaincode(hfClient, channel, key, collectionPolicy, null);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> instantiateChaincode(HFClient hfClient, Channel channel, String key, JsonNode collectionPolicy, Collection<Peer> peers) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ChaincodeCollectionConfigurationException {

        ConfigData.Chaincode chaincodeParameters = getChaincodeDetails(key);

        ChaincodeID chaincodeID = getChaincodeID(key, chaincodeParameters);

        List<String> chaincodeInitArguments = getOrDefault(chaincodeParameters.initArguments, Collections.emptyList());

        InstantiateProposalRequest instantiateProposalRequest = hfClient.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(120000);
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(chaincodeInitArguments.toArray(new String[0]));
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);

        String endorsementPolicy = getFileName(chaincodeParameters.endorsementPolicy, null);
        if (endorsementPolicy != null && !endorsementPolicy.isEmpty()) {
            ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
            chaincodeEndorsementPolicy.fromYamlFile(new File(endorsementPolicy));
            instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
        }


        JsonNode collectionPolicyNode = collectionPolicy;

        if (collectionPolicyNode == null)
            collectionPolicyNode = chaincodeParameters.collectionPolicy;

        if (collectionPolicyNode != null) {
            final ChaincodeCollectionConfiguration chaincodeCollectionConfiguration;
            if (collectionPolicyNode.isTextual() && !collectionPolicyNode.asText().isEmpty()) {
                final String fileName = getFileName(new FileReference(collectionPolicyNode.asText()));
                final File file = new File(fileName);

                if (fileName.endsWith(".yaml") || fileName.endsWith(".yml"))
                    chaincodeCollectionConfiguration = ChaincodeCollectionConfiguration.fromYamlFile(file);
                else if (fileName.endsWith(".json"))
                    chaincodeCollectionConfiguration = ChaincodeCollectionConfiguration.fromJsonFile(file);
                else
                    chaincodeCollectionConfiguration = null;
            } else if (collectionPolicyNode.isArray()) {

                ObjectMapper mapper = new ObjectMapper();

                // FIXME: this is not efficient, needs to find/implement better solution
                final String s = mapper.writeValueAsString(collectionPolicyNode);
                final javax.json.JsonArray jsonValues = Json.createReader(new StringReader(s)).readArray();

                chaincodeCollectionConfiguration = ChaincodeCollectionConfiguration.fromJsonObject(jsonValues);
            } else
                chaincodeCollectionConfiguration = null;

            if (chaincodeCollectionConfiguration == null)
                throw new InvalidArgumentException("collectionPolicy is not if valid type");

            instantiateProposalRequest.setChaincodeCollectionConfiguration(chaincodeCollectionConfiguration);
        }


        logger.info("instantiate chaincode proposal {}/{}:{}", channel.getName(), chaincodeID.getName(), chaincodeID.getVersion());
        Collection<ProposalResponse> instantiateProposalResponses;
        if (peers != null) {
            instantiateProposalResponses = channel.sendInstantiationProposal(instantiateProposalRequest, peers);
        } else {
            instantiateProposalResponses = channel.sendInstantiationProposal(instantiateProposalRequest);
        }

        checkProposalResponse("instantiate chaincode", instantiateProposalResponses);
        return channel.sendTransaction(instantiateProposalResponses);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> upgradeChaincode(HFClient hfClient, Channel channel, List<Peer> peerList, String key) throws InvalidArgumentException, ProposalException {
        ConfigData.Chaincode chaincodeParameters = getChaincodeDetails(key);

        List<String> chaincodeInitArguments = getOrDefault(chaincodeParameters.initArguments, Collections.emptyList());

        String chaincodePathPrefix = getFileName(chaincodeParameters.sourceLocationPrefix, "chaincode");
        String chaincodeType = getOrDefault(chaincodeParameters.type, "GO_LANG");

        ChaincodeID chaincodeID = getChaincodeID(key, chaincodeParameters);
        String chaincodeVersion = chaincodeID.getVersion();

        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(chaincodePathPrefix));
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.valueOf(chaincodeType));
        logger.info("install chaincode proposal {}:{}", chaincodeID.getName(), chaincodeID.getVersion());
        Collection<ProposalResponse> installProposalResponse = hfClient.sendInstallProposal(installProposalRequest, peerList);

        checkProposalResponse("install chaincode", installProposalResponse);

        UpgradeProposalRequest upgradeProposalRequest = hfClient.newUpgradeProposalRequest();
        upgradeProposalRequest.setChaincodeID(chaincodeID);
        upgradeProposalRequest.setChaincodeVersion(chaincodeVersion);
        upgradeProposalRequest.setProposalWaitTime(120000);
        upgradeProposalRequest.setArgs(chaincodeInitArguments.toArray(new String[0]));
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "UpgradeProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "UpgradeProposalRequest".getBytes(UTF_8));
        upgradeProposalRequest.setTransientMap(tm);

        logger.info("upgrade chaincode proposal {}/{}:{}", channel.getName(), chaincodeID.getName(), chaincodeID.getVersion());
        Collection<ProposalResponse> upgradeProposalResponses = channel.sendUpgradeProposal(upgradeProposalRequest, peerList);

        checkProposalResponse("upgrade chaincode", upgradeProposalResponses);
        return channel.sendTransaction(upgradeProposalResponses);
    }


    private void checkProposalResponse(String proposalType, Collection<ProposalResponse> proposalResponses) {
        for (ProposalResponse response : proposalResponses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                logger.info("Successful {} proposal response Txid: {} from peer {}", proposalType, response.getTransactionID(), response.getPeer().getName());
            } else {
                throw new RuntimeException("Proposal failed on peer:" + response.getPeer().getName() + " with reason: " + response.getMessage());
            }
        }
    }

    private static PrivateKey getPrivateKeyFromString(String data) throws IOException {
        final PEMParser pemParser = new PEMParser(new StringReader(data));
        PrivateKeyInfo pemPair = (PrivateKeyInfo) pemParser.readObject();
        PrivateKey privateKey = new JcaPEMKeyConverter().getPrivateKey(pemPair);
        return privateKey;
    }

    private static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException {
        return getPrivateKeyFromString(new String(data));
    }

    public static Enrollment createEnrollment(PrivateKey privateKey, String pemCertificate) {
        return new FabricUserEnrollment(privateKey, pemCertificate);
    }

    public static Enrollment createEnrollment(String pemPrivateKey, String pemCertificate) throws IOException {
        PrivateKey privateKey = getPrivateKeyFromString(pemPrivateKey);
        return new FabricUserEnrollment(privateKey, pemCertificate);
    }

    public static Enrollment createEnrollment(InputStream privateKeyFile, InputStream certFile) throws IOException {
        PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(privateKeyFile));

        return new FabricUserEnrollment(privateKey, IOUtils.toString(certFile));
    }

    public HFCAClient createHFCAClient(String caKey) throws MalformedURLException, InvalidArgumentException {
        ConfigData.CA caParameters = getCADetails(caKey);
        String caUrl = getOrThrow(caParameters.url, String.format("cs[%s].url", caKey));

        final Properties properties = mapToProperties(caParameters.properties);
        updatePemFile(properties, caParameters.pemFile);

        HFCAClient hfcaClient = HFCAClient.createNewInstance(caUrl, properties);
        return hfcaClient;
    }

    public HFCAClient createHFCAClient(String caKey, CryptoSuite cryptoSuite) throws MalformedURLException, InvalidArgumentException {
        if (cryptoSuite == null)
            cryptoSuite = getCryptoSuite();
        HFCAClient hfcaClient = createHFCAClient(caKey);
        hfcaClient.setCryptoSuite(cryptoSuite);
        return hfcaClient;
    }

    public User enrollAdmin(String caKey) throws Exception {
        return enrollAdmin(createHFCAClient(caKey, null), caKey);
    }

    public User enrollAdmin(HFCAClient hfcaClient, String caKey) throws Exception {
        ConfigData.CA caParameters = getCADetails(caKey);
        String caAdminLogin = getOrThrow(caParameters.adminLogin, String.format("ca[%s].adminLogin", caKey));
        String caAdminSecret = getOrThrow(caParameters.adminSecret, String.format("ca[%s].adminSecret", caKey));
        String caMspID = getOrThrow(caParameters.mspID, String.format("ca[%s].mspID", caKey));
        Enrollment adminEnrollment = hfcaClient.enroll(caAdminLogin, caAdminSecret);
        User adminUser = new FabricUser(caAdminLogin, null, null, adminEnrollment, caMspID);
        return adminUser;
    }

    public String registerUser(String caKey, String userName, String userAffiliation) throws Exception {
        HFCAClient hfcaClient = createHFCAClient(caKey, null);
        User admin = enrollAdmin(hfcaClient, caKey);
        return registerUser(hfcaClient, admin, userName, userAffiliation);
    }

    public static String registerUser(HFCAClient hfcaClient, User admin, String userName, String userAffiliation) throws Exception {
        RegistrationRequest registrationRequest = new RegistrationRequest(userName, userAffiliation);
        return hfcaClient.register(registrationRequest, admin);
    }

    public User enrollUser(String caKey, String userName, String userSecret) throws Exception {
        ConfigData.CA caParameters = getCADetails(caKey);
        String caMspID = getOrThrow(caParameters.mspID, String.format("ca[%s].mspID", caKey));
        HFCAClient hfcaClient = createHFCAClient(caKey, null);
        return enrollUser(hfcaClient, userName, userSecret, caMspID);
    }

    public static User enrollUser(HFCAClient hfcaClient, String userName, String userSecret, String mspID) throws Exception {
        Enrollment adminEnrollment = hfcaClient.enroll(userName, userSecret);
        return new FabricUser(userName, null, null, adminEnrollment, mspID);
    }

    public static FabricConfig getConfigFromFile(String configFile) {

        try {
            final Path parent = Paths.get(configFile).getParent();
            final String dir = parent == null ? "." : parent.toString();
            return new FabricConfig(new FileReader(configFile), dir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read config file " + configFile, e);
        }
    }

    private static class TimePair {
        final TimeUnit timeUnit;
        final long value;

        TimePair(long value, TimeUnit timeUnit) {
            this.value = value;
            this.timeUnit = timeUnit;
        }
    }

    private static TimePair parseInterval(String s) {
        final TimePair[] nsUnits = {
                new TimePair(1000, TimeUnit.NANOSECONDS),
                new TimePair(1000, TimeUnit.MICROSECONDS),
                new TimePair(Long.MAX_VALUE, TimeUnit.MILLISECONDS),
        };

        final TimePair[] sUnits = {
                new TimePair(60, TimeUnit.SECONDS),
                new TimePair(60, TimeUnit.MINUTES),
                new TimePair(24, TimeUnit.HOURS),
                new TimePair(Long.MAX_VALUE, TimeUnit.DAYS),
        };

        final Duration duration = Duration.parse(s);
        long value;
        TimeUnit timeUnit = null;

        if (duration.isNegative() || duration.isZero())
            return null;

        if ((value = duration.getNano()) != 0) {
            for (TimePair unit : nsUnits) {
                if (value % unit.value != 0) {
                    timeUnit = unit.timeUnit;
                    break;
                }
                value /= unit.value;
            }
        } else {
            value = duration.getSeconds();

            for (TimePair unit : sUnits) {
                if (value % unit.value != 0) {
                    timeUnit = unit.timeUnit;
                    break;
                }
                value /= unit.value;
            }
        }

        return new TimePair(value, timeUnit);
    }

    /**
     * Convert JSON node to Java properties.
     * @param propertiesNode node with properties
     * @return properties with key as JSON key and value as JSON value
     */
    private Properties jsonToProperties(JsonNode propertiesNode) {
        Properties peerProperties = new Properties();

        if (propertiesNode != null) {
            Iterator<Map.Entry<String, JsonNode>> fields = propertiesNode.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> e = fields.next();
                final String fieldName = e.getKey();
                final JsonNode fieldValue = e.getValue();
                String value = fieldValue.asText();

                switch (fieldName) {
                    case "idleTimeout":
                        /** @see Endpoint#addNettyBuilderProps for more info */
                        final TimePair idleTimeout = parseInterval(value);
                        peerProperties.put("grpc.NettyChannelBuilderOption.idleTimeout", new Object[]{Long.valueOf(idleTimeout.value), idleTimeout.timeUnit});
                        break;
                    default:
                        peerProperties.setProperty(fieldName, fieldValue.asText());
                        break;
                }
            }
        }
        return peerProperties;
    }

    /**
     * Convert Map<String,String> object to Java properties.
     * @param stringMap node with properties
     * @return properties with keys and values from stringMap
     */
    private static Properties mapToProperties(Map<String, String> stringMap) {
        Properties properties = new Properties();

        if (stringMap != null) {
            Set<Map.Entry<String, String>> fields = stringMap.entrySet();
            for (Map.Entry<String, String> field : fields) {
                final String fieldName = field.getKey();
                final String fieldValue = field.getValue();

                switch (fieldName) {
                    case "idleTimeout":
                        /** @see Endpoint#addNettyBuilderProps for more info */
                        final TimePair idleTimeout = parseInterval(fieldValue);
                        properties.put("grpc.NettyChannelBuilderOption.idleTimeout", new Object[]{Long.valueOf(idleTimeout.value), idleTimeout.timeUnit});
                        break;
                    default:
                        properties.setProperty(fieldName, fieldValue);
                        break;
                }
            }
        }
        return properties;
    }

    public static <T extends X, X> X getOrDefault(T value, X defValue) {
        return value == null ? defValue : value;
    }

    public static <T> T getOrThrow(T value, String propName) throws InvalidArgumentException {
        if (value == null)
            throw new InvalidArgumentException("property [" + propName + "] should be set");
        return value;
    }
}
