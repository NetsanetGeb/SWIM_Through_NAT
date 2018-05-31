package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;


public class KIndirectPing {

    private NatedAddress addressToPing;
    private int pingNr;

    public KIndirectPing(NatedAddress addressToPing, int pingNr) {
        this.addressToPing = addressToPing;
        this.pingNr = pingNr;
    }

    public NatedAddress getAddressToPing() {
        return addressToPing;
    }

    public void setAddressToPing(NatedAddress addressToPing) {
        this.addressToPing = addressToPing;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }
}
