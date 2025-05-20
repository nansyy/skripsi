package routing.skripsi;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;

import java.util.*;

public class EstimasiNode implements RoutingDecisionEngine, InterContactTime {

    private Map<DTNHost, Boolean> hasMetAnyNode;

    private Map<DTNHost, Double> lastContactTime;
    protected Map<DTNHost, List<DTNHost>> coupledNode;
    protected double lastIntervalTime;
    private List<Double> t1_samples;
    private List<Double> t2_samples;
    private int estimatedM = 0;
    private double alpha;
    private int threshold;
    protected Coord initialLocation;
    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String INTERVAL_TIME = "intervaltime";
    private List<DTNHost> hostOrder;
    private int totalNode;


    private static double intervalTime = 1800;
    protected double interval;
    protected int initialNrofCopies;
    protected boolean isBinary;
    private boolean hasMoved = false;
    private double movementStartTime = -1;


    public EstimasiNode(Settings s) {
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
        coupledNode = new HashMap<>();
        lastIntervalTime = 0;
        t1_samples = new ArrayList<>();
        t2_samples = new ArrayList<>();
        lastContactTime = new HashMap<>();
        totalNode = s.getInt("nrofHosts");

    }


    //Constructor based on the argument prototype
    public EstimasiNode(EstimasiNode proto) {

        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        coupledNode = new HashMap<>();
        this.estimatedM = proto.estimatedM;
        this.alpha = proto.alpha;
        this.threshold = proto.threshold;
        this.lastIntervalTime = proto.lastIntervalTime;
        this.t1_samples = new ArrayList<>();
        this.t2_samples = new ArrayList<>();
        this.lastContactTime = new HashMap<>();
        this.hasMetAnyNode = new HashMap<>();
        this.hostOrder = new ArrayList<>();


    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        EstimasiNode de = this.getOtherDecisionEngine(peer);

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
        //  System.out.println("Connection up "+thisHost+" dan "+ peer);
        double currentTime = SimClock.getTime();

        if (!this.coupledNode.containsKey(thisHost)) {
            this.coupledNode.put(thisHost, new ArrayList<>());
        }
        List<DTNHost> ck = this.coupledNode.get(thisHost); //get list node yang sudah coupled dengan thisHost
        if (!ck.contains(peer)) { //kalau peer belum coupled dengan thisHost
            //maka ini adalah pertemuan pertama dengan peer

//            //System.out.println("es " + this.lastContactTime.containsKey(thisHost));
//            System.out.println("Checking contact for: " + thisHost + ", known? " + this.lastContactTime.containsKey(thisHost));
//
//            if (!this.lastContactTime.containsKey(thisHost)) { //thisHost belum ketemu dengan node manapun (belum ada terjadi connection down dg node lain)
//                double t1 = currentTime - this.movementStartTime;  //waktu saat ini - waktu saat node mulai bergerak
//                this.t1_samples.add(t1);
//                System.out.println("added t1 "+ t1 + " at " + currentTime);
//            } else { //sthisHost sudah ada ketemu dg node lain
//                double lastContact = this.lastContactTime.get(thisHost); //get waktu connection down yg terakhir dari thisHost
//                double t2 = currentTime - lastContact;
//                this.t2_samples.add(t2);
//                this.t1_samples.add(t2);
//                System.out.println("added t2 "+ t2 + " at " + currentTime);
//
//            }
//            ck.add(peer);
//            this.coupledNode.put(thisHost, ck);
//               System.out.println("samples t1 "+ t1_samples);
//               System.out.println("samples t2 "+ t2_samples);
//            System.out.println("Node yg coupled dengan "+ thisHost +"=> " +ck);
            //System.out.println("Checking contact for: " + thisHost + ", known? " + hasMetAnyNode.getOrDefault(thisHost, false));

            if (!this.hasMetAnyNode.getOrDefault(thisHost, false)) {
                double t1 = currentTime - this.movementStartTime;
                this.t1_samples.add(t1);
                //System.out.println("added t1 " + t1 + " at " + currentTime);
                this.hostOrder.add(thisHost);
            } else {
                double lastContact = this.lastContactTime.getOrDefault(thisHost, this.movementStartTime);
                double t2 = currentTime - lastContact;
                this.t2_samples.add(t2);
                this.t1_samples.add(t2);
                this.hostOrder.add(thisHost);
                //System.out.println("added t2 " + t2 + " at " + currentTime);
            }

            ck.add(peer);
            this.coupledNode.put(thisHost, ck);
            this.hasMetAnyNode.put(thisHost, true); // tandai bahwa sudah pernah bertemu siapa pun

        }

        if (!de.hasMoved) {
            Coord currentLoc = thisHost.getLocation();

            if (de.initialLocation == null) {
                de.initialLocation = new Coord(currentLoc.getX(), currentLoc.getY());
            } else if (!currentLoc.equals(initialLocation)) {
                de.movementStartTime = SimClock.getTime();
                de.hasMoved = true;
            }

        }

        if (!de.coupledNode.containsKey(thisHost)) {
            de.coupledNode.put(thisHost, new ArrayList<>());
        }
        List<DTNHost> ck_de = de.coupledNode.get(thisHost); //get list node yang sudah coupled dengan thisHost
        if (!ck_de.contains(peer)) {

            if (!this.hasMetAnyNode.getOrDefault(thisHost, false)) {
                double t1 = currentTime - this.movementStartTime;
                this.t1_samples.add(t1);
                this.hostOrder.add(thisHost);
            } else {
                double lastContact = this.lastContactTime.getOrDefault(thisHost, this.movementStartTime);
                double t2 = currentTime - lastContact;
                this.t2_samples.add(t2);
                this.t1_samples.add(t2);
                this.hostOrder.add(thisHost);
            }

            ck_de.add(peer);
            this.coupledNode.put(thisHost, ck_de);
            this.hasMetAnyNode.put(thisHost, true); // tandai bahwa sudah pernah bertemu siapa pun

        }
    }

