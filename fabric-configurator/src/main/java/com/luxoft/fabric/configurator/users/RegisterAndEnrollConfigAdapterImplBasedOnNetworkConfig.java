package com.luxoft.fabric.configurator.users;

import org.hyperledger.fabric.sdk.NetworkConfig;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.exception.InvalidArgumentException;
import java.net.MalformedURLException;
import java.util.Set;
import java.util.stream.Collectors;

public class RegisterAndEnrollConfigAdapterImplBasedOnNetworkConfig implements RegisterAndEnrollConfigAdapter {

    private final NetworkConfig networkConfig;

    @Override
    public HFCAClient createHFCAClient(String caKey) throws RuntimeException {
        try {
            return HFCAClient.createNewInstance(getCAInfo(caKey));
        } catch (MalformedURLException | InvalidArgumentException e) {
            throw new RuntimeException("Exception during creation HFCA Client", e);
        }
    }

    @Override
    public String getCaMspId(String caKey) {
        return networkConfig.getClientOrganization().getMspId();
    }

    @Override
    public String getAdminName(String caKey) {
        return getCAInfo(caKey).getRegistrars().iterator().next().getName();
    }

    @Override
    public String getAdminSecret(String caKey) {
        return getCAInfo(caKey).getRegistrars().iterator().next().getEnrollSecret();
    }

    @Override
    public Set<String> getCAsKeys() {
        return networkConfig.getClientOrganization().getCertificateAuthorities()
                .stream().map(NetworkConfig.CAInfo::getName).collect(Collectors.toSet());
    }

    private NetworkConfig.CAInfo getCAInfo(String caKey) {
        for (NetworkConfig.CAInfo caInfo : networkConfig.getClientOrganization().getCertificateAuthorities()) {
            if (caInfo.getName().equals(caKey))
                return caInfo;
        }
        throw new IllegalArgumentException(String.format("No CA with name %s found", caKey));
    }

    RegisterAndEnrollConfigAdapterImplBasedOnNetworkConfig(NetworkConfig networkConfig) {
        this.networkConfig = networkConfig;
    }
}