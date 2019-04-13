package org.hyperledger.fabric.sdk;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.fabric.events.ordering.FabricQueryException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.util.*;

public class SdkTxUtil {

    public static class TransactionActionInfoEx {
        private final TransactionActionDeserializer actionDeserializer;

        public TransactionActionInfoEx(TransactionActionDeserializer actionDeserializer) {
            this.actionDeserializer = actionDeserializer;
        }

        public int getArgsCount() {
            return actionDeserializer.getPayload().getChaincodeProposalPayload().
                    getChaincodeInvocationSpec().getChaincodeInput().getChaincodeInput().getArgsCount();
        }

        public ByteString getArg(int index) {
            return actionDeserializer.getPayload().getChaincodeProposalPayload().
                    getChaincodeInvocationSpec().getChaincodeInput().getChaincodeInput().getArgs(index);
        }

        public List<ByteString> getArgs() {
            return actionDeserializer.getPayload().getChaincodeProposalPayload().
                    getChaincodeInvocationSpec().getChaincodeInput().getChaincodeInput().getArgsList();
        }

        public byte[] getProposalResponseMessageBytes() {
            return actionDeserializer.getPayload().getAction().
                    getProposalResponsePayload().getExtension().getResponseMessageBytes();
        }

        public int getProposalResponseStatus() {
            return actionDeserializer.getPayload().getAction().getProposalResponsePayload().
                    getExtension().getResponseStatus();
        }
    }

    public static class TransactionInfoEx {
        private final TransactionDeserializer transactionDeserializer;

        TransactionInfoEx(final TransactionDeserializer transactionDeserializer) {
            this.transactionDeserializer = transactionDeserializer;
        }

//        private HeaderDeserializer getHeader() {
//            return transactionDeserializer.getPayload().getHeader();
//        }
//
//        public String getTxId() {
//            return getHeader().getChannelHeader().getTxId();
//        }
//
//        public Instant getTimestamp() {
//            final int nanos = getHeader().getChannelHeader().getTimestamp().getNanos();
//            final long seconds = getHeader().getChannelHeader().getTimestamp().getSeconds();
//
//            return Instant.ofEpochSecond(seconds, nanos);
//        }
//
        public int getActionCount() {
            return transactionDeserializer.getActionsCount();
        }

        public TransactionActionInfoEx getAction(int index) {
            return new TransactionActionInfoEx(transactionDeserializer.getTransactionAction(index));
        }

        public Iterable<TransactionActionInfoEx> getActions() {
            final Iterable<TransactionActionDeserializer> transactionActions = transactionDeserializer.getTransactionActions();
            return new Iterable<TransactionActionInfoEx>() {

                @Override
                public Iterator<TransactionActionInfoEx> iterator() {
                    final Iterator<TransactionActionDeserializer> iterator = transactionActions.iterator();

                    return new Iterator<TransactionActionInfoEx>() {
                        @Override
                        public boolean hasNext() {
                            return iterator.hasNext();
                        }

                        @Override
                        public TransactionActionInfoEx next() {
                            return new TransactionActionInfoEx(iterator.next());
                        }
                    };
                }
            };
        }
    }

    public static TransactionInfoEx getTransaction(TransactionInfo transactionInfo) throws InvalidProtocolBufferException {
        final Common.Envelope envelope = transactionInfo.getEnvelope();
        final Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());

        TransactionDeserializer transactionDeserializer = new TransactionDeserializer(payload.getData());

        //        final FabricTransaction.ProcessedTransaction processedTransaction = transactionInfo.getProcessedTransaction();
//        byte validationCode = (byte)processedTransaction.getValidationCode();
//        final ByteString payload = processedTransaction.getTransactionEnvelope().getPayload();
//
//        final EndorserTransactionEnvDeserializer transactionDeserializer = new EndorserTransactionEnvDeserializer(payload, validationCode);

        return new TransactionInfoEx(transactionDeserializer);
    }

    public static List<ChaincodeEvent> queryEventsByTransactionID(Channel channel, String transactionID) throws InvalidArgumentException, ProposalException, InvalidProtocolBufferException, FabricQueryException {
        final TransactionInfo transactionInfo = FabricQueryException.withGuard(()->
                channel.queryTransactionByID(transactionID));
        return getEventsByTransactionInfo(transactionInfo);
    }

    public static List<ChaincodeEvent> getEventsByTransactionInfo(TransactionInfo transactionInfo) throws InvalidProtocolBufferException {
        final List<ChaincodeEvent> result = new ArrayList<>();
        final Common.Envelope envelope = transactionInfo.getEnvelope();
        final Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());

        TransactionDeserializer transactionDeserializer = new TransactionDeserializer(payload.getData());

        final int actionsCount = transactionDeserializer.getActionsCount();
        for (int j = 0; j < actionsCount; ++j) {
            final TransactionActionDeserializer transactionAction = transactionDeserializer.getTransactionAction(j);
            final ChaincodeEvent event = transactionAction.getPayload().getAction().getProposalResponsePayload().getExtension().getEvent();

            if (event != null)
                result.add(event);
        }
        return result;
    }
}
