package com.luxoft.fabric.config;

import com.luxoft.fabric.FabricConfig;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.commons.io.FileUtils;
import org.hyperledger.fabric.sdk.HFClient;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Created by ADoroganov on 25.07.2017.
 */
public class Configurator extends NetworkManager {

    public static final class Arguments {

        private final String var;

        public Arguments(final String var) {
            this.var = var;
        }

        public final String toString() { return var; }

        public final boolean equals(Arguments val) {
            return var.equals(val.toString());
        }

        public static final Arguments CONFIG  = new Arguments("config");
        public static final Arguments DEPLOY  = new Arguments("deploy");
        public static final Arguments UPGRADE = new Arguments("upgrade");
        public static final Arguments ENROLL  = new Arguments("enroll");
        public static final Arguments GET_CHANNEL = new Arguments("getchannel");
        public static final Arguments UPDATE_CHANNEL = new Arguments("updatechannel");
        public static final Arguments SIGN_UPDATE = new Arguments("signupdate");
        public static final Arguments SET_CHANNEL = new Arguments("setchannel");
        public static final Arguments ADD_COMPANY = new Arguments("addcompany");
    }

    public static void main(String[] args) throws Exception {

        OptionParser parser = new OptionParser();
        OptionSpec<Arguments> type = parser.accepts("type").withRequiredArg().ofType(Arguments.class);
        OptionSpec<String> config = parser.accepts("config").withRequiredArg().ofType(String.class).defaultsTo("fabric.yaml");

        OptionSpec<String> name = parser.accepts("name").withRequiredArg().ofType(String.class);
        OptionSpec<String> dest = parser.accepts("dest").withRequiredArg().ofType(String.class);
        OptionSpec<String> src = parser.accepts("src").withRequiredArg().ofType(String.class);
        OptionSpec<String> channel = parser.accepts("channel").withRequiredArg().ofType(String.class);
        OptionSpec<String> company = parser.accepts("company").withRequiredArg().ofType(String.class);
        OptionSpec<String> admin = parser.accepts("admin").withRequiredArg().ofType(String.class);
        OptionSpec<String> signatures = parser.accepts("signature").withRequiredArg().ofType(String.class);

        OptionSet options = parser.parse(args);
        Arguments mode = options.valueOf(type);

        Configurator cfg = new Configurator();

        final String configFile = options.valueOf(config);
        FabricConfig fabricConfig = FabricConfig.getConfigFromFile(configFile);

        if(!options.has(type) || mode.equals(Arguments.CONFIG)) {
            cfg.configNetwork(fabricConfig);
        } else if (mode.equals(Arguments.ENROLL)) {
            UserEnroller.run(fabricConfig);
        } else if (mode.equals(Arguments.GET_CHANNEL)) {
            String destFilePath = options.valueOf(dest);
            String channelName = requireNonNull(options.valueOf(channel), "'channel' argument required with target channel");
            if (destFilePath == null)
                destFilePath = channelName + ".json";

//            FileUtils.writeByteArrayToFile(new File(destFilePath), getChannelConfig(fabricConfig, channelName));
            FileUtils.writeStringToFile(new File(destFilePath), getChannelConfigJson(fabricConfig, channelName), StandardCharsets.UTF_8);
        } else if (mode.equals(Arguments.ADD_COMPANY)) {
            String srcFilePath = requireNonNull(options.valueOf(src), "'src' argument required with path to companyConfigGroup json file");
            String channelName = requireNonNull(options.valueOf(channel), "'channel' argument required with target channel");
            String companyName = requireNonNull(options.valueOf(company), "'company' argument required with company name");
            String destFilePath = options.valueOf(dest);
            if (destFilePath == null)
                destFilePath = srcFilePath + ".update.bin";

            String fileContent = FileUtils.readFileToString(new File(srcFilePath), StandardCharsets.UTF_8);
            FileUtils.writeByteArrayToFile(new File(destFilePath), addCompanyToChannel(fabricConfig, channelName, companyName, fileContent));
//            addCompanyToChannel(fabricConfig, channelName, companyName, fileContent);
        } else if (mode.equals(Arguments.SIGN_UPDATE)) {
            String srcFilePath = requireNonNull(options.valueOf(src), "'src' argument required with path to updated config proto file");
//            String channelName = requireNonNull(options.valueOf(channel), "'channel' argument required with target channel");
            String adminName = requireNonNull(options.valueOf(admin), "'admin' argument required with admin user to sign");
            String destFilePath = options.valueOf(dest);
            if (destFilePath == null)
                destFilePath = srcFilePath + ".sign";

            byte[] fileContent = FileUtils.readFileToByteArray(new File(srcFilePath));
            byte[] channelUpdateConfigSignature = signChannelUpdateConfig(FabricConfig.createHFClient(), fabricConfig, fileContent, adminName);
//            byte[] channelUpdateConfigSignature = signChannelUpdateConfig(fabricConfig, channelName, fileContent, adminName);
            FileUtils.writeByteArrayToFile(new File(destFilePath), channelUpdateConfigSignature);
        } else if (mode.equals(Arguments.SET_CHANNEL)) {
            String srcFilePath = requireNonNull(options.valueOf(src), "'src' argument required with path to updated config proto file");
            String channelName = requireNonNull(options.valueOf(channel), "'channel' argument required with target channel");
            List<String> signaturePaths = options.valuesOf(signatures);
            if (signaturePaths.isEmpty())
                throw new RuntimeException("at least one 'signature' argument is required");

            byte[][] signaturesBytes = new byte[signaturePaths.size()][];
            for (int i = 0, signaturePathsSize = signaturePaths.size(); i < signaturePathsSize; i++) {
                String signaturePath = signaturePaths.get(i);
                signaturesBytes[i] = FileUtils.readFileToByteArray(new File(signaturePath));
            }
            byte[] fileContent = FileUtils.readFileToByteArray(new File(srcFilePath));
            setChannelConfig(fabricConfig, channelName, fileContent, signaturesBytes);
        } else if (mode.equals(Arguments.UPDATE_CHANNEL)) {
            String srcFilePath = requireNonNull(options.valueOf(src), "'src' argument required with path to updated config json file");
            String channelName = requireNonNull(options.valueOf(channel), "'channel' argument required with target channel");
            String destFilePath = options.valueOf(dest);
            if (destFilePath == null)
                destFilePath = srcFilePath + ".sign";

            String fileContent = FileUtils.readFileToString(new File(srcFilePath), StandardCharsets.UTF_8);
            byte[] updatedChannel = updateChannel(fabricConfig, channelName, fileContent);
            FileUtils.writeByteArrayToFile(new File(destFilePath), updatedChannel);
        } else {

            HFClient hfClient = FabricConfig.createHFClient();

            Set<String> names = options.has(name)
                    ? new HashSet<>(options.valuesOf(name))
                    : Collections.EMPTY_SET;

            if (mode.equals(Arguments.DEPLOY))
                deployChaincodes(hfClient, fabricConfig, names);
            else if (mode.equals(Arguments.UPGRADE))
                upgradeChaincodes(hfClient, fabricConfig, names);
        }
    }
}