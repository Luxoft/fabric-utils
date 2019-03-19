package com.luxoft.fabric;


import com.luxoft.fabric.impl.FabricConnectorImplBasedOnFabricConfig;
import com.luxoft.fabric.impl.FabricConnectorImplBasedOnNetworkConfig;
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
public abstract class  FabricConnector {

    public static HFClient createHFClient() throws CryptoException, InvalidArgumentException {
        CryptoSuite cryptoSuite = FabricConfig.getCryptoSuite();
        HFClient hfClient = HFClient.createNewInstance();
        hfClient.setCryptoSuite(cryptoSuite);
        return hfClient;
    }

    public static class Options {
        EventTracker eventTracker;

        public EventTracker getEventTracker() {
            return eventTracker;
        }

        public Options setEventTracker(EventTracker eventTracker) {
            this.eventTracker = eventTracker;
            return this;
        }
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected HFClient hfClient;


    private String defaultChannelName;
    private int defaultMaxRetries = 3;


    public void initConnector(User user, String defaultChannelName, Boolean initChannels, Options options) throws Exception {
        this.defaultChannelName = defaultChannelName;
        hfClient = createHFClient();

        initUserContext(user);

        if (initChannels)
            initChannels(options);

    }


    protected abstract void initUserContext(User user) throws Exception;


    public abstract void initChannels(Options options) throws Exception;

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public HFClient getHfClient() {
        return hfClient;
    }

    public Channel getChannel(String channelName) {
        return getHfClient().getChannel(channelName);
    }

    @SuppressWarnings("unused")
    public Channel getDefaultChannel() {
        return getChannel(defaultChannelName);
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
        return sendProposal(transactionProposalRequest, defaultChannelName, returnOnlySuccessful);
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
        return sendTransaction(transactionProposalRequest, defaultChannelName);
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
        return sendQueryRequest(request, defaultChannelName);
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
        return query(function, chaincode, defaultChannelName, message);
    }

    public CompletableFuture<byte[]> query(String function, String chaincode, String channelName, byte[]... message) {
        return sendQueryRequest(buildQueryRequest(function, chaincode, message), channelName);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, byte[]... message) {
        return invoke(function, chaincode, defaultChannelName, defaultMaxRetries, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, String channelName, byte[]... message) {
        return invoke(function, chaincode, channelName, defaultMaxRetries, message);
    }

    public CompletableFuture<BlockEvent.TransactionEvent> invoke(String function, String chaincode, int maxRetries, byte[]... message) {
        return invoke(function, chaincode, defaultChannelName, maxRetries, message);
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
                            int validationCode = ((TransactionEventException) t.getCause()).getTransactionEvent().getValidationCode();
                            switch (validationCode) {
                                case MVCC_READ_CONFLICT_VALUE:
                                case PHANTOM_READ_CONFLICT_VALUE:
                                    logger.error("", t);
                                    // if ReadSet-related error we recreate transaction
                                    return sendTransaction(buildProposalRequest(function, chaincode, message), channelName);
                                default:
                                    // fail on other Tx errors, retries won't help here
                                    return failedFuture(t);
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

    public static FabricConnectorImplBasedOnFabricConfig.Builder getFabricConfigBuilder(FabricConfig fabricConfig) {
        return new FabricConnectorImplBasedOnFabricConfig.Builder(fabricConfig);
    }

    public static FabricConnectorImplBasedOnNetworkConfig.Builder getNetworkConfigBuilder(NetworkConfig networkConfig) {
        return new FabricConnectorImplBasedOnNetworkConfig.Builder(networkConfig);
    }

    @SuppressWarnings("unused")
    public abstract User enrollUser(String caKey, String userName, String userSecret) throws Exception;

    @SuppressWarnings("unused")
    public abstract String registerUser(String caKey, String userName, String userAffiliation) throws Exception;


    public abstract static class Builder {

        protected Boolean initChannels = true;
        protected User user;
        protected String defaultChannelName;
        protected Options options;

        public Builder withInitChannels(Boolean initChannels) {
            this.initChannels = initChannels;
            return this;
        }

        public Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public Builder withDefaultChannelName(String defaultChannelName) {
            this.defaultChannelName = defaultChannelName;
            return this;
        }

        public Builder withOptions(Options options) {
            this.options = options;
            return this;
        }

        public abstract FabricConnector build() throws Exception;


    }

}
