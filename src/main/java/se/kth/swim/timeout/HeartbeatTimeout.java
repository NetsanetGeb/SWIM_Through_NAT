package se.kth.swim.timeout;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

public class HeartbeatTimeout extends Timeout {

    public HeartbeatTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}
