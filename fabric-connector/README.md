## Fabric-connector

Hyperledger Fabric Java SDK wrapper for convenient use. Fabric Connector can be created from one of two classes:
- native NetworkConfig
- custom FabricConfig

Below there will be example of each usage:


### How to plug fabricConnector in your project dependencies

#### Gradle
```
repositories { 
    maven { url 'https://jitpack.io' }
}
compile group: 'com.github.Luxoft.fabric-utils', name: 'fabric-connector', version: 1.4.2
```
#### Maven
```
<repository>
    <id>Jitpack</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.Luxoft</groupId>
    <artifactId>fabric-connector</artifactId>
    <version>1.4.4</version>
</dependency>
```

### Creating connector form NetworkConfig

```
NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(networkConfig).build());        
```

### Creating connector form NetworkConfig
```
FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(fabricConfig).build());
```  

### Using connector to execute transaction
Here we assume that we already have Fabric Network with channel **mychannel** and chaincode **mychcode** is installed and instantiated 
```
 byte[] key = "someKey".getBytes();
 byte[] value = UUID.randomUUID().toString().getBytes();

 CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
         "put", "mychcode", "mychannel", key, value);
 Assert.assertNotNull(putEventFuture.get());

 CompletableFuture<byte[]> queryFuture = fabricConnector.query(
         "get", "mychcode", "mychannel", key);
 Assert.assertArrayEquals(value, queryFuture.get());   
```