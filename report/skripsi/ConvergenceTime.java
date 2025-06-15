package report.skripsi;

import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;
import report.Report;
import routing.ActiveRouter;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.skripsi.InterContactTime;
import routing.skripsi.SnWskripsi_AR;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConvergenceTime extends Report implements UpdateListener {

    public static final int DEFAULT_WAKTU = 1800;
    public static final String NODE_PERWAKTU = "nodepersatuanwaktu";
    private double lastRecord = Double.MIN_VALUE;
    private int interval;

    public ConvergenceTime() {
        super();

        Settings setting = getSettings();

        if (setting.contains(NODE_PERWAKTU)) {
            interval = setting.getInt(NODE_PERWAKTU);
        } else {
            interval = DEFAULT_WAKTU;

        }
    }

    @Override
    public void updated(List<DTNHost> hosts) {
        if (SimClock.getTime() - lastRecord >= interval) {
            lastRecord = SimClock.getTime();
            printLine(hosts);
        }
    }


    private void printLine(List<DTNHost> hosts) {

        double totalEstimasi = 0;

        for (DTNHost h : hosts) {
            MessageRouter r = h.getRouter();


            if (!(r instanceof DecisionEngineRouter))
                continue;
            RoutingDecisionEngine de = ((DecisionEngineRouter) r).getDecisionEngine();
            InterContactTime ict = (InterContactTime) de;

           double temp = Math.abs(ict.getEstimation());
           // double temp = ict.getEstimation();
            totalEstimasi += temp;
        }
        //double estimasi = totalEstimasi;
        String output =  (int)SimClock.getTime() + "\t" + String.format("%.2f", totalEstimasi);;
        write(output);

    }

}
