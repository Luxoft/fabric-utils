package com.luxoft.fabric.configurator.users;

import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.sdk.User;

public class UserEnrollAndRegisterImplBasedOnFabricConfig implements UserEnrollAndRegisterService {

    private final FabricConfig fabricConfig;

    @Override
    public User enrollUser(String caKey, String userName, String userSecret) throws Exception {
        return fabricConfig.enrollUser(caKey, userName, userSecret);
    }

    @Override
    public String registerUser(String caKey, String userName, String userAffiliation) throws Exception {
        return fabricConfig.registerUser(caKey, userName, userAffiliation);
    }

    public UserEnrollAndRegisterImplBasedOnFabricConfig(FabricConfig fabricConfig) {
        this.fabricConfig = fabricConfig;
    }
}