package com.luxoft.fabric.configurator.users;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.FabricUser;
import com.luxoft.fabric.utils.UserEnrollmentUtils;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class UserEnrollAndRegisterService {

    private static final boolean FORCE_ADMIN_ENROLLMENT = false;
    private static final Logger logger = LoggerFactory.getLogger(UserEnrollAndRegisterService.class);
    private final RegisterAndEnrollConfigAdapter configAdapter;

    private final Map<String, User> registrarsMap = new HashMap<>();


    private void initializeRegistrarsMap() throws Exception {

        for (String caKey : configAdapter.getCAsKeys()) {
            registrarsMap.put(caKey, enrollOrLoadAdmin(caKey));
        }
    }

    private User enrollOrLoadAdmin(String caKey) throws Exception {


        String adminUser = configAdapter.getAdminName(caKey);
        User admin;

        if (FORCE_ADMIN_ENROLLMENT || !UserEnrollmentUtils.isAdminEnrolled(caKey, adminUser)) {

            logger.info("Enrolling admin {} on ca {} ", adminUser, caKey);

            String adminPassword = configAdapter.getAdminSecret(caKey);

            logger.debug("Retrieved adminUser: {}", adminUser);
            admin = enrollUser(caKey, adminUser, adminPassword);
            UserEnrollmentUtils.saveFabricEnrollment(caKey, adminUser, admin.getEnrollment());
        } else {
            logger.info("Loading  previously enrolled admin {} on ca {} ", adminUser, caKey);
            admin = UserEnrollmentUtils.loadAdmin(caKey, adminUser);
        }

        return admin;
    }

    public User enrollUser(String caKey, String userName, String userSecret) throws Exception {

        HFCAClient hfcaClient = configAdapter.createHFCAClient(caKey);
        Enrollment userEnrollment = hfcaClient.enroll(userName, userSecret);
        return new FabricUser(userName, null, null, userEnrollment, configAdapter.getCaMspId(caKey));
    }

    public String registerUser(String caKey, String userName, String userAffiliation) throws Exception {

        RegistrationRequest registrationRequest = new RegistrationRequest(userName, userAffiliation);
        return configAdapter.createHFCAClient(caKey).register(registrationRequest, registrarsMap.get(caKey));
    }

    private UserEnrollAndRegisterService(RegisterAndEnrollConfigAdapter configAdapter) throws Exception {
        this.configAdapter = configAdapter;
        initializeRegistrarsMap();
    }

    public static UserEnrollAndRegisterService getInstance(FabricConfig fabricConfig) throws Exception {
        return new UserEnrollAndRegisterService(
                new RegisterAndEnrollConfigAdapterImplBasedOnFabricConfig(fabricConfig));
    }

    public static UserEnrollAndRegisterService getInstance(NetworkConfig networkConfig) throws Exception {
        return new UserEnrollAndRegisterService(
                new RegisterAndEnrollConfigAdapterImplBasedOnNetworkConfig(networkConfig));
    }
}
