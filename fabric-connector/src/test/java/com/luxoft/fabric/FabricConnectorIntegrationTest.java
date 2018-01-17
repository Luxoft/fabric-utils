package com.luxoft.fabric;

import com.luxoft.fabric.utils.NetworkManager;
import org.apache.commons.io.IOUtils;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for Fabric connector
 *
 * Sets up environment using default configuration from "files/fabric.yaml"
 */
public class FabricConnectorIntegrationTest {

    private static FabricConfig fabricConfig;

    @BeforeClass
    public static void setUp() throws Exception {
        int exitCode = execInDirectory("./fabric.sh restart", "../files/artifacts/");
        Assert.assertEquals(0, exitCode);

        fabricConfig = new FabricConfig(
                new FileReader("../files/fabric.yaml"),
                "../files");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfig);
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
        FabricConnector fabricConnector = new FabricConnector(fabricConfig);

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(value, queryFuture.get());
    }

    public static int execInDirectory(String cmd, String dir) {
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