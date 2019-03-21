package com.luxoft.fabric.integration;

import com.luxoft.fabric.EventTracker;
import com.luxoft.fabric.FabricConnector;
import com.luxoft.fabric.OrderingEventTracker;
import com.luxoft.fabric.Persister;
import com.luxoft.fabric.integration.proto.SimpleMessage;
import org.hyperledger.fabric.sdk.BlockEvent;
import org.hyperledger.fabric.sdk.ChaincodeEvent;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class EventTrackerIntegrationTest extends BaseIntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(EventTrackerIntegrationTest.class);

    CompletableFuture<String> eventStatus = new CompletableFuture<>();

    @Test
    public void testSimpleEventsWithFabricConfig() throws Exception {

        FabricConnector.Options options = new FabricConnector.Options();

        EventTracker eventTracker = new OrderingEventTracker(new Persister() {
            @Override
            public long getStartBlock(String channelName) {
                return 0;
            }

            @Override
            public void setStartBlock(String channelName, long startBlock) {

            }
        });

        TestEventListener testEventListener = new TestEventListener();
        ((OrderingEventTracker) eventTracker).enableEventsDelivery();
        ((OrderingEventTracker) eventTracker).addEventListener("mychcode", ".*", SimpleMessage.Message.class, testEventListener);

        options.setEventTracker(eventTracker);

        FabricConnector fabricConnector = FabricConnector.getFabricConfigBuilder(fabricConfig)
                .withOptions(options).build();


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
        LOG.info("Finished SanityCheck");


        Assert.assertEquals(eventStatus.get(1L, TimeUnit.SECONDS), "NEW STATE");

    }


    public class TestEventListener implements OrderingEventTracker.EventListener<SimpleMessage.Message> {

        @Override
        public CompletableFuture<Boolean> filter(ChaincodeEvent chaincodeEvent) {
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }

        @Override
        public CompletableFuture onEvent(ChaincodeEvent chaincodeEvent, SimpleMessage.Message eventData) {
            LOG.info("Received event: chainCodeEvent: {}, eventData: {}", chaincodeEvent.toString(), eventData.getPayload());


            eventStatus.complete(eventData.getPayload());

            return CompletableFuture.completedFuture(null);
        }

    }

}
