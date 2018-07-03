package org.hyperledger.fabric.sdk;

import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.fabric.ordering.FabricQueryException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.util.ArrayList;
import java.util.List;

public class TxUtil {

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
