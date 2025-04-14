/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import core.DTNHost;

/**
 * Reports the inter-contact time (i.e., the time between the end of previous
 * contact and the beginning of a new contact between two hosts) distribution.
 * The syntax of the report file is the same as in {@link ContactTimesReport}.
 */
public class InterContactTimesReport extends ContactTimesReport {

    @Override
    //dipanggil ketika ada koneksi baru
    public void hostsConnected(DTNHost host1, DTNHost host2) {
        ConnectionInfo ci = this.removeConnection(host1, host2); //cek apakah sudah konek sebelumnya

        if (ci != null) { // connected again (reconnect dari koneksi sebelumnya yg terputus)
            newEvent(); //catat even baru
            ci.connectionEnd(); //tandai waktu berakhirnya periode terputus
            increaseTimeCount(ci.getConnectionTime()); //tambahakan durasi terputusnya ke dalam perhitungan
        }
    }

    @Override
    //dipanggil ketika koneksi terputus
    public void hostsDisconnected(DTNHost host1, DTNHost host2) {
        if (isWarmup()) {
            return;
        }
        // start counting time to next connection
        this.addConnection(host1, host2);
    }

}
