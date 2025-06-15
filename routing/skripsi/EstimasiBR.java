package routing.skripsi;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.community.*;

import java.util.*;

public class EstimasiBR implements RoutingDecisionEngine, InterContactTime {


    private Map<DTNHost, Boolean> hasMetAnyNode;
    protected Map<DTNHost, Double> coupledNodesWithTime;
    private Map<DTNHost, Double> lastContactTime;
    protected Map<DTNHost, List<DTNHost>> coupledNode;
    protected double lastIntervalTime;
    private List<Double> t1_samples;
    private double firstTime, secondTime;
    private Map<DTNHost, Double> T1, T2;
    private List<Double> t2_samples;
    private double estimatedM = 0;
    private double alpha;
    private int threshold;
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
    private boolean hasMoved = false;
    private double movementStartTime = 0;
    protected Map<DTNHost, Double> startTimestamps;
    protected Map<DTNHost, List<Duration>> connHistory;

    public EstimasiBR(Settings s) {
        if (s.contains(ALPHA_ALG_SETTING)) {
            alpha = s.getDouble(ALPHA_ALG_SETTING);
        } else {
            alpha = 0.98;
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
        // coupledNode = new HashMap<>();
        lastIntervalTime = 0;
        t1_samples = new ArrayList<>();
        t2_samples = new ArrayList<>();
        lastContactTime = new HashMap<>();
        firstTime = 0;
        secondTime = 0;
        T1 = new HashMap<>();
        T2 = new HashMap<>();
    }

    //Constructor based on the argument prototype
    public EstimasiBR(routing.skripsi.EstimasiBR proto) {

        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        //coupledNode = new HashMap<>();
        this.estimatedM = proto.estimatedM;
        this.alpha = proto.alpha;
        this.threshold = proto.threshold;
        this.lastIntervalTime = proto.lastIntervalTime;
        this.t1_samples = new ArrayList<>();
        this.t2_samples = new ArrayList<>();
        this.lastContactTime = new HashMap<>();
        this.hasMetAnyNode = new HashMap<>();
        this.coupledNodesWithTime = new HashMap<>();
        this.coupledNode = new HashMap<>();
        startTimestamps = new HashMap<DTNHost, Double>();
        // connHistory = new HashMap<DTNHost, List<Duration>>();
        connHistory = new HashMap<DTNHost, List<Duration>>();

        this.firstTime = proto.firstTime;
        this.secondTime = proto.secondTime;
        this.T1 = proto.T1;
        this.T2 = proto.T2;

    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        routing.skripsi.EstimasiBR de = this.getOtherDecisionEngine(peer);
        double currentTime = SimClock.getTime();

        if (!this.hasMoved) {
            Coord currentLoc = thisHost.getLocation();

            if (this.initialLocation == null) {
                this.initialLocation = new Coord(currentLoc.getX(), currentLoc.getY());
            } else if (!currentLoc.equals(initialLocation)) {
                this.movementStartTime = SimClock.getTime();
                this.hasMoved = true;
                //    System.out.println(thisHost+ " mulai bergerak pada "+movementStartTime);
            }
        }

        if (!coupledNode.containsKey(thisHost)) {
            coupledNode.put(thisHost, new ArrayList<>());
        }
        List<DTNHost> ck = coupledNode.get(thisHost);//


        if (!ck.contains(peer)) {
            // if (!hasMetAnyNode.getOrDefault(thisHost, false)) {
            // if (!connHistory.containsKey(peer)){
            if (!lastContactTime.containsKey(thisHost)) {
                double t1 = currentTime - movementStartTime;
                t1_samples.add(t1);

                //    System.out.println(thisHost+" added t1 " + t1 + " at " + currentTime);
            } else {
                double lastContact = lastContactTime.get(thisHost);
                double t2 = currentTime - lastContact;
                t2_samples.add(t2);
                t1_samples.add(t2);

                //     System.out.println(thisHost+" added t2 " + t2 + " at " + currentTime);
            }
        }
        // coupledNodesWithTime.put(peer, currentTime);
        ck.add(peer);
        this.coupledNode.put(thisHost, ck);

    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost myHost = con.getOtherNode(peer);
        // System.out.println(myHost);
        // update();
        routing.skripsi.EstimasiBR de = this.getOtherDecisionEngine(peer);

        // jika peer baru pertama ketemu node
        if (!T1.containsKey(peer)) {
            double now = SimClock.getTime();
//                this.T1 = now;
            de.firstTime = now;

            this.T1.putIfAbsent(peer, now);
//                de.t1Map.putIfAbsent(myHost, now);
            //         System.out.println("T1 Map " + myHost + ": " + this.T1);
        } else if (!T2.containsKey(peer)) {
            double now = SimClock.getTime();
//                this.T2 = now;
            de.secondTime = now;

            this.T2.putIfAbsent(peer, now);
//                de.t2Map.putIfAbsent(myHost, now);
            //    System.out.println("T2 Map " + myHost + ": " + this.T2);
        }
//        if (!coupledNodesWithTime.containsKey(myHost)) {
//            coupledNodesWithTime.put(myHost, new HashMap<>());
//        }
//        Map<DTNHost, Double> ck = coupledNodesWithTime.get(myHost);
//        if (!ck.containsKey(peer)) {
//            this.startTimestamps.put(peer, SimClock.getTime());
//            de.startTimestamps.put(myHost, SimClock.getTime());
//
//        }
        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());
    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        double time = cek(thisHost, peer); //get waktu mulai koneksi
        double etime = SimClock.getTime();
        EstimasiBR de = this.getOtherDecisionEngine(peer);
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
//
        //calculateT1AndT2(thisHost);
        startTimestamps.remove(peer);
        this.lastContactTime.put(thisHost, etime);
        de.lastContactTime.put(peer, etime);
        this.estimatedM = countingNumberOfNodes();


    }

