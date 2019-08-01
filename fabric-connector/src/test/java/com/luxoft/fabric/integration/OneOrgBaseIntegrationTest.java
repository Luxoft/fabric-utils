package com.luxoft.fabric.integration;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.configurator.NetworkManager;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class OneOrgBaseIntegrationTest extends BaseIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(OneOrgBaseIntegrationTest.class);

    static FabricConfig fabricConfigOrg1;
    FabricConfig fabricConfigServiceDiscovery = FabricConfig.getConfigFromFile("../files/fabric-service-discovery-org1.yaml");
    static final String NETWORK_CONFIG_FILE = Objects.requireNonNull(FabricConnectorIntegrationTest.class.getClassLoader().getResource("network-config.yaml")).getFile();
    static final String NETWORK_CONFIG__SERVICE_DISCOVERY_FILE = Objects.requireNonNull(FabricConnectorIntegrationTest.class.getClassLoader().getResource("network-config-service-discovery.yaml")).getFile();


    @BeforeClass
    public static void setUp() {
        startNetwork();

        fabricConfigOrg1 = FabricConfig.getConfigFromFile("../files/fabric-org1.yaml");

        // Configuring Fabric network
        NetworkManager.configNetwork(fabricConfigOrg1);

        logger.info("Finished preparation");
    }


}
