package com.luxoft.fabric.configurator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.hyperledger.fabric.protos.common.Configtx;

import java.io.IOException;

public class ConfigTxLator {
    private String configtxlatorLocation = "http://localhost:7059";

    private HttpClient httpClient = HttpClients.createDefault();

    public ConfigTxLator() {
    }

    public ConfigTxLator(String location) {
        configtxlatorLocation = location;
    }

    public byte[] queryUpdateConfigurationBytes(String channelName, byte[] currentChannelConfigurationBytes, byte[] targetChannelConfigurationBytes) throws IOException {
        HttpPost httppost = new HttpPost(configtxlatorLocation + "/configtxlator/compute/update-from-configs");

        HttpEntity multipartEntity = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .addBinaryBody("original", currentChannelConfigurationBytes, ContentType.APPLICATION_OCTET_STREAM, "originalFakeFilename")
                .addBinaryBody("updated", targetChannelConfigurationBytes, ContentType.APPLICATION_OCTET_STREAM, "updatedFakeFilename")
                .addBinaryBody("channel", channelName.getBytes()).build();

        httppost.setEntity(multipartEntity);
        HttpResponse response = httpClient.execute(httppost);
        int statuscode = response.getStatusLine().getStatusCode();
        System.out.println(String.format("Got %s status for updated config bytes needed for updateChannelConfiguration", statuscode));
        if (statuscode / 100 != 2) {
            throw new RuntimeException("Failed to fetch update config, reason:\n" + EntityUtils.toString(response.getEntity()));
        }

        return EntityUtils.toByteArray(response.getEntity());
    }

    public byte[] jsonToProtoBytes(String protoType, String json) throws IOException {

        //TODO: configTxLator decode hack, part 1. Fix hack when possible. configTxLator refuses to decode common.ConfigGroup, but ok with common.Config, so we are reformating to more general one.
        String realType = "";
        switch (protoType) {
            case "common.ConfigGroup":
                realType = protoType;
                protoType = "common.Config";
                json = "{\"channel_group\":{\"groups\":{\"Application\":{\"groups\": {\"decodeHackOrg\":" + json + "}}}}}";
                break;
        }

        HttpPost httppost = new HttpPost(configtxlatorLocation + "/protolator/encode/" + protoType);
        httppost.setEntity(new StringEntity(json));
        HttpResponse response = httpClient.execute(httppost);
        int statuscode = response.getStatusLine().getStatusCode();
        System.out.println(String.format("Got %s status for json to proto conversion", statuscode));
        if (statuscode / 100 != 2) {
            throw new RuntimeException("Failed to convert json to proto, reason:\n" + EntityUtils.toString(response.getEntity()));
        }

        byte[] result = EntityUtils.toByteArray(response.getEntity());
        //TODO: configTxLator decode hack, part 2.
        switch (realType) {
            case "common.ConfigGroup":
                result = Configtx.Config.parseFrom(result).getChannelGroup().getGroupsOrThrow("Application").getGroupsOrThrow("decodeHackOrg").toByteArray();
                break;
        }

        return result;
    }

    public String protoToJson(String protoType, byte[] proto) throws IOException {
        HttpPost httppost = new HttpPost(configtxlatorLocation + "/protolator/decode/" + protoType);
        httppost.setEntity(new ByteArrayEntity(proto));
        HttpResponse response = httpClient.execute(httppost);
        int statuscode = response.getStatusLine().getStatusCode();
        System.out.println(String.format("Got %s status for json to proto conversion", statuscode));
        if (statuscode / 100 != 2) {
            throw new RuntimeException("Failed to convert json to proto, reason:\n" + EntityUtils.toString(response.getEntity()));
        }

        return EntityUtils.toString(response.getEntity());
    }
}
