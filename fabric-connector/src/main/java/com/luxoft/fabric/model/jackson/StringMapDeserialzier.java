package com.luxoft.fabric.model.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StringMapDeserialzier<T> extends JsonDeserializer<Map<String, T>> {
    private static Logger logger = LoggerFactory.getLogger(StringMapDeserialzier.class);

    private final Class<T> dataClass;

    public StringMapDeserialzier(Class<T> tClass) {
        dataClass = tClass;
    }

    @Override
    public HashMap<String, T> deserialize(JsonParser parser, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {

        HashMap<String, T> result = new LinkedHashMap<>();
        if (parser.hasToken(JsonToken.START_ARRAY)) { // parse as array
            JsonToken jsonToken;
            while ((jsonToken = parser.nextToken()) != JsonToken.END_ARRAY) {

                if (jsonToken != JsonToken.START_OBJECT)
                    throw InvalidFormatException.from(parser, "Start of expected");

                jsonToken = parser.nextToken();
                readEntry(parser, result, jsonToken);

                jsonToken = parser.nextToken();
                if (jsonToken != JsonToken.END_OBJECT)
                    throw InvalidFormatException.from(parser, "End of object expected");
            }
        } else if (parser.hasToken(JsonToken.START_OBJECT)) {
            JsonToken jsonToken;

            while ((jsonToken = parser.nextToken()) != JsonToken.END_OBJECT) {
                readEntry(parser, result, jsonToken);
            }
        }

        return result;
    }

    private void readEntry(JsonParser parser, HashMap<String, T> result, JsonToken jsonToken) throws IOException {
        if (jsonToken != JsonToken.FIELD_NAME)
            throw InvalidFormatException.from(parser, "Field Name expected");

        final String fieldName = parser.getText();

        jsonToken = parser.nextToken();

        final String path = ConfigModule.getParentPath(parser);
        final T t = parser.readValueAs(dataClass);

        if (t == null) {
            logger.warn("null value is not allowed[{}] at {}", fieldName, path);
        } else if (result.containsKey(fieldName)) {
            logger.warn("duplicate name[{}] at {}", fieldName, path);
        } else {
            result.put(fieldName, t);
        }
    }

}
