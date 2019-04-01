package com.luxoft.fabric.events;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Implementation of @link PayloadDecoder interface for decoding Strings from payload
 */
public class StringPayloadDecoder implements PayloadDecoder<String> {

    private final Class<String> targetClass = String.class;
    private Charset encoding;

    @Override
    public String decode(byte[] encodedPayload) {
        return new String(encodedPayload, encoding);
    }

    @Override
    public Class<String> getTargetClass() {
        return targetClass;
    }

    public StringPayloadDecoder(Charset encoding) {
        this.encoding = encoding;
    }

    @SuppressWarnings("unused")
    public StringPayloadDecoder() {
        this(StandardCharsets.UTF_8);
    }
}
