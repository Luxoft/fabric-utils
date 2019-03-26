package com.luxoft.fabric.integration;

import com.luxoft.fabric.configurator.users.UserEnrollAndRegisterService;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class UserEnrollAndRegisterServiceIntegrationTest extends BaseIntegrationTest {


    @BeforeClass
    public static void cleanUp() throws IOException {

        final String enrollmentsDir = "enrollments";

        if (Files.exists(Paths.get(enrollmentsDir))) {

            Files.walk(Paths.get(enrollmentsDir))
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .peek(System.out::println)
                    .forEach(File::delete);
        }


    }

    @Test
    public void enrollAndRegisterUsingFabricConfigTest() throws Exception {

        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(fabricConfig);

        User adminUser = enroller.enrollUser("ca.org1.example.com", "admin", "adminpw");

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser("ca.org1.example.com", "testUser1", "org1");

        Assert.assertNotNull(userSecret);
    }

    @Test
    public void enrollAndRegisterUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(networkConfig);

        User adminUser = enroller.enrollUser("ca.org1.example.com", "admin", "adminpw");

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser("ca.org1.example.com", "testUser2", "org1");

        Assert.assertNotNull(userSecret);

    }

    @Test
    public void registerTwoUsersUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(networkConfig);

        String userSecret1 = enroller.registerUser("ca.org1.example.com", "testUser4", "org1");

        Assert.assertNotNull(userSecret1);

        UserEnrollAndRegisterService enroller2 = UserEnrollAndRegisterService.getInstance(networkConfig);

        String userSecret2 = enroller2.registerUser("ca.org1.example.com", "testUser5", "org1");

        Assert.assertNotNull(userSecret2);

    }

    @Test
    public void registerTwoUsersUsingFabricConfigTest() throws Exception {


        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(fabricConfig);

        String userSecret1 = enroller.registerUser("ca.org1.example.com", "testUser7", "org1");

        Assert.assertNotNull(userSecret1);

        UserEnrollAndRegisterService enroller2 = UserEnrollAndRegisterService.getInstance(fabricConfig);

        String userSecret2 = enroller2.registerUser("ca.org1.example.com", "testUser8", "org1");

        Assert.assertNotNull(userSecret2);

    }


}
