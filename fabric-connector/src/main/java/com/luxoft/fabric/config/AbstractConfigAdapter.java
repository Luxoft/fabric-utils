package com.luxoft.fabric.config;


import com.luxoft.fabric.events.EventTracker;
import org.hyperledger.fabric.sdk.User;

public abstract class AbstractConfigAdapter implements ConfigAdapter {

    private String defaultChannelName;
    private User user;
    private boolean initChannels;
    EventTracker eventTracker;

    AbstractConfigAdapter(String defaultChannelName, User user, boolean initChannels, EventTracker eventTracker) {
        this.defaultChannelName = defaultChannelName;
        this.user = user;
        this.initChannels = initChannels;
        this.eventTracker = eventTracker;
    }

    @Override
    public String getDefaultChannelName() {
        return defaultChannelName;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public boolean isInitChannels() {
        return initChannels;
    }

}
