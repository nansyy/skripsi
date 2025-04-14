package routing.skripsi;

import core.*;

import java.util.*;

import report.Report;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;


/*
intercontact: waktu dari koneksi terakhir putus sampai waktu node connect lagi dengan node baru
intercontact: waktu saat node ga terhubung dengan node lain
T1 =  time until a node (starting from the stationary distribution) encounters any other node.
T2 = tim euntil two different nodes are encountered
-alurnya-
--dalam satu jendela waktu--
    1. saat A ketemu B, A catat waktu pertemuannya dengan B dan menandai B sebagai node coupled. di sini A hitung intercontact
       yaitu waktu ketemu B - waktu mulai simulasi -> [T1]
    2. saat putus dari B, A catat waktu putusnya dengan B
    3. setelah putus dari B, A ketemu lagi dengan C. nah A catat waktu pertemuan dan tandai C sebagai coupled dan hitung
       intercontactnya yaitu waktu ketemu C - waktu putus dengan B -> [T1][T2]
    4. langkah ini diulangi terus sampai satu jendela waktu selesai.
    5. A hitung rata-rata waktu T1 dan T2
    6. dapat satu sample untuk T1 dan T2 untuk satu jendela waktu
--masuk ke interval baru
--setelah waktu simulasi habis, hitung rata-rata dari sample T1 dan T2.
--subtitusi ke rumus, didapat estimasi jumlah node--

 */
public class SnWskripsi_RDE extends Report implements RoutingDecisionEngine {


    //    protected Map<DTNHost, List<DTNHost>> coupledNode;
    protected Set<DTNHost> coupledNode;
    protected Map<DTNHost, Double> lastMeetingTimes; // Menyimpan waktu terakhir pertemuan dengan setiap node
    protected double intervalTime ;
    protected double lastIntervalTime;
    protected List<Double> intervalAverages; // Menyimpan rata-rata waktu inter-contact per interval
    protected ArrayList<Double> T1_samples;
    protected ArrayList<Double> T2_samples;

    private int estimatedM;
    private double alpha;
    private int threshold;

    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String THRESHOLD_SETTING = "threshold";
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String INTERVAL_TIME = "intervaltime";

    public static final double DEFAULT_INTERVAL = 3600;
    protected int initialNrofCopies;
    protected boolean isBinary;


    public SnWskripsi_RDE(Settings s) {
        if (s.contains(ALPHA_ALG_SETTING)) {
            alpha = s.getDouble(ALPHA_ALG_SETTING);
        } else {
            alpha = 0.98;
        }

        if (s.contains(THRESHOLD_SETTING)) {
            threshold = s.getInt(THRESHOLD_SETTING);
        } else {
            threshold = 25;
        }

        if (s.contains(BINARY_MODE)) {
            isBinary = s.getBoolean(BINARY_MODE);
        } else {
            isBinary = false;
        }

        if (s.contains(NROF_COPIES)) {
            initialNrofCopies = s.getInt(NROF_COPIES);
        }
        if (s.contains(INTERVAL_TIME)){
            intervalTime = s.getDouble(INTERVAL_TIME);
        }else {
            intervalTime = DEFAULT_INTERVAL;
        }
        coupledNode = new HashSet<>();
        lastMeetingTimes = new HashMap<>();
        intervalAverages = new LinkedList<>();
        T1_samples = new ArrayList<>();
        T2_samples = new ArrayList<>();
        lastIntervalTime = 0;

    }

    //Constructor based on the argument prototype
    public SnWskripsi_RDE(SnWskripsi_RDE proto) {
        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        coupledNode = new HashSet<>();
        lastMeetingTimes = new HashMap<>();
        intervalAverages = new LinkedList<>();
        intervalTime = 3600;
        this.T1_samples = new ArrayList<>();
        this.T2_samples = new ArrayList<>();
        this.estimatedM = proto.estimatedM;
        this.alpha = proto.alpha;
        this.threshold = proto.threshold;
        this.lastIntervalTime = proto.lastIntervalTime;
    }

