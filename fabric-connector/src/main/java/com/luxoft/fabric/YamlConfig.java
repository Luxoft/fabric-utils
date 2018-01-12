package com.luxoft.fabric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.Reader;

/**
 * Created by osesov on 04.07.17.
 */
public class YamlConfig {
    JsonNode config;

    public YamlConfig(Reader configReader) throws IOException {
        if (configReader == null)
            config = null;
        else {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            config = mapper.readTree(configReader);
        }
    }

    public JsonNode getRoot() {
        return config;
    }

    public <T> T getValue(Class<T> tClass, String path, T defValue) {
        String s = System.getenv(path);

        if (s == null) {

            if (config == null)
                return defValue;

            final JsonNode value = path.startsWith("/") ? config.at(path) : config.findPath(path); // .findValue(path);
            if (value.isMissingNode())
                return defValue;

            s = value.asText();
        }
        if (tClass == String.class)
            return (T) s;

        else if (tClass == Boolean.class)
            return (T) new Boolean(s);

        else if (tClass == Integer.class)
            return (T) Integer.valueOf(s);

        throw new InternalError(String.format("Unable to cast setting to %s", tClass.getSimpleName()));
    }
}
