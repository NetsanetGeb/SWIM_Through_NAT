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
import se.kth.swim.msg.Pong;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.*;
import se.kth.swim.msg.parent.NewParentNotification;
import se.kth.swim.msg.parent.ParentPort;
import se.kth.swim.node.NodeManager;
import se.kth.swim.timeout.*;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.*;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */

public class SwimComp extends ComponentDefinition {

   
    private static final int K_INDIRECT = 4; //Number of nodes used for indirect K-ping request
    public static final int PIGGYBACK_MESSAGE_SIZE = 10000000; //Piggybacked message size in each pong.
    public static final int LAMBDA = 3; //Number of times the node status change is piggybacked. Lambda * log(n)
    
    
    //Settings for Analysis of Convergence by Limiting Mesage size and Lamda
    //public static final int PIGGYBACK_MESSAGE_SIZE = 6; 
    //public static final int LAMBDA = 3; 
    
    
    private static final int PING_TIMEOUT = 2000; 
    private static final int SUSPECTED_TIMEOUT = 2000; //Timeout for declaring it suspected
    private static final int DEAD_TIMEOUT = 2000; //Timeout for declaring it dead
    private static final int AGGREGATOR_TIMEOUT = 1000; //Latency for sending info to aggregator
    private static final boolean LOGGING_GIVEN = true;

    public static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    private Positive<ParentPort> parentPort = requires(ParentPort.class);

    private final NatedAddress selfAddress;
    private final NatedAddress aggregatorAddress;

    private UUID pingTimeoutId;
    private UUID statusTimeoutId;

    private Random rand;

    //Various counters
    private int sentPings = 0;
    private int receivedPings = 0;
    private int incarnationCounter = 0;
    private int sentStatuses = 0;

    private NodeManager nodeHandler;    //NodeHandler holds all info about nodes in the system
    private List<Integer> sentPingNrs;
    private Map<Integer, NatedAddress> sentIndirectPings;
    private Map<Integer, Integer> kPingNrToPingNrMapping;

