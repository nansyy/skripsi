package routing.skripsi;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.community.Duration;

import java.util.*;

public class Estimasi implements RoutingDecisionEngine, InterContactTime {

    protected Map<DTNHost, Double> lastContactTime;
    private Map<DTNHost, List<Duration>> connHistory;
    private Map<DTNHost, Double> coupledNodes;
    private double mixingTime;
    private List<Double> t1Samples;
    private List<Double> t2Samples;
    private double lastMeetingTime;
    private int meetCount;
    private boolean hasMoved = false;
    private double estimatedM;
    protected Map<DTNHost, Double> startTimestamps;
    private double alpha;
    protected Coord initialLocation;
    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String INTERVAL_TIME = "intervaltime";

    private static double intervalTime = 1800;
    protected double interval;
    protected int initialNrofCopies;
    protected boolean isBinary;
    private double movementStartTime = 0;

    public Estimasi(Settings s) {
        if (s.contains(ALPHA_ALG_SETTING)) {
            alpha = s.getDouble(ALPHA_ALG_SETTING);
        } else {
            alpha = 0.58;
        }

        if (s.contains(BINARY_MODE)) {
            isBinary = s.getBoolean(BINARY_MODE);
        } else {
            isBinary = false;
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

        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.coupledNodes = new HashMap<DTNHost, Double>();
        this.t1Samples = new ArrayList<Double>();
        this.t2Samples = new ArrayList<Double>();
        this.lastMeetingTime = 0;
        this.meetCount = 0;
        this.estimatedM = 0;
        this.startTimestamps = new HashMap<>();
    }

    public Estimasi(Estimasi proto) {
        this.mixingTime = proto.mixingTime;
        this.connHistory = new HashMap<DTNHost, List<Duration>>();
        this.coupledNodes = new HashMap<DTNHost, Double>();
        this.t1Samples = new ArrayList<Double>();
        this.t2Samples = new ArrayList<Double>();
        this.lastMeetingTime = 0;
        this.meetCount = 0;
        this.estimatedM = 0;
        this.startTimestamps = new HashMap<>();
        lastContactTime = new HashMap<>();

    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        update(thisHost);
        double currentTime = SimClock.getTime();


        // Abaikan node yang masih dalam status "coupled"
        if (!coupledNodes.containsKey(peer)) {
            // Tandai node sebagai "coupled" untuk waktu tertentu
            coupledNodes.put(peer, currentTime );


            if (!lastContactTime.containsKey(thisHost)) {
                double t1 = currentTime - movementStartTime;
                t1Samples.add(t1);
               // t1Samples.add(currentTime);

                //System.out.println(thisHost+" added t1 " + t1 + " at " + currentTime);
            } else {
                double lastContact = lastContactTime.get(thisHost);
                double t2 = currentTime - lastContact;
                t2Samples.add(t2);
                t1Samples.add(t2);
                //System.out.println(thisHost+" added t2 " + t2 + " at " + currentTime);
            }
            // hasMetAnyNode.put(thisHost, true); //tandai bahwa sudah pernah bertemu siapa pun

            // lastMeetingTime = currentTime;
        }

    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        double time = cek(thisHost, peer); //get waktu mulai koneksi
        double etime = SimClock.getTime();
//            System.out.println(thisHost);
//            update();

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
        if (!lastContactTime.containsKey(thisHost)) {
            lastContactTime.put(thisHost, SimClock.getTime());
        } else {
            lastContactTime.put(thisHost, SimClock.getTime());
        }
        if (!lastContactTime.containsKey(peer)) {
            lastContactTime.put(peer, SimClock.getTime());
        } else {
            lastContactTime.put(peer, SimClock.getTime());
        }
        // System.out.println("Map connection down "+ lastContactTime);
        this.estimatedM = updateEstimate();

        // updateEstimate();
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
        Estimasi de = this.getOtherDecisionEngine(peer);

        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());
    }

    @Override
    public boolean newMessage(Message m) {
        initialNrofCopies = (int) Math.ceil(this.estimatedM / 2);
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
        return new Estimasi(this);
    }

    @Override
    public void update(DTNHost thisHost) {
        double currentTime = SimClock.getTime();

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
        Iterator<Map.Entry<DTNHost, Double>> it = coupledNodes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DTNHost, Double> entry = it.next();
            if (currentTime - entry.getValue() >= mixingTime) {
                it.remove();
            }
        }
    }

    private double updateEstimate() {
        if (t1Samples.size() < 5 || t2Samples.size() < 5) {
            return estimatedM; // Tunggu sampai cukup sampel
        }

        double avgT1 = calculateAverage(t1Samples);
        double avgT2 = calculateAverage(t2Samples);

        //Rumus dari paper: M = (2*T2 - 3*T1) / (T2 - 2*T1)
        if (avgT2 - 2 * avgT1 != 0) {
            double newEstimasi = (2 * avgT2 - 3 * avgT1) / (avgT2 - 2 * avgT1);

            if (this.estimatedM == 0) {
                this.estimatedM = newEstimasi; //Inisialisasi pertama
            } else {
                this.estimatedM = ((alpha * estimatedM) + (1 - alpha) * newEstimasi);
            }
        }
        //System.out.println("Estimasi: "+estimatedM);
        return estimatedM;
    }

    private double calculateAverage(List<Double> samples) {
        double sum = 0;
        for (Double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    private Estimasi getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
        return (Estimasi) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    @Override
    public double getEstimation() {
        return estimatedM;
    }
}
