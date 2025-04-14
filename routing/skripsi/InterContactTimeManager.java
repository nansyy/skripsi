package routing.skripsi;

import java.util.*;

import core.DTNHost;

public class InterContactTimeManager {

    private final Map<DTNHost, List<DTNHost>> coupledNode;  // Tracks coupled nodes
    private final Map<DTNHost, Double> lastMeetingTimes;   // Last meeting time maps
    private final List<Double> T1_samples;                 // Samples for inter-contact time T1
    private final List<Double> T2_samples;                 // Samples for inter-contact time T2
    private int threshold;                                 // Threshold for sampling
    private final double alpha;                            // Smoothing coefficient for estimation
    private int estimatedM;                                // Current node count estimation
    private double intervalTime;
    private double lastIntervalTime;

    public InterContactTimeManager(double alpha, int threshold, double intervalTime) {
        this.coupledNode = new HashMap<>();
        this.lastMeetingTimes = new HashMap<>();
        this.T1_samples = new ArrayList<>();
        this.T2_samples = new ArrayList<>();
        this.alpha = alpha;
        this.threshold = threshold;
        this.estimatedM = 0;
        this.intervalTime = intervalTime;
        this.lastIntervalTime = 0;
    }

    /**
     * Handles connection between two nodes. This adds a node to coupledNode and computes intercontact time.
     */
    public void handleConnection(DTNHost thisHost, DTNHost peer, double currentTime) {
        coupledNode.putIfAbsent(thisHost, new ArrayList<>());
        coupledNode.get(thisHost).add(peer);

        if (lastMeetingTimes.containsKey(peer)) {
            double lastMeetingTime = lastMeetingTimes.get(peer);
            double interContactTime = currentTime - lastMeetingTime;

            T1_samples.add(interContactTime);
            T2_samples.add(interContactTime);
        }
        lastMeetingTimes.put(peer, currentTime);
    }

    /**
     * Updates last meeting time for a disconnection.
     */
    public void handleDisconnection(DTNHost thisHost, DTNHost peer, double currentTime) {
        lastMeetingTimes.put(peer, currentTime);
    }

    /**
     * Resets the collected data when a new interval starts.
     */
    public void reset(double currentTime) {
        if (currentTime - lastIntervalTime >= intervalTime) {
            coupledNode.clear();
            lastMeetingTimes.clear();
            lastIntervalTime = currentTime;
            T1_samples.clear();
            T2_samples.clear();
        }
    }

    /**
     * Estimates the number of nodes based on inter-contact time samples.
     */
    public int estimateNumberOfNodes() {
        if (T1_samples.size() < threshold || T2_samples.size() < threshold) {
            return estimatedM; // Not enough samples
        }

        double avgT1 = calculateAverage(T1_samples);
        double avgT2 = calculateAverage(T2_samples);

        double denominator = avgT2 - 2 * avgT1;
        if (Math.abs(denominator) < 1e-6) {
            return estimatedM; // Avoid division by zero
        }

        int currentM = (int) ((2 * avgT2 - 3 * avgT1) / denominator);
        currentM = Math.max(1, currentM);

        estimatedM = estimatedM == 0
                ? currentM
                : (int) (alpha * estimatedM + (1 - alpha) * currentM); // Running average

        return estimatedM;
    }

    /**
     * Calculates the average of a list of samples.
     */
    private double calculateAverage(List<Double> samples) {
        return samples.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    // Getters for unit testing or other needs
    public List<Double> getT1Samples() {
        return T1_samples;
    }

    public List<Double> getT2Samples() {
        return T2_samples;
    }

    public Map<DTNHost, List<DTNHost>> getCoupledNode() {
        return coupledNode;
    }
}


