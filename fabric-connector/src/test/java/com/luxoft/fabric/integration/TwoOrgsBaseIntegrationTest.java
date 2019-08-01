package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.configurator.NetworkManager;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwoOrgsBaseIntegrationTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(OneOrgBaseIntegrationTest.class);
    private static final int initializationTimeout = 10000;

    static FabricConfig fabricConfigOrg1;
    static FabricConfig fabricConfigOrg2;


    @BeforeClass
    public static void setUp() throws Exception {
        startNetwork();

        fabricConfigOrg1 = FabricConfig.getConfigFromFile("../files/fabric-org1.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfigOrg1, true, initializationTimeout);

        fabricConfigOrg2 = FabricConfig.getConfigFromFile("../files/fabric-org2.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfigOrg2, true, initializationTimeout);

        logger.info("Finished preparation");
    }
}
