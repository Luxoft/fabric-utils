package com.luxoft.fabric.configurator;

import com.luxoft.fabric.FabricConfig;
import com.luxoft.fabric.model.ConfigData;
import com.luxoft.fabric.utils.UserEnrollmentUtils;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.security.Key;
import static com.luxoft.fabric.FabricConfig.getOrDefault;

/**
 * Created by ADoroganov on 11.08.2017.
 */
// TODO: make use of UserEnrollerAndRegisterService so that logic is not duplicated between those classes
public class UserEnroller {

    private static final Logger logger = LoggerFactory.getLogger(UserEnroller.class);

    public static String getKeyInPemFormat(String type, Key key) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PemObject pemObject = new PemObject(type, key.getEncoded());
        PemWriter pemWriter = new PemWriter(new OutputStreamWriter(outputStream));
        try {
            pemWriter.writeObject(pemObject);
        } finally {
            pemWriter.close();
        }
        return outputStream.toString();
    }

    public static void run(FabricConfig fabricConfig) throws Exception {
        ConfigData.Users usersDetails = fabricConfig.getUsersDetails();
        if (usersDetails == null) {
            throw new RuntimeException("User details not found");
        }

        String caKey = usersDetails.caKey;
        if (caKey == null) {
            throw new RuntimeException("users.caKey should be provided");
        }

        String userAffiliation = usersDetails.userAffiliation;
        if (userAffiliation == null) {
            throw new RuntimeException("users.userAffiliation should be provided");
        }

        String destFilesRootPath = getOrDefault(usersDetails.destFilesPath, "users/");
        String privateKeyFileName = getOrDefault(usersDetails.privateKeyFileName, "pk.pem");
        String certFileName = getOrDefault(usersDetails.certFileName, "cert.pem");

        logger.info("Enrolling users at CA {}", caKey);
        logger.info("Reading users with affiliation ({}) and storing at ({}%%username%%) with cert in ({}) and pk in ({})",
                userAffiliation, destFilesRootPath, certFileName, privateKeyFileName);

        long cnt = 0;

        for (String userName : usersDetails.list) {

            logger.info("Processing user " + userName);
            HFCAClient hfcaClient = fabricConfig.createHFCAClient(caKey, null);
            User admin = fabricConfig.enrollAdmin(hfcaClient, caKey);
            String mspId = admin.getMspId();
            try {
                String secret = UserEnrollmentUtils.registerUser(hfcaClient, admin, userName, userAffiliation);
                User user = UserEnrollmentUtils.enrollUser(hfcaClient, userName, secret, mspId);
                File userDir = new File(destFilesRootPath + userName);
                userDir.mkdirs();
                File privateKeyFile = new File(userDir, privateKeyFileName);
                FileUtils.writeStringToFile(privateKeyFile, getKeyInPemFormat("PRIVATE KEY", user.getEnrollment().getKey()));
                File certFile = new File(userDir, certFileName);
                FileUtils.writeStringToFile(certFile, user.getEnrollment().getCert());
                cnt++;
            } catch (Exception e) {
                logger.error("Failed to process user {}", userName, e);
            }
        }
        logger.info("Finished, successfully processed user count: {}", cnt);
    }

}
