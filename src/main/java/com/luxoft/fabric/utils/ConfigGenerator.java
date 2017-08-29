package com.luxoft.fabric.utils;

import org.hyperledger.fabric.sdk.ChannelConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class ConfigGenerator {


    public final String BASE_DIR = "network";


    public ChannelConfiguration generateChannelConfiguration(String channel) throws Exception {

        generateChannelArtifacts(channel);

        File txFile = new File(BASE_DIR,"/channel-artifacts/" + channel + "/channel.tx");

        if(!txFile.exists()) throw new IOException("Tx file not generated");

        return new ChannelConfiguration(txFile);
    }

    public void generateCryptoConfig(String channel) throws Exception {
        callScript(BASE_DIR, "./generateCerts.sh", "-c", channel);
    }


    public void generateChannelArtifacts(String channel) throws Exception {
        callScript(BASE_DIR, "./generateChannelArtifacts.sh", "-c", channel);
    }


    private void callScript( String dir, String... commandAndArgs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(commandAndArgs);
        if (dir != null) pb.directory(new File(dir));
        pb.inheritIO();
        Process p = pb.start();
        p.waitFor(10, TimeUnit.SECONDS);
        int exitValue = p.exitValue();
        if(exitValue != 0) throw new IllegalStateException("Process exited with non-zero error code: " + exitValue);
    }
}
