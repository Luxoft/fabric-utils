package com.luxoft.fabric.integration;

import com.luxoft.fabric.configurator.users.UserEnrollAndRegisterImplBasedOnFabricConfig;
import com.luxoft.fabric.configurator.users.UserEnrollAndRegisterImplBasedOnNetworkConfig;
import com.luxoft.fabric.configurator.users.UserEnrollAndRegisterService;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class UserEnrollAndRegisterServiceIntegrationTest extends BaseIntegrationTest {

    private static final String CA_KEY = "ca.org1.example.com";
    private static final String USER_AFFILATION = "org1";
    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "adminpw";

    @Test
    public void enrollAndRegisterUsingFabricConfigTest() throws Exception {


        UserEnrollAndRegisterService enroller = new UserEnrollAndRegisterImplBasedOnFabricConfig(fabricConfig);
        User adminUser = enroller.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser("ca.org1.example.com", "testUser1", USER_AFFILATION);

        Assert.assertNotNull(userSecret);
    }

    @Test
    public void enrollAndRegisterUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = new UserEnrollAndRegisterImplBasedOnNetworkConfig(networkConfig);
        User adminUser = enroller.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser(CA_KEY, "testUser2", USER_AFFILATION);

        Assert.assertNotNull(userSecret);

    }
}
