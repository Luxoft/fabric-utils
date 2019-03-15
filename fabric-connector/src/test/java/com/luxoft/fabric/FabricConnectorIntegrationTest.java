package com.luxoft.fabric;

import com.luxoft.fabric.config.NetworkManager;
import org.apache.commons.io.IOUtils;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.Charset;
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
public class FabricConnectorIntegrationTest {

    private static FabricConfig fabricConfig;
    private static final Logger LOG = LoggerFactory.getLogger(FabricConnectorIntegrationTest.class);
    private static final  String NETWORK_CONFIG_FILE = FabricConnectorIntegrationTest.class.getClassLoader().getResource("network-config.yaml").getFile();

    @BeforeClass
    public static void setUp() throws Exception {
        LOG.info("Starting preparation");
        int exitCode = execInDirectory("./fabric.sh restart", "../files/artifacts/");

        LOG.info("Waiting some time to give network the time to initialize");
        Thread.sleep(5000);
        LOG.info("Restarted network");
        Assert.assertEquals(0, exitCode);

        fabricConfig = FabricConfig.getConfigFromFile("../files/fabric.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfig);
        LOG.info("Finished preparation");
    }

    @AfterClass
    public static void tearDown() {
        execInDirectory("./fabric.sh clean", "../files/artifacts/");
    }

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

    private static int execInDirectory(String cmd, String dir) {
        try {
            Process process = new ProcessBuilder()
                    .command(cmd.split(" "))
                    .directory(new File(System.getProperty("user.dir") + File.separator + dir).getCanonicalFile())
                    .start();

            int exitCode = process.waitFor();

            System.out.println(IOUtils.toString(process.getInputStream(), Charset.defaultCharset()));
            System.err.println(IOUtils.toString(process.getErrorStream(), Charset.defaultCharset()));

            return exitCode;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}