package routing.skripsi;

import java.util.*;

import routing.*;
import core.*;

public class SnWskripsi_AR extends ActiveRouter implements InterContactTime {

    protected Map<DTNHost, Double> lastContactTime;
    protected Map<DTNHost, Map<DTNHost, Double>> coupledNodesWithTime;
    //protected Map<DTNHost, List<DTNHost>> coupledNode;
    protected ArrayList<Double> t1_samples;
    protected ArrayList<Double> t2_samples;
    private double estimatedM;
    private double alpha;
    protected Coord initialLocation;
    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String LIVE_INTERVAL = "live_interval";

    private static double interval_live = 3600;
    private double intervalTime;
    private double lastInterval = 0;
    protected int initialNrofCopies;
    protected boolean isBinary;
    protected int totalNode;
    private Map<DTNHost, Boolean> hasMetAnyNode;
    List<DTNHost> hostOrder;
    private boolean hasMoved = false;
    private double movementStartTime = 0;


    public SnWskripsi_AR(Settings s) {
        super(s);
        if (s.contains(ALPHA_ALG_SETTING)) {
            alpha = s.getDouble(ALPHA_ALG_SETTING);
        } else alpha = 0.98;

        if (s.contains(BINARY_MODE)) {
            isBinary = s.getBoolean(BINARY_MODE);
        } else {
            isBinary = false;
        }
        if (s.contains(NROF_COPIES)) {
            initialNrofCopies = s.getInt(NROF_COPIES);
        }
        if (s.contains(LIVE_INTERVAL)) {
            intervalTime = s.getInt(LIVE_INTERVAL);
        } else {
            intervalTime = interval_live;
        }
        totalNode = s.getInt("nrofHosts");
        //coupledNode = new HashMap<>();
        t1_samples = new ArrayList<>();
        t2_samples = new ArrayList<>();
        lastContactTime = new HashMap<>();
        coupledNodesWithTime = new HashMap<>();
    }

    public SnWskripsi_AR(SnWskripsi_AR proto) {
        super(proto);
        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        // coupledNode = new HashMap<>();
        this.t1_samples = new ArrayList<>();
        this.t2_samples = new ArrayList<>();
        this.lastContactTime = new HashMap<>();
        this.hasMetAnyNode = new HashMap<>();
        hostOrder = new ArrayList<>();
        this.coupledNodesWithTime = new HashMap<>();

    }

    @Override
    public MessageRouter replicate() {
        return new SnWskripsi_AR(this);
    }

    @Override
    public void connectionUp(Connection con) {
        DTNHost thisHost = this.getHost();
        DTNHost peer = con.getOtherNode(thisHost);
        double currentTime = SimClock.getTime();

        if (!coupledNodesWithTime.containsKey(thisHost)) {
            coupledNodesWithTime.put(thisHost, new HashMap<>());
        }
        //List<DTNHost> ck = coupledNodesWithTime.get(thisHost);
        Map<DTNHost, Double> ck = coupledNodesWithTime.get(thisHost);

        //hapus node yang sudah melewati mixing time
        Iterator<Map.Entry<DTNHost, Double>> iterator = ck.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<DTNHost, Double> entry = iterator.next();
            if (currentTime - entry.getValue() > intervalTime) {
                iterator.remove();
            }
        }
        if (!ck.containsKey(peer)) {
            // if (!hasMetAnyNode.getOrDefault(thisHost, false)) {
            if (!lastContactTime.containsKey(thisHost)) {
                double t1 = currentTime - this.movementStartTime;
                t1_samples.add(t1);
                hostOrder.add(thisHost);
                //System.out.println(thisHost+" added t1 " + t1 + " at " + currentTime);
            } else {
                double lastContact = lastContactTime.get(thisHost);
                double t2 = currentTime - lastContact;
                // t2_samples.add(currentTime);
                t2_samples.add(t2);
                //t1_samples.add(t2);
                hostOrder.add(thisHost);
                //System.out.println(thisHost+" added t2 " + t2 + " at " + currentTime);
            }
            ck.put(peer, currentTime);
            hasMetAnyNode.put(thisHost, true); //tandai bahwa sudah pernah bertemu siapa pun
            this.coupledNodesWithTime.put(thisHost, ck);

            // System.out.println("sample t1 "+ t1_samples);
            // System.out.println("sample t2 "+ t2_samples);
            //  System.out.println("coupled node dari "+ thisHost +" => "+ck);

        }
    }

    @Override
    public void connectionDown(Connection con) {
        DTNHost myHost = getHost();
        DTNHost peer = con.getOtherNode(myHost);
        // System.out.println("Connection down " + myHost + " dan " + peer);

        if (!lastContactTime.containsKey(myHost)) {
            lastContactTime.put(myHost, SimClock.getTime());
        } else {
            this.lastContactTime.replace(myHost, SimClock.getTime());
        }
        // System.out.println("Map connection down "+ lastContactTime);
        this.estimatedM = countingNumberOfNodes();
    }


    private double countingNumberOfNodes() {

        if (t1_samples.isEmpty() || t2_samples.isEmpty()) {
            return estimatedM;
        }
        //   System.out.println("Sample t1: "+ t1_samples);
        //  System.out.println("Sample t2: "+ t2_samples);

        //hitung rata-rata T1 dan T2
        double avgT1 = calculateAverage(t1_samples);
        double avgT2 = calculateAverage(t2_samples);

        // System.out.println("Avg t1: " + avgT1);
        //  System.out.println("Avg t2: " + avgT2);

        double denominator = (avgT2 - (2 * avgT1));
        if (Math.abs(denominator) < 1e-6) {
            return estimatedM;
        }

        //estimasi M pake rumus

        double currentM = (((2 * avgT2) - (3 * avgT1)) / (denominator));
        //  int currentM = (int) (((2 * avgT2) - (3 * avgT1)) / (denominator));
        //currentM = Math.max(1, currentM); //pastikan tidak negatif
        //System.out.println("currentM: " + currentM);

        // System.out.println("estimatedM : " + estimatedM);
        //update estimasi dengan running average
        if (this.estimatedM == 0) {
            this.estimatedM = currentM; // Inisialisasi pertama
        } else {
            this.estimatedM = ((alpha * estimatedM) + (1 - alpha) * currentM);
        }
        //System.out.println("estimatedM dari " + getHost() + ": " + estimatedM + " at time: " + SimClock.getTime());
        return this.estimatedM;
    }

    //    private int countingNumberOfNodes() {
