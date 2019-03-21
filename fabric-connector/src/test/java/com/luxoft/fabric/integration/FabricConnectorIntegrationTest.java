package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConnector;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Integration tests for Fabric connector
 *
 * Sets up environment using default configuration from "files/fabric.yaml"
 */
public class FabricConnectorIntegrationTest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(FabricConnectorIntegrationTest.class);
    private static final  String NETWORK_CONFIG_FILE = FabricConnectorIntegrationTest.class.getClassLoader().getResource("network-config.yaml").getFile();

    private static final String CA_KEY = "ca.org1.example.com";
    private static final String USER_AFFILATION = "org1";
    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "adminpw";



    /**
     * Write smth to blockchain and query it
     */
    @Test
    public void testSanityCheck() throws Exception {
        LOG.info("Starting SanityCheck");
        FabricConnector fabricConnector = FabricConnector.getFabricConfigBuilder(fabricConfig).build();

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(value, queryFuture.get());
        LOG.info("Finished SanityCheck");
    }

    @Test
    public void testNetworkConfigSanityCheck() throws Exception {
        LOG.info("Starting SanityCheck");
        
        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        FabricConnector fabricConnector = FabricConnector.getNetworkConfigBuilder(networkConfig).build();

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(value, queryFuture.get());
        LOG.info("Finished SanityCheck");
    }

    @Test
    public void testTxRace() throws Exception {
        LOG.info("Starting testTxRace");
        FabricConnector fabricConnector = FabricConnector.getFabricConfigBuilder(fabricConfig).build();

        AtomicInteger success = new AtomicInteger();

        byte[] key = "someKey".getBytes();
        String value1 = "value1";
        String value2 = "value2";

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value1.getBytes());

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture2 = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value2.getBytes());

        putEventFuture.exceptionally(t -> {
            t.printStackTrace();
            fail();
            return null;
        }).thenAcceptAsync(tx -> {
            System.out.println("Tx 1 finished");
            assertTrue(tx.isValid());
            success.getAndIncrement();
        });

        putEventFuture2.exceptionally(t -> {
            t.printStackTrace();
            fail();
            return null;
        }).thenAcceptAsync(tx -> {
            System.out.println("Tx 2 finished");
            assertTrue(tx.isValid());
            success.getAndIncrement();
        });

        // To wait until all tx commit
        Thread.sleep(10000);

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);

        String finalValue = new String(queryFuture.get());

        // Its race so anyone can finish first
        assertTrue(finalValue.equals(value1) || finalValue.equals(value2));
        assertEquals(2, success.get());
        LOG.info("Finished testTxRace");
    }


    @Test
    public void enrollAndRegisterUsingFabricConfigTest() throws Exception {

        FabricConnector fabricConnector = FabricConnector.getFabricConfigBuilder(fabricConfig).build();
        User adminUser = fabricConnector.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = fabricConnector.registerUser(CA_KEY, "testUser1", USER_AFFILATION);

        Assert.assertNotNull(userSecret);
    }

    @Test
    public void enrollAndRegisterUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        FabricConnector fabricConnector = FabricConnector.getNetworkConfigBuilder(networkConfig).build();
        User adminUser = fabricConnector.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = fabricConnector.registerUser(CA_KEY, "testUser2", USER_AFFILATION);

        Assert.assertNotNull(userSecret);

    }


}