package com.luxoft.fabric.config;

import com.luxoft.fabric.EventTracker;
import org.hyperledger.fabric.sdk.*;

public class NetworkConfigAdapterImpl extends AbstractConfigAdapter {

    private NetworkConfig networkConfig;


    private NetworkConfigAdapterImpl(User user, String defaultChannelName, NetworkConfig networkConfig, boolean initChannels, EventTracker eventTracker) {
        super(defaultChannelName, user, initChannels, eventTracker);
        this.networkConfig = networkConfig;
    }

    @Override
    public User getDefaultUserContext() throws Exception {

        return networkConfig.getPeerAdmin();

    }

    /*TODO: add some warning to the method that it does not support:
     *   - EventTracker.getStartBlock(Channel channel)
     *   - Eventtracker.useFilteredBlocks(Channel channel)
     *  due to limitations of NetworkConfig     *
     */
    @Override
    public void initChannels(HFClient hfClient) throws Exception {

        for (String channelName : networkConfig.getChannelNames()) {
            Channel channel = hfClient.loadChannelFromConfig(channelName, networkConfig);
            if (eventTracker != null)
                eventTracker.configureChannel(channel);

            channel.initialize();

            if (eventTracker != null)
                eventTracker.connectChannel(channel);

        }

    }

    public static class Builder extends ConfigAdapter.Builder {

        private NetworkConfig networkConfig;

        Builder(NetworkConfig networkConfig) {
            this.networkConfig = networkConfig;
        }

        public NetworkConfigAdapterImpl build() {
            return new NetworkConfigAdapterImpl(user, defaultChannelName, networkConfig, initChannels, eventTracker);
        }

    }

}
