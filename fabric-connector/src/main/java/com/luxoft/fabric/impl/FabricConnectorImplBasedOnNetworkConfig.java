package com.luxoft.fabric.impl;

import com.luxoft.fabric.FabricConnector;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;

public class FabricConnectorImplBasedOnNetworkConfig extends FabricConnector {

    private final NetworkConfig networkConfig;

    private FabricConnectorImplBasedOnNetworkConfig(User user, String defaultChannelName, NetworkConfig networkConfig, Boolean initChannels, Options options) throws Exception {
        this.networkConfig = networkConfig;
        initConnector(user, defaultChannelName, initChannels, options);
    }

    @Override
    protected void initUserContext(User user) throws Exception {
        if (user != null)
            hfClient.setUserContext(user);
        else if (hfClient.getUserContext() == null)
            hfClient.setUserContext(networkConfig.getPeerAdmin());
    }

    @Override
    public void initChannels(Options options) throws Exception {
        for (String channelName : networkConfig.getChannelNames()) {
            hfClient.loadChannelFromConfig(channelName, networkConfig).initialize();
        }

    }

    public static class Builder extends FabricConnector.Builder{

        private NetworkConfig networkConfig;

        public Builder(NetworkConfig networkConfig) {

            this.networkConfig = networkConfig;
        }

        public FabricConnectorImplBasedOnNetworkConfig build() throws Exception {

            return new FabricConnectorImplBasedOnNetworkConfig(user, defaultChannelName, networkConfig, initChannels, options);
        }


    }

}
