package com.luxoft.fabric;

import org.hyperledger.fabric.sdk.Channel;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.User;

import java.util.ArrayList;

public class FabricConfigurator {

    private HFClient hfClient;
    private FabricConfig fabricConfig;
    private String defaultChannelName;


    public void deployChaincode(String chaincodeName) throws Exception {
        deployChaincode(chaincodeName, defaultChannelName);
    }

    public void upgradeChaincode(String chaincodeName) throws Exception {
        upgradeChaincode(chaincodeName, defaultChannelName);
    }

    public void deployChaincode(String chaincodeName, String channelName) throws Exception {
        Channel channel = hfClient.getChannel(channelName);
        if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConfig.installChaincode(hfClient, new ArrayList<>(channel.getPeers()), chaincodeName);
        fabricConfig.instantiateChaincode(hfClient, channel, chaincodeName, null);
    }

    public void upgradeChaincode(String chaincodeName, String channelName) throws Exception {
        Channel channel = hfClient.getChannel(channelName);
        if(channel == null) throw new IllegalAccessException("Channel not found for name: " + channelName);
        fabricConfig.upgradeChaincode(hfClient, channel, new ArrayList<>(channel.getPeers()), chaincodeName);
    }

    public FabricConfigurator(User user, String defaultChannelName, FabricConfig fabricConfig) throws Exception {
        this.fabricConfig = fabricConfig;
        this.defaultChannelName = defaultChannelName;

        hfClient = FabricConnector.createHFClient();

        if (user != null)
            hfClient.setUserContext(user);
        else if (hfClient.getUserContext() == null)
            hfClient.setUserContext(fabricConfig.getAdmin(fabricConfig.getAdminsKeys().get(0)));

    }



}
