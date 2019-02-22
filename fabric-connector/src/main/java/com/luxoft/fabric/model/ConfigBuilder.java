package com.luxoft.fabric.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigBuilder {

    public static class Admin {
        private String name;
        private FileReference cert;
        private FileReference privateKey;
        private String mspID;

        public Admin withName(String name) {
            this.name = name;
            return this;
        }

        public Admin withCert(FileReference cert) {
            this.cert = cert;
            return this;
        }

        public Admin withPrivateKey(FileReference privateKey) {
            this.privateKey = privateKey;
            return this;
        }

        public Admin withMspID(String mspID) {
            this.mspID = mspID;
            return this;
        }

        public ConfigData.Admin build() {
            return new ConfigData.Admin(name, cert, privateKey, mspID);
        }
    }

    public static class CA {
        private String url;
        private FileReference pemFile;
        private String adminLogin;
        private String adminSecret;
        private String mspID;
        private Map<String, String> properties = new LinkedHashMap<>();

        public CA withUrl(String url) {
            this.url = url;
            return this;
        }

        public CA withPemFile(FileReference pemFile) {
            this.pemFile = pemFile;
            return this;
        }

        public CA withAdminLogin(String adminLogin) {
            this.adminLogin = adminLogin;
            return this;
        }

        public CA withAdminSecret(String adminSecret) {
            this.adminSecret = adminSecret;
            return this;
        }

        public CA withMspID(String mspID) {
            this.mspID = mspID;
            return this;
        }

        public CA withProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public CA addProperty(String key, String value) {
            this.properties.put(key, value);
            return this;
        }

        public CA addProperties(Map<String, String> m) {
            this.properties.putAll(m);
            return this;
        }

        public ConfigData.CA build() {
            return new ConfigData.CA(url, pemFile, adminLogin, adminSecret, mspID, properties);
        }
    }

    public static class ChannelChaincode {
        private String name;
        private JsonNode collectionPolicy;

        public ChannelChaincode withName(String name) {
            this.name = name;
            return this;
        }

        public ChannelChaincode withCollectionPolicy(JsonNode collectionPolicy) {
            this.collectionPolicy = collectionPolicy;
            return this;
        }

        public ConfigData.ChannelChaincode build() {
            return new ConfigData.ChannelChaincode(name, collectionPolicy);
        }
    }

    public static class Users {
        private String userAffiliation;
        private Collection<String> list;
        private String caKey;
        private String destFilesPath;
        private String privateKeyFileName;
        private String certFileName;

        public Users withUserAffiliation(String userAffiliation) {
            this.userAffiliation = userAffiliation;
            return this;
        }

        public Users withList(Collection<String> list) {
            this.list = list;
            return this;
        }

        public Users withCaKey(String caKey) {
            this.caKey = caKey;
            return this;
        }

        public Users withDestFilesPath(String destFilesPath) {
            this.destFilesPath = destFilesPath;
            return this;
        }

        public Users withPrivateKeyFileName(String privateKeyFileName) {
            this.privateKeyFileName = privateKeyFileName;
            return this;
        }

        public Users withCertFileName(String certFileName) {
            this.certFileName = certFileName;
            return this;
        }

        public ConfigData.Users build() {
            return new ConfigData.Users(userAffiliation, list, caKey, destFilesPath, privateKeyFileName, certFileName);
        }
    }

    public static class Chaincode {
        private String id;
        private String sourceLocation;
        private FileReference sourceLocationPrefix;
        private String version;
        private String type;
        private Collection<String> initArguments;
        private JsonNode collectionPolicy;
        private FileReference endorsementPolicy;

        public Chaincode withId(String id) {
            this.id = id;
            return this;
        }

        public Chaincode withSourceLocation(String sourceLocation) {
            this.sourceLocation = sourceLocation;
            return this;
        }

        public Chaincode withSourceLocationPrefix(FileReference sourceLocationPrefix) {
            this.sourceLocationPrefix = sourceLocationPrefix;
            return this;
        }

        public Chaincode withVersion(String version) {
            this.version = version;
            return this;
        }

        public Chaincode withType(String type) {
            this.type = type;
            return this;
        }

        public Chaincode withInitArguments(Collection<String> initArguments) {
            this.initArguments = initArguments;
            return this;
        }

        public Chaincode withCollectionPolicy(JsonNode collectionPolicy) {
            this.collectionPolicy = collectionPolicy;
            return this;
        }

        public Chaincode withEndorsementPolicy(FileReference endorsementPolicy) {
            this.endorsementPolicy = endorsementPolicy;
            return this;
        }

        public ConfigData.Chaincode build() {
            return new ConfigData.Chaincode(id, sourceLocation, sourceLocationPrefix, version, type, initArguments, collectionPolicy, endorsementPolicy);
        }
    }

    public static class Channel {
        private String admin;
        private Collection<String> orderers;
        private Collection<String> peers;
        private Collection<String> eventhubs;
        private FileReference txFile;
        private Collection<ConfigData.ChannelChaincode> chaincodes;

        public Channel withAdmin(String admin) {
            this.admin = admin;
            return this;
        }

        public Channel withOrderers(Collection<String> orderers) {
            this.orderers = orderers;
            return this;
        }

        public Channel withPeers(Collection<String> peers) {
            this.peers = peers;
            return this;
        }

        public Channel withEventhubs(Collection<String> eventhubs) {
            this.eventhubs = eventhubs;
            return this;
        }

        public Channel withTxFile(FileReference txFile) {
            this.txFile = txFile;
            return this;
        }

        public Channel withChaincodes(Collection<ConfigData.ChannelChaincode> chaincodes) {
            this.chaincodes = chaincodes;
            return this;
        }

        public ConfigData.Channel build() {
            return new ConfigData.Channel(admin, orderers, peers, eventhubs, txFile, chaincodes);
        }
    }

    public static class EventhubBuilder {
        private String url;
        private String name;
        private FileReference pemFile;
        private Map<String, String> properties;

        public EventhubBuilder withUrl(String url) {
            this.url = url;
            return this;
        }

        public EventhubBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public EventhubBuilder withPemFile(FileReference pemFile) {
            this.pemFile = pemFile;
            return this;
        }

        public EventhubBuilder withProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public ConfigData.Eventhub build() {
            return new ConfigData.Eventhub(url, name, pemFile, properties);
        }
    }

    public static class OrdererBuilder {
        private String url;
        private FileReference pemFile;
        private Map<String, String> properties;

        public OrdererBuilder withUrl(String url) {
            this.url = url;
            return this;
        }

        public OrdererBuilder withPemFile(FileReference pemFile) {
            this.pemFile = pemFile;
            return this;
        }

        public OrdererBuilder withProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public ConfigData.Orderer build() {
            return new ConfigData.Orderer(url, pemFile, properties);
        }
    }

    public static class PeerBuilder {
        private String url;
        private String name;
        private FileReference pemFile;
        private Map<String, String> properties;

        public PeerBuilder withUrl(String url) {
            this.url = url;
            return this;
        }

        public PeerBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public PeerBuilder withPemFile(FileReference pemFile) {
            this.pemFile = pemFile;
            return this;
        }

        public PeerBuilder withProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public ConfigData.Peer build() {
            return new ConfigData.Peer(url, name, pemFile, properties);
        }
    }

    public static class Config {
        private Map<String, ConfigData.Admin> admins;
        private Map<String, ConfigData.Peer> peers;
        private Map<String, ConfigData.Orderer> orderers;
        private Map<String, ConfigData.Channel> channels;
        private Map<String, ConfigData.CA> cas;
        private Map<String, ConfigData.Eventhub> eventhubs;
        private Map<String, ConfigData.Chaincode> chaincodes;
        private ConfigData.Users users;

        public Config withAdmins(Map<String, ConfigData.Admin> admins) {
            this.admins = admins;
            return this;
        }

        public Config withPeers(Map<String, ConfigData.Peer> peers) {
            this.peers = peers;
            return this;
        }

        public Config withOrderers(Map<String, ConfigData.Orderer> orderers) {
            this.orderers = orderers;
            return this;
        }

        public Config withChannels(Map<String, ConfigData.Channel> channels) {
            this.channels = channels;
            return this;
        }

        public Config withCas(Map<String, ConfigData.CA> cas) {
            this.cas = cas;
            return this;
        }

        public Config withEventhubs(Map<String, ConfigData.Eventhub> eventhubs) {
            this.eventhubs = eventhubs;
            return this;
        }

        public Config withChaincodes(Map<String, ConfigData.Chaincode> chaincodes) {
            this.chaincodes = chaincodes;
            return this;
        }

        public Config withUsers(ConfigData.Users users) {
            this.users = users;
            return this;
        }

        public ConfigData.Root build() {
            return new ConfigData.Root(admins, peers, orderers, channels, cas, eventhubs, chaincodes, users);
        }
    }
}
