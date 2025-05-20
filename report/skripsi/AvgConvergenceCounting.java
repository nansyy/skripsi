
package report.skripsi;

import core.DTNHost;
import core.Settings;
import core.SimClock;

import core.UpdateListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.skripsi.InterContactTime;
import report.*;
import routing.skripsi.SnWskripsi_AR;

public class AvgConvergenceCounting extends Report implements UpdateListener {

    public static final int DEFAULT_WAKTU = 1800;
    public static final String NODE_PERWAKTU = "nodepersatuanwaktu";
    private double lastRecord = Double.MIN_VALUE;
    private int interval;
    double alpha = 0.98;
    Map<DTNHost, Double> smoothedEstimate = new HashMap<>();

    // double estimasi = 0;
    int nodeActive;
    public AvgConvergenceCounting() {
        super();

        Settings setting = getSettings();
        if (setting.contains(NODE_PERWAKTU)) {
            interval = setting.getInt(NODE_PERWAKTU);
        } else {
            interval = DEFAULT_WAKTU;
        }
    }

    public void updated(List<DTNHost> hosts) {
        if (SimClock.getTime() - lastRecord >= interval) {
            lastRecord = SimClock.getTime();
            printLine(hosts);

        }
    }

    public void printLine(List<DTNHost> hosts) {
        double newEstimasi = 0;
      double totalEstimasi = 0;
      double currentEst = 0;
        for (DTNHost h : hosts) {
            MessageRouter r = h.getRouter();
            InterContactTime ict = null;

            if (r instanceof DecisionEngineRouter) {
                RoutingDecisionEngine de = ((DecisionEngineRouter) r).getDecisionEngine();
                ict = (InterContactTime) de;
            } else if (r instanceof SnWskripsi_AR) {
                ict = (InterContactTime) r;
            } else {
                continue;
            }
            double est = Math.abs(ict.getEstimation());
            System.out.println("host " + h + " counting: " + est);
            //totalEstimasi += est;
            if (currentEst == 0) {
                newEstimasi = est; //
                //currentEst = newEstimasi;// Inisialisasi pertama
            } else {
               newEstimasi = ((alpha * currentEst) + (1 - alpha) * est);
                //System.out.println("estimatedM (after running avg): " + estimatedM);
            }
            totalEstimasi += newEstimasi;
            currentEst = newEstimasi;
            //System.out.println("new estimasi: " + newEstimasi);
           // System.out.println("current estimasi: "+currentEst);
            System.out.println("total estimasi: " + totalEstimasi);


            //    }
        }
        //   double avg_estimasi = activeNodes > 0 ? (double) estimasi / activeNodes : 0.0;
        double avg_estimasi = totalEstimasi/ hosts.size();
        String output = ((int) SimClock.getTime()) + " \t" + String.format("%.2f", avg_estimasi);
        write(output);
    }
}



