package routing.skripsi;

import java.util.*;

import routing.*;
import core.*;
import routing.community.Duration;

public class SnWskripsi_AR extends ActiveRouter implements InterContactTime {

    protected Map<DTNHost, Double> lastContactTime;
    protected Map<DTNHost, Double> coupledNodesWithTime;
    protected ArrayList<Double> t1_samples;
    protected ArrayList<Double> t2_samples;
    private double movementStartTime = 0;
    private double estimatedM;
    private double alpha;
    protected Coord initialLocation;
    public static final String ALPHA_ALG_SETTING = "alpha";  //added
    public static final String NROF_COPIES = "nrofCopies";
    public static final String BINARY_MODE = "binaryMode";
    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." + "copies";
    public static final String LIVE_INTERVAL = "live_interval";

    private double mixingTime = 1800;
    private static double interval_live = 1800;
    private double intervalTime;
    private double lastInterval = 0;
    protected int initialNrofCopies;
    protected boolean isBinary;
    // int totalNode;
    protected double lastMeetingTime;
    private Map<DTNHost, Boolean> hasMetAnyNode;
    List<DTNHost> hostOrder;
    private boolean hasMoved = false;

    public Map<DTNHost, List<Double>> estimasiPerNode;


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
        t1_samples = new ArrayList<>();
        t2_samples = new ArrayList<>();
        lastContactTime = new HashMap<>();

    }
    public SnWskripsi_AR(SnWskripsi_AR proto) {
        super(proto);
        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        this.t1_samples = new ArrayList<>();
        this.t2_samples = new ArrayList<>();
        this.lastContactTime = new HashMap<>();
        hostOrder = new ArrayList<>();
        this.coupledNodesWithTime = new HashMap<>();
        this.estimasiPerNode = new HashMap<>();

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

        update();
        if (!coupledNodesWithTime.containsKey(peer)) {
            if (!lastContactTime.containsKey(thisHost)) {
                double t1 = currentTime - lastMeetingTime;
                //t1_samples.add(t1);
                t1_samples.add(t1);
               //  System.out.println(thisHost+" added t1 " + t1 );

            } else {
                double lastContact = lastContactTime.get(thisHost);
                double t2 = currentTime - lastContact;
                //t2_samples.add(t2);
                t2_samples.add(t2);
                //t1_samples.add(currentTime);
                t1_samples.add(t2);

                //System.out.println(thisHost+" added t2 " + t2 + " at " + currentTime);
            }
        coupledNodesWithTime.put(peer, currentTime);
        }

        //System.out.println("sample t1 "+ t1_samples);
        //System.out.println("sample t2 "+ t2_samples);
        //  System.out.println("coupled node dari "+ thisHost +" => "+ck);

    }

    @Override
    public void connectionDown(Connection con) {
        DTNHost myHost = getHost();
        DTNHost peer = con.getOtherNode(myHost);
        // System.out.println("Connection down " + myHost + " dan " + peer);

        if (!lastContactTime.containsKey(peer)) {
            lastContactTime.put(peer, SimClock.getTime());
        } else {
            lastContactTime.put(peer, SimClock.getTime());
        }
        if (!lastContactTime.containsKey(myHost)) {
            lastContactTime.put(myHost, SimClock.getTime());
        } else {
            lastContactTime.put(myHost, SimClock.getTime());
        }

        this.estimatedM = countingNumberOfNodes();
        //System.out.println("Estimasi dari "+getHost()+": "+estimatedM);
    }

    private double countingNumberOfNodes() {

        if (t1_samples.isEmpty() || t2_samples.isEmpty()) {
            return estimatedM;
        }
        //System.out.println("Sample t1: "+ t1_samples);
        //System.out.println("Sample t2: "+ t2_samples);

        //hitung rata-rata T1 dan T2
        double avgT1 = calculateAverage(t1_samples);
        double avgT2 = calculateAverage(t2_samples);

        //System.out.println("Avg t1: " + avgT1);
        //System.out.println("Avg t2: " + avgT2);

        double denominator = (avgT2 - (2 * avgT1));
        if (Math.abs(denominator) < 1e-6) {
            return estimatedM;
        }

        int currentM = (int) (((2 * avgT2) - (3 * avgT1)) / (denominator));
        currentM = Math.max(1, currentM); //pastikan tidak negatif
        //System.out.println("currentM: " + currentM);

        // System.out.println("estimatedM : " + estimatedM);
        if (this.estimatedM == 0) {
            this.estimatedM = currentM;
        } else {
            this.estimatedM = ((alpha * estimatedM) + (1 - alpha) * currentM);  //running average
        }

        //System.out.println("estimatedM dari " + getHost() + ": " + estimatedM + " at time: " + SimClock.getTime());
        return this.estimatedM;
    }

//private double countingNumberOfNodes() {
//    if (t1_samples.isEmpty() ) {
//        return estimatedM;
//    }
//    int totalNode = 100;
//    //List<Double> t1List = t1_samples;
//    int n = t1_samples.size();
//    double t1_hat = 0.0;
//    int ck = coupledNodesWithTime.size();
//
//    //hitung rata-rata berbobot untuk T1
//    for (int k = 0; k < n; k++) {
//        double T1k = t1_samples.get(k);
//
//        if ((totalNode - 1) > 0) {
//            double weight = (double) (totalNode - ck) / (totalNode - 1);
//            t1_hat += weight * T1k;
//        }
//    }
//    t1_hat /= n;
//
//    //hitung rata-rata berbobot untuk T2
//    double t2_hat = 0.0;
//    for (int k = 1; k < n; k++) {
//        double T1_prev = t1_samples.get(k - 1);
//        double T1_curr = t1_samples.get(k);
//
//
//        double term1 = (totalNode - ck-1) / (double) (totalNode - 1);
//        double term2 = (totalNode - ck) / (double) (totalNode - 2);
//
//        t2_hat += (term1 * T1_prev + term2 * T1_curr);
//    }
//    t2_hat /= (n - 1);
//
//    // Estimasi jumlah node M
//    double denominator = t2_hat - (2 * t1_hat);
//
//    if (Math.abs(denominator) < 1e-6) {
//        return estimatedM;  // hindari pembagian dengan nol
//    }
//
//    int currentM = (int) (((2 * t2_hat) - (3 * t1_hat)) / denominator);
//
//    // Perbarui dengan running average
//    if (this.estimatedM == 0) {
//        this.estimatedM = currentM;
//    } else {
//        this.estimatedM = (int) ((alpha * estimatedM) + (1 - alpha) * currentM);
//    }
//
//   // System.out.println("hasil estimasi:"+estimatedM);
//    return this.estimatedM;
//}

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
       // this.estimatedM = countingNumberOfNodes();
        Iterator<Map.Entry<DTNHost, Double>> it = coupledNodesWithTime.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<DTNHost, Double> entry = it.next();
            if (SimClock.getTime() - entry.getValue() >= mixingTime) {
                it.remove();
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
        //   System.out.println(getHost()+" estimasi: "+estimatedM);
        return this.estimatedM;

    }






}






