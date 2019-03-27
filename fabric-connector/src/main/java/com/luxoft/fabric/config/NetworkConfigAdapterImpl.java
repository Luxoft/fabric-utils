package com.luxoft.fabric.config;

import com.luxoft.fabric.events.EventTracker;
import org.hyperledger.fabric.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkConfigAdapterImpl extends AbstractConfigAdapter {

    private NetworkConfig networkConfig;
    private static final Logger LOG = LoggerFactory.getLogger(NetworkConfigAdapterImpl.class);


    private NetworkConfigAdapterImpl(User user, String defaultChannelName, NetworkConfig networkConfig, EventTracker eventTracker) {
        super(defaultChannelName, user, eventTracker);
        this.networkConfig = networkConfig;
    }

    @Override
    public User getDefaultUserContext() throws Exception {

        return networkConfig.getPeerAdmin();

    }

    @Override
    public void initChannels(HFClient hfClient) throws Exception {

        for (String channelName : networkConfig.getChannelNames()) {
            Channel channel = hfClient.loadChannelFromConfig(channelName, networkConfig);
            if (eventTracker != null) {

                /* The method does not support following functionality
                 *   - EventTracker.getStartBlock(Channel channel)
                 *   - Eventtracker.useFilteredBlocks(Channel channel)
                 *  due to limitations of  NetworkConfig. It allows to set those parameters only inside of PeerOptions class,
                 *  but there is no methods in NetworkConfig class which we could use to set those options. If this functionality is needed use FabricConfigAdapterImpl
                 *  See https://jira.hyperledger.org/browse/FABJ-430. Once it is resolved, we can rewrite this part accordingly.
                 */
                LOG.warn("Using events with NetworkConfig. EventTracker.getStartBlock & Eventtracker.useFilteredBlocks are not supported. See FABJ-430");
                eventTracker.configureChannel(channel);
            }

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
            return new NetworkConfigAdapterImpl(user, defaultChannelName, networkConfig, eventTracker);
        }

    }

}
