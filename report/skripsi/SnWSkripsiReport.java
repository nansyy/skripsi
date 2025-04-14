///*
// * NodeEstimationReport.java
// * Report untuk mencatat estimasi jumlah node berdasarkan inter-contact time
// */
//package report.skripsi;
//
//import core.DTNHost;
//import core.Settings;
//import core.SimClock;
//import java.util.HashMap;
//import java.util.Map;
//
//public class NodeEstimationReport extends Report {
//    private Map<DTNHost, Double> lastMeetingTimes;
//    private Map<Double, Integer> nodeEstimations; // Key: waktu simulasi, Value: estimasi node
//    private double estimationInterval;
//    private double lastEstimationTime;
//
//    public static final String ESTIMATION_INTERVAL = "interval";
//    public static final double DEFAULT_INTERVAL = 3600.0; // 1 jam dalam detik
//
//    public NodeEstimationReport() {
//        Settings settings = getSettings();
//        this.estimationInterval = settings.getDouble(ESTIMATION_INTERVAL);
//        this.lastMeetingTimes = new HashMap<>();
//        this.nodeEstimations = new HashMap<>();
//        this.lastEstimationTime = 0.0;
//    }
//
//    public void hostsConnected(DTNHost host1, DTNHost host2) {
//        double currentTime = SimClock.getTime();
//
//        // Update last meeting time untuk kedua host
//        updateLastMeetingTime(host1, host2, currentTime);
//        updateLastMeetingTime(host2, host1, currentTime);
//
//        // Lakukan estimasi pada interval tertentu
//        if (currentTime - lastEstimationTime >= estimationInterval) {
//            int estimatedNodes = estimateNodeCount();
//            nodeEstimations.put(currentTime, estimatedNodes);
//            lastEstimationTime = currentTime;
//        }
//    }
//
//    private void updateLastMeetingTime(DTNHost host1, DTNHost host2, double currentTime) {
//        if (lastMeetingTimes.containsKey(host2)) {
//            double lastTime = lastMeetingTimes.get(host2);
//            double interContactTime = currentTime - lastTime;
//
//            // Asumsikan router mengimplementasikan interface tertentu
//            if (host1.getRouter() instanceof InterContactRecorder) {
//                ((InterContactRecorder)host1.getRouter()).recordInterContactTime(interContactTime);
//            }
//        }
//        lastMeetingTimes.put(host2, currentTime);
//    }
//
//    private int estimateNodeCount() {
//        int totalEstimates = 0;
//        int validRouters = 0;
//
//        for (DTNHost host : getHosts()) {
//            if (host.getRouter() instanceof InterContactRecorder) {
//                InterContactRecorder router = (InterContactRecorder)host.getRouter();
//                int estimate = router.getNodeEstimate();
//                if (estimate > 0) {
//                    totalEstimates += estimate;
//                    validRouters++;
//                }
//            }
//        }
//
//        return validRouters > 0 ? totalEstimates / validRouters : 0;
//    }
//
//    @Override
//    public void done() {
//        write("Node Estimation Report");
//        write("=====================");
//        write("Format: SimulationTime EstimatedNodeCount");
//        write("----------------------------------------");
//
//        for (Map.Entry<Double, Integer> entry : nodeEstimations.entrySet()) {
//            write(String.format("%.2f\t%d", entry.getKey(), entry.getValue()));
//        }
//
//        write("\nFinal Node Estimation: " +
//                (nodeEstimations.isEmpty() ? "N/A" : nodeEstimations.values().toArray()[nodeEstimations.size()-1]));
//
//        super.done();
//    }
//
//    /**
//     * Interface yang harus diimplementasikan oleh router untuk merekam inter-contact time
//     */
//    public interface InterContactRecorder {
//        void recordInterContactTime(double time);
//        int getNodeEstimate();
//    }
//}