package com.luxoft.fabric.model.jackson;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.luxoft.fabric.model.ConfigBuilder;
import com.luxoft.fabric.model.ConfigData;
import com.luxoft.fabric.model.FileReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ConfigModule {
    private static Logger logger = LoggerFactory.getLogger(ConfigModule.class);

    public static String getParentPath(JsonParser p) {
        final String delim = "/";
        JsonStreamContext parsingContext = p.getParsingContext();
        LinkedList<String> s = new LinkedList<>();
        while (parsingContext != null) {
            if (parsingContext.inObject())
                s.addFirst(parsingContext.getCurrentName());
            else if (parsingContext.inArray())
                s.addFirst(String.format("[%s]", parsingContext.getCurrentIndex()));
            else if (parsingContext.inRoot())
                s.addFirst("{ROOT}");

            parsingContext = parsingContext.getParent();
        }

        return String.join(delim, s);
    }

    public static void configure(ObjectMapper mapper) {
        mapper.registerModule(ConfigModule.getModule());
        mapper.addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p, JsonDeserializer<?> deserializer, Object beanOrClass, String propertyName) throws IOException {
                // just log and skip unknown properties
                final String parent = getParentPath(p);
                final String s = p.getTokenLocation().toString();
                logger.warn("Unknown property: {}", parent, propertyName);
                p.skipChildren();
                return true;
            }
        });

    }

    private static SimpleModule getModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(FileReference.class, new FileReferenceDeserializer());
        return module;
    }

    public static class AdminListDeserializer extends StringMapDeserialzier<ConfigData.Admin> {
        public AdminListDeserializer() {
            super(ConfigData.Admin.class);
        }
    }

    public static class PeerListDeserializer extends StringMapDeserialzier<ConfigData.Peer> {
        public PeerListDeserializer() {
            super(ConfigData.Peer.class);
        }
    }

    public static class OrdererListDeserializer extends StringMapDeserialzier<ConfigData.Orderer> {
        public OrdererListDeserializer() {
            super(ConfigData.Orderer.class);
        }
    }

    public static class ChannelListDeserializer extends StringMapDeserialzier<ConfigData.Channel> {
        public ChannelListDeserializer() {
            super(ConfigData.Channel.class);
        }
    }

    public static class CaListDeserializer extends StringMapDeserialzier<ConfigData.CA> {
        public CaListDeserializer() {
            super(ConfigData.CA.class);
        }
    }

    public static class EventhubListDeserializer extends StringMapDeserialzier<ConfigData.Eventhub> {
        public EventhubListDeserializer() {
            super(ConfigData.Eventhub.class);
        }
    }

    public static class ChaincodeListDeserializer extends StringMapDeserialzier<ConfigData.Chaincode> {
        public ChaincodeListDeserializer() {
            super(ConfigData.Chaincode.class);
        }
    }

    public static class ChannelChaincodeDeserializer extends StdDeserializer<List<ConfigData.ChannelChaincode>> {

        protected ChannelChaincodeDeserializer() {
            super(ConfigData.ChannelChaincode.class);
        }

        @Override
        public List<ConfigData.ChannelChaincode> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {

            List<ConfigData.ChannelChaincode> res = new ArrayList<>();

            if (!p.isExpectedStartArrayToken()) {
                throw InvalidFormatException.from(p, "Array expected");
            }

            while (true) {
                String value;
                if ((value = p.nextTextValue()) != null) {
                    res.add(new ConfigBuilder.ChannelChaincode().withName(value).build());
                } else {
                    JsonToken t = p.getCurrentToken();
                    if (t == JsonToken.END_ARRAY) {
                        break;
                    }
                    // Ok: no need to convert Strings, but must recognize nulls
                    if (t == JsonToken.VALUE_NULL) {
                        continue;
                    } else if (t == JsonToken.START_OBJECT) {
                        res.add(p.readValueAs(ConfigData.ChannelChaincode.class));
                    }
                }
            }

            return res;
        }
    }
}
