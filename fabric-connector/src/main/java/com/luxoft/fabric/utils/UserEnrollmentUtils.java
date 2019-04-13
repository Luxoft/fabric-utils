package com.luxoft.fabric.utils;

import com.luxoft.fabric.FabricUser;
import com.luxoft.fabric.FabricUserEnrollment;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReaderInputStream;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;


public class UserEnrollmentUtils {

    public static final String ENROLLMENT_DIRECTORY = "enrollments";
    private static final String USER_KEY_FILE_NAME = "key";
    private static final String USER_CERT_FILE_NAME = "cert";

    private static final Logger logger = LoggerFactory.getLogger(UserEnrollmentUtils.class);


    public static Enrollment createEnrollment(InputStream privateKeyFile, InputStream certFile) throws IOException {
        PrivateKey privateKey = getPrivateKeyFromBytes(IOUtils.toByteArray(privateKeyFile));

        return new FabricUserEnrollment(privateKey, IOUtils.toString(certFile));
    }

    public static PrivateKey getPrivateKeyFromString(String data) throws IOException {
        final PEMParser pemParser = new PEMParser(new StringReader(data));

        Object pemObject = pemParser.readObject();

        PrivateKeyInfo privateKeyInfo;

        if (pemObject instanceof PrivateKeyInfo) {
            privateKeyInfo = (PrivateKeyInfo) pemObject;

        } else if (pemObject instanceof PEMKeyPair) {
            privateKeyInfo = ((PEMKeyPair) pemObject).getPrivateKeyInfo();
        } else {
            throw new IllegalArgumentException(String.format("Unknown private key format: %s", pemObject.getClass().toString()));
        }

        return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
    }

    public static PrivateKey getPrivateKeyFromBytes(byte[] data) throws IOException {
        return getPrivateKeyFromString(new String(data));
    }

    public static Enrollment createEnrollment(PrivateKey privateKey, String pemCertificate) {
        return new FabricUserEnrollment(privateKey, pemCertificate);
    }

    public static Enrollment createEnrollment(String pemPrivateKey, String pemCertificate) throws IOException {
        PrivateKey privateKey = UserEnrollmentUtils.getPrivateKeyFromString(pemPrivateKey);
        return new FabricUserEnrollment(privateKey, pemCertificate);
    }

    public static void saveFabricEnrollment(String caKey, String username, Enrollment enrollment) throws IOException {

        Files.createDirectories(getUserEnrollmentDirPath(caKey, username));
        Files.write(getCertFilePath(caKey, username), enrollment.getCert().getBytes());

        JcaPEMWriter pemWriter = new JcaPEMWriter(new FileWriter(getKeyFilePath(caKey, username).toFile()));
        pemWriter.writeObject(enrollment.getKey());
        pemWriter.close();

    }

    public static User loadAdmin(String caKey, String username) throws IOException {

        final String pemPrivateKey = new String(Files.readAllBytes(getKeyFilePath(caKey, username)), StandardCharsets.US_ASCII);
        final String pemCertificate = new String(Files.readAllBytes(getCertFilePath(caKey, username)), StandardCharsets.US_ASCII);

        final ReaderInputStream privateKey = new ReaderInputStream(new StringReader(pemPrivateKey), StandardCharsets.US_ASCII);
        final ReaderInputStream cert = new ReaderInputStream(new StringReader(pemCertificate), StandardCharsets.US_ASCII);

        final Enrollment enrollment = UserEnrollmentUtils.createEnrollment(privateKey, cert);
        return new FabricUser(null, null, null, enrollment, null);

    }

    private static Path getKeyFilePath(String caKey, String username) {

        return getUserEnrollmentDirPath(caKey, username).resolve(USER_KEY_FILE_NAME);

    }

    private static Path getCertFilePath(String caKey, String username) {
        return getUserEnrollmentDirPath(caKey, username).resolve(USER_CERT_FILE_NAME);

    }

    private static Path getUserEnrollmentDirPath(String caKey, String username) {
        String enrollmentPath = ENROLLMENT_DIRECTORY + "/" + caKey + "/" + username + "/";
        return Paths.get(enrollmentPath);

    }


    public static boolean isAdminEnrolled(String caKey, String username) {

        Path keyFilePath = getKeyFilePath(caKey, username);
        Path certFilePath = getCertFilePath(caKey, username);

        final boolean noKeyFile = !Files.exists(keyFilePath);
        final boolean noCertFile = !Files.exists(certFilePath);
        if (noKeyFile)
            logger.info("CA admin key file {} missing", keyFilePath.toString());

        if (noCertFile)
            logger.info("CA admin cert file {} missing", certFilePath.toString());

        return !(noKeyFile || noCertFile);

    }

    public static User enrollUser(HFCAClient hfcaClient, String userName, String userSecret, String mspID) throws Exception {
        Enrollment adminEnrollment = hfcaClient.enroll(userName, userSecret);
        return new FabricUser(userName, null, null, adminEnrollment, mspID);
    }

    public static String registerUser(HFCAClient hfcaClient, User admin, String userName, String userAffiliation) throws Exception {
        RegistrationRequest registrationRequest = new RegistrationRequest(userName, userAffiliation);
        return hfcaClient.register(registrationRequest, admin);
    }
}
