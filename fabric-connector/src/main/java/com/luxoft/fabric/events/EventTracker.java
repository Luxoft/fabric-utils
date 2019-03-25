package com.luxoft.fabric.events;

import org.hyperledger.fabric.sdk.Channel;

public interface EventTracker {
    void configureChannel(Channel channel) throws Exception;
    void connectChannel(Channel channel) throws Exception;

    long getStartBlock(Channel channel);
    boolean useFilteredBlocks(Channel channel);
}
