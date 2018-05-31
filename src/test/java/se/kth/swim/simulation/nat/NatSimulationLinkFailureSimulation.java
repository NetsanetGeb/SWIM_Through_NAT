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
package se.kth.swim.simulation.nat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import se.kth.swim.simulation.SwimScenario;
import se.sics.kompics.Kompics;
import se.sics.kompics.simulation.SimulatorScheduler;
import se.sics.p2ptoolbox.simulator.run.LauncherComp;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

public class NatSimulationLinkFailureSimulation {
    
    private static final int LENGTH_OF_SIMULATION = 200; //Length of simulation, in cycles.
    private static final int NUMBER_OF_NODES = 50; //Number of nodes in the simulation.
    private static final int NUM_OF_BOOTSTRAP = 4; //Number of bootstrap nodes. 
    private static final boolean ENABLE_NAT = true; //true if NATED nodes are to be allowed.
    private static final int NAT_FRACTION  = 2; 
    private static final int NUMBER_OF_FAILS = 20; // Number of nodes that should Fail
    private static final int FAILURE_AFTER = 100; // Start time of failure

    public static void main(String[] args) {
        
        LauncherComp.scheduler = new SimulatorScheduler();
        long seedSim = 123; 
         
        /** 
         * Tests the Nodes including the ones behind Nats(NATed Nodes) by Simulating Link Failure and varying 
         *   1. Number of nodes at the third parameter 
         *   2. Piggy back Message size set in SwimComp.java
         *   3. Bootstrap size at the fourth parameter
         *   4. Number of link fails at the seventh parameter
         *   5. Start of failure at the last parameter
        **/
        
      LauncherComp.scenario = SwimScenario.linkFailure(seedSim, LENGTH_OF_SIMULATION, NUMBER_OF_NODES, NUM_OF_BOOTSTRAP, ENABLE_NAT, NAT_FRACTION , NUMBER_OF_FAILS, FAILURE_AFTER);

        /*
           To test the below scenarios uncomment a specific scenario and run it as a file.
        */
      
       //Test with link deaths, killing 100 links
       // LauncherComp.scenario = SwimScenario.linkFailure(seedSim, 200, 50, 4, ENABLE_NAT, 2, 100, 50);
       // LauncherComp.scenario = SwimScenario.linkFailure(seedSim, 200, 50, 4, ENABLE_NAT, 2, 50, 50);
       
        try {
            LauncherComp.simulatorClientAddress = new BasicNatedAddress(new BasicAddress(InetAddress.getByName("127.0.0.1"), 30000, -1));
        } catch (UnknownHostException ex) {
            throw new RuntimeException("cannot create address for localhost");
        }

        Kompics.setScheduler(LauncherComp.scheduler);
        Kompics.createAndStart(LauncherComp.class, 1);
        try {
            Kompics.waitForTermination();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }
}

