//
//package routing.skripsi;
//
//import java.util.*;
//
//import core.*;
//import routing.*;
//import routing.community.Duration;
//
///**
// * Implementation of Spray and wait router as depicted in
// * <I>Spray and Wait: An Efficient Routing Scheme for Intermittently
// * Connected Mobile Networks</I> by Thrasyvoulos Spyropoulus et al.
// */
//public class SnWskripsi extends ActiveRouter implements RoutingDecisionEngine {
//    /**
//     * identifier for the initial number of copies setting ({@value})
//     */
//    public static final String NROF_COPIES = "nrofCopies";
//    /**
//     * identifier for the binary-mode setting ({@value})
//     */
//    public static final String BINARY_MODE = "binaryMode";
//    /**
//     * SprayAndWait router's settings name space ({@value})
//     */
//    public static final String SPRAYANDWAIT_NS = "SprayAndWaitRouter";
//    /**
//     * Message property key
//     */
//    public static final String MSG_COUNT_PROPERTY = SPRAYANDWAIT_NS + "." +
//            "copies";
//    public static final String ALPHA_ALG_SETTING = "alpha";  //added
//    public static final String THRESHOLD_SETTING = "threshold";
//
//    protected int initialNrofCopies;
//    protected boolean isBinary;
//
//    private List<Double> T1Samples = new ArrayList<>();
//    private List<Double> T2Samples = new ArrayList<>();
//    protected Map<DTNHost, List<Double>> connHistory; //riwayat durasi koneksi antara node saat ini dan peer (waktu mulai dan selesai setiap koneksi)
//    protected Map<DTNHost, List<DTNHost>> coupledNode;
//    protected Map<DTNHost, Double> lastMeetingTimes;
//    private int estimatedM = 0;
//    private double alpha = 0.9;
//    protected  double intervalTime;
//    private int threshold;
//
//    public SnWskripsi(Settings s) {
//        super(s); //panggil konstruktor dari super class
//        Settings snwSettings = new Settings(SPRAYANDWAIT_NS);
//
//        initialNrofCopies = snwSettings.getInt(NROF_COPIES);
//        isBinary = snwSettings.getBoolean(BINARY_MODE);
//        if (s.contains(ALPHA_ALG_SETTING)) s.getDouble(ALPHA_ALG_SETTING);
//        else alpha = 0.98;
//
//        if (s.contains(THRESHOLD_SETTING)) threshold = s.getInt(THRESHOLD_SETTING);
//        else threshold = 25;
//    }
//
//    /**
//     * Copy constructor.
//     *
//     * @param r The router prototype where setting values are copied from
//     */
//    //membuat salinan dari router yg sudah ada
//    protected SnWskripsi(routing.skripsi.SnWskripsi r) {
//        super(r);
//        this.initialNrofCopies = r.initialNrofCopies;
//        this.isBinary = r.isBinary;
//        this.coupledNode = new HashMap<>();
//        this.lastMeetingTimes = new HashMap<>();
//        this.T1Samples = new ArrayList<>(r.T1Samples);
//        this.T2Samples = new ArrayList<>(r.T2Samples);
//        this.estimatedM = r.estimatedM;
//        this.alpha = r.alpha;
//        this.intervalTime = 3600;
//
//    }
//
//    @Override //saat router menerima pesan dari host lain
//    public int receiveMessage(Message m, DTNHost from) {
//        //panggil dari kelas induk
//        return super.receiveMessage(m, from);
//    }
//
//    @Override //pesan berhasil ditransfer ke host lain
//    public Message messageTransferred(String id, DTNHost from) {
//        Message msg = super.messageTransferred(id, from);
//        Integer nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
//
//        //pesan snw harus punya properti jumlah salinan
//        //jika nrofCopies = null -> false -> tampilkan pesan error
//        assert nrofCopies != null : "Not a SnW message: " + msg;
//
//        if (isBinary) {
//            /* in binary S'n'W the receiving node gets ceil(n/2) copies */
//            nrofCopies = (int) Math.ceil(nrofCopies / 2.0);
//        } else {
//            /* in standard S'n'W the receiving node gets only single copy */
//            nrofCopies = 1;
//        }
//
//        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
//        return msg;
//    }
//
//    @Override//buat pesan baru dan menambahlannya ke buffer
//    public boolean createNewMessage(Message msg) {
//        makeRoomForNewMessage(msg.getSize());
//
//        msg.setTtl(this.msgTtl);
//        msg.addProperty(MSG_COUNT_PROPERTY, new Integer(initialNrofCopies));
//        addToMessages(msg, true);
//        return true;
//    }
//
//    @Override //update status router dan mencoba mengirim pesan
//    public void update() {
//        super.update();
//        if (!canStartTransfer() || isTransferring()) {
//            return; // nothing to transfer or is currently transferring
//        }
//
//        /* try messages that could be delivered to final recipient */
//        if (exchangeDeliverableMessages() != null) {
//            return;
//        }
//
//        /* create a list of SAWMessages that have copies left to distribute */
//        @SuppressWarnings(value = "unchecked")
//        List<Message> copiesLeft = sortByQueueMode(getMessagesWithCopiesLeft());
//
//        //kalo ada pesan yang masih punya sisa salinan, kirim ke koneksi yang tersedia
//        if (copiesLeft.size() > 0) {
//            /* try to send those messages */
//            this.tryMessagesToConnections(copiesLeft, getHost().getConnections());
//        }
//    }
//
//    /**
//     * Creates and returns a list of messages this router is currently
//     * carrying and still has copies left to distribute (nrof copies > 1).
//     *
//     * @return A list of messages that have copies left
//     */
//    protected List<Message> getMessagesWithCopiesLeft() {
//        List<Message> list = new ArrayList<Message>();
//
//        for (Message m : getMessageCollection()) { //iterasi melalui semua pesan dalam buffer
//            Integer nrofCopies = (Integer) m.getProperty(MSG_COUNT_PROPERTY);
//
//
//            assert nrofCopies != null : "SnW message " + m + " didn't have " +
//                    "nrof copies property!";
//            if (nrofCopies > 1) {
//                list.add(m);
//            }
//        }
//
//        return list;
//    }
//
//    /**
//     * Called just before a transfer is finalized (by
//     * {@link ActiveRouter#update()}).
//     * Reduces the number of copies we have left for a message.
//     * In binary Spray and Wait, sending host is left with floor(n/2) copies,
//     * but in standard mode, nrof copies left is reduced by one.
//     */
//    @Override //transfer pesan selesai
//    protected void transferDone(Connection con) {
//        Integer nrofCopies;
//        String msgId = con.getMessage().getId(); //ambil id pesan yg berhasil ditransfer
//        /* get this router's copy of the message */
//        Message msg = getMessage(msgId);
//
//        if (msg == null) { // message has been dropped from the buffer after..
//            return; // ..start of transfer -> no need to reduce amount of copies
//        }
//
//        /* reduce the amount of copies left */
//        nrofCopies = (Integer) msg.getProperty(MSG_COUNT_PROPERTY);
//        if (isBinary) {
//            nrofCopies /= 2;
//        } else {
//            nrofCopies--;
//        }
//        msg.updateProperty(MSG_COUNT_PROPERTY, nrofCopies);
//    }
//
//    @Override
//    public void connectionUp(DTNHost thisHost, DTNHost peer) {
//        // Catat waktu pertemuan saat koneksi terbentuk
//        double meetingTime = SimClock.getTime();
//        double endtime;
//        double intercontacttime;
//        if (connHistory.containsKey(thisHost)) {
//          List<Double> history = connHistory.get(thisHost);
//
//          if (!history.isEmpty()) {
//              endtime = history.size() - 1;
//
//              intercontacttime = meetingTime-endtime;
//              T1Samples.add(intercontacttime);
//
//          }
//
//        }
//
//        // Perbarui estimasi jumlah node
//        updateEstimation();
//    }
//
//
//    private int updateEstimation() {
//        if (T1Samples.size() == 0 || T2Samples.size() == 0) {
//            return 0; // Tidak ada cukup data untuk estimasi
//        }
//
//        // Hitung rata-rata T1 dan T2
//        double avgT1 = calculateAverage(T1Samples);
//        double avgT2 = calculateAverage(T2Samples);
//
//        // Estimasi M menggunakan rumus
//        int currentM = (int) ((int) (2 * avgT2 - 3 * avgT1) / (avgT2 - 2 * avgT1));
//
//        // Perbarui estimasi dengan running average
//        if (estimatedM == 0) {
//            estimatedM = currentM; // Inisialisasi pertama
//        } else {
//            estimatedM = (int) (alpha * estimatedM + (1 - alpha) * currentM);
//        }
//        return estimatedM;
//    }
//
//
//    /**
//     * Method untuk menghitung rata-rata dari sebuah list.
//     *
//     * @param samples List yang berisi sampel data.
//     * @return Rata-rata dari sampel.
//     */
//    private double calculateAverage(List<Double> samples) {
//        double sum = 0;
//        for (double sample : samples) {
//            sum += sample;
//        }
//        return sum / samples.size();
//    }
//
//    /**
//     * Method untuk mendapatkan estimasi jumlah node (M).
//     *
//     * @return Estimasi jumlah node.
//     */
//    public double getEstimatedM() {
//        return estimatedM;
//    }
//
//
//    @Override
//    public void connectionDown(DTNHost thisHost, DTNHost peer) {
//        double etime = SimClock.getTime();
//
//        List history;
//        if (connHistory.containsKey(thisHost)) {
//            if (connHistory.get(thisHost) == null) {
//                history = new LinkedList();
//                history.add(etime);
//            }
//            history = connHistory.get(thisHost);
//            connHistory.put(thisHost, history);
//        }
//
//
//    }
//
//    @Override
//    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
//
//    }
//
//    @Override
//    public boolean newMessage(Message m) {
//        return false;
//    }
//
//    @Override
//    public boolean isFinalDest(Message m, DTNHost aHost) {
//        return false;
//    }
//
//    @Override
//    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
//        return false;
//    }
//
//    @Override
//    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
//        return false;
//    }
//
//    @Override
//    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
//        return false;
//    }
//
//    @Override
//    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
//        return false;
//    }
//
//    @Override
//    public routing.skripsi.SnWskripsi replicate() {
//
//        return new routing.skripsi.SnWskripsi(this);
//    }
//}
//
//
