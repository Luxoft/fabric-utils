package org.hyperledger.fabric.sdk;

/**
 * ChaincodeEventListener implemented by classes to receive chaincode events.
 */

public interface ChaincodeEventListener
{
    /**
     * Receiving a chaincode event. ChaincodeEventListener should not be long lived as they can take up thread resources.
     *
     * @param handle         The handle of the chaincode event listener that produced this event.
     * @param blockEvent     The block event information that contained the chaincode event. See {@link BlockEvent}
     * @param chaincodeEvent The chaincode event. see {@link ChaincodeEvent}
     */
    void received(String handle, BlockEvent blockEvent, ChaincodeEvent chaincodeEvent);
}
