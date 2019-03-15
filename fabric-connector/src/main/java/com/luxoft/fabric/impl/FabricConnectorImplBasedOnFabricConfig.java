package com.luxoft.fabric.impl;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricConnector;
import org.hyperledger.fabric.sdk.User;


public class FabricConnectorImplBasedOnFabricConfig extends FabricConnector {

    private final FabricConfig fabricConfig;

    private FabricConnectorImplBasedOnFabricConfig(User user, String defaultChannelName, FabricConfig fabricConfig, Boolean initChannels, Options options) throws Exception {
        this.fabricConfig = fabricConfig;
        initConnector(user, defaultChannelName, initChannels, options);
    }

    @Override
    protected void initUserContext(User user) throws Exception {
        if (user != null)
            hfClient.setUserContext(user);
        else if (hfClient.getUserContext() == null)
            hfClient.setUserContext(fabricConfig.getAdmin(fabricConfig.getAdminsKeys().get(0)));
    }

    @Override
    public void initChannels(Options options) throws Exception {
        for (String channel : fabricConfig.getChannelsKeys()) {
            fabricConfig.initChannel(hfClient, channel, hfClient.getUserContext(), options);
        }

    }

    public static class Builder  extends FabricConnector.Builder {

        private FabricConfig fabricConfig;

        public Builder(FabricConfig fabricConfig) {
            this.fabricConfig = fabricConfig;
        }

        public FabricConnectorImplBasedOnFabricConfig build() throws Exception {
            return new FabricConnectorImplBasedOnFabricConfig(user, defaultChannelName, fabricConfig, initChannels, options);
        }

    }

}