//        // Pastikan data cukup untuk estimasi
//        if (t1_samples.isEmpty() || t2_samples.isEmpty() || hostOrder.size() < t1_samples.size()) {
//            return estimatedM;
//        }
//
//        int n = t1_samples.size();
//        double t1_hat = 0.0;
//
//        // Hitung rata-rata berbobot untuk T1
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
//        // Hitung rata-rata berbobot untuk T2
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
//        t2_hat /= (n - 1);
//
//        // Estimasi jumlah node M
//        double denominator = t2_hat - (2 * t1_hat);
//
//        if (Math.abs(denominator) < 1e-6) {
//            return estimatedM;  // hindari pembagian dengan nol
//        }
//
//        int currentM = (int) (((2 * t2_hat) - (3 * t1_hat)) / denominator);
//       // System.out.println("CurrentM: "+currentM);
//        // currentM = Math.max(2, currentM);  // batas bawah minimal 2 node
//
//        // Perbarui dengan running average
//        if (this.estimatedM == 0) {
//            this.estimatedM = currentM;
//        } else {
//            this.estimatedM = (int) ((alpha * estimatedM) + (1 - alpha) * currentM);
//        }
//
//
//         //System.out.println("estimatedM (after running avg): " + estimatedM);
//        return this.estimatedM;
//    }
//
    private double calculateAverage(List<Double> samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }

    @Override
    public boolean createNewMessage(Message msg) {
        makeRoomForNewMessage(msg.getSize());

        msg.setTtl(this.msgTtl);
        initialNrofCopies = (int) Math.ceil(this.estimatedM / 2);
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
        for (Map<DTNHost, Double> coupledMap : coupledNodesWithTime.values()) {
            Iterator<Map.Entry<DTNHost, Double>> iterator = coupledMap.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DTNHost, Double> entry = iterator.next();
                if (SimClock.getTime() - entry.getValue() > intervalTime) {
                    iterator.remove();
                }
            }
        }
        //System.out.println("update: "+SimClock.getTime());
        estimatedM = countingNumberOfNodes();

        // if (SimClock.getTime() - lastInterval == 1800) {
        //    lastInterval = SimClock.getTime();
        //System.out.println("waktu: " + SimClock.getTime() + " hasil estimasi di update: " + estimatedM);

        //  t1_samples.clear();
        //  t2_samples.clear();
        //    coupledNode.clear();
        //  }
        if (!hasMoved) {
            Coord currentLoc = getHost().getLocation();

            if (initialLocation == null) {
                initialLocation = new Coord(currentLoc.getX(), currentLoc.getY());
            } else if (!currentLoc.equals(initialLocation)) {
                movementStartTime = SimClock.getTime();
                hasMoved = true;
                // System.out.println(getHost() + " mulai bergerak pada waktu: " + movementStartTime);
            }
        }

        if (!canStartTransfer() || isTransferring()) {
            return; // nothing to transfer or is currently transferring
        }

        /* try messages that could be delivered to final recipient */
        if (exchangeDeliverableMessages() != null) {
            return;
        }

        /* create a list of SAWMessages that have copies left to distribute */
        @SuppressWarnings(value = "unchecked")
        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());

        if (copiesLeft.size() > 0) {
            /* try to send those messages */
            this.tryMessagesToConnections(copiesLeft, getHost().getConnections());
        }

    }

    protected List<Message> getMessagesWithCopiesLeft() {
        List<Message> list = new ArrayList<Message>();

        for (Message m : getMessageCollection()) {
            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
            assert nrofCopies != null : "SnW message " + m + " didn't have " +
                    "nrof copies property!";
            if (nrofCopies > 1) {
                list.add(m);
            }
        }

        return list;
    }

    @Override
    public double getEstimation() {
        return this.estimatedM;
    }
}






