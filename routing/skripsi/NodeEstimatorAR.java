package routing.skripsi;

import core.*;
import routing.*;

import routing.ActiveRouter;

import java.util.*;


public class NodeEstimatorAR extends ActiveRouter implements InterContactTime {

    // --- Estimation Parameters (read from settings) ---
    private double mixingTime;
    private int minSamplesForEstimate;
    private double estimationInterval;
    private double smoothingFactorAlpha; // For running average
    private int recentEncountersMaxSize; // How many recent encounters to store for T2 calculation

    // --- Estimation State ---
    private double lastEncounterTimeAny = 0.0;
    // Stores encounter info: {timestamp, nodeId}. Use LinkedList for efficient add/remove first.
    private LinkedList<EncounterRecord> recentEncounters;
    // Stores nodes considered "coupled" and until when: {nodeId, uncoupleTime}
    private Map<Integer, Double> coupledNodesMap;
    // Lists to store valid samples
    private List<Double> t1Samples;
    private List<Double> t2Samples;
    // Current estimate of M
    private double currentMEstimate = 2.0; // Initialize to a minimum sensible value
    private double lastEstimationTime = 0.0;
    /** identifier for the initial number of copies setting ({@value})*/
    public static final String NROF_COPIES = "nrofCopies";
    /** identifier for the binary-mode setting ({@value})*/
    public static final String BINARY_MODE = "binaryMode";
    /** SprayAndWait router's settings name space ({@value})*/
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    /** Message property key */
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
            "copies";

    protected int initialNrofCopies;
    protected boolean isBinary;

    public NodeEstimatorAR(Settings s) {
        super(s);
        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
        // Read parameters from settings file (example keys)
        mixingTime = s.getDouble("NodeEstimatorAR.mixingTime"); // Default: 10 minutes
        minSamplesForEstimate = s.getInt("NodeEstimatorAR.minSamples");
        estimationInterval = s.getDouble("NodeEstimatorAR.estimationInterval"); // Default: 5 minutes
        smoothingFactorAlpha = s.getDouble("NodeEstimatorAR.smoothingFactorAlpha"); // High alpha = more smoothing
        recentEncountersMaxSize = s.getInt("NodeEstimatorAR.recentEncountersMaxSize");


        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
        isBinary = snwSettings.getBoolean(BINARY_MODE);
        // Initialize state
        recentEncounters = new LinkedList<>();
        coupledNodesMap = new HashMap<>();
        t1Samples = new ArrayList<>();
        t2Samples = new ArrayList<>();

    }

    protected NodeEstimatorAR(NodeEstimatorAR r) {
        super(r);
        this.mixingTime = r.mixingTime;
        this.minSamplesForEstimate = r.minSamplesForEstimate;
        this.estimationInterval = r.estimationInterval;
        this.smoothingFactorAlpha = r.smoothingFactorAlpha;
        this.recentEncountersMaxSize = r.recentEncountersMaxSize;

        // Deep copy state if necessary, or reinitialize
        this.recentEncounters = new LinkedList<>(r.recentEncounters); // Shallow copy might be ok for EncounterRecord
        this.coupledNodesMap = new HashMap<>(r.coupledNodesMap);
        this.t1Samples = new ArrayList<>(r.t1Samples);
        this.t2Samples = new ArrayList<>(r.t2Samples);
        this.currentMEstimate = r.currentMEstimate;
        this.lastEncounterTimeAny = r.lastEncounterTimeAny;
        this.lastEstimationTime = r.lastEstimationTime;

        this.initialNrofCopies = r.initialNrofCopies;
        this.isBinary = r.isBinary;
    }

