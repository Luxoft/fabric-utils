package com.luxoft.fabric;

import org.hyperledger.fabric.sdk.Enrollment;

import java.io.Serializable;
import java.security.PrivateKey;

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

}
