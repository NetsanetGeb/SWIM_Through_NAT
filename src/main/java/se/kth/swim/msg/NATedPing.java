package se.kth.swim.msg;


public class NATedPing {
    int pingNr;

    public int getPingNr() {
        return pingNr;
    }

    public NATedPing(int pingNr) {
        this.pingNr = pingNr;
    }
}
