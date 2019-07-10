package com.luxoft.fabric.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.luxoft.fabric.model.jackson.ConfigModule;

import java.util.*;

public class ConfigData {

    public static class Root {

        @JsonDeserialize(using = ConfigModule.AdminListDeserializer.class/*, as = LinkedHashMap.class*/)
        public final Map<String, Admin> admins;

        @JsonDeserialize(using = ConfigModule.PeerListDeserializer.class/*, as = LinkedHashMap.class*/)
        public final Map<String, Peer> peers;

        @JsonDeserialize(using = ConfigModule.OrdererListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, Orderer> orderers;

        @JsonDeserialize(using = ConfigModule.ChannelListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, Channel> channels;

        @JsonDeserialize(using = ConfigModule.CaListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, CA> cas;

        @JsonDeserialize(using = ConfigModule.EventhubListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, Eventhub> eventhubs;

        @JsonDeserialize(using = ConfigModule.ChaincodeListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, Chaincode> chaincodes;

        public final Users users;

        // Jackson specific constructor
        private Root() {
            this(null, null, null, null, null, null, null, null);
        }

        public Root(Map<String, Admin> admins,
                      Map<String, Peer> peers,
                      Map<String, Orderer> orderers,
                      Map<String, Channel> channels,
                      Map<String, CA> cas,
                      Map<String, Eventhub> eventhubs,
                      Map<String, Chaincode> chaincodes,
                      Users users) {
            this.admins = buildMap(admins);
            this.peers = buildMap(peers);
            this.orderers = buildMap(orderers);
            this.channels = buildMap(channels);
            this.cas = buildMap(cas);
            this.eventhubs = buildMap(eventhubs);
            this.chaincodes = buildMap(chaincodes);
            this.users = users;
        }
    }

    // for some reasons, specifying builders here disables
    // custom deserializers, used to process collection of objects
    // from top-level object
    public static class Admin {
        public final String name;
        public final FileReference cert;
        public final FileReference privateKey;
        public final String mspID;

        // Jackson specific constructor
        private Admin() {
            this(null, null, null, null);
        }

        public Admin(String name, FileReference cert, FileReference privateKey, String mspID) {
            this.name = name;
            this.cert = cert;
            this.privateKey = privateKey;
            this.mspID = mspID;
        }
    }

    public static class CA {
        public final String url;
        public final FileReference pemFile;
        public final String adminLogin;
        public final String adminSecret;
        public final String mspID;

        @JsonDeserialize(as = LinkedHashMap.class)
        public final Map<String, String> properties;

        // Jackson specific constructor
        private CA() {
            this(null, null, null, null, null, null);
        }

        public CA(String url,
                  FileReference pemFile,
                  String adminLogin,
                  String adminSecret,
                  String mspID,
                  Map<String, String> properties) {
            this.url = url;
            this.pemFile = pemFile;
            this.adminLogin = adminLogin;
            this.adminSecret = adminSecret;
            this.mspID = mspID;
            this.properties = buildMap(properties);
        }
    }

    public static class ChannelChaincode {
        public final String name;
        public final JsonNode collectionPolicy;

        // Jackson specific constructor
        private ChannelChaincode() {
            this(null, null);
        }

        public ChannelChaincode(String name, JsonNode collectionPolicy) {
            this.name = name;
            this.collectionPolicy = collectionPolicy;
        }

    }

    public static class ChannelPeer {

        public final Map<String, Boolean> roles;

        // Jackson specific constructor
        private ChannelPeer() {
            this(null);
        }

        public ChannelPeer(Map<String, Boolean> roles) {
            this.roles = roles;
        }
    }

    public static class Channel {
        public final String admin;

        @JsonDeserialize(as = LinkedHashSet.class)
        public final Set<String> orderers;

        @JsonDeserialize(using = ConfigModule.ChannelPeerListDeserializer.class, as = LinkedHashMap.class)
        public final Map<String, ChannelPeer> peers;

        @JsonDeserialize(as = LinkedHashSet.class)
        public final Set<String> eventhubs;

        public final FileReference txFile;

        @JsonDeserialize(using = ConfigModule.ChannelChaincodeDeserializer.class, as = ArrayList.class)
        public final List<ChannelChaincode> chaincodes;

        // Jackson specific constructor
        private Channel() {
            this(null, null, null, null, null, null);
        }

        public Channel(String admin,
                       Collection<String> orderers,
                       Map<String, ChannelPeer> peers,
                       Collection<String> eventhubs,
                       FileReference txFile,
                       Collection<ChannelChaincode> chaincodes) {
            this.admin = admin;
            this.txFile = txFile;

            this.orderers = buildSet(orderers);
            this.peers = buildMap(peers);
            this.eventhubs = buildSet(eventhubs);
            this.chaincodes = buildList(chaincodes);
        }

    }

    public static class Eventhub {
        public final String url;
        public final String name;
        public final FileReference pemFile;

        @JsonDeserialize(as = LinkedHashMap.class)
        public final Map<String, String> properties;

        // Jackson specific constructor
        private Eventhub() {
            this(null, null, null, null);
        }

        public Eventhub(String url, String name, FileReference pemFile, Map<String, String> properties) {
            this.url = url;
            this.name = name;
            this.pemFile = pemFile;
            this.properties = buildMap(properties);
        }
    }

    public static class Orderer {
        public final String url;
        public final FileReference pemFile;

        @JsonDeserialize(as = LinkedHashMap.class)
        public final Map<String, String> properties;

        // Jackson specific constructor
        private Orderer() {
            this(null, null, null);
        }

        Orderer(String url, FileReference pemFile, Map<String, String> properties) {
            this.url = url;
            this.pemFile = pemFile;
            this.properties = buildMap(properties);
        }
    }

    public static class Peer {
        public final String url;
        public final String name;


        @JsonProperty("external")
        public Boolean isExternal;
        public final FileReference pemFile;

        @JsonDeserialize(as = LinkedHashMap.class)
        public final Map<String, String> properties;

        // Jackson specific constructor
        private Peer() {
            this(null, null, null, null, null);
        }

        public Peer(String url, String name, FileReference pemFile, Boolean isExternal, Map<String, String> properties) {
            this.url = url;
            this.name = name;
            this.pemFile = pemFile;
            this.isExternal = false; //default value
            this.properties = buildMap(properties);
        }

    }

    public static class Chaincode {
        public final String id;
        public final String sourceLocation;
        public final FileReference sourceLocationPrefix;
        public final String version;
        public final String type;

        @JsonDeserialize(as = ArrayList.class)
        public final List<String> initArguments;
        public final JsonNode collectionPolicy;
        public final FileReference endorsementPolicy;

        // Jackson specific constructor
        private Chaincode() {
            this(null, null, null, null, null, null, null, null);
        }

        public Chaincode(String id,
                         String sourceLocation,
                         FileReference sourceLocationPrefix,
                         String version,
                         String type,
                         Collection<String> initArguments,
                         JsonNode collectionPolicy,
                         FileReference endorsementPolicy) {
            this.id = id;
            this.sourceLocation = sourceLocation;
            this.sourceLocationPrefix = sourceLocationPrefix;
            this.version = version;
            this.type = type;
            this.initArguments = buildList(initArguments);
            this.collectionPolicy = collectionPolicy;
            this.endorsementPolicy = endorsementPolicy;
        }

    }

    public static class Users {
        public final String userAffiliation;
        @JsonDeserialize(as = ArrayList.class)
        public final List<String> list;
        public final String caKey;
        public final String destFilesPath;
        public final String privateKeyFileName;
        public final String certFileName;

        // Jackson specific constructor
        protected Users() {
            this(null, null, null, null, null, null);
        }

        public Users(String userAffiliation,
                     Collection<String> list,
                     String caKey,
                     String destFilesPath,
                     String privateKeyFileName,
                     String certFileName) {
            this.userAffiliation = userAffiliation;
            this.list = buildList(list);
            this.caKey = caKey;
            this.destFilesPath = destFilesPath;
            this.privateKeyFileName = privateKeyFileName;
            this.certFileName = certFileName;
        }

    }


    ///////////////////////////////////////////////////////////////////
    // Utilities
    ///////////////////////////////////////////////////////////////////

    private static <T> List<T> buildList(Collection<T> data) {
        if (data == null)
            return Collections.emptyList();

        else if (data instanceof List)
            return Collections.unmodifiableList((List<T>) data);
        else
            return Collections.unmodifiableList(new ArrayList<>(data));
    }

    private static <T, V> Map<T, V> buildMap(Map<T, V> data) {
        if (data == null)
            return Collections.emptyMap();

        if (data instanceof NavigableMap)
            return Collections.unmodifiableNavigableMap((NavigableMap<T, V>) data);
        else if (data instanceof SortedMap)
            return Collections.unmodifiableSortedMap((SortedMap<T, V>) data);
        else
            return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    private static <T> Set<T> buildSet(Collection<T> data) {
        if (data == null)
            return Collections.emptySet();

        if (data instanceof NavigableSet)
            return Collections.unmodifiableNavigableSet((NavigableSet<T>) data);
        else if (data instanceof SortedSet)
            return Collections.unmodifiableSortedSet((SortedSet<T>) data);
        else
            return Collections.unmodifiableSet(new LinkedHashSet<>(data));
    }
}
