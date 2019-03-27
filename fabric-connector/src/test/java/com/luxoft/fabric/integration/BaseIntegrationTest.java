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

    private static final Logger logger = LoggerFactory.getLogger(BaseIntegrationTest.class);

    @BeforeClass
    public static void setUp() throws Exception {
        logger.info("Starting preparation");
        int exitCode = execInDirectory("./fabric.sh restart", "../files/artifacts/");

        logger.info("Waiting some time to give network the time to initialize");
        Thread.sleep(5000);
        logger.info("Restarted network");
        Assert.assertEquals(0, exitCode);

        fabricConfig = FabricConfig.getConfigFromFile("../files/fabric.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfig);
        logger.info("Finished preparation");
    }

    @AfterClass
    public static void tearDown() {
        execInDirectory("./fabric.sh clean", "../files/artifacts/");
    }


    /**
     * @param cmd This function supports only cmd parameters split by spaces
     * @param dir Directory where the method should be executed
     * @return exitCode
     */
    static int execInDirectory(String cmd, String dir) {
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
