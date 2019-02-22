package com.luxoft.fabric.model;

import com.luxoft.fabric.utils.MiscUtils;

public class FileReference {
    private final String value;

    public FileReference(String value) {
        this.value = value;
    }

    public String asString() {
        return value;
    }

    public String getFileName(String topDir) {
        return MiscUtils.resolveFile(value, topDir);
    }
}
