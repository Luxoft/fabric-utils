package com.luxoft.fabric;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;

import java.io.Serializable;
import java.security.PrivateKey;

import static com.luxoft.fabric.Configurator.getConfigReader;

/**
 * Created by ADoroganov on 26.07.2017.
 */
public class FabricUserEnrollment implements Enrollment, Serializable {
    private final String certificate;
    private final PrivateKey privateKey;


    FabricUserEnrollment(PrivateKey privateKey, String certificate) {
        this.certificate = certificate;
        this.privateKey = privateKey;
    }

    @Override
    public PrivateKey getKey() {
        return privateKey;
    }

    @Override
    public String getCert() {
        return certificate;
    }

    public static void main(String[] args) throws Exception {
        FabricConfig fabricConfig = new FabricConfig(getConfigReader());
        String caKey = "ca.org1.example.com";
        String userName = "vasia";
        String secret = fabricConfig.registerUser(caKey, userName, "org1");
        User user = fabricConfig.enrollUser(caKey, userName, secret);
        System.out.println(user.getEnrollment().getCert());
    }
}
