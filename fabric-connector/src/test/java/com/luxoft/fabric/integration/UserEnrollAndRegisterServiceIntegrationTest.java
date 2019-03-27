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

    private static final String CA_KEY = "ca.org1.example.com";
    private static final String USER_AFFILATION = "org1";
    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "adminpw";


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

        User adminUser = enroller.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser(CA_KEY, "testUser1", USER_AFFILATION);

        Assert.assertNotNull(userSecret);
    }

    @Test
    public void enrollAndRegisterUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(networkConfig);

        User adminUser = enroller.enrollUser(CA_KEY, ADMIN, ADMIN_PASSWORD);

        Assert.assertNotNull(adminUser);

        String userSecret = enroller.registerUser(CA_KEY, "testUser2", USER_AFFILATION);

        Assert.assertNotNull(userSecret);

    }

    @Test
    public void registerTwoUsersUsingNetworkConfigTest() throws Exception {

        NetworkConfig networkConfig = NetworkConfig.fromYamlFile(new File(NETWORK_CONFIG_FILE));
        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(networkConfig);

        String userSecret1 = enroller.registerUser(CA_KEY, "testUser4", USER_AFFILATION);

        Assert.assertNotNull(userSecret1);

        UserEnrollAndRegisterService enroller2 = UserEnrollAndRegisterService.getInstance(networkConfig);

        String userSecret2 = enroller2.registerUser(CA_KEY, "testUser5", USER_AFFILATION);

        Assert.assertNotNull(userSecret2);

    }

    @Test
    public void registerTwoUsersUsingFabricConfigTest() throws Exception {


        UserEnrollAndRegisterService enroller = UserEnrollAndRegisterService.getInstance(fabricConfig);

        String userSecret1 = enroller.registerUser(CA_KEY, "testUser7", USER_AFFILATION);

        Assert.assertNotNull(userSecret1);

        UserEnrollAndRegisterService enroller2 = UserEnrollAndRegisterService.getInstance(fabricConfig);

        String userSecret2 = enroller2.registerUser(CA_KEY, "testUser8", USER_AFFILATION);

        Assert.assertNotNull(userSecret2);

    }


}
