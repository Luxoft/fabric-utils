package com.luxoft.fabric.config;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.utils.NetworkManager;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.security.CryptoSuite;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

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
        public static final Arguments WAIT_CHAINCODE = new Arguments("wait-chaincode");
    }

    public static void main(String[] args) throws Exception {

        OptionParser parser = new OptionParser();
        OptionSpec<Arguments> type = parser.accepts("type").withRequiredArg().ofType(Arguments.class);

        OptionSpec<String> name   = parser.accepts("name").withOptionalArg().ofType(String.class);
        OptionSpec<Integer> timeout = parser.accepts("timeout").withOptionalArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<String> config = parser.accepts("config").withOptionalArg().ofType(String.class);

        OptionSet options = parser.parse(args);
        Arguments mode = options.valueOf(type);

        Configurator cfg = new Configurator();

        final String configFile = options.has(config) ? options.valueOf(config): "fabric.yaml";
        FabricConfig fabricConfig = FabricConfig.getConfigFromFile(configFile);

        if(!options.has(type) || mode.equals(Arguments.CONFIG)) {
            cfg.configNetwork(fabricConfig);
        } else if (mode.equals(Arguments.ENROLL)) {
            UserEnroller.run(fabricConfig);
        } else {

            HFClient hfClient = HFClient.createNewInstance();
            hfClient.setCryptoSuite(CryptoSuite.Factory.getCryptoSuite());

            Set<String> names = options.has(name)
                    ? new HashSet<>(options.valuesOf(name))
                    : Collections.EMPTY_SET;

            if (mode.equals(Arguments.DEPLOY))
                cfg.deployChaincodes(hfClient, fabricConfig, names);
            else if (mode.equals(Arguments.UPGRADE))
                cfg.upgradeChaincodes(hfClient, fabricConfig, names);
            else if (mode.equals(Arguments.WAIT_CHAINCODE))
                cfg.waitChaincodes(hfClient, fabricConfig, names, options.valueOf(timeout));
        }
    }
}