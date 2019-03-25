package com.luxoft.fabric.events;

/**
 * Interface which allows decode arbitrary message types coming from chaincode.
 */
public interface PayloadDecoder {

    Object decode(byte[] encodedPayload) throws Exception;

    Class getTargetClass();
}
