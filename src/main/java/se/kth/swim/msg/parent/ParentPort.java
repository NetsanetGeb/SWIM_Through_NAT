package se.kth.swim.msg.parent;

import se.sics.kompics.PortType;

public class ParentPort extends PortType {
    {
        indication(NewParentNotification.class);
    }
}
