package com.luxoft.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Created by nvolkov on 26.07.17.
 */
public class FabricConnector {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected HFClient hfClient;
    protected FabricConfig fabricConfig;

    private String defaultChannelName;

    public FabricConnector(FabricConfig fabricConfig, Boolean initChannels) throws Exception {
        this(null, null, fabricConfig, initChannels);
    }

    public FabricConnector(FabricConfig fabricConfig) throws Exception {
        this(null, null, fabricConfig);
    }

    public FabricConnector(User user, FabricConfig fabricConfig) throws Exception {
        this(user, null, fabricConfig);
    }

    public FabricConnector(String defaultChannelName, FabricConfig fabricConfig) throws Exception {
        this(null, defaultChannelName, fabricConfig);
    }

    public FabricConnector(User user, String defaultChannelName, FabricConfig fabricConfig, Boolean initChannels) throws Exception {
        this.fabricConfig = fabricConfig;
        this.defaultChannelName = defaultChannelName;

        hfClient = HFClient.createNewInstance();
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        hfClient.setCryptoSuite(cryptoSuite);

        if (user != null)
            hfClient.setUserContext(user);
        else if (hfClient.getUserContext() == null)
            hfClient.setUserContext(fabricConfig.getAdmin(fabricConfig.getAdminsKeys().get(0)));

        if (!initChannels) return;
        // init channels
        initChannels();
    }

    public FabricConnector(User user, String defaultChannelName, FabricConfig fabricConfig) throws Exception {
        this(user, defaultChannelName, fabricConfig, true);
    }

    public void initChannels() throws Exception {
        for (Iterator<JsonNode> it = fabricConfig.getChannels(); it.hasNext(); ) {
            String channel = it.next().fields().next().getKey();
            fabricConfig.initChannel(hfClient, channel);
        }
    }

    public HFClient getHfClient() {
        return hfClient;
    }

    public FabricConfig getFabricConfig() {
        return fabricConfig;
    }

    public void setUserContext(User user) throws Exception {
        hfClient.setUserContext(user);
    }

    public void deployChaincode(String chaincodeName) throws Exception {
        deployChaincode(chaincodeName, defaultChannelName);
    }

    public void upgradeChaincode(String chaincodeName) throws Exception {
        upgradeChaincode(chaincodeName, defaultChannelName);
    }

    public void deployChaincode(String chaincodeName, String channelName) throws Exception {
        Channel channel = hfClient.getChannel(channelName);
        if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConfig.installChaincode(hfClient, new ArrayList<>(channel.getPeers()), chaincodeName);
        fabricConfig.instantiateChaincode(hfClient, channel, chaincodeName);
    }

    public void upgradeChaincode(String chaincodeName, String channelName) throws Exception {
        Channel channel = hfClient.getChannel(channelName);
        if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConfig.upgradeChaincode(hfClient, channel, new ArrayList<>(channel.getPeers()), chaincodeName);
    }

    public TransactionProposalRequest buildProposalRequest(String function, String chaincode, byte[][] message) {

        final TransactionProposalRequest transactionProposalRequest = hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(chaincode).build());
        transactionProposalRequest.setFcn(function);
        transactionProposalRequest.setArgBytes(message);