    @Override
    public void changedConnection(Connection con) {
        super.changedConnection(con);
        if (con.isUp()) {
            DTNHost otherHost = con.getOtherNode(getHost());
            int otherHostId = otherHost.getAddress();
            double currentTime = SimClock.getTime();

            // --- Update Coupling Map ---
            // Always update/add the encountered node to the coupling map
            coupledNodesMap.put(otherHostId, currentTime + mixingTime);

            // --- Check if the other node is currently coupled ---
            boolean isCoupled = false;
            if (coupledNodesMap.containsKey(otherHostId)) {
                // Check if the uncoupling time is in the future
                if (coupledNodesMap.get(otherHostId) > currentTime + 1e-9) { // Add small epsilon for float comparison
                    // This specific encounter happened *before* the uncoupling time calculated previously
                    // BUT the paper implies we check coupling *before* collecting samples for the *current* encounter.
                    // Let's check if the *previous* encounter put it into a coupled state extending to now.
                    // A simpler check: Is the node's uncouple time (set during its *last* encounter) still in the future?
                    // We need to find the *actual* uncouple time set for this node ID.
                    // The map already holds this: coupledNodesMap.get(otherHostId) is the time it becomes uncoupled.
                    if (coupledNodesMap.get(otherHostId) > currentTime + mixingTime) { // If its existing uncouple time is *later* than what we just set
                        isCoupled = true;
                    }
                }
            }
            // Clean up old entries in coupled map (optional, for memory)
            // coupledNodesMap.entrySet().removeIf(entry -> entry.getValue() < currentTime);

            // --- Collect Samples if NOT Coupled ---
            if (!isCoupled) {
                // T1 Sample: Time since *any* last encounter
                if (lastEncounterTimeAny > 0) { // Ensure there was a previous encounter
                    double deltaT1 = currentTime - lastEncounterTimeAny;
                    if (deltaT1 > 1e-9) { // Avoid zero/negative times due to simultaneous events
                        t1Samples.add(deltaT1);
                        // System.out.println("Host " + getHost().getAddress() + " T1 sample: " + deltaT1);
                    }
                }

                // T2 Sample: Time since encountering the second-to-last *distinct* node
                // This requires iterating back through recentEncounters
                Integer lastDistinctNodeId = null;
                Double timeOfLastDistinctNode = null;
                Integer secondLastDistinctNodeId = null;
                Double timeOfSecondLastDistinctNode = null;

                for (EncounterRecord record : recentEncounters) {
                    if (lastDistinctNodeId == null) {
                        if (record.nodeId != otherHostId) { // Found the most recent distinct node
                            lastDistinctNodeId = record.nodeId;
                            timeOfLastDistinctNode = record.timestamp;
                        }
                    } else {
                        // Now look for the next distinct node (different from current and lastDistinct)
                        if (record.nodeId != otherHostId && record.nodeId != lastDistinctNodeId) {
                            secondLastDistinctNodeId = record.nodeId;
                            timeOfSecondLastDistinctNode = record.timestamp;
                            break; // Found it
                        }
                    }
                }

                if (timeOfSecondLastDistinctNode != null) {
                    double deltaT2 = currentTime - timeOfSecondLastDistinctNode;
                    if (deltaT2 > 1e-9) {
                        t2Samples.add(deltaT2);
                        // System.out.println("Host " + getHost().getAddress() + " T2 sample: " + deltaT2);
                    }
                }
            }

            // --- Update State for Next Encounter ---
            lastEncounterTimeAny = currentTime;
            // Add current encounter to history
            recentEncounters.addFirst(new EncounterRecord(currentTime, otherHostId));
            // Keep history size bounded
            if (recentEncounters.size() > recentEncountersMaxSize) {
                recentEncounters.removeLast();
            }
        }
    }


    /**
     * Returns the current estimate of the total number of nodes (M).
     * The Spray and Wait logic would call this.
     * @return The estimated number of nodes.
     */
    public double getEstimatedNodeCount() {
        // Return the smoothed estimate
        System.out.println("current estimasi: "+currentMEstimate);
        return this.currentMEstimate;
    }

    @Override
    public double getEstimation() {
        return this.currentMEstimate;
    }


    // Helper class to store encounter history
    private static class EncounterRecord {
        final double timestamp;
        final int nodeId;

        EncounterRecord(double timestamp, int nodeId) {
            this.timestamp = timestamp;
            this.nodeId = nodeId;
        }
    }

    @Override
    public MessageRouter replicate() {

        return new NodeEstimatorAR(this);
    }

    @Override
    public void connectionUp(Connection con) {
    }

    @Override
    public void connectionDown(Connection con) {
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());

        msg.setTtl(this.msgTtl);
        initialNrofCopies = (int) Math.ceil(this.getEstimatedNodeCount() / 2);
        msg.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
        addToMessages(msg, true);
        return true;
    }

    @Override
    public int receiveMessage(Message m, DTNHost from) {

        return super.receiveMessage(m, from);
    }

    @Override //shouldSaveReceivedMessage
    public Message messageTransferred(String id, DTNHost from) {
        Message msg = super.messageTransferred(id, from);

        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);

        if (nrofCopies == null) {
            throw new IllegalStateException("Not a S'n'W message: " + msg);
        }
        // assert nrofCopies != null : "Not a SnW message: " + msg;

        if (isBinary) {
            /* in binary S'n'W the receiving node gets ceil(n/2) copies */
            nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        } else {
            /* in standard S'n'W the receiving node gets only single copy */
            nrofCopies = 1;
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return msg;
    }

    @Override //shouldDeleteSentMessage
    protected void transferDone(Connection con) {
        Integer nrofCopies;
        String msgId = con.getMessage().getId();
        /* get this router's copy of the message */
        Message msg = getMessage(msgId);

        if (msg == null) { // message has been dropped from the buffer after..
            return; // ..start of transfer -> no need to reduce amount of copies
        }

        /* reduce the amount of copies left */
        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies /= 2;
        } else {
            nrofCopies--;
        }
        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
    }

    @Override
    public void update() {
        super.update();

    }
}






