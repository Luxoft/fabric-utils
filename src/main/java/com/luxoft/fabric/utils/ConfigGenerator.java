package com.luxoft.fabric.utils;

import org.hyperledger.fabric.sdk.ChannelConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ConfigGenerator {


    private String baseDir = "network";


    public ConfigGenerator() {
    }

    public ConfigGenerator(String baseDir) {
        this.baseDir = baseDir;
    }

    public ChannelConfiguration generateChannelConfiguration(String channel) throws Exception {
        return generateChannelConfiguration(channel, null, null);
    }

    public ChannelConfiguration generateChannelConfiguration(String channel, String channelProfile, String genesisProfile) throws Exception {
        generateChannelArtifacts(channel, channelProfile, genesisProfile);

        File txFile = new File(baseDir,"channel-artifacts/" + channel + "/channel.tx");

        if(!txFile.exists()) throw new IOException("Tx file not generated");

        return new ChannelConfiguration(txFile);
    }

    public void generateCryptoConfig(String channel) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("./generateCryptoConfig.sh");

        if(channel != null)        { command.add("-c"); command.add(channel); }

        callScript(command, baseDir);
    }


    public void generateChannelArtifacts(String channel, String channelProfile, String genesisProfile) throws Exception {
        ArrayList<String> command = new ArrayList<>();
        command.add("./generateChannelArtifacts.sh");

        if(channel != null) { command.add("-c"); command.add(channel); }
        if(channelProfile != null) { command.add("-p"); command.add(channelProfile); }
        if(genesisProfile != null) { command.add("-g"); command.add(genesisProfile); }

        callScript(command, baseDir);
    }


    private void callScript(ArrayList<String> commandAndArgs, String dir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(commandAndArgs);
        if (dir != null) pb.directory(new File(dir));
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor(10, TimeUnit.SECONDS);
        int exitValue = p.exitValue();
        if(exitValue != 0) throw new IllegalStateException("Process exited with non-zero error code: " + exitValue);
    }


    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }
}
