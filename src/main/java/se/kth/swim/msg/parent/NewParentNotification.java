package se.kth.swim.msg.parent;

import se.sics.kompics.KompicsEvent;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.Set;


public class NewParentNotification implements KompicsEvent {
    Set<NatedAddress> parents;

    public NewParentNotification(Set<NatedAddress> address) {
        super();
        this.parents = address;
    }

    public Set<NatedAddress> getParents() {
        return parents;
    }

}
