package com.luxoft.fabric.events;

import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.lang.reflect.Method;

/**
 * Implementation of @link PayloadDecoder interface for decoding Protobuff encoded  messages from payload
 */
public class ProtobufMessagePayloadDecoder<T extends Message> implements PayloadDecoder<T> {

    private final Class<T> targetClass;

    public ProtobufMessagePayloadDecoder(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T decode(byte[] encodedPayload) throws InvalidProtocolBufferException, ReflectiveOperationException {

        final Method newBuilder = targetClass.getMethod("newBuilder");
        final Message.Builder builder = (Message.Builder) newBuilder.invoke(null);
        final T message;


        if (Empty.class.isAssignableFrom(targetClass))
            message = (T)Empty.getDefaultInstance();
        else if (Void.class.isAssignableFrom(targetClass))
            message = null;
        else
            message = (T)builder.mergeFrom(encodedPayload).build();

        return message;
    }

    @Override
    public Class<T> getTargetClass() {
        return targetClass;
    }
}
