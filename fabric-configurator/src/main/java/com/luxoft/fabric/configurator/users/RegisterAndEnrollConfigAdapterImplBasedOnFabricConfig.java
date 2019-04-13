package com.luxoft.fabric.configurator.users;

import com.luxoft.fabric.FabricConfig;
import org.hyperledger.fabric.sdk.exception.InvalidArgumentException;
import org.hyperledger.fabric_ca.sdk.HFCAClient;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;


public class RegisterAndEnrollConfigAdapterImplBasedOnFabricConfig implements RegisterAndEnrollConfigAdapter {

    private final FabricConfig fabricConfig;

    @Override
    public HFCAClient createHFCAClient(String caKey) throws RuntimeException {
        try {
            return fabricConfig.createHFCAClient(caKey, null);
        } catch (MalformedURLException | InvalidArgumentException e) {
            throw new RuntimeException("Exception during creation HFCA Client", e);
        }
    }

    @Override
    public String getCaMspId(String caKey) throws InvalidArgumentException {
        return FabricConfig.getOrThrow(fabricConfig.getCADetails(caKey).mspID, String.format("ca[%s].mspID", caKey));
    }

    @Override
    public String getAdminName(String caKey) {
        return fabricConfig.getCADetails(caKey).adminLogin;
    }

    @Override
    public String getAdminSecret(String caKey) {
        return fabricConfig.getCADetails(caKey).adminSecret;
    }

    @Override
    public Set<String> getCAsKeys() {
        return new HashSet<>(fabricConfig.getCAsKeys());
    }

    RegisterAndEnrollConfigAdapterImplBasedOnFabricConfig(FabricConfig fabricConfig) {
        this.fabricConfig = fabricConfig;
    }
}