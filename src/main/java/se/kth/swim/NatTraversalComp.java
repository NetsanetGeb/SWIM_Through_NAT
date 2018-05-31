/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.util.Container;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.msg.net.NetNATedPing;
import se.kth.swim.msg.net.NetNATedPong;
import se.kth.swim.msg.parent.NewParentNotification;
import se.kth.swim.msg.parent.ParentPort;
//import se.kth.swim.simulation.SwimScenario;
import se.kth.swim.timeout.HeartbeatTimeout;
import se.kth.swim.timeout.NATedPingTimeout;
import se.sics.kompics.*;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {

  
    private static final Logger log = LoggerFactory.getLogger(NatTraversalComp.class);
    private Negative<Network> local = provides(Network.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Negative<ParentPort> parentPort = provides(ParentPort.class);

    private final NatedAddress selfAddress;
    private final Random rand;
    private static final int HEARTBEAT_TIMEOUT = 500;   
    private static final int PING_TIMEOUT = 500;     
    private int sentPings;                              //Number of Pings sent
    private Set<Integer> pingedParents;                 //Parents pinged which have not sent a pong sofar
    private Set<NatedAddress> latestParentSample;       //Sample received from croupier about parents
    private Set<Address> deadParents;                  
    public static int nrBootstrap;
    
    private static final boolean GIVEN_LOGGING = false;
    private static final boolean LOGGING_NEW = false;
    
    public NatTraversalComp(NatTraversalInit init) {
        this.selfAddress = init.selfAddress;

        if (GIVEN_LOGGING) {
            log.info("{} {} initiating...", new Object[]{selfAddress.getId(), (selfAddress.isOpen() ? "OPEN" : "NATED")});
        }

        this.rand = new Random(init.seed);

        this.pingedParents = new HashSet<Integer>();
        this.deadParents = new HashSet<Address>();
        this.latestParentSample = new HashSet<NatedAddress>();
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleIncomingMsg, network);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleOutgoingMsg, local);
        subscribe(handleCroupierSample, croupier);
        subscribe(handleHeartbeatTimeout, timer);
        subscribe(handlePingTimeout, timer);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (GIVEN_LOGGING) {
                log.info("{} starting...", new Object[]{selfAddress.getId()});
            }
            scheduleHeartbeating();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (GIVEN_LOGGING) {
                log.info("{} stopping...", new Object[]{selfAddress.getId()});
            }
        }

    };

    private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            if (GIVEN_LOGGING) {
                log.trace("{} received msg:{}", new Object[]{selfAddress.getId(), msg});
            }
            Header<NatedAddress> header = msg.getHeader();
            if (header instanceof SourceHeader) {
                if (!selfAddress.isOpen()) {
                    throw new RuntimeException("source header msg received on nated node - nat traversal logic error");
                }
                SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
                if (sourceHeader.getActualDestination().getParents().contains(selfAddress)) {
                    if (GIVEN_LOGGING) {
                        log.info("{} relaying message for:{}", new Object[]{selfAddress.getId(), sourceHeader.getSource()});
                    }
                    RelayHeader<NatedAddress> relayHeader = sourceHeader.getRelayHeader();
                    trigger(msg.copyMessage(relayHeader), network);
                    return;
                }
                else {
                    if (GIVEN_LOGGING) {
                        log.warn("{} received weird relay message:{} - dropping it", new Object[]{selfAddress.getId(), msg});
                    }
                    return;
                }
            }
            else if (header instanceof RelayHeader) {
                if (selfAddress.isOpen()) {
                    throw new RuntimeException("relay header msg received on open node - nat traversal logic error");
                }
                RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
                if (GIVEN_LOGGING) {
                    log.info("{} delivering relayed message:{} from:{}", new Object[]{selfAddress.getId(), msg, relayHeader.getActualSource()});
                }
                Header<NatedAddress> originalHeader = relayHeader.getActualHeader();
                trigger(msg.copyMessage(originalHeader), local);
                return;
            }
            else {
                if (GIVEN_LOGGING) {
                    log.info("{} delivering direct message:{} from:{}", new Object[]{selfAddress.getId(), msg, header.getSource()});
                }
                trigger(msg, local);
                return;
            }
        }

    };

    private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {

        @Override
        public void handle(NetMsg<Object> msg) {
            if (GIVEN_LOGGING) {
                log.trace("{} sending msg:{}", new Object[]{selfAddress.getId(), msg});
            }
            Header<NatedAddress> header = msg.getHeader();
            if (header.getDestination().isOpen()) {
                if (GIVEN_LOGGING) {
                    log.info("{} sending direct message:{} to:{}", new Object[]{selfAddress.getId(), msg, header.getDestination()});
                }
                trigger(msg, network);
                return;
            }
            else {
                if (header.getDestination().getParents().isEmpty()) {
                    throw new RuntimeException("nated node with no parents in node " + selfAddress + ". The orphan is " + header.getDestination());
                }
                NatedAddress parent = randomNode(header.getDestination().getParents());
                SourceHeader<NatedAddress> sourceHeader = new SourceHeader(header, parent);
                if (GIVEN_LOGGING) {
                    log.info("{} sending message:{} to relay:{}", new Object[]{selfAddress.getId(), msg, parent});
                }
                trigger(msg.copyMessage(sourceHeader), network);
                return;
            }
        }

    };

   
    private Handler handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            latestParentSample.clear();

            if (GIVEN_LOGGING) {
                log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(), event.publicSample);
            }

            if (!selfAddress.isOpen()) {
                //use this to change parent in case it died
                Set<Container<NatedAddress, Object>> publicSample = new HashSet<Container<NatedAddress, Object>>(event.publicSample);
                for (Container<NatedAddress, Object> container : publicSample) {
                    latestParentSample.add(container.getSource()); 
                }
                if (LOGGING_NEW) {
                    if (latestParentSample.size() == 0) {
                        log.info( " Empty Sample Received By:"+ selfAddress);
                    }
                }
                sendNewParents(latestParentSample);                
            }
        }
    };

   //Updating parents if old parent has died.
    private void sendNewParents(Set<NatedAddress> inputPeers) {
        Set<NatedAddress> samplePeers = new HashSet<NatedAddress>();
        //Identify dead parents from alive parents
        for (NatedAddress node : inputPeers) { 
            if (!deadParents.contains(node.getBaseAdr())) {
                samplePeers.add(node);
            }
        }
        for (NatedAddress node : selfAddress.getParents()) { 
            if (!deadParents.contains(node.getBaseAdr())) {
                samplePeers.add(node);
            }
        }

        List<NatedAddress> samplePeerList = new ArrayList<NatedAddress>(samplePeers); 
        Collections.shuffle(samplePeerList, rand);
        Set<NatedAddress> aliveParents = new HashSet<NatedAddress>(selfAddress.getParents());
        Set<NatedAddress> addressesToRemove = new HashSet<NatedAddress>();
        for (NatedAddress node : aliveParents) {
            if (deadParents.contains(node.getBaseAdr()))
                addressesToRemove.add(node);
        }
        aliveParents.removeAll(addressesToRemove);
        if (LOGGING_NEW) {
            if (aliveParents.size() < nrBootstrap) {
                log.info(selfAddress + "I'm low on parents. THe croupier sample is " + latestParentSample);
            }
        }
        boolean listUpdated = false;                                      
        for (NatedAddress address : samplePeerList) {
            
            if (aliveParents.size() >= nrBootstrap) {
                break;
            }
            if (!address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
                listUpdated = true;
                aliveParents.add(address);
            }
        }

        if (listUpdated) {                                              
            Set<NatedAddress> setToSend = new HashSet<NatedAddress>(aliveParents);
            if (LOGGING_NEW) {
                log.info("Sending a new parent! The list is " + setToSend);
            }

            trigger(new NewParentNotification(setToSend), parentPort);
        }
    }

    //Handler for pings.
    private Handler<NetNATedPing> handlePing = new Handler<NetNATedPing>() {
        @Override
        public void handle(NetNATedPing netNatPing) {
            if (LOGGING_NEW) {
                log.info("Answering hearbeat from " + netNatPing.getSource() + ". I'm node " + selfAddress);
            }

            trigger(new NetNATedPong(selfAddress, netNatPing.getSource(), netNatPing.getContent().getPingNr()), network);
        }
    };

    //Handler for Pongs received by removing the node that sent pong from pingedParents
    private Handler<NetNATedPong> handlePong = new Handler<NetNATedPong>() {
        @Override
        public void handle(NetNATedPong netNatPong) {
            if (LOGGING_NEW) {
                log.info("Received a NatPong from " + netNatPong.getSource() + ". I'm node " + selfAddress);
            }

            pingedParents.remove(netNatPong.getContent().getPingNr());
        }
    };

    //Sent pings according to heartbeat time out
    private Handler<HeartbeatTimeout> handleHeartbeatTimeout = new Handler<HeartbeatTimeout>() {
        @Override
        public void handle(HeartbeatTimeout heartbeatTimeout) {

            for (NatedAddress address : selfAddress.getParents()) {
                if (LOGGING_NEW) {
                    log.info("Sending a hearbeat from address " + selfAddress + " to address " + address);
                }

                trigger(new NetNATedPing(selfAddress, address, sentPings), network);

                pingedParents.add(sentPings);
                ScheduleTimeout spt = new ScheduleTimeout(PING_TIMEOUT);
                NATedPingTimeout sc = new NATedPingTimeout(spt, address, sentPings);
                sentPings++;
                spt.setTimeoutEvent(sc);
                trigger(spt, timer);
            }
        }
    };

    //Handler for timeout pings in and nodes which are still in the pingedParents Set  are declared dead.
    private Handler<NATedPingTimeout> handlePingTimeout = new Handler<NATedPingTimeout>() {
        @Override
        public void handle(NATedPingTimeout natPingTimeout) {
            if (pingedParents.contains(natPingTimeout.getPingNr())) {
                if (LOGGING_NEW) {
                    log.info("Declaring node " + natPingTimeout.getAddress() + " dead. I'm node " + selfAddress);
                }

                deadParents.add(natPingTimeout.getAddress().getBaseAdr());
                pingedParents.remove(natPingTimeout.getPingNr());
                sendNewParents(latestParentSample);
            }
        }
    };

    //Scheduling Heartbeat
    private void scheduleHeartbeating() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(HEARTBEAT_TIMEOUT, HEARTBEAT_TIMEOUT);
        HeartbeatTimeout sc = new HeartbeatTimeout(spt);
        spt.setTimeoutEvent(sc);
        trigger(spt, timer);
    }

    //Returns a random node from the available nodes
    private NatedAddress randomNode(Set<NatedAddress> nodes) {
        int index = rand.nextInt(nodes.size());
        Iterator<NatedAddress> it = nodes.iterator();
        while (index > 0) {
            it.next();
            index--;
        }
        return it.next();
    }
    
    public static class NatTraversalInit extends Init<NatTraversalComp> {

        public final NatedAddress selfAddress;
        public final long seed;

        public NatTraversalInit(NatedAddress selfAddress, long seed) {
            this.selfAddress = selfAddress;
            this.seed = seed;
        }
    }

}
