package se.kth.swim.msg.net;

import se.kth.swim.msg.NATedPing;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNATedPing extends NetMsg<NATedPing> {

    public NetNATedPing(NatedAddress src, NatedAddress dst, int pingNr) {
        super(src, dst, new NATedPing(pingNr));
    }

    private NetNATedPing(Header<NatedAddress> header, NATedPing content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetNATedPing(newHeader, getContent());
    }


}
