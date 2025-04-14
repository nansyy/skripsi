package routing.skripsi;

import java.util.*;

import routing.*;
import core.*;

public class SnWskripsi_AR extends ActiveRouter {
    protected Map<DTNHost, List<DTNHost>> coupledNode;
    protected Map<DTNHost, Double> lastMeetingTimes; // Menyimpan waktu terakhir pertemuan dengan setiap node
    protected double intervalTime; // Waktu mulai interval saat ini
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

    protected int initialNrofCopies;
    protected boolean isBinary;

    public SnWskripsi_AR(Settings s) {
        super(s);
        if (s.contains(ALPHA_ALG_SETTING)){
           alpha = s.getDouble(ALPHA_ALG_SETTING);
        }
        else alpha = 0.98;

        if (s.contains(THRESHOLD_SETTING)){
            threshold = s.getInt(THRESHOLD_SETTING);
        }
        else{
            threshold = 25;
        }
        if (s.contains(BINARY_MODE)){
            isBinary = s.getBoolean(BINARY_MODE);
        } else{
            isBinary = false;
        }
        if (s.contains(NROF_COPIES)){
            initialNrofCopies = s.getInt(NROF_COPIES);
        }
        intervalTime = 3600;
        coupledNode = new HashMap<>();
        lastMeetingTimes = new HashMap<>();
        intervalAverages = new LinkedList<>();
        T1_samples = new ArrayList<>();
        T2_samples = new ArrayList<>();
        lastIntervalTime = 0;
    }

    public SnWskripsi_AR(SnWskripsi_AR proto) {
        super(proto);
        this.initialNrofCopies = proto.initialNrofCopies;
        this.isBinary = proto.isBinary;
        coupledNode = new HashMap<>();
        lastMeetingTimes = new HashMap<>();
        intervalAverages = new LinkedList<>();
        this.T1_samples = new ArrayList<>();
        this.T2_samples = new ArrayList<>();
        this.lastIntervalTime = proto.lastIntervalTime;
        this.intervalTime = 3600;

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
    public MessageRouter replicate() {

        return new SnWskripsi_AR(this);
    }

    @Override
    public void connectionUp(Connection con) {
        DTNHost thisHost = getHost();
        DTNHost peer = con.getOtherNode(thisHost);
        double currentTime = SimClock.getTime();

        reset();
        // Pastikan thisHost ada di coupledNode
        coupledNode.putIfAbsent(thisHost, new ArrayList<>());
        if(this.coupledNode.get(thisHost).isEmpty()) {
            this.T1_samples.add(currentTime);
            coupledNode.get(thisHost).add(peer);
        }
        if (!this.coupledNode.get(thisHost).contains(peer)) {
            double lastMeetingTime = this.lastMeetingTimes.get(thisHost);
            double interContactTime = currentTime - lastMeetingTime;
            this.T2_samples.add(interContactTime);
            coupledNode.get(thisHost).add(peer);
        }


//        if (coupledNode.containsKey(thisHost)) {
//            List<DTNHost> couple = coupledNode.get(thisHost);
//            if (!couple.contains(peer)) {
//                couple.add(peer);
//            }
//        } else {
//            List<DTNHost> couple = coupledNode.get(peer);
//            if (!couple.contains(thisHost)) {
//                couple.add(thisHost);
//            }
//        }
        lastMeetingTimes.put(thisHost, currentTime);
        estimateNumberOfNodes();
    }

    private int estimateNumberOfNodes() {
        // Hitung rata-rata dari semua sample waktu inter-contact
        if (T1_samples.size() < threshold || T2_samples.size() < threshold) {
            return estimatedM; //belum cukup sample
        }

        // Hitung rata-rata T1 dan T2
        double avgT1 = calculateAverage(T1_samples);
        double avgT2 = calculateAverage(T2_samples);

        double denominator = avgT2 - 2 * avgT1;
        if (Math.abs(denominator) < 1e-6) {
            return estimatedM; // hindari pembagian nol
        }

        // Estimasi M menggunakan rumus
        int currentM = (int) ((2 * avgT2 - 3 * avgT1) / denominator);
        currentM = Math.max(1, currentM); //pastikan tidak negatif

        // Perbarui estimasi dengan running average
        if (this.estimatedM == 0) {
            this.estimatedM = currentM; // Inisialisasi pertama
        } else {
            this.estimatedM = (int) (alpha * estimatedM + (1 - alpha) * currentM);
        }


        return this.estimatedM;
    }

    private double calculateAverage(List<Double> samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample;
        }
        return sum / samples.size();
    }


    @Override
    public void connectionDown(Connection con) {
        DTNHost myHost = getHost();
        DTNHost peer = con.getOtherNode(myHost);
        DecisionEngineRouter de = (DecisionEngineRouter) peer.getRouter();

        this.lastMeetingTimes.put(peer, SimClock.getTime());


    }

    private void reset() {
        double currentTime = SimClock.getTime();
        if (currentTime - lastIntervalTime >= intervalTime) {
            coupledNode.clear();
            lastMeetingTimes.clear();
            lastIntervalTime = currentTime;
            T1_samples.clear();
            T2_samples.clear();
        }
    }


    @Override
    public int receiveMessage(Message m, DTNHost from) {
        return super.receiveMessage(m, from);
    }

    @Override
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


    @Override
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

}






