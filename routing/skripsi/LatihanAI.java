package routing.skripsi;

import core.*;
import routing.*;
import java.util.*;

public class LatihanAI implements RoutingDecisionEngine {
        private final InterContactTimeManager contactTimeManager; // Handles inter-contact logic
        private static final String ALPHA_ALG_SETTING = "alpha";
        private static final String THRESHOLD_SETTING = "threshold";

    public LatihanAI(Settings s) {
            double alpha = s.contains(ALPHA_ALG_SETTING) ? s.getDouble(ALPHA_ALG_SETTING) : 0.98;
            int threshold = s.contains(THRESHOLD_SETTING) ? s.getInt(THRESHOLD_SETTING) : 25;
            double intervalTime = 1000.0; // Default interval time (change if needed)

            this.contactTimeManager = new InterContactTimeManager(alpha, threshold, intervalTime);
        }

        @Override
        public void connectionUp(DTNHost thisHost, DTNHost peer) {
            double currentTime = SimClock.getTime();
            contactTimeManager.reset(currentTime);

            // Handle connection in logic manager
            contactTimeManager.handleConnection(thisHost, peer, currentTime);

            // Optionally update other logic or data here
            contactTimeManager.estimateNumberOfNodes();
        }

        @Override
        public void connectionDown(DTNHost thisHost, DTNHost peer) {
            double currentTime = SimClock.getTime();
            contactTimeManager.handleDisconnection(thisHost, peer, currentTime);
        }

    @Override
    public void doExchangeForNewConnection(Connection con, DTNHost peer) {

    }

    @Override
        public boolean newMessage(Message m) {
            return true; // Always accept new messages
        }

        @Override
        public boolean isFinalDest(Message m, DTNHost aHost) {
            return m.getTo() == aHost;
        }

        @Override
        public boolean shouldSaveReceivedMessage(Message m, DTNHost thisHost) {
            return m.getTo() != thisHost;
        }

        @Override
        public boolean shouldSendMessageToHost(Message m, DTNHost otherHost) {
            return m.getTo() == otherHost; // Send if it's the destination
        }

        @Override
        public boolean shouldDeleteSentMessage(Message m, DTNHost otherHost) {
            return true; // Safe to delete sent messages
        }

        @Override
        public boolean shouldDeleteOldMessage(Message m, DTNHost hostReportingOld) {
            return true; // Default behavior for old messages
        }

        @Override
        public RoutingDecisionEngine replicate() {
            return new LatihanAI(this); // Replicate configuration
        }

    private LatihanAI(LatihanAI proto) {
            this.contactTimeManager = proto.contactTimeManager; // Copy the manager
        }
    }