    public SwimComp(SwimInit init) {
        if (LOGGING_GIVEN) {
            log.info("{} initiating...", init.selfAddress);
        }

        selfAddress = init.selfAddress;
        aggregatorAddress = init.aggregatorAddress;

        this.rand = new Random(init.seed);

        nodeHandler = new NodeManager(selfAddress, init.seed);

        sentPingNrs = new ArrayList<Integer>();
        sentIndirectPings = new HashMap<Integer, NatedAddress>();
        kPingNrToPingNrMapping = new HashMap<Integer, Integer>();

        // Adding all bootstrap nodes to alive nodes list.
        for (NatedAddress address : init.bootstrapNodes) {
            nodeHandler.addAlive(address, 0);
        }

        if (LOGGING_GIVEN) {
            nodeHandler.printAliveNodes();
        }

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handleAlive, network);
        subscribe(handleNetKPing, network);
        subscribe(handleNetKPong, network);
        subscribe(handleNewParent, parentPort);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handlePongTimeout, timer);
        subscribe(handleSuspectedTimeout, timer);
        subscribe(handleDeadTimeout, timer);
    }

    //  Starting  SWIM Component and scheduling periodic pings and status messages.
     
    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (LOGGING_GIVEN) {
                log.info("{} starting...", new Object[]{selfAddress.getId()});
            }

            schedulePeriodicPing();
            schedulePeriodicStatus();
        }

    };

    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (LOGGING_GIVEN) {
                log.info("{} stopping...", new Object[]{selfAddress.getId()});
            }

            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }

            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

    };
    
    
    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            if (LOGGING_GIVEN) {
                log.info("{} received ping num {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});
            }

            receivedPings++;

            //Adding sender to alive node list
            nodeHandler.copyAlive(event.getSource(), event.getContent().getIncarnationCounter());

            if (LOGGING_GIVEN) {
                log.info("{} sending pong num {} to :{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getSource()});
            }

            //Send a pong
            Pong pong = nodeHandler.getPong(event.getContent().getPingNr(), incarnationCounter);
            trigger(new NetPong(selfAddress, event.getSource(), pong), network);

            if (LOGGING_GIVEN) {
                nodeHandler.printAliveNodes();
            }
        }

    };
    
    private Handler<NetPong> handlePong = new Handler<NetPong>() {

        @Override
        public void handle(NetPong event) {
            if (LOGGING_GIVEN) {
                log.info("{} received pong num {} from:{}", new Object[]{selfAddress.getId(), event.getContent().getPingNr(), event.getHeader().getSource()});
            }

            //If the ping number of the pong was in the list of sent pings, it was a regular ping.
            boolean wasRegularPing = sentPingNrs.remove(Integer.valueOf(event.getContent().getPingNr()));
            if (wasRegularPing) {
                //Adding new nodes to alive node list, considering incarnation numbers.
                for (NatedAddress address : event.getContent().getNewNodes().keySet()) {
                    nodeHandler.addAlive(address, event.getContent().getNewNodes().get(address));
                }

                //Adding all suspected nodes to our suspected list, considering incarnation numbers into account.
                for (NatedAddress address : event.getContent().getSuspectedNodes().keySet()) {
                    nodeHandler.addSuspected(address, event.getContent().getSuspectedNodes().get(address));
                }

                //Adding all dead nodes to the dead list.
                for (NatedAddress address : event.getContent().getDeadNodes().keySet()) {
                    if (LOGGING_GIVEN) {
                        log.info("{} Declared node {} dead from pong", new Object[]{selfAddress.getId(), address});
                    }

                    nodeHandler.addDead(address, event.getContent().getDeadNodes().get(address));
                }

                //Add the node who sent the pong to the alive list.
                nodeHandler.copyAlive(event.getSource(), event.getContent().getIncarnationCounter());

                //finding then node itself in the suspected list
                if (event.getContent().getSuspectedNodes().containsKey(selfAddress)) {
                    if (LOGGING_GIVEN) {
                        log.info("{} Found self in suspected list from node: {}", new Object[]{selfAddress.getId(), event.getSource()});
                    }

                    //Increase the incarnation number and send Alive messages to all alive nodes.
                    incarnationCounter++;

                    for (NatedAddress address : nodeHandler.getAliveNodes().keySet()) {
                        trigger(new NetAliveMsg(selfAddress, address, incarnationCounter), network);
                    }
                }
            }
            //Otherwise, if not a regular ping it was a K-ping. Check if it is still in sent list.
            else if (sentIndirectPings.containsKey(event.getContent().getPingNr())) {
                if (LOGGING_GIVEN) {
                    log.info("{} forwarding KPing result for suspected node {} to: {}", new Object[]{selfAddress.getId(), event.getSource(), sentIndirectPings.get(event.getContent().getPingNr())});
                }

                //Forward response to a k-ping to the requester node.
                trigger(new NetKIndirectPong(selfAddress, sentIndirectPings.get(event.getContent().getPingNr()), event.getSource(), event.getContent().getIncarnationCounter(), kPingNrToPingNrMapping.get(event.getContent().getPingNr())), network);
                sentIndirectPings.remove(event.getContent().getPingNr());
                kPingNrToPingNrMapping.remove(event.getContent().getPingNr());
            }

            if (LOGGING_GIVEN) {
                nodeHandler.printAliveNodes();
            }
        }

    };

    

    // Handler for receivied new parent nodes from the NatTraversal component.
    private Handler<NewParentNotification> handleNewParent = new Handler<NewParentNotification>() {

        @Override
        public void handle(NewParentNotification event) {
         
            selfAddress.getParents().clear();
            selfAddress.getParents().addAll(event.getParents());

            if (LOGGING_GIVEN) {
                log.info("{} New parents arrived: {}", new Object[]{selfAddress.getId(), event.getParents()});
            }

            incarnationCounter++;
            nodeHandler.addNewNodeToSendBuffer(selfAddress, incarnationCounter);
        }

    };

    //  Handler for receiving alive messages and remove node from suspected list by adding it to alive list
    private Handler<NetAliveMsg> handleAlive = new Handler<NetAliveMsg>() {

        @Override
        public void handle(NetAliveMsg netAlive) {
            if (LOGGING_GIVEN) {
                log.info("{} Restored suspected node by alive message from: {}", new Object[]{selfAddress.getId(), netAlive.getSource()});
            }

            nodeHandler.addAlive(netAlive.getSource(), netAlive.getContent().getIncarnationCounter());
        }

    };

    // Handler for receiving K-ping messages and sending ping to the specified node.
    private Handler<NetKIndirectPing> handleNetKPing = new Handler<NetKIndirectPing>() {

        @Override
        public void handle(NetKIndirectPing netKPing) {
            if (LOGGING_GIVEN) {
                log.info("{} received KPing request for suspected node {}", new Object[]{selfAddress.getId(), netKPing.getContent().getAddressToPing()});
            }

            trigger(new NetPing(selfAddress, netKPing.getContent().getAddressToPing(), sentPings, incarnationCounter), network);
            sentIndirectPings.put(sentPings, netKPing.getSource());
            kPingNrToPingNrMapping.put(sentPings, netKPing.getContent().getPingNr());
            sentPings++;
        }

    };

    //  Handler for a receivied response for the K-ping, add the node to the alive list
    private Handler<NetKIndirectPong> handleNetKPong = new Handler<NetKIndirectPong>() {

        @Override
        public void handle(NetKIndirectPong netKPong) {
            if (LOGGING_GIVEN) {
                log.info("{} received KPong for suspected node {}", new Object[]{selfAddress.getId(), netKPong.getContent().getAddress()});
            }

            nodeHandler.copyAlive(netKPong.getContent().getAddress(), netKPong.getContent().getIncarnationCounter());
            sentPingNrs.remove((Integer) netKPong.getContent().getPingNr());

            if (LOGGING_GIVEN) {
                nodeHandler.printAliveNodes();
            }
        }

    };

    //Trigger periodic sending of ping message to a random alive node and handle ping time out.
    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            NatedAddress partnerAddress = nodeHandler.getRandomAliveNode();

            if (partnerAddress != null) {
                if (LOGGING_GIVEN) {
                    log.info("{} sending ping num {} to partner:{}", new Object[]{selfAddress.getId(), sentPings, partnerAddress});
                }

                //Periodically send pings to a random alive node.
                trigger(new NetPing(selfAddress, partnerAddress, sentPings, incarnationCounter), network);

                //Start a timer for when the ping will timeout and we will suspect the node being dead.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(PING_TIMEOUT);
                PongTimeout pongTimeout = new PongTimeout(scheduleTimeout, sentPings, partnerAddress);
                scheduleTimeout.setTimeoutEvent(pongTimeout);
                trigger(scheduleTimeout, timer);

                sentPingNrs.add(sentPings);
                sentPings++;
            }
        }

    };

   
    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            if (LOGGING_GIVEN) {
                log.info("{} sending status num:{} to bootstrap:{}", new Object[]{selfAddress.getId(), sentStatuses, aggregatorAddress});
            }

            //Send a status of nodes to the bootstrap component periodically
            Map<NatedAddress, Integer> sendAliveNodes = new HashMap<NatedAddress, Integer>(nodeHandler.getAliveNodes());
            trigger(new NetStatusMsg(selfAddress, aggregatorAddress, new Status(sentStatuses, receivedPings, sentPings, sendAliveNodes, nodeHandler.getSuspectedNodes(), nodeHandler.getDeadNodes())), network);

            sentStatuses++;
        }

    };

    // Handler for pong timeout and if response to ping wasn't received before pong timeout, the node will become suspected and 
   //Send K-Indirect pings  are sent to ping the node who didn't respond to the ping.
    private Handler<PongTimeout> handlePongTimeout = new Handler<PongTimeout>() {

        @Override
        public void handle(PongTimeout pongTimeout) {
            if (sentPingNrs.contains(pongTimeout.getPingNr())) {
                if (LOGGING_GIVEN) {
                    log.info("{} Suspected missing ping nr {} from node: {}", new Object[]{selfAddress.getId(), pongTimeout.getPingNr(), pongTimeout.getAddress()});
                }

                //Adding the node to suspected list.
                nodeHandler.addSuspected(pongTimeout.getAddress());

                //Get a random selection of our alive nodes to K-ping.
                List<NatedAddress> aliveNodes = new ArrayList<NatedAddress>(nodeHandler.getAliveNodes().keySet());
                aliveNodes.remove(pongTimeout.getAddress());
                Collections.shuffle(aliveNodes, rand);

                //Sending K indirect pings.
                for (int i = 0; i < K_INDIRECT && i < aliveNodes.size(); i++) {
                    if (LOGGING_GIVEN) {
                        log.info("{} sending KPing for suspected node {} to: {}", new Object[]{selfAddress.getId(), pongTimeout.getAddress(), aliveNodes.get(i)});
                    }

                    trigger(new NetKIndirectPing(selfAddress, aliveNodes.get(i), pongTimeout.getAddress(), pongTimeout.getPingNr()), network);
                }

                //Initiating other timer for the K-pings to finish before declaring the node suspected.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(SUSPECTED_TIMEOUT);
                SuspectedTimeout suspectedTimeout = new SuspectedTimeout(scheduleTimeout, pongTimeout.getAddress(), pongTimeout.getPingNr());
                scheduleTimeout.setTimeoutEvent(suspectedTimeout);
                trigger(scheduleTimeout, timer);
            }
        }
    };

   
    private Handler<SuspectedTimeout> handleSuspectedTimeout = new Handler<SuspectedTimeout>() {

        @Override
        public void handle(SuspectedTimeout suspectedTimeout) {
            //If k-pings timeout and the node is still suspected, declare the node dead.
            if (sentPingNrs.contains(suspectedTimeout.getPingNr())) {
                if (LOGGING_GIVEN) {
                    log.info("{} Suspected node: {}", new Object[]{selfAddress.getId(), suspectedTimeout.getAddress()});
                }

                //Ttimer for the K-pings to end before declaring the node suspected.
                ScheduleTimeout scheduleTimeout = new ScheduleTimeout(DEAD_TIMEOUT);
                DeadTimeout deadTimeout = new DeadTimeout(scheduleTimeout, suspectedTimeout.getAddress(), suspectedTimeout.getPingNr());
                scheduleTimeout.setTimeoutEvent(deadTimeout);
                trigger(scheduleTimeout, timer);
            }
        }
    };

   
   
    private Handler<DeadTimeout> handleDeadTimeout = new Handler<DeadTimeout>() {

        @Override
        public void handle(DeadTimeout deadTimeout) {
            //If k-pings timeout and the node is still suspected, declare the node dead.
            if (sentPingNrs.contains(deadTimeout.getPingNr()) && nodeHandler.addDead(deadTimeout.getAddress())) {
                if (LOGGING_GIVEN) {
                    log.info("{} Declared node dead: {}", new Object[]{selfAddress.getId(), deadTimeout.getAddress()});
                }
            }
        }
    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, AGGREGATOR_TIMEOUT);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }

    public static class SwimInit extends Init<SwimComp> {

    public final NatedAddress selfAddress;
    public final Set<NatedAddress> bootstrapNodes;
    public final NatedAddress aggregatorAddress;
    public final long seed;

    public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress, long seed) {
        this.selfAddress = selfAddress;
        this.bootstrapNodes = bootstrapNodes;
        this.aggregatorAddress = aggregatorAddress;
        this.seed = seed;
    }
}

}

