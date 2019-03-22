package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.configurator.NetworkManager;
import org.apache.commons.io.IOUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.Charset;

public class BaseIntegrationTest {

    static FabricConfig fabricConfig;
    static final String NETWORK_CONFIG_FILE = FabricConnectorIntegrationTest.class.getClassLoader().getResource("network-config.yaml").getFile();

    private static final Logger LOG = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @BeforeClass
    public static void setUp() throws Exception {
        LOG.info("Starting preparation");
        int exitCode = execInDirectory("./fabric.sh restart", "../files/artifacts/");

        LOG.info("Waiting some time to give network the time to initialize");
        Thread.sleep(5000);
        LOG.info("Restarted network");
        Assert.assertEquals(0, exitCode);

        fabricConfig = FabricConfig.getConfigFromFile("../files/fabric.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfig);
        LOG.info("Finished preparation");
    }

    @AfterClass
    public static void tearDown() {
        execInDirectory("./fabric.sh clean", "../files/artifacts/");
    }

    private static int execInDirectory(String cmd, String dir) {
        try {
            Process process = new ProcessBuilder()
                    .command(cmd.split(" "))
                    .directory(new File(System.getProperty("user.dir") + File.separator + dir).getCanonicalFile())
                    .start();

            int exitCode = process.waitFor();

            System.out.println(IOUtils.toString(process.getInputStream(), Charset.defaultCharset()));
            System.err.println(IOUtils.toString(process.getErrorStream(), Charset.defaultCharset()));

            return exitCode;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
