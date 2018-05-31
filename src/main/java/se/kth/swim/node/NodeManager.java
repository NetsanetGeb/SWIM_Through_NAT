package se.kth.swim.node;

import se.kth.swim.SwimComp;
import se.kth.swim.msg.Pong;
import se.sics.kompics.network.Address;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;


public class NodeManager {

    private NatedAddress selfAddress;
    private Random rand;
    private Map<Address, Integer> aliveNodes, suspectedNodes, deadNodes; //Maps containing our nodes. Key is address, value is incarnation counter.
    private Map<Address, NatedAddress> addressMapping;    //Keeps the mapping between Address and NatedAddress.
    private Map<Address, NodeDetails> sendBuffer;   //Sendbuffer holding the recent node changes that are to be piggybacked.
    private List<Address> pingList;  
    private int pingIndex;    
    
    public NodeManager(NatedAddress selfAddress, long seed) {
        
        this.selfAddress = selfAddress;
        this.rand = new Random(seed);

        aliveNodes = new HashMap<Address, Integer>();
        addressMapping = new HashMap<Address, NatedAddress>();
        suspectedNodes = new HashMap<Address, Integer>();
        deadNodes = new HashMap<Address, Integer>();
        sendBuffer = new HashMap<Address, NodeDetails>();
        pingList = new ArrayList<Address>();
    }

    // Adding a node to alive list and considering incarnation counter 
   
