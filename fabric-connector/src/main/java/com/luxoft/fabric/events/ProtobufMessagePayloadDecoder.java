package com.luxoft.fabric.events;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.lang.reflect.Method;

/**
 * Implementation of @link PayloadDecoder interface for decoding Protobuff encoded  messages from payload
 */
public class ProtobufMessagePayloadDecoder implements PayloadDecoder {

    private final Class targetClass;

    public ProtobufMessagePayloadDecoder(Class targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public Message decode(byte[] encodedPayload) throws InvalidProtocolBufferException, ReflectiveOperationException {

        final Method newBuilder = targetClass.getMethod("newBuilder");
        final Message.Builder builder = (Message.Builder) newBuilder.invoke(null);
        final Message message;

        if (Empty.class.isAssignableFrom(targetClass))
            message = Empty.getDefaultInstance();
        else if (Void.class.isAssignableFrom(targetClass))
            message = null;
        else
            message = builder.mergeFrom(encodedPayload).build();

        return message;
    }

    @Override
    public Class getTargetClass() {
        return targetClass;
    }
}
