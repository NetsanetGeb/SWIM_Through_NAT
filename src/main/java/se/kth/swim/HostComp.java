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
import se.kth.swim.croupier.CroupierComp;
import se.kth.swim.croupier.CroupierConfig;
import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.util.OverlayFilter;
import se.kth.swim.msg.parent.ParentPort;
import se.sics.kompics.*;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

import java.util.ArrayList;
import java.util.Set;
import se.kth.swim.NatTraversalComp.NatTraversalInit;
import se.kth.swim.SwimComp.SwimInit;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class HostComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(HostComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private Component swim;
    private Component nat;
    private Component croupier;
    private static final boolean LOGGING_GIVEN = false;

    public HostComp(HostInit init) {
        this.selfAddress = init.selfAddress;

        if (LOGGING_GIVEN) {
            log.info("{} initiating...", new Object[]{selfAddress});
        }

        subscribe(handleStart, control);
        subscribe(handleStop, control);

        int overlayId = 1; //so far we don' start multiple croupier overlay
        croupier = create(CroupierComp.class, new CroupierComp.CroupierInit(selfAddress, new ArrayList<NatedAddress>(init.bootstrapNodes), init.seed, init.croupierConfig, overlayId));
        connect(croupier.getNegative(Timer.class), timer);
        connect(croupier.getNegative(Network.class), network, new OverlayFilter(overlayId));

        nat = create(NatTraversalComp.class, new NatTraversalInit(selfAddress, init.seed));
        connect(nat.getNegative(Timer.class), timer);
        connect(nat.getNegative(Network.class), network);
        connect(nat.getNegative(CroupierPort.class), croupier.getPositive(CroupierPort.class));
        
         /* nat = create(NatTraversalComp.class, new NatTraversalComp.NatTraversalInit(selfAddress, init.seed));
        connect(nat.getNegative(Network.class), network, Channel.TWO_WAY);
        connect(nat.getNegative(Timer.class), timer, Channel.TWO_WAY);
        connect(nat.getNegative(CroupierPort.class), croupier.getPositive(CroupierPort.class), Channel.TWO_WAY);*/

        swim = create(SwimComp.class, new SwimInit(selfAddress, init.bootstrapNodes, init.aggregatorAddress, init.seed));
        connect(swim.getNegative(Timer.class), timer);
        connect(swim.getNegative(Network.class), nat.getPositive(Network.class));
        connect(swim.getNegative(ParentPort.class), nat.getPositive(ParentPort.class));
        
         /* swim = create(SwimComp.class, new SwimComp.SwimInit(selfAddress, init.bootstrapNodes));
        connect(swim.getNegative(Timer.class), timer, Channel.TWO_WAY);
        connect(swim.getNegative(Network.class), nat.getPositive(Network.class), Channel.TWO_WAY); */
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            if (LOGGING_GIVEN) {
                log.info("{} starting...", new Object[]{selfAddress});
            }
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            if (LOGGING_GIVEN) {
                log.info("{} stopping...", new Object[]{selfAddress});
            }
        }

    };

    public static class HostInit extends Init<HostComp> {

        public final NatedAddress selfAddress;
        public final Set<NatedAddress> bootstrapNodes;
        public final NatedAddress aggregatorAddress;
        public final long seed;
        public final CroupierConfig croupierConfig;
        
         /*public HostInit(NatedAddress selfAddress, List<NatedAddress> bootstrapNodes, long seed, CroupierConfig croupierConfig) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.seed = seed;
            this.croupierConfig = croupierConfig;
        }*/

        public HostInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress, long seed, CroupierConfig croupierConfig) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
            this.seed = seed;
            this.croupierConfig = croupierConfig;
        }
    }
}
