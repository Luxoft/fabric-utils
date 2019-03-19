package com.luxoft.fabric;

import com.luxoft.fabric.impl.FabricConnectorImplBasedOnFabricConfig;
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


    @SuppressWarnings("unused")
    public FabricConfigurator(FabricConnectorImplBasedOnFabricConfig fabricConnector) {
        this.hfClient = fabricConnector.getHfClient();
        this.fabricConfig = fabricConnector.getFabricConfig();
        this.defaultChannelName = fabricConnector.getDefaultChannel().getName();

    }



}
