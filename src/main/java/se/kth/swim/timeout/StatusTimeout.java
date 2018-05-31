package se.kth.swim.timeout;

import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;

public class StatusTimeout extends Timeout {

    public StatusTimeout(SchedulePeriodicTimeout request) {
        super(request);
    }
}