package org.hyperledger.fabric.sdk;

import com.google.protobuf.ByteString;

public class ChaincodeEvent
{
    private final String chaincodeId;
    private final String txId;
    private final String eventName;
    private final byte[] payload;

    public ChaincodeEvent(String chaincodeId, String txId, String eventName, byte[] payload) {

        this.chaincodeId = chaincodeId;
        this.txId = txId;
        this.eventName = eventName;
        this.payload = payload;
    }

    public String getChaincodeId() {
        return chaincodeId;
    }

    public String getTxId() {
        return txId;
    }

    public String getEventName() {
        return eventName;
    }

    public byte[] getPayload() {
        return payload;
    }
}
