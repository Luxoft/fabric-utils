package com.luxoft.fabric;

import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Created by nvolkov on 26.07.17.
 */
public class FabricConnector {

    protected final Channel channel;
    protected HFClient hfClient;
    protected FabricConfig fabricConfig;

    public FabricConnector(String channelName, FabricConfig fabricConfig) throws Exception {
        this(null, channelName, fabricConfig);
    }

    public FabricConnector(User user, String channelName, FabricConfig fabricConfig) throws Exception {
        hfClient = HFClient.createNewInstance();
        CryptoSuite cryptoSuite = CryptoSuite.Factory.getCryptoSuite();
        hfClient.setCryptoSuite(cryptoSuite);
        this.fabricConfig = fabricConfig;
        channel = user == null ?
                fabricConfig.getChannel(this.hfClient, channelName) :
                fabricConfig.getChannel(hfClient, channelName, user);
    }

    public void setUserContext(User user) throws Exception {
        hfClient.setUserContext(user);
    }

    public void deployChaincode(String chaincodeName) throws Exception {
        fabricConfig.instantiateChaincode(hfClient, channel, new ArrayList<>(channel.getPeers()), chaincodeName);
    }

    public void upgradeChaincode(String chaincodeName) throws Exception {
        fabricConfig.upgradeChaincode(hfClient, channel, new ArrayList<>(channel.getPeers()), chaincodeName);
    }

    public TransactionProposalRequest buildProposalRequest(String function, String chaincode, byte[][] message) {

        final TransactionProposalRequest transactionProposalRequest = hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(chaincode).build());
        transactionProposalRequest.setFcn(function);
        transactionProposalRequest.setArgBytes(message);

        return transactionProposalRequest;
    }

    public CompletableFuture<Collection<ProposalResponse>> buildProposalFuture(TransactionProposalRequest transactionProposalRequest, boolean returnOnlySuccessful) throws Exception {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Collection<ProposalResponse> proposalResponses = channel.sendTransactionProposal(transactionProposalRequest, channel.getPeers());
                Collection<ProposalResponse> successful = new LinkedList<>();

                if (returnOnlySuccessful) {
                    for (ProposalResponse response : proposalResponses) {
                        if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
                            System.out.printf("Successful transaction proposal response Txid: %s from peer %s\n", response.getTransactionID(), response.getPeer().getName());
                            successful.add(response);
                        }
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

    public CompletableFuture<BlockEvent.TransactionEvent> buildTransactionFuture(TransactionProposalRequest transactionProposalRequest) throws Exception {

        return buildProposalFuture(transactionProposalRequest, true).thenCompose(proposalResponses -> {
            CompletableFuture<BlockEvent.TransactionEvent> future = null;
            try {
                future = channel.sendTransaction(proposalResponses);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return future;
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                final Collection<ProposalResponse> proposalResponses = channel.queryByChaincode(request);
                for (ProposalResponse proposalResponse : proposalResponses) {
                    if (!proposalResponse.isVerified() || proposalResponse.getStatus() != ProposalResponse.Status.SUCCESS) {
                        continue;
                    }
                    return proposalResponse.getChaincodeActionResponsePayload();
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to send query", e);
            }
            throw new RuntimeException("Unable to send query");
        });
    }

    public CompletableFuture<byte[]> query(String function, String chaincode, byte[]... message) {
        return sendQueryRequest(buildQueryRequest(function, chaincode, message));
    }
}
