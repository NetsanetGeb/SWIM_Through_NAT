package se.kth.swim.timeout;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

public class PingTimeout extends Timeout {

    public PingTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}