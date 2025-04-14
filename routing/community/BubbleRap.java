/*
 * Bubble Rap (by Antok)
 */
package routing.community;

import java.util.*;

import core.*;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
//CommunityDetectionEngine untuk report
public class BubbleRap implements RoutingDecisionEngine, CommunityDetectionEngine {

    // Start-initialisation
    public static final String COMMUNITY_ALG_SETTING = "communityDetectAlg";  //added
    public static final String CENTRALITY_ALG_SETTING = "centralityAlg";

    protected Map<DTNHost, Double> startTimestamps; //menyimpan waktu koneksi dimulai
    protected Map<DTNHost, List<Duration>> connHistory; //riwayat durasi koneksi antara node dan peer (waktu mulai dan selesai setiap koneksi)

    protected CommunityDetection community;  //added
    protected Centrality centrality;

    //End-initialisation
    //Constructor based on the settings
    public BubbleRap(Settings s) {
        if (s.contains(COMMUNITY_ALG_SETTING)) //added
        {
            this.community = (CommunityDetection) s.createIntializedObject(s.getSetting(COMMUNITY_ALG_SETTING));
        } else {
            this.community = new SimpleCommunityDetection(s);
        }

        if (s.contains(CENTRALITY_ALG_SETTING)) {
            this.centrality = (Centrality) s.createIntializedObject(s.getSetting(CENTRALITY_ALG_SETTING));
        } else {
            this.centrality = new AverageWinCentrality1(s);
        }
    }

    //Constructor based on the argument prototype
    public BubbleRap(BubbleRap proto) {
        this.community = proto.community.replicate();	//added
        this.centrality = proto.centrality.replicate();
        startTimestamps = new HashMap<DTNHost, Double>();
        connHistory = new HashMap<DTNHost, List<Duration>>();
    }

    public void connectionUp(DTNHost thisHost, DTNHost peer) {
    }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {
        DTNHost myHost = con.getOtherNode(peer);
        BubbleRap de = this.getOtherDecisionEngine(peer);

        this.startTimestamps.put(peer, SimClock.getTime());
        de.startTimestamps.put(myHost, SimClock.getTime());

        this.community.newConnection(myHost, peer, de.community);	//added
    }

    @Override
    public void connectionDown(DTNHost thisHost, DTNHost peer) {
        double time = cek(thisHost, peer); //get waktu mulai koneksi
        double etime = SimClock.getTime();//waktu  saat ini (saat koneksi terputus)

        // Find or create the connection history list
        //Duration : public class
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

        CommunityDetection peerCD = this.getOtherDecisionEngine(peer).community; //added
        community.connectionLost(thisHost, peer, peerCD, history); //added
        //thisHost: node yg mengalami putus koneksi
        //peer: node lain yg terlibat dalam koneksi
        // deteksi community dari peer
        //history: riwayat koneksi antara thisHost dan peer

        startTimestamps.remove(peer); // memastikan waktu koneksi sebelumnya tidak digunakan lagi

        /* saat koneksi terputus, ambil waktu mulai koneksi,
        hitung durasi koneksi dengan membandingkan waktu awal dan akhir,
        add durasi tsb ke connHistory,
        lapor kehilangan koneksi ke sistem deteksi community,
        hapus data koneksi dari startTimestamps
        */

    }

    public double cek(DTNHost thisHost, DTNHost peer) {
        //cek apakah startTimestamps mengandung entry untuk peer
        if (startTimestamps.containsKey(peer)) {
            return  startTimestamps.get(peer); //return waktu mulai koneksi dengan peer
        }
        return 0;
    }


    @Override
    public boolean newMessage(Message m) {
        return true;
    }

    @Override
    public boolean isFinalDest(Message m, DTNHost aHost) {
        return m.getTo() == aHost;

    }

