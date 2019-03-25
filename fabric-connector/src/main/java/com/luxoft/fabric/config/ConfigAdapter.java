package com.luxoft.fabric.config;

import com.luxoft.fabric.events.EventTracker;
import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;

/*
 * FabricConnector can be opened from different config types (native for Fabric NetworkConfig or made by Luxoft FabricConfig)
 * This class' purpose is to make FabricConnector invariant to the used config type
 */
public interface ConfigAdapter {


    User getDefaultUserContext() throws Exception;

    void initChannels(HFClient hfClient) throws Exception;

    String getDefaultChannelName();

    User getUser();

    boolean isInitChannels();

    abstract class Builder {

        Boolean initChannels = true;
        User user;
        String defaultChannelName;
        EventTracker eventTracker;

        public ConfigAdapter.Builder withInitChannels(Boolean initChannels) {
            this.initChannels = initChannels;
            return this;
        }

        public ConfigAdapter.Builder withUser(User user) {
            this.user = user;
            return this;
        }

        public ConfigAdapter.Builder withDefaultChannelName(String defaultChannelName) {
            this.defaultChannelName = defaultChannelName;
            return this;
        }

        public ConfigAdapter.Builder withEventtracker(EventTracker eventTracker) {
            this.eventTracker = eventTracker;
            return this;
        }

        public abstract ConfigAdapter build() throws Exception;
    }


    static ConfigAdapter.Builder getBuilder(FabricConfig fabricConfig) {
        return new FabricConfigAdapterImpl.Builder(fabricConfig);
    }

    static ConfigAdapter.Builder getBuilder(NetworkConfig networkConfig) {
        return new NetworkConfigAdapterImpl.Builder(networkConfig);
    }
}
