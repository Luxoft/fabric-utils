package com.luxoft.fabric.utils;

import com.luxoft.YamlConfig;
import com.luxoft.fabric.FabricConfig;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import java.io.*;
import java.security.Key;

/**
 * Created by ADoroganov on 11.08.2017.
 */
public class UserEnroller {

    private static BufferedReader getUsersReader(String path) {
        try {
            return new BufferedReader(new FileReader(path));
        } catch (FileNotFoundException e) {
            return null;
        }
    }

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

    public static void main(String[] args) throws Exception {
        YamlConfig config = new YamlConfig(null);
        FabricConfig fabricConfig = new FabricConfig(Configurator.getConfigReader("config.yaml"));

        String caKey = config.getValue(String.class, "ca_key", null);
        if (caKey == null)
            throw new RuntimeException("ca_key environment should be provided");
        String userAffiliation = config.getValue(String.class, "user_affiliation", null);
        if (userAffiliation == null)
            throw new RuntimeException("user_affiliation environment should be provided");

        String userFilePath = config.getValue(String.class, "user_file_path", "users.txt");
        String destFilesRootPath = config.getValue(String.class, "dest_file_path", "users/");
        String certFileName = config.getValue(String.class, "cert_file_name", "cert.pem");
        String privateKeyFileName = config.getValue(String.class, "pk_file_name", "pk.pem");
        System.out.println("Enrolling users at CA " + caKey);
        System.out.printf("Reading users from (%s), with affiliation (%s) and storing at (%s%%username%%) with cert in (%s) and pk in (%s)\n",
                userFilePath, userAffiliation, destFilesRootPath, certFileName, privateKeyFileName);

        BufferedReader usersReader = getUsersReader(userFilePath);
        String userName = usersReader.readLine();
        long cnt = 0;

        while (userName != null) {
            System.out.println("Processing user " + userName);
            HFCAClient hfcaClient = fabricConfig.createHFCAClient(caKey, null);
            User admin = fabricConfig.enrollAdmin(hfcaClient, caKey);
            String mspId = admin.getMspId();
            try {
                String secret = fabricConfig.registerUser(hfcaClient, admin, userName, userAffiliation);
                User user = fabricConfig.enrollUser(hfcaClient, userName, secret, mspId);
                File userDir = new File(destFilesRootPath + userName);
                userDir.mkdirs();
                File privateKeyFile = new File(userDir, privateKeyFileName);
                FileUtils.writeStringToFile(privateKeyFile, getKeyInPemFormat("PRIVATE KEY", user.getEnrollment().getKey()));
                File certFile = new File(userDir, certFileName);
                FileUtils.writeStringToFile(certFile, user.getEnrollment().getCert());
                cnt++;
            } catch (Exception e) {
                System.out.printf("Failed to process user %s, reason: \n%s\n", userName, e.getMessage());
                e.printStackTrace();
            }
            userName = usersReader.readLine();
        }
        System.out.println("Finished, successfully processed user count: " + cnt);
    }

}