        return transactionProposalRequest;
    }

    public CompletableFuture<Collection<ProposalResponse>> buildProposalFuture(TransactionProposalRequest transactionProposalRequest, boolean returnOnlySuccessful) {
        return buildProposalFuture(transactionProposalRequest, defaultChannelName, returnOnlySuccessful);
    }

    public CompletableFuture<Collection<ProposalResponse>> buildProposalFuture(TransactionProposalRequest transactionProposalRequest, String channelName, boolean returnOnlySuccessful) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Channel channel = hfClient.getChannel(channelName);
                if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
                Collection<ProposalResponse> proposalResponses = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
                Collection<ProposalResponse> successful = new LinkedList<>();

                if (returnOnlySuccessful) {
                    for (ProposalResponse response : proposalResponses) {
                        if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                            logger.info("Successful transaction proposal response Txid: {} from peer {}", response.getTransactionID(), response.getPeer().getName());
                            successful.add(response);
                        } else
                            logger.warn("Unsuccessful transaction proposal response Txid: {} from peer {}, reason: {}", response.getTransactionID(), response.getPeer().getName(), response.getMessage());
                    }

                    // Check that all the proposals are consistent with each other. We should have only one set
                    // where all the proposals above are consistent.
                    if (!successful.isEmpty()) {
                        Collection<Set<ProposalResponse>> proposalConsistencySets = SDKUtils.getProposalConsistencySets(successful);
                        if (proposalConsistencySets.size() != 1) {
                            throw new RuntimeException("More than 1 consistency sets: " + proposalConsistencySets.size());
                        }
                    }

                    return successful;
                } else {
                    return proposalResponses;
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<BlockEvent.TransactionEvent> buildTransactionFuture(TransactionProposalRequest transactionProposalRequest) {
        return buildTransactionFuture(transactionProposalRequest, defaultChannelName);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> buildTransactionFuture(TransactionProposalRequest transactionProposalRequest, String channelName) {

        return buildProposalFuture(transactionProposalRequest, channelName, true).thenCompose(proposalResponses -> {
            CompletableFuture<BlockEvent.TransactionEvent> future = null;
            try {
                Channel channel = hfClient.getChannel(channelName);
                if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
                future = channel.sendTransaction(proposalResponses);
            } catch (Exception e) {
                logger.error("Failed to send transaction to channel", e);
            }

            return future;
        });
    }

    public static class InvokeResult
    {
        final BlockEvent.TransactionEvent transactionEvent;
        final ProposalResponse proposalResponse;

        public InvokeResult(BlockEvent.TransactionEvent transactionEvent, ProposalResponse proposalResponse) {
            this.transactionEvent = transactionEvent;
            this.proposalResponse = proposalResponse;
        }

        public BlockEvent.TransactionEvent getTransactionEvent() {
            return transactionEvent;
        }

        public ProposalResponse getProposalResponse() {
            return proposalResponse;
        }
    }

    public CompletableFuture<InvokeResult> buildTransactionFutureEx(TransactionProposalRequest transactionProposalRequest, String channelName) {

        return buildProposalFuture(transactionProposalRequest, true)
                .thenCompose(proposalResponses -> {
            try {
                Channel channel = hfClient.getChannel(channelName);
                if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);

                return channel.sendTransaction(proposalResponses)
                        .thenApply(transactionEvent -> new InvokeResult(transactionEvent, proposalResponses.iterator().next()));
            } catch (Exception e) {
                throw new RuntimeException("Failed to send transaction to channel", e);
            }
        });
    }

    public QueryByChaincodeRequest buildQueryRequest(String function, String chaincode, byte[][] message) {

        final QueryByChaincodeRequest queryByChaincodeRequest = hfClient.newQueryProposalRequest();
        queryByChaincodeRequest.setChaincodeID(ChaincodeID.newBuilder().setName(chaincode).build());
        queryByChaincodeRequest.setFcn(function);
        queryByChaincodeRequest.setArgBytes(message);

        return queryByChaincodeRequest;
    }

    public CompletableFuture<byte[]> sendQueryRequest(QueryByChaincodeRequest request) {
        return sendQueryRequest(request, defaultChannelName);
    }

    public CompletableFuture<byte[]> sendQueryRequest(QueryByChaincodeRequest request, String channelName) {
        return CompletableFuture.supplyAsync(() -> {
            ProposalResponse lastFailProposal = null;
            try {
                Channel channel = hfClient.getChannel(channelName);
                if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
                final Collection<ProposalResponse> proposalResponses = channel.queryByChaincode(request);
                for (ProposalResponse proposalResponse : proposalResponses) {
                    if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                        lastFailProposal = proposalResponse;
                        continue;
                    }
                    return proposalResponse.getChaincodeActionResponsePayload();
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to send query", e);
            }
            if (lastFailProposal == null) {
                throw new RuntimeException("Unable to process query, no responses received");
            } else {
                throw new RuntimeException(String.format("Unable to process query, txId: %s, message: %s",
                        lastFailProposal.getTransactionID(), lastFailProposal.getMessage()));
            }
        });
    }

    public CompletableFuture<byte[]> query(String function, String chaincode, byte[]... message) {
        return query(function, chaincode, defaultChannelName, message);
    }

    public CompletableFuture<byte[]> query(String function, String chaincode, String channelName, byte[]... message) {
        return sendQueryRequest(buildQueryRequest(function, chaincode, message), channelName);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, byte[]... message) throws Exception {
        return invoke(function, chaincode, defaultChannelName, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, String channelName, byte[]... message) throws Exception {
        return buildTransactionFuture(buildProposalRequest(function, chaincode, message), channelName);
    }

    public CompletableFuture<InvokeResult> invokeEx(String function, String chaincode, byte[]... message) throws Exception {
        return buildTransactionFutureEx(buildProposalRequest(function, chaincode, message), defaultChannelName);
    }
}
