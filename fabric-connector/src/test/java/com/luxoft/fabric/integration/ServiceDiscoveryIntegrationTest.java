package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.config.ConfigAdapter;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ServiceDiscoveryIntegrationTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(FabricConnectorIntegrationTest.class);

    /*Attention!  Test will work only in case you have following lines in your /etc/hosts file:
        127.0.0.1       orderer.example.com
        127.0.0.1       peer0.org1.example.com
     This is necessary, because during Service Discovery real names of peers and orderer are returned.
     Those name's make sense inside of docker container, but not in local java application.
     Alternative solutions would be running our own DNS inside of this Java process or using a lot of reflexion to slip the names after they were discovered.
     I believe having customs /etc/hosts is the cleanest solution, though not ideal.
    */
    @Test
    public void testServiceDiscoveryWithFabricConfig() throws Exception {
        logger.info("Starting SanityCheck");


        FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(fabricConfigServiceDiscovery).build());

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(value, queryFuture.get());
        logger.info("Finished SanityCheck");
    }

    /*Attention!  Test will work only in case you have following lines in your /etc/hosts file:
        127.0.0.1       orderer.example.com
        127.0.0.1       peer0.org1.example.com
     This is necessary, because during Service Discovery real names of peers and orderer are returned.
     Those name's make sense inside of docker container, but not in local java application.
     Alternative solutions would be running our own DNS inside of this Java process or using a lot of reflexion to slip the names after they were discovered.
     I believe having customs /etc/hosts is the cleanest solution, though not ideal.
    */
    @Test
    public void testServiceDiscoveryWithNetworkConfig() throws Exception {
        logger.info("Starting SanityCheck");

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG__SERVICE_DISCOVERY_FILE));

        FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(networkConfig).build());

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(value, queryFuture.get());
        logger.info("Finished SanityCheck");
    }
}
