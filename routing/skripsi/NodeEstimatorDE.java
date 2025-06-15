package routing.skripsi;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.community.*;
import java.util.*;

public class NodeEstimatorDE implements RoutingDecisionEngine, InterContactTime {


    private Map<DTNHost, List<Duration>> connHistory;
    private Map<DTNHost, Double> coupledNodes;
    private double mixingTime;
    private List<Double> t1Samples;
    private List<Double> t2Samples;
    private double lastMeetingTime;
    private int meetCount;
    private double estimatedM;
    protected Map<DTNHost, Double> startTimestamps;
    private double alpha;
    protected Map<DTNHost, Double> lastContactTime;
    private double movementStartTime = 0;
    protected Coord initialLocation;
    private boolean hasMoved = false;
    private static double intervalTime = 1800;
    protected double interval;
    protected double lastIntervalTime=0;
    private boolean isFirstNode = true;
    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitDE";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String INTERVAL_TIME = "intervaltime";
    DTNHost lastPeer;
    protected int initialNrofCopies;
    protected boolean isBinary;


    public NodeEstimatorDE(Settings s) {
        if (s.contains(ALPHA_ALG_SETTING)) {
            alpha = s.getDouble(ALPHA_ALG_SETTING);
        } else {
            alpha = 0.5;
        }

        if (s.contains(BINARY_MODE)) {
            isBinary = s.getBoolean(BINARY_MODE);
        } else {
            this.isBinary = false;
        }

        if (s.contains(NROF_COPIES)) {
            initialNrofCopies = s.getInt(NROF_COPIES);
        }
        if (s.contains(INTERVAL_TIME)) {
            interval = s.getDouble(INTERVAL_TIME);
        } else {
            interval = intervalTime;
        }
        if (s.contains("mixingTime")) {
            this.mixingTime = s.getDouble("mixingTime");
        } else {
            this.mixingTime = 1800;
        }

        this.connHistory = new HashMap<>();
        this.coupledNodes = new HashMap<>();
        this.t1Samples = new ArrayList<>();
        this.t2Samples = new ArrayList<>();
        this.lastMeetingTime = 0;
        this.meetCount = 0;
        this.estimatedM = 0;
        this.startTimestamps = new HashMap<>();
    }

    public NodeEstimatorDE(NodeEstimatorDE r) {
        this.initialNrofCopies = r.initialNrofCopies;
        this.isBinary = r.isBinary;
        this.mixingTime = r.mixingTime;
        this.connHistory = new HashMap<>();
        this.coupledNodes = new HashMap<>();
        this.t1Samples = new ArrayList<>();
        this.t2Samples = new ArrayList<>();
        this.lastMeetingTime = 0;
        this.meetCount = 0;
        this.estimatedM = 0;
        this.startTimestamps = new HashMap<>();
        lastContactTime = new HashMap<>();
        this.alpha = 0.5;


    }

//    @Override
//    public void connectionUp(DTNHost thisHost, DTNHost peer) {
//        double now = SimClock.getTime();
//
//        if (isFirstNode) {
//            t1Samples.add(now); // T1 absolut
//            isFirstNode = false;
//        }
//        else if (!peer.equals(lastPeer)) {
//            double interval = now - lastMeetingTime;
//            if (meetCount == 0) t1Samples.add(interval);
//            else t2Samples.add(interval);
//            meetCount = (meetCount + 1) % 2;
//        }
//
//        lastPeer = peer;
//    }
    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {


        double currentTime = SimClock.getTime();

//        Iterator<Map.Entry<DTNHost, Double>> it = coupledNodes.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry<DTNHost, Double> entry = it.next();
//            if (currentTime - entry.getValue() >= mixingTime) {
//                it.remove();
//            }
//        }
        // Abaikan node yang masih dalam status "coupled"
        if (!coupledNodes.containsKey(peer)) {
//            // Tandai node sebagai "coupled" untuk waktu tertentu
            coupledNodes.put(peer, currentTime);

            // Catat waktu pertemuan untuk T1/T2
            if (lastMeetingTime > 0) {
                double interMeetingTime = currentTime - lastMeetingTime;
                meetCount++;

                if (meetCount == 1) {
                    // Ini adalah T1 (waktu sampai bertemu satu node)
                    //t1Samples.add(currentTime);
                    t1Samples.add(interMeetingTime);
                } else if (meetCount == 2) {
                    // Ini adalah T2 (waktu sampai bertemu dua node berbeda)
                    //t2Samples.add(currentTime);
                    t2Samples.add(interMeetingTime);
                    meetCount = 0; // Reset counter

                }


            }

        }

    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        double time = cek(thisHost, peer); //get waktu mulai koneksi
        double etime = SimClock.getTime();


        List<Duration> history;
        if (!connHistory.containsKey(peer)) {//jika peer belum ada di connHistory

            history = new LinkedList<Duration>();
            connHistory.put(peer, history);
        } else {//jika sudah ada
            history = connHistory.get(peer);
        }

        // add this connection to the list
        if (etime - time > 0) {//jika durasi koneksi positif
            history.add(new Duration(time, etime));//add durasi baru(start,end)
        }

        lastMeetingTime = etime;
        startTimestamps.remove(peer);
        updateEstimate();
    }

