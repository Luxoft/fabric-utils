package com.luxoft.fabric.configurator.users;

import org.hyperledger.fabric.sdk.User;

public interface UserEnrollAndRegisterService {

    User enrollUser(String caKey, String userName, String userSecret) throws Exception;

    String registerUser(String caKey, String userName, String userAffiliation) throws Exception;

}
