# SWIM_Through_NAT

Different simulation scenarios and parameter setting were used to test, analyze and evaluate the performance of SWIM distributed 
membership protocol. Many of the tests and evaluations focus on Calculating convergence of Open and NATed peers in the system under
different scenarios.

There are six different simulation scenario files available for triggering different situations like node failures and link failures 
for the OPEN and NATed peers.

To `run` these simulation go to the Test package and select `Run File` after selecting the scenario file you wish to execute or run it as
a file.  There are 2 packages for simulation:

### 1)	Package  se.kth.swim.simulation.nat:   

This package consists of 3 simulation files with different scenarios inside each of these files for testing SWIM with 50% NATed nodes:
	
```
   - NatBootSimulation.java
   - NatNodeFailureSimulation.java
   - NatSimulationLinkFailureSimulation.java
```

To test the SWIM NAT transversal without failures simply select the `NATBootSimulation.java` and Run it as a file (Run File). There are 
different scenarios inside this file for analyzing convergence under different network sizes and we can test them by `uncommenting` the 
specific scenarios that we wish to execute and run them as file. 

For testing the SWIM with NAT transversal that considers Node failures after specified time, Run the file `NatNodeFailureSimulation.java`
and similarly we can uncomment the scenario you wish to run inside this file.

For testing the SWIM with NAT transversal that considers Link failures after specified time, Run the file `NatSimulationLinkFailureSimula
tion.java` and similarly we can uncomment the scenario you wish to execute inside this file.

### 2)	Package se.kth.swim.simulation.open

This package consists of 3 simulation files with different scenarios inside each of these files for testing SWIM with 100 % OPEN nodes:

```
   - BootSimulationTest.java
   - NodeFailureSimulation.java
   - SimulationLinkFailure.java
```

Similarly to test these files select the `.java` test file you wish to execute and run it as a file (`Run File`).

## Effect of Message Size Limmiting

The SWIM component (SwimComp.java) contains the following important parameters which are used for analyzing performance:

```
    -	PIGGYBACK_MESSAGE_SIZE: Amount of information piggybacked over single message.
    -	K_INDIRECT: Number of indirect pings that should be sent if a direct ping fails.
    -	LAMBDA: How many times each node status change is piggybacked. (LAMBDA * log(n))
```

To test the effect of `limiting message size`, change the `PIGGYBACK_MESSAGE_SIZE` which is found in the `SwimComp.java`.

## Note: 

For the simulations a `p2ptoolbox.simulator` dependency is used in the SWIM project instead of the `kompics.simulator` dependency. 
Because as we tried it last time tin the lab session there have been some issues in terms of compatibility with windows. Hence there
are some changes in the usage of the basic natted address.

