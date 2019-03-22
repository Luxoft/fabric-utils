package com.luxoft.fabric.config;

import com.luxoft.fabric.EventTracker;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.User;

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

    @Override
    public void initChannels(HFClient hfClient) throws Exception {

        for (String channelName : networkConfig.getChannelNames()) {
            hfClient.loadChannelFromConfig(channelName, networkConfig).initialize();
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
