package com.luxoft.fabric.model;

import java.util.Map;

public class ConfigDataValidator {

    final ConfigData.Root configData;

    public ConfigDataValidator(ConfigData.Root configData) {
        this.configData = configData;
    }

    public void validate() {

        Map<String, ConfigData.Admin> admins = configData.admins;
        for (Map.Entry<String, ConfigData.Admin> adminDataEntry : configData.admins.entrySet()) {
            if (adminDataEntry.getValue().managedOrgs == null || adminDataEntry.getValue().managedOrgs.isEmpty()) {
                throw new RuntimeException(String.format("Admin with key %s does not have any managed orgs", adminDataEntry.getKey()));
            }
        }

    }


}
