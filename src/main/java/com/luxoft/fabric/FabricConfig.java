package com.luxoft.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.YamlConfig;
import com.luxoft.fabric.utils.ConfigGenerator;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class FabricConfig extends YamlConfig {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ConfigGenerator configGenerator = new ConfigGenerator();

    static {
        //loading Fabric security provider to the system
        CryptoSuite.Factory.getCryptoSuite();
    }

    public FabricConfig(Reader configReader) throws IOException {
        super(configReader);
    }

    public Iterator<JsonNode> getChannels() {
        return getRoot().get("channels").elements();
    }

    public JsonNode getChannelDetails(String key) {
        return getRoot().withArray("channels").findValue(key);
    }

    public JsonNode getPeerDetails(String key) {
        return getRoot().get("peers").findValue(key);
    }

    public JsonNode getEventhubDetails(String key) {
        return getRoot().get("eventhubs").findValue(key);
    }

    public JsonNode getOrdererDetails(String key) {
        return getRoot().get("orderers").findValue(key);
    }

    public JsonNode getChaincodeDetails(String key) {
        return getRoot().get("chaincodes").findValue(key);
    }

    public JsonNode getAdminDetails(String key) {
        return getRoot().get("admins").findValue(key);
    }

    public JsonNode getCADetails(String key) {
        return getRoot().get("cas").findValue(key);
    }

    public User getAdmin(String key) throws Exception {
        JsonNode adminParameters = requireNonNull(getAdminDetails(key));

        String adminName = adminParameters.get("name").asText();
        String adminMspID = adminParameters.get("mspID").asText();
        String adminCert = adminParameters.get("cert").asText();
        String adminPrivateKey = adminParameters.get("privateKey").asText();

        Enrollment enrollment = FabricConfig.createEnrollment(new FileInputStream(adminPrivateKey), new FileInputStream(adminCert));
        return new FabricUser(adminName, null, null, enrollment, adminMspID);
    }

    public Orderer getNewOrderer(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode ordererParameters = requireNonNull(getOrdererDetails(key));

        String ordererUrl = ordererParameters.get("url").asText();

        String ordererPemFile = ordererParameters.path("pemFile").asText("");
        String ordererSSLProvider = ordererParameters.path("sslProvider").asText("openSSL");
        String ordererNegotiationType = ordererParameters.path("negotiationType").asText("TLS");
        String ordererHostnameOverride = ordererParameters.path("hostnameOverride").asText("");
        String ordererWaitTimeMilliSecs = ordererParameters.path("waitTime").asText("2000");

        Properties ordererProperties = null;
        if (!ordererPemFile.isEmpty()) {
            ordererProperties = new Properties();
            ordererProperties.setProperty("pemFile", ordererPemFile);
            ordererProperties.setProperty("sslProvider", ordererSSLProvider);
            ordererProperties.setProperty("negotiationType", ordererNegotiationType);
            ordererProperties.setProperty("ordererWaitTimeMilliSecs", ordererWaitTimeMilliSecs);
            if (!ordererHostnameOverride.isEmpty())
                ordererProperties.setProperty("hostnameOverride", ordererHostnameOverride);
        }

        return hfClient.newOrderer(key, ordererUrl, ordererProperties);
    }

    public Peer getNewPeer(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode peerParameters = requireNonNull(getPeerDetails(key));

        String peerUrl = peerParameters.get("url").asText();

        String peerName = peerParameters.path("name").asText(key);
        String peerPemFile = peerParameters.path("pemFile").asText("");
        String peerSSLProvider = peerParameters.path("sslProvider").asText("openSSL");
        String peerNegotiationType = peerParameters.path("negotiationType").asText("TLS");
        String peerHostnameOverride = peerParameters.path("hostnameOverride").asText("");

        Properties peerProperties = null;
        if (!peerPemFile.isEmpty()) {
            peerProperties = new Properties();
            peerProperties.setProperty("pemFile", peerPemFile);
            peerProperties.setProperty("sslProvider", peerSSLProvider);
            peerProperties.setProperty("negotiationType", peerNegotiationType);
            if (!peerHostnameOverride.isEmpty())
                peerProperties.setProperty("hostnameOverride", peerHostnameOverride);
        }

        return hfClient.newPeer(peerName, peerUrl, peerProperties);
    }

    public EventHub getNewEventhub(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode eventhubParameters = requireNonNull(getEventhubDetails(key));

        String eventhubUrl = eventhubParameters.get("url").asText();

        String eventhubName = eventhubParameters.path("name").asText(key);
        String eventhubPemFile = eventhubParameters.path("pemFile").asText("");
        String eventhubSSLProvider = eventhubParameters.path("sslProvider").asText("openSSL");
        String eventhubNegotiationType = eventhubParameters.path("negotiationType").asText("TLS");
        String eventhubHostnameOverride = eventhubParameters.path("hostnameOverride").asText("");

        Properties eventhubProperties = null;
        if (!eventhubPemFile.isEmpty()) {
            eventhubProperties = new Properties();
            eventhubProperties.setProperty("pemFile", eventhubPemFile);
            eventhubProperties.setProperty("sslProvider", eventhubSSLProvider);
            eventhubProperties.setProperty("negotiationType", eventhubNegotiationType);
            if (!eventhubHostnameOverride.isEmpty())
                eventhubProperties.setProperty("hostnameOverride", eventhubHostnameOverride);
        }

        return hfClient.newEventHub(eventhubName, eventhubUrl, eventhubProperties);
    }

    public Channel generateChannel(HFClient hfClient, String channelName, User fabricUser, Orderer orderer) throws Exception {
        ChannelConfiguration channelConfiguration = configGenerator.generateChannelConfiguration(channelName);
        byte[] channelConfigurationSignature = hfClient.getChannelConfigurationSignature(channelConfiguration, fabricUser);
        return hfClient.newChannel(channelName, orderer, channelConfiguration, channelConfigurationSignature);
    }

    public void initChannel(HFClient hfClient, String channelName) throws Exception {
        getChannel(hfClient, channelName);
    }

    public void initChannel(HFClient hfClient, String channelName, User fabricUser) throws Exception {
        getChannel(hfClient, channelName, fabricUser);
    }

    public Channel getChannel(HFClient hfClient, String channelName) throws Exception {
        JsonNode channelParameters = requireNonNull(getChannelDetails(channelName));
        String adminKey = channelParameters.get("admin").asText();
        final User fabricUser = getAdmin(adminKey);
        return getChannel(hfClient, channelName, fabricUser);
    }

    public Channel getChannel(HFClient hfClient, String channelName, User fabricUser) throws Exception {
        JsonNode channelParameters = requireNonNull(getChannelDetails(channelName));
        requireNonNull(fabricUser);
        hfClient.setUserContext(fabricUser);

        String ordererName = channelParameters.get("orderers").get(0).asText();
        Orderer orderer = getNewOrderer(hfClient, ordererName);

        Iterator<JsonNode> peers = channelParameters.get("peers").iterator();
        if (!peers.hasNext())
            throw new RuntimeException("Peers list can`t be empty");
        List<Peer> peerList = new ArrayList<>();
        while (peers.hasNext()) {
            String peerKey = peers.next().asText();
            Peer peer = getNewPeer(hfClient, peerKey);
            peerList.add(peer);
        }

        Iterator<JsonNode> eventhubs = channelParameters.get("eventhubs").iterator();
        List<EventHub> eventhubList = new ArrayList<>();
        while (eventhubs.hasNext()) {
            String eventhubKey = eventhubs.next().asText();
            EventHub eventhub = getNewEventhub(hfClient, eventhubKey);
            eventhubList.add(eventhub);
        }

        Channel channel = hfClient.newChannel(channelName);
        channel.addOrderer(orderer);
        for (Peer peer : peerList) {
            channel.addPeer(peer);
        }
        for (EventHub eventhub : eventhubList) {
            channel.addEventHub(eventhub);
        }
        channel.initialize();
        return channel;
    }


    public void installChaincode(HFClient hfClient, List<Peer> peerList, String key) throws InvalidArgumentException, ProposalException {
        JsonNode chaincodeParameters = getChaincodeDetails(key);

        String chaincodeIDString = chaincodeParameters.get("id").asText();
        String chaincodePath = chaincodeParameters.get("sourceLocation").asText();

        String chaincodePathPrefix = chaincodeParameters.path("sourceLocationPrefix").asText("chaincode");
        String chaincodeVersion = chaincodeParameters.path("version").asText("0");
        String chaincodeType = chaincodeParameters.path("type").asText("GO_LANG");

        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincodeIDString).setVersion(chaincodeVersion).setPath(chaincodePath).build();

        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(chaincodePathPrefix));
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.valueOf(chaincodeType));
        Collection<ProposalResponse> installProposalResponse = hfClient.sendInstallProposal(installProposalRequest, peerList);

        checkProposalResponse("install chaincode", installProposalResponse);
    }


    public CompletableFuture<BlockEvent.TransactionEvent> instantiateChaincode(HFClient hfClient, Channel channel, String key) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ExecutionException, InterruptedException {
        return instantiateChaincode(hfClient, channel, key, null);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> instantiateChaincode(HFClient hfClient, Channel channel, String key, Collection<Peer> peers) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ExecutionException, InterruptedException {

        JsonNode chaincodeParameters = getChaincodeDetails(key);

        String chaincodeIDString = chaincodeParameters.get("id").asText();
        String chaincodePath = chaincodeParameters.get("sourceLocation").asText();
        String chaincodeVersion = chaincodeParameters.path("version").asText("0");
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincodeIDString).setVersion(chaincodeVersion).setPath(chaincodePath).build();

        String endorsementPolicy = chaincodeParameters.path("endorsementPolicy").asText("");

        List<String> chaincodeInitArguments = new ArrayList<>();
        chaincodeParameters.withArray("initArguments").forEach(element -> chaincodeInitArguments.add(element.asText()));

        InstantiateProposalRequest instantiateProposalRequest = hfClient.newInstantiationProposalRequest();
        instantiateProposalRequest.setProposalWaitTime(60000);
        instantiateProposalRequest.setChaincodeID(chaincodeID);
        instantiateProposalRequest.setFcn("init");
        instantiateProposalRequest.setArgs(chaincodeInitArguments.stream().toArray(String[]::new));
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
        instantiateProposalRequest.setTransientMap(tm);
        if (!endorsementPolicy.isEmpty()) {
            ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
            chaincodeEndorsementPolicy.fromYamlFile(new File(endorsementPolicy));
            instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);
        }
        Collection<ProposalResponse> instantiateProposalResponses;
        if(peers != null) {
            instantiateProposalResponses = channel.sendInstantiationProposal(instantiateProposalRequest, peers);
        } else {
            instantiateProposalResponses = channel.sendInstantiationProposal(instantiateProposalRequest);
        }

        checkProposalResponse("instantiate chaincode", instantiateProposalResponses);
        return channel.sendTransaction(instantiateProposalResponses);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> upgradeChaincode(HFClient hfClient, Channel channel, List<Peer> peerList, String key) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException, ExecutionException, InterruptedException {
        JsonNode chaincodeParameters = getChaincodeDetails(key);

        List<String> chaincodeInitArguments = new ArrayList<>();
        chaincodeParameters.withArray("initArguments").forEach(element -> chaincodeInitArguments.add(element.asText()));

        String chaincodePathPrefix = chaincodeParameters.path("sourceLocationPrefix").asText("chaincode");
        String chaincodeIDString = chaincodeParameters.get("id").asText();
        String chaincodePath = chaincodeParameters.get("sourceLocation").asText();
        String chaincodeVersion = chaincodeParameters.path("version").asText("0");
        String chaincodeType = chaincodeParameters.path("type").asText("GO_LANG");

        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincodeIDString).setVersion(chaincodeVersion).setPath(chaincodePath).build();

        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(chaincodePathPrefix));
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.valueOf(chaincodeType));
        Collection<ProposalResponse> installProposalResponse = hfClient.sendInstallProposal(installProposalRequest, peerList);

        checkProposalResponse("install chaincode", installProposalResponse);

        UpgradeProposalRequest upgradeProposalRequest = hfClient.newUpgradeProposalRequest();
        upgradeProposalRequest.setChaincodeID(chaincodeID);
        upgradeProposalRequest.setChaincodeVersion(chaincodeVersion);
        upgradeProposalRequest.setProposalWaitTime(60000);
        upgradeProposalRequest.setArgs(chaincodeInitArguments.stream().toArray(String[]::new));
        Map<String, byte[]> tm = new HashMap<>();
        tm.put("HyperLedgerFabric", "UpgradeProposalRequest:JavaSDK".getBytes(UTF_8));
        tm.put("method", "UpgradeProposalRequest".getBytes(UTF_8));
        upgradeProposalRequest.setTransientMap(tm);

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

    private static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        final PEMParser pemParser = new PEMParser(new StringReader(new String(data)));
        PrivateKeyInfo pemPair = (PrivateKeyInfo) pemParser.readObject();
        PrivateKey privateKey = new JcaPEMKeyConverter().getPrivateKey(pemPair);
        return privateKey;
    }

    public static Enrollment createEnrollment(InputStream privateKeyFile, InputStream certFile) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(privateKeyFile));

        return new FabricUserEnrollment(privateKey, IOUtils.toString(certFile));
    }

    public HFCAClient createHFCAClient(String caKey) throws MalformedURLException {
        JsonNode caParameters = getCADetails(caKey);
        Properties properties = null;

        String caUrl = caParameters.get("url").asText();

        String caCertPem = caParameters.path("pemFile").asText("");
        String caAllowAllHostNames = caParameters.path("allowAllHostNames").asText("");

        if (!caCertPem.isEmpty()) {
            properties = new Properties();
            properties.setProperty("pemFile", caCertPem);
            if (!caAllowAllHostNames.isEmpty())
                properties.setProperty("allowAllHostNames", caAllowAllHostNames);
        }
        HFCAClient hfcaClient = HFCAClient.createNewInstance(caUrl, properties);
        return hfcaClient;
    }

    public HFCAClient createHFCAClient(String caKey, CryptoSuite cryptoSuite) throws MalformedURLException {
        if (cryptoSuite == null)
            cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        HFCAClient hfcaClient = createHFCAClient(caKey);
        hfcaClient.setCryptoSuite(cryptoSuite);
        return hfcaClient;
    }

    public User enrollAdmin(String caKey) throws Exception {
        return enrollAdmin(createHFCAClient(caKey, null), caKey);
    }

    public User enrollAdmin(HFCAClient hfcaClient, String caKey) throws Exception {
        JsonNode caParameters = getCADetails(caKey);
        String caAdminLogin = caParameters.get("adminLogin").asText();
        String caAdminSecret = caParameters.get("adminSecret").asText();
        String caMspID = caParameters.get("mspID").asText();
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
        JsonNode caParameters = getCADetails(caKey);
        String caMspID = caParameters.get("mspID").asText();
        HFCAClient hfcaClient = createHFCAClient(caKey, null);
        return enrollUser(hfcaClient, userName, userSecret, caMspID);
    }

    public static User enrollUser(HFCAClient hfcaClient, String userName, String userSecret, String mspID) throws Exception {
        Enrollment adminEnrollment = hfcaClient.enroll(userName, userSecret);
        User user = new FabricUser(userName, null, null, adminEnrollment, mspID);
        return user;
    }
}
