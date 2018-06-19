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

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.fabric.utils.ConfigGenerator;
import com.luxoft.fabric.utils.MiscUtils;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class FabricConfig extends YamlConfig {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ConfigGenerator configGenerator = new ConfigGenerator();
    private final String confDir;

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
        super(configReader);
        this.confDir = confDir == null ? "." : confDir;
    }

    public Iterator<JsonNode> getChannels() {
        JsonNode channels = getRoot().get("channels");
        if (channels != null)
            return channels.elements();
        else
            return Collections.emptyIterator();
    }

    public JsonNode getChannelDetails(String key) {
        return getRoot().withArray("channels").findValue(key);
    }

    public JsonNode getPeerDetails(String key) {
        return getRoot().get("peers").findValue(key);
    }

    protected List<String> getElementKeys(String elementName) {
        Set<String> names = new HashSet<>();
        JsonNode elements = getRoot().get(elementName);
        if (elements == null)
            return Collections.EMPTY_LIST;
        elements.iterator().forEachRemaining(element -> names.add(element.fieldNames().next()));
        return new ArrayList(names);
    }

    public List<String> getPeersKeys() {
        return getElementKeys("peers");
    }

    public List<String> getOrderersKeys() {
        return getElementKeys("orderers");
    }

    public List<String> getEventHubsKeys() {
        return getElementKeys("eventhubs");
    }

    public List<String> getChaincodesKeys() {
        return getElementKeys("chaincodes");
    }

    public List<String> getChannelsKeys() {
        return getElementKeys("channels");
    }

    public List<String> getCAsKeys() {
        return getElementKeys("cas");
    }

    public List<String> getAdminsKeys() {
        return getElementKeys("admins");
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

    public JsonNode getUsersDetails() {
        return getRoot().get("users");
    }

    public String getFileName(JsonNode jsonNode, String name, String defaultValue) {
        final JsonNode node = jsonNode.get(name);
        String value = defaultValue;
        if (node == null) {
            if (defaultValue == null || defaultValue.isEmpty())
                return defaultValue;
        }
        else
            value = node.asText();
        return MiscUtils.resolveFile(value, confDir);
    }

    public String getFileName(JsonNode jsonNode, String name)
    {
        return getFileName(jsonNode, name, null);
    }

    public User getAdmin(String key) throws Exception {
        JsonNode adminParameters = requireNonNull(getAdminDetails(key));

        String adminName = adminParameters.get("name").asText();
        String adminMspID = adminParameters.get("mspID").asText();
        String adminCert = getFileName(adminParameters, "cert");
        String adminPrivateKey = getFileName(adminParameters, "privateKey");

        Enrollment enrollment = FabricConfig.createEnrollment(new FileInputStream(adminPrivateKey), new FileInputStream(adminCert));
        return new FabricUser(adminName, null, null, enrollment, adminMspID);
    }

    /**
     * Create new orderer using fabric.yaml. Properties for orderer can be defined by corresponding key.
     * List of supported properties:
     * @see HFClient#newOrderer(String, String, Properties)
     *
     * @param hfClient Hyperledger Fabric client
     * @param key key of the orderer in fabric.yaml
     *
     * @return the orderer
     * @throws InvalidArgumentException throws by SDK in case of exception
     */
    public Orderer getNewOrderer(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode ordererParameters = requireNonNull(getOrdererDetails(key));

        String ordererUrl = ordererParameters.get("url").asText();
        String ordererPemFile = getFileName(ordererParameters, "pemFile", "");

        Properties ordererProperties = jsonToProperties(ordererParameters.get("properties"));
        ordererProperties.setProperty("pemFile", ordererPemFile);

        logger.info("Creating Orderer with props: {}", ordererProperties);

        return hfClient.newOrderer(key, ordererUrl, ordererProperties);
    }

    /**
     * Create new peer using fabric.yaml. Properties for peer can be defined by corresponding key.
     * List of supported properties:
     * @see HFClient#newPeer(String, String, Properties)
     *
     * @param hfClient Hyperledger Fabric client
     * @param key key of the peer in fabric.yaml
     *
     * @return the peer
     * @throws InvalidArgumentException throws by SDK in case of exception
     */
    public Peer getNewPeer(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode peerParameters = requireNonNull(getPeerDetails(key));

        String peerUrl = peerParameters.get("url").asText();
        String peerName = peerParameters.path("name").asText(key);
        String peerPemFile = getFileName(peerParameters, "pemFile", "");

        Properties peerProperties = jsonToProperties(peerParameters.get("properties"));
        peerProperties.setProperty("pemFile", peerPemFile);

        logger.info("Creating Peer with props: {}", peerProperties);

        return hfClient.newPeer(peerName, peerUrl, peerProperties);
    }

    /**
     * Create new eventhub using fabric.yaml. Properties for eventhub can be defined by corresponding key.
     * List of supported properties:
     * @see HFClient#newEventHub(String, String, Properties)
     *
     * @param hfClient Hyperledger Fabric client
     * @param key key of the eventhub in fabric.yaml
     *
     * @return the eventhub
     * @throws InvalidArgumentException throws by SDK in case of exception
     */
    public EventHub getNewEventhub(HFClient hfClient, String key) throws InvalidArgumentException {
        JsonNode eventhubParameters = requireNonNull(getEventhubDetails(key));

        String eventhubUrl = eventhubParameters.get("url").asText();
        String eventhubName = eventhubParameters.path("name").asText(key);
        String eventhubPemFile = getFileName(eventhubParameters, "pemFile", "");

        Properties eventhubProperties = jsonToProperties(eventhubParameters.get("properties"));
        eventhubProperties.setProperty("pemFile", eventhubPemFile);

        logger.info("Creating Eventhub with props: {}", eventhubProperties);

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

        // String chaincodePathPrefix = chaincodeParameters.path("sourceLocationPrefix").asText("chaincode");
        String chaincodePathPrefix = getFileName(chaincodeParameters, "sourceLocationPrefix", "chaincode");
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

        String endorsementPolicy = getFileName(chaincodeParameters, "endorsementPolicy", "");
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

        String chaincodePathPrefix = getFileName(chaincodeParameters, "sourceLocationPrefix", "chaincode");
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

        String caCertPem = getFileName(caParameters, "pemFile", "");
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
            cryptoSuite = getCryptoSuite();
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
    };

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
}
