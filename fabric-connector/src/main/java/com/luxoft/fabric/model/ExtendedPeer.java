package com.luxoft.fabric.model;

import org.hyperledger.fabric.sdk.Peer;

public class ExtendedPeer {
    private final Peer peer;
    private final boolean isExternal;

    public ExtendedPeer(Peer peer, boolean isExternal) {
        this.peer = peer;
        this.isExternal = isExternal;
    }

    public Peer getPeer() {
        return peer;
    }

    public boolean isExternal() {
        return isExternal;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExtendedPeer)) return false;

        ExtendedPeer that = (ExtendedPeer) o;

        if (isExternal != that.isExternal) return false;
        return peer.equals(that.peer);
    }

    @Override
    public int hashCode() {
        int result = peer.hashCode();
        result = 31 * result + (isExternal ? 1 : 0);
        return result;
    }
}