    public double cek(DTNHost thisHost, DTNHost peer) {
        //cek apakah startTimestamps mengandung entry untuk peer
        if (startTimestamps.containsKey(peer)) {
            return startTimestamps.get(peer); //return waktu mulai koneksi dengan peer
        }
        return 0;
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost myHost = con.getOtherNode(peer);
        NodeEstimatorDE de = this.getOtherDecisionEngine(peer);

        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());
    }

    @Override
    public boolean newMessage(Message m) {
        initialNrofCopies = (int) this.estimatedM / 2;
        m.addProperty(MSG_COUNT_PROPERTY, initialNrofCopies);
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {

        return m.getTo() == aHost;
    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
        } else {
            nrofCopies = 1;
        }
        m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return m.getTo() != thisHost;
    }

    @Override
    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
        if (m.getTo() == otherHost) {
            return true; // deliver to final destination
        }
        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
        if (nrofCopies > 1) {
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        if (m.getTo() == otherHost) {
            return false;
        }
        Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
        if (isBinary) {
            nrofCopies /= 2;
        } else {
            nrofCopies--;
        }
        m.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
        return false;
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        return m.getTo() == hostReportingOld;
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new NodeEstimatorDE(this);
    }

    @Override
    public void update(DTNHost thisHost) {
        System.out.println("update");
        double currentTime = SimClock.getTime();

//        if (currentTime - lastIntervalTime >= interval) {
//            updateEstimate();
//            lastIntervalTime = currentTime;
//        }
        if (!hasMoved) {
            Coord currentLoc = thisHost.getLocation();

            if (initialLocation == null) {
                initialLocation = new Coord(currentLoc.getX(), currentLoc.getY());
            } else if (!currentLoc.equals(initialLocation)) {
                movementStartTime = SimClock.getTime();
                hasMoved = true;

                // System.out.println(getHost() + " mulai bergerak pada waktu: " + movementStartTime);
            }
        }
        // Bersihkan node yang sudah tidak "coupled" lagi
//        Iterator<Map.Entry<DTNHost, Double>> it = coupledNodes.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry<DTNHost, Double> entry = it.next();
//            if (currentTime - entry.getValue() >= mixingTime) {
//                it.remove();
//            }
//        }
    }

    private void updateEstimate() {
        if (t1Samples.size() < 5 || t2Samples.size() < 5) {
            return; // tunggu sampai cukup sampel
        }

            double avgT1 = calculateAverage(t1Samples);
            double avgT2 = calculateAverage(t2Samples);

            // rumus dari paper: M = (2*T2 - 3*T1) / (T2 - 2*T1)
            if (avgT2 - 2 * avgT1 != 0) {
                double newEstimasi = (2 * avgT2 - 3 * avgT1) / (avgT2 - 2 * avgT1);
               // System.out.println("new Estimasi" + this + ": " + newEstimasi);
                if (this.estimatedM == 0) {
                    this.estimatedM = newEstimasi; // inisialisasi pertama
                } else {
                    this.estimatedM = ((alpha * estimatedM) + (1 - alpha) * newEstimasi);
                 //   System.out.println("after running avg" + this + ": " + estimatedM);
                }
            }
            // System.out.println("Estimasi: "+estimatedM);
        }



    private double calculateAverage(List<Double> samples) {
        double sum = 0;
        for (Double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    private NodeEstimatorDE getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
        return (NodeEstimatorDE) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    @Override
    public double getEstimation() {
        return estimatedM;
    }
}