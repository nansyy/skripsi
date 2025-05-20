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

    public static final int DEFAULT_WAKTU = 3600;
    public static final String NODE_PERWAKTU = "nodepersatuanwaktu";
    private double lastRecord = Double.MIN_VALUE;
    private int interval;
    private double updateInterval = 0;
    private static Map<DTNHost, ArrayList<Double>> estimasi = new HashMap<DTNHost, ArrayList<Double>>();

    public ConvergenceTime(){
        super();

        Settings setting = getSettings();

        if (setting.contains(NODE_PERWAKTU)) {
            interval = setting.getInt(NODE_PERWAKTU);
        } else {
            interval = -1;
        }
        if (interval < 0){
            interval = DEFAULT_WAKTU;
        }
    }

    @Override
    public void updated(List<DTNHost> hosts) {
        double currentTime = SimClock.getTime();
        if(isWarmup()){
            return;
        }
        if ((currentTime - lastRecord >= interval)){
            printLine(hosts);
            this.lastRecord = currentTime - currentTime % interval;
        }
    }
//
    private void printLine(List<DTNHost> hosts) {
        for (DTNHost h : hosts) {
            MessageRouter r = h.getRouter();
            InterContactTime ict = null;

            if (r instanceof DecisionEngineRouter){
                RoutingDecisionEngine de = ((DecisionEngineRouter) r).getDecisionEngine();
                ict = (InterContactTime) de;
            }else if (r instanceof SnWskripsi_AR){
                ict = (InterContactTime) r;
            }else{
                continue;
            }

            ArrayList<Double> listNode = new ArrayList<>();

            double temp = ict.getEstimation();
            if(estimasi.containsKey(h)){
                listNode = estimasi.get(h);
                listNode.add(temp);
                estimasi.put(h, listNode);
            }else{
                estimasi.put(h, listNode);
            }
          //  System.out.println("map estimasi di report residu: "+estimasi);

        }

    }

    public void done(){
        for (Map.Entry<DTNHost, ArrayList<Double>> entry : estimasi.entrySet()){
            String printHost = entry.getKey().getAddress() + "\t";
            for (Double listEstimasi : entry.getValue()){
                printHost = printHost + "\t" +listEstimasi;
            }
            write(printHost);
        }
        super.done();
    }
}
