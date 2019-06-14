package com.luxoft.fabric;


import com.luxoft.fabric.config.ConfigAdapter;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.CryptoException;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.TransactionEventException;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.hyperledger.fabric.protos.peer.FabricTransaction.TxValidationCode.MVCC_READ_CONFLICT_VALUE;
import static org.hyperledger.fabric.protos.peer.FabricTransaction.TxValidationCode.PHANTOM_READ_CONFLICT_VALUE;

/**
 * Created by nvolkov on 26.07.17.
 */
public class FabricConnector {

    private static final Logger logger = LoggerFactory.getLogger(FabricConnector.class);


    private final ConfigAdapter configAdapter;
    private final CryptoSuite cryptoSuite;
    private HFClient hfClient;
    private int defaultMaxReties = 3;

    public static HFClient createHFClient() throws CryptoException, InvalidArgumentException {
        CryptoSuite cryptoSuite = FabricConfig.getCryptoSuite();
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(cryptoSuite);
        return hfClient;
    }

    public static HFClient createHFClientWithoutCryptoSuite() throws CryptoException, InvalidArgumentException {
        HFClient hfClient = HFClient.createNewInstance();
        return hfClient;
    }


    private void initConnector() throws Exception {
        if (cryptoSuite != null) {
            hfClient = createHFClientWithoutCryptoSuite();
            hfClient.setCryptoSuite(cryptoSuite);
        } else {
            hfClient = createHFClient();
        }

        initUserContext();

        configAdapter.initChannels(hfClient);
    }


    private void initUserContext() throws Exception {
        User user = configAdapter.getUser();
        if (user != null)
            hfClient.setUserContext(user);
        else if (hfClient.getUserContext() == null)
            hfClient.setUserContext(configAdapter.getDefaultUserContext());
    }

    public int getDefaultMaxRetries() {
        return defaultMaxReties;
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxReties = defaultMaxRetries;
    }

    public HFClient getHfClient() {
        return hfClient;
    }

    public Channel getChannel(String channelName) {
        return getHfClient().getChannel(channelName);
    }

    @SuppressWarnings("unused")
    public Channel getDefaultChannel() {
        return getChannel(configAdapter.getDefaultChannelName());
    }

    public void setUserContext(User user) throws Exception {
        hfClient.setUserContext(user);
    }


    public TransactionProposalRequest buildProposalRequest(String function, String chaincode, byte[][] message) {

        final TransactionProposalRequest transactionProposalRequest = hfClient.newTransactionProposalRequest();
        transactionProposalRequest.setChaincodeID(ChaincodeID.newBuilder().setName(chaincode).build());
        transactionProposalRequest.setFcn(function);
        transactionProposalRequest.setArgBytes(message);

        return transactionProposalRequest;
    }

    public CompletableFuture<Collection<ProposalResponse>> sendProposal(TransactionProposalRequest transactionProposalRequest, boolean returnOnlySuccessful) {
        return sendProposal(transactionProposalRequest, configAdapter.getDefaultChannelName(), returnOnlySuccessful);
    }

    public CompletableFuture<Collection<ProposalResponse>> sendProposal(TransactionProposalRequest transactionProposalRequest, String channelName, boolean returnOnlySuccessful) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                Channel channel = hfClient.getChannel(channelName);
                if (channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
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

    @SuppressWarnings("unused")
    public CompletableFuture<BlockEvent.TransactionEvent> sendTransaction(TransactionProposalRequest transactionProposalRequest) {
        return sendTransaction(transactionProposalRequest, configAdapter.getDefaultChannelName());
    }

    public CompletableFuture<BlockEvent.TransactionEvent> sendTransaction(TransactionProposalRequest transactionProposalRequest, String channelName) {

        return sendProposal(transactionProposalRequest, channelName, true).thenCompose(proposalResponses -> {
            CompletableFuture<BlockEvent.TransactionEvent> future = null;
            try {
                Channel channel = hfClient.getChannel(channelName);
                if (channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
                future = channel.sendTransaction(proposalResponses);
            } catch (Exception e) {
                logger.error("Failed to send transaction to channel", e);
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
        return sendQueryRequest(request, configAdapter.getDefaultChannelName());
    }

    public CompletableFuture<byte[]> sendQueryRequest(QueryByChaincodeRequest request, String channelName) {
        return CompletableFuture.supplyAsync(() -> {
            ProposalResponse lastFailProposal = null;
            try {
                Channel channel = hfClient.getChannel(channelName);
                if (channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
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

    @SuppressWarnings("unused")
    public CompletableFuture<byte[]> query(String function, String chaincode, byte[]... message) {
        return query(function, chaincode, configAdapter.getDefaultChannelName(), message);
    }

    public CompletableFuture<byte[]> query(String function, String chaincode, String channelName, byte[]... message) {
        return sendQueryRequest(buildQueryRequest(function, chaincode, message), channelName);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, byte[]... message) {
        return invoke(function, chaincode, configAdapter.getDefaultChannelName(), defaultMaxReties, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, String channelName, byte[]... message) {
        return invoke(function, chaincode, channelName, defaultMaxReties, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, int maxRetries, byte[]... message) {
        return invoke(function, chaincode, configAdapter.getDefaultChannelName(), maxRetries, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, String channelName, int maxRetries, byte[]... message) {
        CompletableFuture<BlockEvent.TransactionEvent> f = sendTransaction(buildProposalRequest(function, chaincode, message), channelName);

        // Here we handle retry 'maxRetries' times
        // Basically we just chain N(='maxRetries') dummy futures that push successful one further
        // In case of exception it checks transaction error code and either push forward the exception or recreates transaction on retry-able errors
        for (int i = 0; i < maxRetries; i++) {
            f = f.thenApply(CompletableFuture::completedFuture)
                    .exceptionally(t -> {
                        try {
                            Throwable cause = t.getCause();
                            if (cause instanceof TransactionEventException) {
                                int validationCode = ((TransactionEventException) cause).getTransactionEvent().getValidationCode();
                                switch (validationCode) {
                                    case MVCC_READ_CONFLICT_VALUE:
                                    case PHANTOM_READ_CONFLICT_VALUE:
                                        logger.warn("ReadSet-related error so we recreate transaction ", t);
                                        return sendTransaction(buildProposalRequest(function, chaincode, message), channelName);
                                    default:
                                        // fail on other Tx errors, retries won't help here
                                        return failedFuture(t);
                                }
                            } else {
                                return failedFuture(cause);
                            }
                        } catch (Throwable e) {
                            // In case of any unexpected errors
                            return failedFuture(e);
                        }
                    })
                    .thenCompose(Function.identity());
        }
        return f;
    }

    // In Java 9 we already have such method but while we are on 8...
    private static <T> CompletableFuture<T> failedFuture(Throwable t) {
        final CompletableFuture<T> cf = new CompletableFuture<>();
        cf.completeExceptionally(t);
        return cf;
    }

    public FabricConnector(ConfigAdapter configAdapter) throws Exception {
        this.configAdapter = configAdapter;
        this.cryptoSuite = null;
        initConnector();
    }

    public FabricConnector(ConfigAdapter configAdapter, CryptoSuite customCryptoSuite) throws Exception {
        this.configAdapter = configAdapter;
        this.cryptoSuite = customCryptoSuite;
        initConnector();
    }
}
