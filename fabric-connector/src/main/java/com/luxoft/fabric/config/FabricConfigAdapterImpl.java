package com.luxoft.fabric.config;

import com.luxoft.fabric.EventTracker;
import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.User;

public class FabricConfigAdapterImpl extends AbstractConfigAdapter {


    private FabricConfig fabricConfig;

    private FabricConfigAdapterImpl(User user, String defaultChannelName, FabricConfig fabricConfig, boolean initChannels, EventTracker eventTracker) {
        super(defaultChannelName, user, initChannels, eventTracker);
        this.fabricConfig = fabricConfig;

    }

    @Override
    public User getDefaultUserContext() throws Exception {

        return fabricConfig.getAdmin(fabricConfig.getAdminsKeys().get(0));

    }

    @Override
    public void initChannels(HFClient hfClient) throws Exception {

        for (String channel : fabricConfig.getChannelsKeys()) {
            fabricConfig.initChannel(hfClient, channel, hfClient.getUserContext(), eventTracker);
        }

    }

    public static class Builder extends ConfigAdapter.Builder {

        private FabricConfig fabricConfig;

        public Builder(FabricConfig fabricConfig) {
            this.fabricConfig = fabricConfig;
        }

        public FabricConfigAdapterImpl build() throws Exception {
            return new FabricConfigAdapterImpl(user, defaultChannelName, fabricConfig, initChannels, eventTracker);
        }

    }

}
