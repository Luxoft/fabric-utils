package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.config.ConfigAdapter;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class PrivateDataIntegrationTest extends TwoOrgsBaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PrivateDataIntegrationTest.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    /**
     * Write smth to blockchain and query it
     */
    @Test
    public void testPositiveCase() throws Exception {
        logger.info("Starting SanityCheck");

        FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(fabricConfigOrg1).build());

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        putToBcPrivate(fabricConnector, key, value);

        //Replace this by querying using another peer form the same organization
        getFromBcPrivate(fabricConnector, key, value);

        logger.info("Finished SanityCheck");
    }

    //TODO: IMPORTANT SECURITY ISSUE: memberOnlyRead property does not work
    // Test is passing only because of check in the chaincode before returning data
    @Test
    public void testNegativeCase() throws Exception {
        logger.info("Starting SanityCheck");

        FabricConnector fabricConnector = new FabricConnector(ConfigAdapter.getBuilder(fabricConfigOrg1).build());
        FabricConnector fabricConnectorOrg2 = new FabricConnector(ConfigAdapter.getBuilder(fabricConfigOrg2).build());

        byte[] key = "someKey".getBytes();
        byte[] value = UUID.randomUUID().toString().getBytes();

        putToBcPrivate(fabricConnector, key, value);

        //Replace this by querying using another peer form the same organization
        getFromBcPrivate(fabricConnector, key, value);

        exceptionRule.expect(ExecutionException.class);
        exceptionRule.expectMessage("Forbidden");

        getFromBcPrivate(fabricConnectorOrg2, key, value);

        logger.info("Finished SanityCheck");
    }


    private void putToBc(FabricConnector fabricConnector, byte[] key, byte[] value) throws ExecutionException, InterruptedException, InvalidArgumentException {

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, value);
        Assert.assertNotNull(putEventFuture.get());

    }

    private void putToBcPrivate(FabricConnector fabricConnector, byte[] key, byte[] value) throws ExecutionException, InterruptedException, InvalidArgumentException {

        Map<String, byte[]> transientMap = new HashMap<>();

        transientMap.put("key", key);
        transientMap.put("value", value);

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "putPrivate", "mychcode", "mychannel", transientMap);
        Assert.assertNotNull(putEventFuture.get());

    }


    private void getFromBc(FabricConnector fabricConnector, byte[] key, byte[] expectedValue) throws ExecutionException, InterruptedException, InvalidArgumentException {

        //Replace this by querying using another peer form the same organization
        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);
        Assert.assertArrayEquals(expectedValue, queryFuture.get());

    }

    private void getFromBcPrivate(FabricConnector fabricConnector, byte[] key, byte[] expectedValue) throws ExecutionException, InterruptedException, InvalidArgumentException {

        Map<String, byte[]> transientMap = new HashMap<>();

        transientMap.put("key", key);

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "getPrivate", "mychcode", "mychannel", transientMap);
        Assert.assertArrayEquals(expectedValue, queryFuture.get());

    }

}