    @Override
    public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {

        return m.getTo() != thisHost;
    }

    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost, DTNHost thisHost) {
        if (m.getTo() == otherHost) {
            return true; // deliver to final destination
        }
        //now we decide where to forward a message to relay node
        DTNHost dest = m.getTo();
        BubbleRap de = getOtherDecisionEngine(otherHost);

        boolean peerInCommunity = de.commumesWithHost(dest); // Is peer in dest'community
        boolean meInCommunity = this.commumesWithHost(dest); // Is THIS in dest'community

        if (peerInCommunity && !meInCommunity) // peer is in dest's community, but THIS is not
        {
            return true; //forward pesan ke peer
        } else if (!peerInCommunity && meInCommunity) // THIS is in dest'community, but peer is not
        {
            return false; //tidak forward pesan
        } else if (peerInCommunity) // We're both in dest'community
        {
            // Forward to the one with the higher local centrality (in dest'community)
            if (de.getLocalCentrality() > this.getLocalCentrality()) {
                return true;
            } else {
                return false;
            }
        } else if (de.getGlobalCentrality() > this.getGlobalCentrality()) {//peer dan this tidak ada di dest' komunitas
            return true; //Forward to the one with the higher local centrality (in dest'community)

        }

        return false;
    }

    @Override
    public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
        // delete the message once it is forwarded to the node in the dest'community

        BubbleRap de = this.getOtherDecisionEngine(otherHost);
        return de.commumesWithHost(m.getTo()) //peer satu komunitas dengan dest
                && !this.commumesWithHost(m.getTo()); //node saat ini tidak satu komunitas dengan dest

        //hapus jika peer sudah di komunitas tujuan DAN node saat ini tidak di komunitas tujuan
    }

    @Override
    public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
        //BubbleRap de = this.getOtherDecisionEngine(hostReportingOld);
        //return de.commumesWithHost(m.getTo()) &&
        //		!this.commumesWithHost(m.getTo());

        return true;
    }

    @Override
    public RoutingDecisionEngine replicate() {
        return new BubbleRap(this);
    }

    protected boolean commumesWithHost(DTNHost h) {
        return community.isHostInCommunity(h);
    }

    protected double getLocalCentrality() {
        return this.centrality.getLocalCentrality(connHistory, community);
    }

    protected double getGlobalCentrality() {
        return this.centrality.getGlobalCentrality(connHistory);
    }

    private BubbleRap getOtherDecisionEngine(DTNHost h) {
        MessageRouter otherRouter = h.getRouter();
        assert otherRouter instanceof DecisionEngineRouter : "This router only works "
                + " with other routers of same type";

        return (BubbleRap) ((DecisionEngineRouter) otherRouter).getDecisionEngine();
    }

    //for REPORT purpose: CommunityDetectionReport
    @Override
    public Set<DTNHost> getLocalCommunity() {
        return this.community.getLocalCommunity();
    }

    public void update(DTNHost thisHost) {
    }


    public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {

        if (m.getTo() == otherHost) {
            return true; // trivial to deliver to final dest
        }
        DTNHost dest = m.getTo();
        BubbleRap de = getOtherDecisionEngine(otherHost);

        // Which of us has the dest in our local communities, this host or the peer
        boolean peerInCommunity = de.commumesWithHost(dest);
        boolean meInCommunity = this.commumesWithHost(dest);

        if (peerInCommunity && !meInCommunity) // peer is in local commun. of dest
        {
            return true;
        } else if (!peerInCommunity && meInCommunity) // I'm in local commun. of dest
        {
            return false;
        } else if (peerInCommunity) // we're both in the local community of destination
        {
            // Forward to the one with the higher local centrality (in our community)
            if (de.getLocalCentrality() > this.getLocalCentrality()) {
                return true;
            } else {
                return false;
            }
        } // Neither in local community, forward to more globally central node
        else if (de.getGlobalCentrality() > this.getGlobalCentrality()) {
            return true;
        }

        return false;
    }
}






