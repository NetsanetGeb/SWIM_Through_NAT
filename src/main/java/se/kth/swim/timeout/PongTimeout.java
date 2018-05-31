package se.kth.swim.timeout;

import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class PongTimeout extends Timeout {

    private int pingNr;
    private NatedAddress address;

    public PongTimeout(ScheduleTimeout request, int pingNr, NatedAddress address) {
        super(request);

        this.pingNr = pingNr;
        this.address = address;
    }

    public int getPingNr() {
        return pingNr;
    }

    public void setPingNr(int pingNr) {
        this.pingNr = pingNr;
    }

    public NatedAddress getAddress() {
        return address;
    }

    public void setAddress(NatedAddress address) {
        this.address = address;
    }
}