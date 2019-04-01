package com.luxoft.fabric.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.fabric.events.ordering.FabricQueryException;
import org.hyperledger.fabric.sdk.*;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric.sdk.exception.ProposalException;

import java.util.*;

public class TxUtils {
    public static Iterator<ChaincodeEvent> getBlockEvents(BlockInfo blockInfo) {

        Iterator<ChaincodeEvent> iterator = new Iterator<ChaincodeEvent>() {
            final Iterator<BlockInfo.EnvelopeInfo> envelopeInfoIterator = blockInfo.getEnvelopeInfos().iterator();
            Iterator<BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo> actionIterator = Collections.emptyIterator();

            boolean hasNext = false;
            ChaincodeEvent nextObject = null;

            private ChaincodeEvent nextObject() {
                checkNextObject:
                while (true) {
                    while (actionIterator.hasNext()) {
                        final BlockInfo.TransactionEnvelopeInfo.TransactionActionInfo actionInfo = actionIterator.next();

                        final ChaincodeEvent event = actionInfo.getEvent();
                        if (event != null)
                            return event;
                    }

                    while (envelopeInfoIterator.hasNext()) {
                        final BlockInfo.EnvelopeInfo envelopeInfo = envelopeInfoIterator.next();

                        if (envelopeInfo.isValid() && envelopeInfo instanceof BlockInfo.TransactionEnvelopeInfo) {
                            final BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo = (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;
                            actionIterator = transactionEnvelopeInfo.getTransactionActionInfos().iterator();
                            continue checkNextObject;
                        }
                    }
                    return null;
                }
            }

            @Override
            public boolean hasNext() {
                if (!hasNext) {
                    nextObject = nextObject();
                    hasNext = nextObject != null;
                }

                return hasNext;
            }

            @Override
            public ChaincodeEvent next() {
                hasNext();
                if (!hasNext)
                    throw new NoSuchElementException();
                hasNext = false;
                return nextObject;
            }
        };

        return iterator;
    }

    public static Iterator<BlockInfo.TransactionEnvelopeInfo> getBlockTransactions(BlockInfo blockInfo) {

        Iterator<BlockInfo.TransactionEnvelopeInfo> iterator = new Iterator<BlockInfo.TransactionEnvelopeInfo>() {
            final Iterator<BlockInfo.EnvelopeInfo> envelopeInfoIterator = blockInfo.getEnvelopeInfos().iterator();

            boolean hasNext = false;
            BlockInfo.TransactionEnvelopeInfo nextObject = null;

            private BlockInfo.TransactionEnvelopeInfo nextObject() {
                while (envelopeInfoIterator.hasNext()) {
                    final BlockInfo.EnvelopeInfo envelopeInfo = envelopeInfoIterator.next();

                    if (envelopeInfo.isValid() && envelopeInfo instanceof BlockInfo.TransactionEnvelopeInfo)
                        return (BlockInfo.TransactionEnvelopeInfo) envelopeInfo;
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                if (!hasNext) {
                    nextObject = nextObject();
                    hasNext = nextObject != null;
                }

                return hasNext;
            }

            @Override
            public BlockInfo.TransactionEnvelopeInfo next() {
                hasNext();
                if (!hasNext)
                    throw new NoSuchElementException();
                hasNext = false;
                return nextObject;
            }
        };

        return iterator;
    }

    public static List<ChaincodeEvent> getTransactionEvents(BlockInfo.TransactionEnvelopeInfo transactionEnvelopeInfo) {
        List<ChaincodeEvent> result = new ArrayList<>();

        transactionEnvelopeInfo.getTransactionActionInfos().forEach(transactionActionInfo -> {
            final ChaincodeEvent event = transactionActionInfo.getEvent();
            if (event != null)
                result.add(event);
        });

        return result;
    }

    public static List<ChaincodeEvent> queryEventsByTransactionID(Channel channel, String transactionID) throws InvalidArgumentException, ProposalException, InvalidProtocolBufferException, FabricQueryException {
        return SdkTxUtil.queryEventsByTransactionID(channel, transactionID);
    }

    public static List<ChaincodeEvent> getTransactionEvents(TransactionInfo transactionInfo) throws InvalidProtocolBufferException {
        return SdkTxUtil.getEventsByTransactionInfo(transactionInfo);
    }

    public static SdkTxUtil.TransactionInfoEx getTransaction(TransactionInfo transactionInfo) throws InvalidProtocolBufferException {
        return SdkTxUtil.getTransaction(transactionInfo);
    }

}
