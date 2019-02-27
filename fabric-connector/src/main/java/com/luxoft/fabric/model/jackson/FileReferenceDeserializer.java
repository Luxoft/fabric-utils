package com.luxoft.fabric.model.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.luxoft.fabric.model.FileReference;

import java.io.IOException;

public class FileReferenceDeserializer extends StdScalarDeserializer<FileReference> {

    protected FileReferenceDeserializer() {
        super(FileReference.class);
    }

    @Override
    public FileReference deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {

        if (p.hasToken(JsonToken.VALUE_STRING)) {
            return new FileReference(p.getText());
        }
        JsonToken t = p.getCurrentToken();
        // [databind#381]
        if (t == JsonToken.START_ARRAY) {
            return _deserializeFromArray(p, ctxt);
        }
        return (FileReference) ctxt.handleUnexpectedToken(_valueClass, p);
    }
}
