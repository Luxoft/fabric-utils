package com.luxoft.fabric.events;

public interface Persister {
    /**
     * Returns advised start block. Returns {@link Long#MAX_VALUE } if unknown
     * @param channelName - get channel associated state
     * @return the block to start from
     */
    long getStartBlock(String channelName);

    /**
     * Update start block for the given channel.
     * @param channelName Associated channel
     * @param startBlock new block number. It might be less than previous value
     *                  when blockchain appears to be shorter than reported value.
     */
    void setStartBlock(String channelName, long startBlock);
}
