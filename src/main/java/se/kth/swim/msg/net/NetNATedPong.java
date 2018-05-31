package se.kth.swim.msg.net;

import se.kth.swim.msg.NATedPong;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetNATedPong extends NetMsg<NATedPong> {

    public NetNATedPong(NatedAddress src, NatedAddress dst, int pingNr) {
        super(src, dst, new NATedPong(pingNr));
    }

    private NetNATedPong(Header<NatedAddress> header, NATedPong content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetNATedPong(newHeader, getContent());
    }

}
