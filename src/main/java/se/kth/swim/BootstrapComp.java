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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.NetStatusMsg;
import se.sics.kompics.*;
import se.sics.kompics.network.Address;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;


public class BootstrapComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(BootstrapComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private static final boolean LOGGING_INFO = false;
    public static Map<Integer, Map<Address, Status>> stateReport;  //Status report with status number and address who sent the report.

    public BootstrapComp(BootstrapInit init) {
        this.selfAddress = init.selfAddress;

        if (LOGGING_INFO) {
            log.info("{} initiating...", new Object[]{selfAddress.getId()});
        }

        stateReport = new HashMap<Integer, Map<Address, Status>>();

        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (LOGGING_INFO) {
                log.info("{} starting...", new Object[]{selfAddress});
            }
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (LOGGING_INFO) {
                log.info("{} stopping...", new Object[]{selfAddress});
            }
        }

    };

    //Handler for status reports and SwimComponent sending Statuses periodically
     
    private Handler<NetStatusMsg> handleStatus = new Handler<NetStatusMsg>() {

        @Override
        public void handle(NetStatusMsg status) {
            if (LOGGING_INFO) {
                log.info("{} status num:{} from:{} received-pings:{} sent-pings:{}, Alive nodes: {}", new Object[]{selfAddress.getId(), status.getContent().statusNr, status.getHeader().getSource(), status.getContent().receivedPings, status.getContent().sentPings, status.getContent().getAliveNodes()});
            }

            Map<Address, Status> statusesFromNode = stateReport.get(status.getContent().getStatusNr());

            if (statusesFromNode == null) {
                statusesFromNode = new HashMap<Address, Status>();
                stateReport.put(status.getContent().getStatusNr(), statusesFromNode);
            }

            statusesFromNode.put(status.getSource().getBaseAdr(), status.getContent());
        }
    };

    // Calculation  Of Convergence
    public static void calculateSystemConvergence() throws UnsupportedEncodingException {
     
        PrintWriter writer = null;
        try {
            writer = new PrintWriter("SystemConvergance.txt", "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        Map<Integer, Double> convergenceByStatusNr = new HashMap<Integer, Double>();

        //Loop through all status numbers 
        for (int statusNr : stateReport.keySet()) {
            Map<Address, Status> statusesForNr = stateReport.get(statusNr);

            Set<Address> allAliveNodes = new HashSet<Address>();
            Set<Address> commonAliveNodes = null;
            int nrOfDisconnectedNodes = 0;
            
            for (Address address : statusesForNr.keySet()) {
                Status status = statusesForNr.get(address);

                NatedAddress natedAddress = new BasicNatedAddress((BasicAddress) address); //Adding sender node to alive nodes list
                status.getAliveNodes().put(natedAddress, 0);
                allAliveNodes.addAll(convertToAddress(status.getAliveNodes().keySet()));   //Add all alive nodes to a set

                if (status.getAliveNodes().isEmpty()) {
                    nrOfDisconnectedNodes++;
                    System.out.println("Node num: " + ((BasicAddress) address).getId() + " doesn't have any alive nodes!");
                }
                else {
                    if (commonAliveNodes == null) {
                        commonAliveNodes = new HashSet<Address>(convertToAddress(status.getAliveNodes().keySet()));
                    }
                    else {
                        commonAliveNodes.retainAll(convertToAddress(status.getAliveNodes().keySet()));
                    }
                }

            }

            //The convergence is calculated by dividing common nodes to total nodes in the system.
            double convergenceRate = Math.max(((double) (commonAliveNodes.size() - nrOfDisconnectedNodes) / (double) Math.max(1, allAliveNodes.size())), 0);

            if (convergenceRate > 1) { 
                convergenceRate = 1 / convergenceRate;
            }

            log.info("Number of alive nodes: " + commonAliveNodes.size() + ", Number of alive nodes: " + allAliveNodes.size());
            convergenceByStatusNr.put(statusNr, convergenceRate);
        }

        for (int statusNr : convergenceByStatusNr.keySet()) {
            SwimComp.log.info("Convergence at iteration {}: {}", statusNr, convergenceByStatusNr.get(statusNr));
            writer.println(convergenceByStatusNr.get(statusNr).toString());
        }
        writer.close();
    }
    
    private static Set<Address> convertToAddress(Set<NatedAddress> nodes) {
        Set<Address> addresses = new HashSet<Address>();
        for (NatedAddress node : nodes) {
            addresses.add(node.getBaseAdr());
        }
        return addresses;
    }

    public static class BootstrapInit extends Init<BootstrapComp> {

        public final NatedAddress selfAddress;

        public BootstrapInit(NatedAddress selfAddress) {
            this.selfAddress = selfAddress;
        }
    }

}