    @Override
    public void connectionUp(DTNHost thisHost, DTNHost peer) {
        this.lastMeetingTimes.put(peer, SimClock.getTime());
//        SnWskripsi_RDE de = this.getOtherDecisionEngine(peer);
//        double currentTime = SimClock.getTime();
//
//
//        reset();
//        // Pastikan thisHost ada di coupledNode
//        this.coupledNode.putIfAbsent(thisHost, new ArrayList<>());
//        if (this.coupledNode.get(thisHost).isEmpty()) {
//            this.T1_samples.add(currentTime);
//            coupledNode.get(thisHost).add(peer);
//
//        }
//        if (!this.coupledNode.get(thisHost).contains(peer)) {
//            double lastMeetingTime = this.lastMeetingTimes.get(thisHost);
//            double interContactTime = currentTime - lastMeetingTime;
//            this.T2_samples.add(interContactTime);
//            coupledNode.get(thisHost).add(peer);
//        }
//
//        this.lastMeetingTimes.put(thisHost, currentTime);
//
//        this.estimateNumberOfNodes();
    }

    @Override//mencatat durasi koneksi antara dua host ketika koneksi terputus
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        SnWskripsi_RDE de = this.getOtherDecisionEngine(peer);

        this.lastMeetingTimes.put(peer, SimClock.getTime());
        de.lastMeetingTimes.put(thisHost, SimClock.getTime());

        this.estimateNumberOfNodes();
        de.estimateNumberOfNodes();

        System.out.println("estimasi m = "+ estimatedM);

    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost thisHost = con.getOtherNode(peer);
        SnWskripsi_RDE de = this.getOtherDecisionEngine(peer);
        double currentTime = SimClock.getTime();

        update();

        if (!this.coupledNode.contains(peer) && !de.coupledNode.contains(thisHost)) {
            double lastMeeting = lastMeetingTimes.get(peer);
            double interMeeting = currentTime - lastMeeting;
            this.T1_samples.add(interMeeting);
            this.coupledNode.add(peer);
            de.coupledNode.add(thisHost);
        }
        if(this.coupledNode.size()>2 && !this.coupledNode.contains(peer)){
            double lastMeeting = lastMeetingTimes.get(peer);
            double interMeeting = currentTime - lastMeeting;
            this.T2_samples.add(interMeeting);
            this.coupledNode.add(peer);
            de.coupledNode.add(thisHost);
        }
        this.lastMeetingTimes.put(peer, SimClock.getTime());
        de.lastMeetingTimes.put(thisHost, SimClock.getTime());


    }

    private void update() {
        double currentTime = SimClock.getTime();
        if (currentTime - lastIntervalTime >= intervalTime) {
            coupledNode.clear();
            lastMeetingTimes.clear();
            lastIntervalTime = currentTime;
            T1_samples.clear();
            T2_samples.clear();
        }
    }


    private int estimateNumberOfNodes() {
        if (T1_samples.size() < threshold || T2_samples.size() < threshold) {
            return estimatedM; //belum cukup sample
        }

        // Hitung rata-rata T1 dan T2
        double avgT1 = calculateAverage(T1_samples);
        double avgT2 = calculateAverage(T2_samples);

        double denominator = avgT2 - 2 * avgT1;
        if (denominator == 0) {
            return estimatedM; // hindari pembagian nol
        }
        // Estimasi M menggunakan rumus
        int currentM = (int) ((int) (2 * avgT2 - 3 * avgT1) / denominator);
        currentM = Math.max(1, currentM);

        // Perbarui estimasi dengan running average
        if (estimatedM == 0) {
            estimatedM = currentM; // Inisialisasi pertama
        } else {
            estimatedM = (int) (alpha * estimatedM + (1 - alpha) * currentM);
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
        return m.getTo() != hostReportingOld;
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new SnWskripsi_RDE(this);
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
    private SnWskripsi_RDE getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();//get MessageROuter yg dipake h. MessageRouter
        assert otherRouter instanceof DecisionEngineRouter : "This router only works " + " with other routers of same type";
        return (SnWskripsi_RDE) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }


}


