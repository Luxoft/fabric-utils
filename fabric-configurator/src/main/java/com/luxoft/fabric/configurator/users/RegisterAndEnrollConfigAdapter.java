package com.luxoft.fabric.configurator.users;

import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import java.util.Set;

public interface RegisterAndEnrollConfigAdapter {

    Set<String> getCAsKeys();

    String getAdminName(String caKey);

    String getAdminSecret(String caKey);

    HFCAClient createHFCAClient(String caKey) throws RuntimeException;

    String getCaMspId(String caKey) throws InvalidArgumentException;
}