    public void addAlive(NatedAddress address, int incarnationCounter) {
  
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            //If incarnation counter is lower, this is newer, update info.
            if (aliveNodes.get(address.getBaseAdr()) < incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                //If node reported alive is suspected by us, remove it from suspected list.
                if (suspectedNodes.containsKey(address.getBaseAdr())) {
                    suspectedNodes.remove(address.getBaseAdr());
                }

                //Also update counter in send queue
                if (sendBuffer.containsKey(address.getBaseAdr())) {
                    NodeDetails nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(incarnationCounter);
                    nodeInfo.setType(NodeDetails.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        //If the node is not already in our alive list, but not declared dead, add it to alive list.
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);

           
            sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.NEW));

            
            addToPingList(address);
        }
    }

   
    public void copyAlive(NatedAddress address, int incarnationCounter) {
      
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            if (aliveNodes.get(address.getBaseAdr()) <= incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                if (suspectedNodes.containsKey(address.getBaseAdr())) {
                    suspectedNodes.remove(address.getBaseAdr());
                }

                if (sendBuffer.containsKey(address.getBaseAdr())) {
                    NodeDetails nodeInfo = sendBuffer.get(address.getBaseAdr());
                    nodeInfo.setIncarnationCounter(incarnationCounter);
                    nodeInfo.setType(NodeDetails.Type.NEW);
                    sendBuffer.put(address.getBaseAdr(), nodeInfo);
                }
            }
        }
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);
            sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.NEW));
            addToPingList(address);
        }
    }

    
 // Propagating when new parents are received
    public void addNewNodeToSendBuffer(NatedAddress address, int incarnationCounter) {
        sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.NEW));
    }

   
    private void addToPingList(NatedAddress address) {
        int insertIndex = (int) (pingList.size() * rand.nextDouble());
        pingList.add(insertIndex, address.getBaseAdr());
    }

   
    public void addSuspected(NatedAddress address, int incarnationCounter) {

        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            if (aliveNodes.get(address.getBaseAdr()) <= incarnationCounter) {
                aliveNodes.put(address.getBaseAdr(), incarnationCounter);
                addressMapping.put(address.getBaseAdr(), address);

                if (!suspectedNodes.containsKey(address.getBaseAdr())) {
                    sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.SUSPECTED));
                }

                suspectedNodes.put(address.getBaseAdr(), incarnationCounter);
            }
        }
        else if (!deadNodes.containsKey(address.getBaseAdr())) {
            aliveNodes.put(address.getBaseAdr(), incarnationCounter);
            addressMapping.put(address.getBaseAdr(), address);
            suspectedNodes.put(address.getBaseAdr(), incarnationCounter);

            //Add node to send buffer in order to propagate it.
            sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.SUSPECTED));
        }
    }

   
    public void addSuspected(NatedAddress address) {
        int incarnationCounter = 0;

        if (aliveNodes.containsKey(address.getBaseAdr())) {
            incarnationCounter = aliveNodes.get(address.getBaseAdr());
        }

        suspectedNodes.put(address.getBaseAdr(), incarnationCounter);
        addressMapping.put(address.getBaseAdr(), address);

        //Add node to send buffer in order to propagate it.
        sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.SUSPECTED));
    }

    
    public void addDead(NatedAddress address, int incarnationCounter) {
        //Never add self to lists.
        if (address.getBaseAdr().equals(selfAddress.getBaseAdr())) {
            return;
        }

        aliveNodes.remove(address.getBaseAdr());
        suspectedNodes.remove(address.getBaseAdr());
        pingList.remove(address.getBaseAdr());
        deadNodes.put(address.getBaseAdr(), incarnationCounter);
        addressMapping.put(address.getBaseAdr(), address);

        //Add node to send buffer in order to propagate it.
        sendBuffer.put(address.getBaseAdr(), new NodeDetails(address, incarnationCounter, NodeDetails.Type.DEAD));
    }

  
    public boolean addDead(NatedAddress address) {
        
        if (suspectedNodes.containsKey(address.getBaseAdr())) {
            addDead(address, 0);

            return true;
        }

        return false;
    }

  
    public NatedAddress getRandomAliveNode() {
        NatedAddress natedAddress = null;
        boolean twice = false;
        while (natedAddress == null) {
            if (pingList.isEmpty() || pingIndex >= pingList.size()) {
                pingList.clear();
                pingList.addAll(aliveNodes.keySet());
                Collections.shuffle(pingList, rand);
                pingIndex = 0;
                if (!twice) {
                    twice = true;
                }
                else {
                    break;
                }
            }
            if (pingList.isEmpty()) {
                return null;
            }
            Address address = pingList.get(pingIndex);
            natedAddress = addressMapping.get(address);
            pingIndex++;
        }
        return natedAddress;
    }

   
    public Pong getPong(int pingNr, int incarnationCounter) {
        Map<Address, Integer> newNodesToSend = new HashMap<Address, Integer>();
        Map<Address, Integer> suspectedNodesToSend = new HashMap<Address, Integer>();
        Map<Address, Integer> deadNodesToSend = new HashMap<Address, Integer>();

        List<NodeDetails> bufferAsList = new ArrayList<NodeDetails>(sendBuffer.values());

        Collections.sort(bufferAsList, new Comparator<NodeDetails>() {
            @Override
            public int compare(NodeDetails o1, NodeDetails o2) {
                if (o1.getSendCounter() > o2.getSendCounter()) {
                    return 1;
                }
                else if (o1.getSendCounter() < o2.getSendCounter()) {
                    return -1;
                }
                else {
                    return 0;
                }
            }
        });

        int messageSizeCounter = 0;

        for (NodeDetails nodeInfo : bufferAsList) {
            if (messageSizeCounter > SwimComp.PIGGYBACK_MESSAGE_SIZE) {
                break;
            }

            nodeInfo.setSendCounter(nodeInfo.getSendCounter() + 1);

            switch (nodeInfo.getType()) {

                case NEW:
                    newNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
                case SUSPECTED:
                    suspectedNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
                case DEAD:
                    deadNodesToSend.put(nodeInfo.getAddress().getBaseAdr(), nodeInfo.getIncarnationCounter());
                    break;
            }

            if (nodeInfo.getSendCounter() > SwimComp.LAMBDA * Math.max(1, Math.log(Math.max(1, aliveNodes.size())))) {
                sendBuffer.remove(nodeInfo.getAddress().getBaseAdr());
            }

            messageSizeCounter++;
        }
        return new Pong(convertToNated(newNodesToSend), convertToNated(suspectedNodesToSend), convertToNated(deadNodesToSend), pingNr, incarnationCounter);
    }

  
    public void printAliveNodes() {
        SwimComp.log.info("{} Node state:\nAlive nodes({}): {}\nSuspected nodes: {}\nDead Nodes: {}", new Object[]{selfAddress.getId(), aliveNodes.size(), aliveNodes, suspectedNodes, deadNodes});
    }

   
    public Map<NatedAddress, Integer> convertToNated(Map<Address, Integer> nodes) {
        Map<NatedAddress, Integer> natedAddresses = new HashMap<NatedAddress, Integer>();
        for (Address node : nodes.keySet()) {
            NatedAddress address = addressMapping.get(node);
            if (address != null) {
                try {
                    NatedAddress addressToSend = new BasicNatedAddress(new BasicAddress(InetAddress.getByName("127.0.0.1"), 12345, address.getId()), address.getNatType(), new HashSet<NatedAddress>(address.getParents()));
                    natedAddresses.put(addressToSend, nodes.get(node));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }


            }
        }
        return natedAddresses;
    }

    public Map<NatedAddress, Integer> getAliveNodes() {
        return convertToNated(aliveNodes);
    }

   
    public Map<NatedAddress, Integer> getDeadNodes() {
        return convertToNated(deadNodes);
    }

    public Map<NatedAddress, Integer> getSuspectedNodes() {
        return convertToNated(suspectedNodes);
    }

}