    public double cek(DTNHost thisHost, DTNHost peer) {
        //cek apakah startTimestamps mengandung entry untuk peer
        if (startTimestamps.containsKey(peer)) {
            return startTimestamps.get(peer); //return waktu mulai koneksi dengan peer
        }
        return 0;
    }

    public void calculateT1AndT2(DTNHost thisHost) {
        List<DTNHost> uniquePeers = new ArrayList<>();
        List<Double> meetingTimes = new ArrayList<>();

        // Iterasi melalui connHistory untuk mengumpulkan semua pertemuan unik
        for (Map.Entry<DTNHost, List<Duration>> entry : connHistory.entrySet()) {
            DTNHost peer = entry.getKey();
            List<Duration> durations = entry.getValue();

            // Ambil waktu awal pertemuan pertama dengan peer ini
            if (!durations.isEmpty()) {
                double firstMeetingTime = durations.get(0).getStart();
                if (!uniquePeers.contains(peer)) {
                    uniquePeers.add(peer);
                    if (!startTimestamps.containsKey(peer)) {
                        meetingTimes.add(firstMeetingTime);
                    }
                }
            }
        }

        // Urutkan waktu pertemuan
        Collections.sort(meetingTimes);

        // Hitung t1 (waktu sampai pertemuan pertama)
        if (meetingTimes.size() >= 1) {
            this.t1_samples.add(meetingTimes.get(0));  // Pertemuan pertama
        }

        // Hitung t2 (waktu sampai pertemuan kedua dengan node berbeda)
        if (meetingTimes.size() >= 2) {
            this.t2_samples.add(meetingTimes.get(1));  // Pertemuan kedua
        }

        // Panggil fungsi estimasi
        this.estimatedM = countingNumberOfNodes();
        // System.out.println("Estimasi M: "+estimatedM);
    }

    public void update() {
        Iterator<Map.Entry<DTNHost, Double>> iterator = coupledNodesWithTime.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DTNHost, Double> entry = iterator.next();
            if (SimClock.getTime() - lastIntervalTime > interval) {  // Jika mixing time sudah habis (<= 0)
                lastIntervalTime = SimClock.getTime();
                iterator.remove();  // Hapus entry dari Map
            }
        }

//        if (SimClock.getTime() - lastIntervalTime >= interval) {
//            lastIntervalTime = SimClock.getTime();
//            coupledNodesWithTime.clear();;


        //System.out.println("==== Connection History Report ====");
//            for (Map.Entry<DTNHost, List<Duration>> outer : connHistory.entrySet()) {
//                DTNHost src = outer.getKey();
//                List<Duration> durations = outer.getValue();
//                for (Duration d : durations) {
//                    System.out.println(src + ": " + d.getStart() + " - " + d.getEnd());
//                }
//            }
    }

    private double countingNumberOfNodes() {
        //  System.out.println("sample t1: "+t1_samples);
        //  System.out.println("sample t2: "+t2_samples);


        if (t1_samples.isEmpty() || t2_samples.isEmpty()) {
            return estimatedM;
        }


        // Hitung rata-rata T1 dan T2
        double avgT1 = calculateAverage(t1_samples);
        double avgT2 = calculateAverage(t2_samples);

        //System.out.println("rata-rata t1: "+avgT1);
        // System.out.println("rata-rata t2: "+avgT2);
        double denominator = avgT2 - 2 * avgT1;
        if (denominator == 0) {
            return estimatedM; //hindari pembagian nol
        }

        // Estimasi M menggunakan rumus
        double currentM = ((2 * avgT2 - 3 * avgT1) / (avgT2 - 2 * avgT1));
        //    System.out.println("currentM: "+currentM);


        if (estimatedM == 0) {
            estimatedM = currentM; // Inisialisasi pertama
        } else {
            estimatedM = (alpha * estimatedM + (1 - alpha) * currentM);
            //     System.out.println("estimatedM "+ estimatedM);
        }
        return estimatedM;
    }


    private double calculateAverage(List<Double> samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
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
    public void update(DTNHost host) {

    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new routing.skripsi.EstimasiBR(this);
    }


    private routing.skripsi.EstimasiBR getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
        return (routing.skripsi.EstimasiBR) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }


    @Override
    public double getEstimation() {
        return estimatedM;
    }

    public double getFirstTime() {
        return firstTime;
    }

    public void setFirstTime(double firstTime) {
        this.firstTime = firstTime;
    }

    public double getSecondTime() {
        return secondTime;
    }

    public void setSecondTime(double secondTime) {
        this.secondTime = secondTime;
    }
}

