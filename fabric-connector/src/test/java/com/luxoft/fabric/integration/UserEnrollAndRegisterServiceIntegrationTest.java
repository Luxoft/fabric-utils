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

    @Test
    public void enrollAndRegisterUsingFabricConfigTest() throws Exception {


        UserEnrollAndRegisterService enroller = new UserEnrollAndRegisterImplBasedOnFabricConfig(fabricConfig);
        User adminUser = enroller.enrollUser("ca.org1.example.com", "admin", "adminpw");

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser("ca.org1.example.com", "testUser1", "org1");

        Assert.assertNotNull(userSecret);
    }

    @Test
    public void enrollAndRegisterUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = new UserEnrollAndRegisterImplBasedOnNetworkConfig(networkConfig);
        User adminUser = enroller.enrollUser("ca.org1.example.com", "admin", "adminpw");

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser("ca.org1.example.com", "testUser2", "org1");

        Assert.assertNotNull(userSecret);

    }
}
