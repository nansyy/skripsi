package report.skripsi;
//catat jumlah node yg estimasinya salah (tidak persis jumlah node yg sebenarnya)
import core.DTNHost;
import core.Settings;
import core.SimClock;
import core.UpdateListener;
import report.Report;
import routing.DecisionEngineRouter;
import routing.MessageRouter;
import routing.RoutingDecisionEngine;
import routing.skripsi.InterContactTime;
import routing.skripsi.SnWskripsi_AR;

import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Residu extends Report implements UpdateListener {
    public static final String NODE_PERWAKTU = "nodepersatuanwaktu";
    public static final int DEFAULT_WAKTU = 1800;
    private double lastRecord = Double.MIN_VALUE;
    private int interval;


    public Residu() {
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
        Settings s = new Settings();
        int numberNode1 = s.getInt("Group.nrofHosts");
       // int nrofNode = 100;
        int residu = 0;
        double estimasi = 0;

        for (DTNHost h : hosts) {
            MessageRouter r = h.getRouter();
            InterContactTime ict = null;
            if ((r instanceof DecisionEngineRouter)) {
                RoutingDecisionEngine de = ((DecisionEngineRouter) r).getDecisionEngine();
                ict = (InterContactTime) de;
            } else if (r instanceof SnWskripsi_AR) {
                ict = (InterContactTime) r;

            }else{
                continue;
            }

            double temp = ict.getEstimation();
            if (temp < numberNode1) {
                residu++;
            } else if (temp > numberNode1) {
                residu++;
            }
        }
        int totalResidu = residu;
        String output = (int) SimClock.getTime() + "\t" + totalResidu;
        write(output);
    }
}
