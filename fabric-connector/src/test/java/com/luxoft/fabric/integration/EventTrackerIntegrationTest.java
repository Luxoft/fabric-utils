package com.luxoft.fabric.integration;

import com.google.protobuf.InvalidProtocolBufferException;
import com.luxoft.fabric.*;
import com.luxoft.fabric.config.ConfigAdapter;
import com.luxoft.fabric.events.*;
import com.luxoft.fabric.integration.proto.SimpleMessage;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EventTrackerIntegrationTest extends OneOrgBaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(EventTrackerIntegrationTest.class);

    private OrderingEventTracker eventTracker = new OrderingEventTracker(new Persister() {
        @Override
        public long getStartBlock(String channelName) {
            return 0;
        }

        @Override
        public void setStartBlock(String channelName, long startBlock) {

        }
    });


    @Test
    public void testEventTrackerWithFabricConfig() throws Exception {

        FabricConnector fabricConnector = new FabricConnector(
                ConfigAdapter.getBuilder(fabricConfigOrg1)
                        .withEventTracker(eventTracker)
                        .build());

        sendTransactionAndCheckEvents(fabricConnector);
    }

    @Test
    public void testEventTrackerWithFabricConfigAndServiceDiscovery() throws Exception {

        FabricConnector fabricConnector = new FabricConnector(
                ConfigAdapter.getBuilder(fabricConfigServiceDiscovery)
                        .withEventTracker(eventTracker)
                        .build());

        sendTransactionAndCheckEvents(fabricConnector);
    }

    @Test
    public void testEventTrackerWithNetworkConfig() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        FabricConnector fabricConnector = new FabricConnector(
                ConfigAdapter.getBuilder(networkConfig)
                        .withEventTracker(eventTracker)
                        .build());

        sendTransactionAndCheckEvents(fabricConnector);
    }


    private void sendTransactionAndCheckEvents(FabricConnector fabricConnector) throws ExecutionException, InterruptedException, InvalidProtocolBufferException, TimeoutException, InvalidArgumentException {


        CompletableFuture<String> eventStatus = new CompletableFuture<>();

        final ProtobufMessagePayloadDecoder<SimpleMessage.Message> payloadDecoder = new ProtobufMessagePayloadDecoder<>(SimpleMessage.Message.class);

        TestEventListener testEventListener = new TestEventListener(eventStatus);
        eventTracker.enableEventsDelivery();
        eventTracker.addEventListener("mychcode", ".*", payloadDecoder, testEventListener);


        byte[] key = "someKey".getBytes();

        String value = UUID.randomUUID().toString();
        SimpleMessage.Message simpleMessage = SimpleMessage.Message.newBuilder().setPayload(value).build();

        CompletableFuture<BlockEvent.TransactionEvent> putEventFuture = fabricConnector.invoke(
                "put", "mychcode", "mychannel", key, simpleMessage.toByteArray());
        Assert.assertNotNull(putEventFuture.get());

        CompletableFuture<byte[]> queryFuture = fabricConnector.query(
                "get", "mychcode", "mychannel", key);

        SimpleMessage.Message receivedSimpleMessage = SimpleMessage.Message.parseFrom(queryFuture.get());
        String receivedValue = receivedSimpleMessage.getPayload();

        Assert.assertEquals(value, receivedValue);
        logger.info("Finished SanityCheck");

        Assert.assertEquals("NEW STATE", eventStatus.get(1L, TimeUnit.SECONDS));

    }


    public class TestEventListener implements OrderingEventTracker.EventListener<SimpleMessage.Message> {

        CompletableFuture<String> eventStatus;

        @Override
        public CompletableFuture<Boolean> filter(ChaincodeEvent chaincodeEvent) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture onEvent(ChaincodeEvent chaincodeEvent, SimpleMessage.Message eventData) {
            logger.info("Received event: chainCodeEvent: {}, eventData: {}", chaincodeEvent.toString(), eventData.getPayload());

            eventStatus.complete(eventData.getPayload());

            return CompletableFuture.completedFuture(null);
        }

        TestEventListener(CompletableFuture<String> eventStatus) {
            this.eventStatus = eventStatus;
        }
    }
}
