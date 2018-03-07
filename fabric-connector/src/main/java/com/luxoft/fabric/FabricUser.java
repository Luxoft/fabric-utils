package com.luxoft.fabric;

import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;

import java.util.Set;

/**
 * Created by osesov on 03.07.17.
 */
public class FabricUser implements User {
    private final String name;
    private final Set<String> roles;
    private final String account;
    private final Enrollment enrollment;
    private final String mspid;

    public FabricUser(String name, Set<String> roles, String account, Enrollment enrollment, String mspid) {
        this.name = name;
        this.roles = roles;
        this.account = account;
        this.enrollment = enrollment;
        this.mspid = mspid;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Set<String> getRoles() {
        return this.roles;
    }

    @Override
    public String getAccount() {
        return this.account;
    }

    @Override
    public String getAffiliation() {
        return null;
    }

    @Override
    public Enrollment getEnrollment() {
        return this.enrollment;
    }

    @Override
    public String getMspId() {
        return this.mspid;
    }
}
