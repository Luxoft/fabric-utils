package com.luxoft.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.luxoft.YamlConfig;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.ChaincodeEndorsementPolicyParseException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class FabricConfig extends YamlConfig {

    public FabricConfig(Reader configReader) throws IOException {
        super(configReader);
    }

    public Iterator<JsonNode> getChannels() {
        return getRoot().get("channels").elements();
    }

    public JsonNode getPeerDetails(String key) {
        return getRoot().get("peers").findValue(key);
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

    public void deployChaincode(HFClient hfClient, Channel channel, List<Peer> peerList, String key) throws InvalidArgumentException, ProposalException, IOException, ChaincodeEndorsementPolicyParseException {
        JsonNode chaincodeParameters = getChaincodeDetails(key);

        String chaincodeIDString = chaincodeParameters.get("id").asText();
        String chaincodePath = chaincodeParameters.get("sourceLocation").asText();

        String chaincodePathPrefix = chaincodeParameters.path("sourceLocationPrefix").asText("chaincode");
        String chaincodeVersion = chaincodeParameters.path("version").asText("0");
        String chaincodeType = chaincodeParameters.path("type").asText("GO_LANG");
        String endorsementPolicy = chaincodeParameters.path("endorsementPolicy").asText("");
        List<String> chaincodeInitArguments = new ArrayList<>();
        chaincodeParameters.withArray("initArguments").forEach(element -> chaincodeInitArguments.add(element.asText()));

        InstallProposalRequest installProposalRequest = hfClient.newInstallProposalRequest();
        ChaincodeID chaincodeID = ChaincodeID.newBuilder().setName(chaincodeIDString).setVersion(chaincodeVersion).setPath(chaincodePath).build();
        installProposalRequest.setChaincodeID(chaincodeID);
        installProposalRequest.setChaincodeSourceLocation(new File(chaincodePathPrefix));
        installProposalRequest.setChaincodeVersion(chaincodeVersion);
        installProposalRequest.setChaincodeLanguage(TransactionRequest.Type.valueOf(chaincodeType));
        Collection<ProposalResponse> installProposalResponse = hfClient.sendInstallProposal(installProposalRequest, peerList);

        checkProposalResponse("install chaincode", installProposalResponse);

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
        Collection<ProposalResponse> instantiateProposalResponses = channel.sendInstantiationProposal(instantiateProposalRequest);

        checkProposalResponse("instantiate chaincode", instantiateProposalResponses);
        channel.sendTransaction(instantiateProposalResponses);
    }

    private static void checkProposalResponse(String proposalType, Collection<ProposalResponse> proposalResponses) {
        for (ProposalResponse response : proposalResponses) {
            if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                System.out.format("Successful %s proposal response Txid: %s from peer %s", proposalType, response.getTransactionID(), response.getPeer().getName());
                System.out.println();
            } else {
                throw new RuntimeException("Proposal failed on peer:" + response.getPeer().getName());
            }
        }
    }

    private static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException, NoSuchProviderException, NoSuchAlgorithmException, InvalidKeySpecException {
        final PEMParser pemParser = new PEMParser(new StringReader(new String(data)));
        PrivateKeyInfo pemPair = (PrivateKeyInfo) pemParser.readObject();
        PrivateKey privateKey = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getPrivateKey(pemPair);
        return privateKey;
    }

    public static Enrollment createEnrollment(InputStream privateKeyFile, InputStream certFile) throws IOException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
        PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(privateKeyFile));

        return new FabricUserEnrollment(privateKey, IOUtils.toString(certFile));
    }
}