    @Override//mencatat durasi koneksi antara dua host ketika koneksi terputus
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        //   System.out.println("Koneksi down "+thisHost+" dan "+peer);
        EstimasiNode de = this.getOtherDecisionEngine(peer);
        if (!this.lastContactTime.containsKey(thisHost)) {
            this.lastContactTime.put(thisHost, SimClock.getTime());
        } else {
            this.lastContactTime.replace(thisHost, SimClock.getTime());
        }
        if (!de.lastContactTime.containsKey(thisHost)) {
            de.lastContactTime.put(thisHost, SimClock.getTime());
        } else {
            de.lastContactTime.replace(thisHost, SimClock.getTime());
        }
        this.estimatedM = countingNumberOfNodes();
        de.estimatedM = countingNumberOfNodes();
        //  System.out.println("Map lastContactTime: "+lastContactTime);
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {

    }

        private int countingNumberOfNodes() {
        //  System.out.println("sample t1: "+t1_samples);
        //  System.out.println("sample t2: "+t2_samples);
        if (t1_samples.size() >=25 || t2_samples.size()>=25) {
            // return estimatedM;

            // Hitung rata-rata T1 dan T2
            double avgT1 = calculateAverage(t1_samples);
            double avgT2 = calculateAverage(t2_samples);

            //      System.out.println("rata-rata t1: "+avgT1);
            //     System.out.println("rata-rata t2: "+avgT2);
            double denominator = avgT2 - 2 * avgT1;
            if (denominator == 0) {
                return estimatedM; // hindari pembagian nol
            }

            // Estimasi M menggunakan rumus
            int currentM = ((int) ((2 * avgT2 - 3 * avgT1) / (avgT2 - 2 * avgT1)));
            //currentM = Math.max(1, currentM);
            //          // Perbarui estimasi dengan running average
            if (estimatedM == 0) {
                estimatedM = currentM; // Inisialisasi pertama
            } else {
                estimatedM = (int) (alpha * estimatedM + (1 - alpha) * currentM);
                        System.out.println("estimatedM "+ estimatedM);
            }
        }

        return estimatedM;

    }
//    private int countingNumberOfNodes() {
//
//        if (t1_samples.isEmpty() || t2_samples.isEmpty() || hostOrder.size() < t1_samples.size()) {
//            return estimatedM;
//        }
//
//        int n = t1_samples.size();
//        double t1_hat = 0.0;
//
//        //hitung rata-rata T1
//        for (int k = 0; k < n; k++) {
//            double T1k = t1_samples.get(k);
//            DTNHost host = hostOrder.get(k);
//            List<DTNHost> coupled = coupledNode.getOrDefault(host, new ArrayList<>());
//            int ck = coupled.size();
//
//            if ((totalNode - 1) > 0) {
//                double weight = (double) (totalNode - ck) / (totalNode - 1);
//                t1_hat += weight * T1k;
//            }
//        }
//        t1_hat /= n;
//
//        //hitung rata-rata T2
//        double t2_hat = 0.0;
//        for (int k = 1; k < n; k++) {
//            double T1_prev = t1_samples.get(k - 1);
//            double T1_curr = t1_samples.get(k);
//            DTNHost host_prev = hostOrder.get(k - 1);
//            DTNHost host_curr = hostOrder.get(k);
//
//            int ck_prev = coupledNode.getOrDefault(host_prev, new ArrayList<>()).size();
//            int ck_curr = coupledNode.getOrDefault(host_curr, new ArrayList<>()).size();
//
//            double term1 = (totalNode - ck_prev) / (double) (totalNode - 1);
//            double term2 = (totalNode - ck_curr) / (double) (totalNode - 2);
//
//            t2_hat += (term1 * T1_prev + term2 * T1_curr);
//        }
//        t2_hat /= n;//(n - 1);
//
//        // Estimasi jumlah node M
//        double denominator = t2_hat - (2 * t1_hat);
//        ////        if (Math.abs(denominator) < 1e-6) {
//        ////            return estimatedM;  // hindari pembagian dengan nol
//        ////        }
//
//        int currentM = (int) (((2 * t2_hat) - (3 * t1_hat)) / denominator);
//        // currentM = Math.max(2, currentM);  // batas bawah minimal 2 node
//
//        // Perbarui dengan running average
//        if (this.estimatedM == 0) {
//            this.estimatedM = currentM;
//        } else {
//            this.estimatedM = (int) ((alpha * estimatedM) + (1 - alpha) * currentM);
//        }
//
//        System.out.println("estimatedM: " + estimatedM + " at time: " + SimClock.getTime());
//        return this.estimatedM;
//    }

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
    public RoutingDecisionEngine replicate() {
        return new EstimasiNode(this);
    }

    //    private LatihanSkripsi getOtherDecisionEngine(DTNHost h) {
//        //Setiap DTNHost (node dalam simulasi) memiliki sebuah MessageRouter yang bertanggung jawab untuk mengatur routing pesan.
//        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
//
//        //assert:meriksa kondisi, jika tdk terpenuhi, stop eksekusi dan tampilkan pesan error
//        //jika otherRouter bukan instance dari DER, tampilkan pesan
//        //penting krn RDE hanya bekeerja dengan router yg pake DER
//        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
//
//        //(DecisionEngineRouter) otherRouter): meng-cast otherRouter ke tipe DecisionEngineRouter krn otherRouter sebelumnya bertipe MessageRouter
//        //((DecisionEngineRouter) otherRouter).getDecisionEngine(): untuk mengambil objek RoutingDecisionEngine yang digunakan oleh router
//        //Intercontactnode): meng-cast RoutingDecisionEngine yg di-return ke tipe IntercontactNode
//        return (LatihanSkripsi) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
//    }
    private EstimasiNode getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
        return (EstimasiNode) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    @Override
    public double getEstimation() {
        return estimatedM;
    }

}
