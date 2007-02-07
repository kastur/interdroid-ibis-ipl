/* $Id$ */

package ibis.impl.registry.tcp;

import ibis.connect.controlHub.ControlHub;
import ibis.impl.registry.NSProps;
import ibis.io.Conversion;
import ibis.impl.IbisIdentifier;
import ibis.util.IPUtils;
import ibis.util.PoolInfoServer;
import ibis.util.TypedProperties;
import ibis.util.ThreadPool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Vector;
import java.util.ArrayList;

import org.apache.log4j.Logger;

public class NameServer extends Thread implements Protocol {

    public static final int TCP_IBIS_NAME_SERVER_PORT_NR
            = TypedProperties.intProperty(NSProps.s_port, 9826);

    static final int PINGER_TIMEOUT
        = TypedProperties.intProperty(NSProps.s_pinger_timeout, 60) * 1000;
        // Property is in seconds, convert to milliseconds.

    static final int CONNECT_TIMEOUT
        = TypedProperties.intProperty(NSProps.s_connect_timeout, 10) * 1000;
        // Property is in seconds, convert to milliseconds.

    static final int JOINER_INTERVAL
        = TypedProperties.intProperty(NSProps.s_joiner_interval, 5) * 1000;

    // In seconds, as PoolChecker expects.
    static final int CHECKER_INTERVAL
        = TypedProperties.intProperty(NSProps.s_poolchecker_interval, 0);

    static final int MAXTHREADS =
        TypedProperties.intProperty(NSProps.s_max_threads, 8);

    static final Logger logger = 
            ibis.util.GetLogger.getLogger(NameServer.class.getName());

    /**
     * The <code>Sequencer</code> class provides a global numbering.
     * This can be used, for instance, for global ordering of messages.
     * A sender must then first obtain a sequence number from the sequencer,
     * and tag the message with it. The receiver must then handle the messages
     * in the "tag" order.
     * <p>
     * A Sequencer associates a numbering scheme with a name, so the user can
     * associate different sequences with different names.
     */
    private static class Sequencer {
        private HashMap counters;

        private static class LongObject {
            long val;

            LongObject(long v) {
                val = v;
            }

            public String toString() {
                return "" + val;
            }
        }

        Sequencer() {
            counters = new HashMap();
        }

        /**
         * Returns the next sequence number associated with the specified name.
         * @param name the name of the sequence.
         * @return the next sequence number
         */
        public synchronized long getSeqno(String name) {
            LongObject i = (LongObject) counters.get(name);
            if (i == null) {
                i = new LongObject(ibis.ipl.ReadMessage.INITIAL_SEQNO);
                counters.put(name, i);
            }
            return i.val++;
        }

        public String toString() {
            return "" + counters;
        }
    }

    static InetAddress myAddress;
    static {
        myAddress = IPUtils.getAlternateLocalHostAddress();
        try {
            myAddress = InetAddress.getByName(myAddress.getHostName());
        } catch(Exception e) {
            logger.warn("Got exception in initializer", e);
        }
    }

    static class IbisInfo {
        int ibisNameServerport;
        InetAddress ibisNameServerAddress;
        boolean needsUpcalls;
        boolean completelyJoined = false;
        IbisIdentifier id;

        IbisInfo(InetAddress ibisNameServerAddress, int ibisNameServerport,
                boolean needsUpcalls, RunInfo p, byte[] data, String[] cluster,
                String poolId) throws IOException {
            this.ibisNameServerAddress = ibisNameServerAddress;
            this.ibisNameServerport = ibisNameServerport;
            this.needsUpcalls = needsUpcalls;
            synchronized(p) {
                id = new IbisIdentifier(Integer.toString(p.joinCount++), data,
                        null, cluster, poolId);
            }
        }

        public boolean equals(Object other) {
            if (other instanceof IbisInfo) {
                return id.equals(((IbisInfo) other).id);
            }
            return false;
        }

        public int hashCode() {
            return id.hashCode();
        }

        public String toString() {
            return "ibisInfo(" + id + " at " + ibisNameServerAddress
                    + ":" + ibisNameServerport + ")";
        }
    }

    static class PingerEntry {
        String poolId;
        String id;

        PingerEntry(String poolId, String id) {
            this.poolId = poolId;
            this.id = id;
        }

        boolean largerOrEqual(PingerEntry e) {
            if (poolId == null) {
                // poolId == null means: ping everything.
                return true;
            }
            if (e.poolId == null) {
                return false;
            }
            if (! poolId.equals(e.poolId)) {
                // unrelated.
                return false;
            }

            if (id == null) {
                // Same poolId, so this one pings whole pool.
                return true;
            }

            return false;
        }
    }

    static class DeadNotifier implements Runnable {
        ArrayList corpses = new ArrayList();
        final RunInfo runInfo;
        boolean done = false;
        int count = 0;
        final int port;
        final InetAddress addr;
        final byte message;

        DeadNotifier(RunInfo r, InetAddress a, int p, byte m) {
            runInfo = r;
            port = p;
            addr = a;
            message = m;
        }

        synchronized void addCorpses(IbisIdentifier[] ids) {
            if (ids.length == 0) {
                return;
            }
            corpses.add(ids);
            count += ids.length;
            notifyAll();
        }

        synchronized void quit() {
            done = true;
            notifyAll();
        }

        public void run() {
            for (;;) {
                IbisIdentifier[] deadOnes = null;
                synchronized(this) {
                    while (! done && count == 0) {
                        try {
                            this.wait();
                        } catch(Exception e) {
                            // ignored
                        }
                    }
                    if (count > 0) {
                        deadOnes = new IbisIdentifier[count];
                        int i = 0;
                        while (corpses.size() > 0) {
                            IbisIdentifier[] el
                                    = (IbisIdentifier[]) corpses.remove(0);
                            for (int j = 0; j < el.length; j++) {
                                deadOnes[i] = el[j];
                                i++;
                            }
                        }
                        count = 0;
                    }
                }
                if (deadOnes != null) {
                    try {
                        send(deadOnes);
                    } catch(Exception e) {
                        // ignored
                    }
                }
                synchronized(this) {
                    if (count != 0) {
                        continue;
                    }
                    if (done) {
                        return;
                    }
                }
            }
        }

        private void send(IbisIdentifier[] ids) throws IOException {
            Socket s = null;
            DataOutputStream out2 = null;

            try {
                s = NameServerClient.socketFactory.createClientSocket(
                        addr, port, null, CONNECT_TIMEOUT);
                out2 = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                out2.writeByte(message);
                out2.writeInt(ids.length);
                for (int i = 0; i < ids.length; i++) {
                    ids[i].writeTo(out2);
                }
            } finally {
                closeConnection(null, out2, s);
            }
        }
    }

    static class RunInfo {
        ArrayList unfinishedJoins; // a list of IbisInfos

        ArrayList arrayPool;    // IbisInfos in fixed order.

        Hashtable pool;

        ArrayList leavers;

        int forwarders;

        int pingers;

        int failed;

        ElectionServer electionServer;

        DeadNotifier electionKiller;

        long pingLimit;

        boolean silent;

        int joinCount = 0;

        RunInfo(boolean silent) throws IOException {
            unfinishedJoins = new ArrayList();
            arrayPool = new ArrayList();
            pool = new Hashtable();
            leavers = new ArrayList();
            electionServer = new ElectionServer(silent,
                    NameServerClient.socketFactory);
            electionKiller = new DeadNotifier(this, myAddress,
                    electionServer.getPort(), ELECTION_KILL);
            ThreadPool.createNew(electionKiller, "ElectionKiller");
            pingLimit = System.currentTimeMillis() + PINGER_TIMEOUT;
            this.silent = silent;
        }

        public String toString() {
            String res = "runinfo:\n" + "  pool = \n";
            IbisInfo[] elts = instances();

            for (int i = 0; i < elts.length; i++) {
                res += "    " + elts[i] + "\n";
            }

            return res;
        }

        public IbisInfo[] instances() {
            return (IbisInfo[]) pool.values().toArray(new IbisInfo[0]);
        }

        public void remove(IbisInfo iinf) {
            pool.remove(iinf.id.idData);
            if (! iinf.completelyJoined) {
                int index = unfinishedJoins.indexOf(iinf);
                if (index == -1) {
                    if (! silent) {
                        logger.error("Internal error: " + iinf.id.idData + " not completelyJoined but not in unfinishedJoins!");
                    }
                } else {
                    unfinishedJoins.remove(index);
                }
            }
        }
    }

    private Hashtable pools;

    private ArrayList pingerEntries = new ArrayList();

    private ServerSocket serverSocket;

    private DataInputStream in;

    private DataOutputStream out;

    private boolean singleRun;

    private boolean joined;

    boolean silent;

    private ControlHub controlHub = null;

    private Sequencer seq;

    static class CloseJob {
        DataInputStream in;
        DataOutputStream out;
        ByteArrayOutputStream baos;
        Socket s;
        long startTime;
        int opcode;

        long myStartTime = System.currentTimeMillis();
        
        CloseJob(DataInputStream in, DataOutputStream out,
                ByteArrayOutputStream baos, Socket s,
                int opcode, long start) {
            this.in = in;
            this.out = out;
            this.baos = baos;
            this.s = s;
            this.opcode = opcode;
            this.startTime = start;
        }

        void close(boolean silent) {
            try {
                out.flush();
                baos.writeTo(s.getOutputStream());
                closeConnection(in, out, s);
            } catch(Exception e) {
                if (! silent) {
                    logger.error("Exception in close", e);
                }
            }
            if (logger.isInfoEnabled() && opcode >= 0) {
                String job = "unknown opcode " + opcode;
                switch(opcode) {
                case (IBIS_ISALIVE):
                    job = "ISALIVE"; break;
                case (IBIS_DEAD):
                    job = "DEAD"; break;
                case (IBIS_JOIN):
                    job = "JOIN"; break;
                case (IBIS_MUSTLEAVE):
                    job = "MUSTLEAVE"; break;
                case (IBIS_LEAVE):
                    job = "LEAVE"; break;
                case (IBIS_CHECK):
                    job = "CHECK"; break;
                case (IBIS_CHECKALL):
                    job = "CHECKALL"; break;
                default:
                    job = "unknown opcode " + opcode; break;
                }
                
                long now = System.currentTimeMillis();
                
                logger.info("Request " + job + " took "
                        + (now - startTime) + " ms. -> " 
                        + "job took " + (myStartTime - startTime) 
                        + " closing took " + (now-myStartTime));
            }
        }
    }

    static ArrayList closeJobs = new ArrayList();
    static int numClosers;

    private static class Closer implements Runnable {
        boolean silent;
        Closer(boolean silent) {
            this.silent = silent;
            numClosers++;
        }

        static void addJob(CloseJob cl) {
            synchronized(closeJobs) {
                closeJobs.add(cl);
                closeJobs.notify();
            }
        }

        public void run() {
            for (;;) {
                CloseJob cl;
                synchronized(closeJobs) {
                    while (closeJobs.size() == 0) {
                        if (numClosers > 1) {
                            numClosers--;
                            return;
                        }
                        try {
                            closeJobs.wait();
                        } catch(Exception e) {
                            // ignored
                        }
                    }
                    cl = (CloseJob) closeJobs.remove(0);
                    if (numClosers < MAXTHREADS && closeJobs.size() > 0) {
                        ThreadPool.createNew(new Closer(silent), "Closer");
                    }

                }
                cl.close(silent);
            }
        }
    }

    private NameServer(boolean singleRun, boolean poolserver,
            boolean controlhub, boolean silent) throws IOException {

        this.singleRun = singleRun;
        this.joined = false;
        this.silent = silent;

        seq = new Sequencer();

        String hubPort = System.getProperty("ibis.connect.hub.port");
        String poolPort = System.getProperty("ibis.pool.server.port");
        int port = TCP_IBIS_NAME_SERVER_PORT_NR;

        if (! silent && logger.isInfoEnabled()) {
            logger.info("Creating nameserver on " + myAddress);
        }

        if (controlhub) {
            if (hubPort == null) {
                hubPort = Integer.toString(port + 2);
                System.setProperty("ibis.connect.hub.port", hubPort);
            }
            try {
                controlHub = new ControlHub();
                controlHub.setDaemon(true);
                controlHub.start();
                Thread.sleep(2000); // Give it some time to start up
            } catch (Throwable e) {
                throw new IOException("Could not start control hub" + e);
            }
        }

        if (poolserver) {
            if (poolPort == null) {
                poolPort = Integer.toString(port + 1);
                System.setProperty("ibis.pool.server.port", poolPort);
            }
            try {
                PoolInfoServer p = new PoolInfoServer(singleRun);
                p.setDaemon(true);
                p.start();
            } catch (Throwable e) {
                // May have been started by PoolInfoClient already.
                // throw new IOException("Could not start poolInfoServer" + e);
            }
        }

        if (CHECKER_INTERVAL != 0) {
            final PoolChecker ck
                    = new PoolChecker(null, myAddress.getHostName(), port,
                        CHECKER_INTERVAL);
            Thread p = new Thread("PoolChecker Upcaller") {
                public void run() {
                    ck.run();
                }
            };
            p.setDaemon(true);
            p.start();
        }

        if (! silent && logger.isInfoEnabled()) {
            logger.info("NameServer: singleRun = " + singleRun);
        }

        // Create a server socket.
        serverSocket = NameServerClient.socketFactory.createServerSocket(port,
                null, 256, false, null);

        pools = new Hashtable();

        Thread p = new Thread("NameServer Upcaller") {
            public void run() {
                upcaller();
            }
        };

        p.setDaemon(true);
        p.start();

        p = new Thread("Pinger Handler") {
            public void run() {
                pingRunner();
            }
        };

        p.setDaemon(true);
        p.start();

        if (! silent && logger.isInfoEnabled()) {
            logger.info("NameServer: created server on " + serverSocket);
        }

        if (! silent && logger.isInfoEnabled()) {
            Runtime.getRuntime().addShutdownHook(
                new Thread("Nameserver ShutdownHook") {
                    public void run() {
                        logger.info("Shutdown hook triggered");
                    }
                });
        }
    }

    // Should be called within synchronized on inf.
    private void sendLeavers(RunInfo inf) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("sendLeavers ... size = " + inf.leavers.size()
                    + ", forwarders = " + inf.forwarders);
        }

        IbisInfo[] leavers = null;

        if (inf.leavers.size() != 0) {
            IbisInfo[] iinf = new IbisInfo[0];
            leavers = (IbisInfo[]) inf.leavers.toArray(iinf);
            inf.leavers.clear();

            // Obtain elements to send to first. The forward() method
            // may wait (and loose the lock).
            IbisInfo[] elts = inf.instances();
            for (int i = 0; i < elts.length; i++) {
                if (elts[i].needsUpcalls) {
                    forward(IBIS_LEAVE, inf, elts[i], leavers, 0);
                }
            }

            for (int i = 0; i < leavers.length; i++) {
                if (leavers[i].needsUpcalls) {
                    forward(IBIS_LEAVE, inf, leavers[i], leavers, 0);
                }
            }

            IbisIdentifier[] ids = new IbisIdentifier[leavers.length];

            for (int i = 0; i < leavers.length; i++) {
                ids[i] = leavers[i].id;
            }

            // Let the election server know about it.
            inf.electionKiller.addCorpses(ids);
        }

        // After sendLeavers finishes, forwarders should be 0, even if
        // this instance did not send anything! It may be called from
        // several threads, so even if this instance does not send anything,
        // there may still be work in progress. We wait for it to finish here.
        while (inf.forwarders != 0) {
            try {
                inf.wait();
            } catch(Exception ex) {
                // ignored
            }
        }
    }

    void pingRunner() {
        for (;;) {
            PingerEntry e = null;
            synchronized(pingerEntries) {
                while (pingerEntries.size() == 0) {
                    try {
                        pingerEntries.wait();
                    } catch(Exception ex) {
                        // ignored
                    }
                }
                e = (PingerEntry) pingerEntries.get(0);
            }
            if (e.poolId == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Doing full check");
                }
                poolPinger();
            } else if (e.id == null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Doing check of pool " + e.poolId);
                }
                poolPinger(e.poolId);
            } else {
                RunInfo p = (RunInfo) pools.get(e.poolId);
                if (p != null) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Doing check of ibis " + e.id);
                    }
                    checkPool(p, e.id, false, e.poolId);
                }
            }
            synchronized(pingerEntries) {
                // note: other threads may only replace/remove indices != 0.
                pingerEntries.remove(0);
            }
        }
    }

    void addPingerEntry(String poolId, String id) {

        PingerEntry added = new PingerEntry(poolId, id);
        boolean replaced = false;

        synchronized(pingerEntries) {
            // First check if this request is already present. If so,
            // just return. If a "larger" request is already present,
            // also just return.
            // Vice versa, if the request to be added is "larger" than
            // any request in the list, remove/replace the "smaller" requests.
            for (int i = 0; i < pingerEntries.size(); i++) {
                PingerEntry e = (PingerEntry) pingerEntries.get(i);
                if (e.largerOrEqual(added)) {
                    return;
                }
                if (added.largerOrEqual(e)) {
                    if (i == 0) {
                        continue;
                    }
                    if (! replaced) {
                        pingerEntries.set(i, added);
                        replaced = true;
                    } else {
                        pingerEntries.remove(i);
                        i--;
                    }
                }
            }
            if (! replaced) {
                pingerEntries.add(added);
                pingerEntries.notifyAll();
            }
        }
    }

    void upcaller() {
        for (;;) {
            try {
                Thread.sleep(JOINER_INTERVAL);
            } catch(InterruptedException e) {
                // ignore
            }
            for (Enumeration e = pools.keys(); e.hasMoreElements();) {
                String poolId = (String) e.nextElement();
                RunInfo inf = (RunInfo) pools.get(poolId);
                boolean joinFailed = false;

                synchronized(inf) {
                    try {
                        sendLeavers(inf);
                    } catch(IOException ex) {
                        if (! silent) {
                            logger.error("Got exception: " + ex);
                        }
                    }

                    if (inf.unfinishedJoins.size() > 0) {
                        inf.failed = 0;
                        IbisInfo[] message = (IbisInfo[])
                                inf.unfinishedJoins.toArray(new IbisInfo[0]);

                        inf.unfinishedJoins.clear();

                        // Obtain elements to send to first. The forward()
                        // method may wait (and loose the lock).
                        IbisInfo[] elts = inf.instances();
                        for (int i = 0; i < elts.length; i++) {
                            if (elts[i].completelyJoined && elts[i].needsUpcalls) {
                                forward(IBIS_JOIN, inf, elts[i], message, 0);
                            }
                        }

                        for (int i = 0; i < message.length; i++) {
                            IbisInfo ibisInf = message[i];
                            if (ibisInf.needsUpcalls && i+1 < message.length) {
                                forward(IBIS_JOIN, inf, ibisInf, message, i+1);
                            }
                            ibisInf.completelyJoined = true;
                        }

                        while (inf.forwarders != 0) {
                            try {
                                inf.wait();
                            } catch(Exception ex) {
                                // ignored
                            }
                        }
                        if (inf.failed != 0) {
                            joinFailed = true;
                        }
                    }
                    if (joinFailed) {
                        addPingerEntry(poolId, null);
                    }
                }
            }
        }
    }

    private class Forwarder implements Runnable {
        RunInfo inf;
        IbisInfo dest;
        IbisInfo info[];
        int offset;
        byte message;

        Forwarder(byte message, RunInfo inf, IbisInfo dest, IbisInfo[] info,
                int offset) {
            this.inf = inf;
            this.dest = dest;
            this.info = info;
            this.offset = offset;
            this.message = message;
        }

        private String type(int msg) {
            switch(msg) {
            case IBIS_DEAD:
                return "dead";
            case IBIS_LEAVE:
                return "leave";
            case IBIS_JOIN:
                return "join";
            case IBIS_MUSTLEAVE:
                return "mustLeave";
            default:
                return "unknown";
            }
        }

        public void run() {
            Socket s = null;
            DataOutputStream out2 = null;
            boolean failed = true;

            // QUICK HACK -- JASON
            for (int h=0;h<3;h++) { 
                try {
                    s = NameServerClient.socketFactory.createClientSocket(
                            dest.ibisNameServerAddress, dest.ibisNameServerport, null, CONNECT_TIMEOUT);
                    out2 = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                    out2.writeByte(message);
                    out2.writeInt(info.length - offset);
                    for (int i = offset; i < info.length; i++) {
                        info[i].id.writeTo(out2);
                        
                        if (logger.isDebugEnabled()) {
                            logger.debug("NameServer: forwarding "
                                    + type(message) + " of "
                                    + info[i].id.idData + " to " + dest + " DONE");
                        }
                    }

                    failed = false;
                    closeConnection(null, out2, s);
                    break;
                } catch (Exception e) {
                    closeConnection(null, out2, s);
                    if (! silent) {
                        logger.error("Could not forward "
                                + type(message) + " to " + dest, e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException x) {
                        // ignore
                    }
                }
            }

            synchronized(inf) {
                inf.forwarders--;
                if (logger.isDebugEnabled()) {
                    logger.debug("NameServer: forwarders decr: " + inf.forwarders);
                }
                if (failed) {
                    inf.failed++;
                    if (logger.isDebugEnabled()) {
                        logger.debug("NameServer: failed: " + inf.failed);
                    }
                }
                if (inf.forwarders == 0 || inf.forwarders == MAXTHREADS-1) {
                    inf.notifyAll();
                }
            }
        }
    }

    private void forward(byte message, RunInfo inf, IbisInfo dest,
            IbisInfo[] info, int offset) {

        if (logger.isDebugEnabled()) {
            logger.debug("NameServer: forwarding to " + dest);
        }

        if (offset >= info.length) {
            return;
        }

        Forwarder forwarder = new Forwarder(message, inf, dest, info, offset);

        synchronized(inf) {
            while (inf.forwarders >= MAXTHREADS) {
                try {
                    inf.wait();
                } catch(Exception e) {
                    // Ignored
                }
            }
            inf.forwarders++;
            if (logger.isDebugEnabled()) {
                logger.debug("NameServer: forwarders = " + inf.forwarders);
            }
            ThreadPool.createNew(forwarder, "Forwarder thread");
        }
    }

    private class PingThread implements Runnable {
        RunInfo run;
        IbisInfo dest;
        String poolId;
        Vector deadIbises;

        PingThread(RunInfo run, IbisInfo dest, String poolId, Vector deadIbises) {
            this.run = run;
            this.dest = dest;
            this.poolId = poolId;
            this.deadIbises = deadIbises;
        }

        public void run() {
            doPing();
            synchronized(run) {
                run.pingers--;
                run.notifyAll();
            }
        }

        private void doPing() {
            Socket s = null;
            DataOutputStream out2 = null;
            DataInputStream in2 = null;

            try {
                s = NameServerClient.socketFactory.createClientSocket(
                        dest.ibisNameServerAddress, dest.ibisNameServerport, null, CONNECT_TIMEOUT);
                out2 = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
                out2.writeByte(IBIS_PING);
                out2.flush();
                in2 = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                String k = in2.readUTF();
                String n = in2.readUTF();
                if (!k.equals(poolId) || n != dest.id.idData) {
                    deadIbises.add(dest);
                }
            } catch (Exception e) {
                deadIbises.add(dest);
            } finally {
                closeConnection(in2, out2, s);
            }
        }
    }

    /**
     * Checks or kills a pool.
     * @param p the pool.
     * @param id the id of the ibis instance that must be checked/killed,
     *     or <code>null</code>, in which case the whole pool is checked/killed.
     * @param kill <code>true</code> when the victim must be killed,
     *     <code>false</code> otherwise.
     * @param poolId the poolId of the pool.
     */
    private void checkPool(RunInfo p, String id, boolean kill, String poolId) {

        Vector deadIbises = new Vector();

        synchronized(p) {
            // Obtain elements to send to first.
            IbisInfo[] elts = p.instances();
            for (int i = 0; i < elts.length; i++) {
                IbisInfo temp = elts[i];
                if (id == null || temp.id.idData.equals(id)) {
                    if (! kill) {
                        PingThread pt = new PingThread(p, temp, poolId, deadIbises);
                        while (p.pingers >= MAXTHREADS) {
                            try {
                                p.wait();
                            } catch(Exception ex) {
                                // ignored
                            }
                        }
                        p.pingers++;
                        ThreadPool.createNew(pt, "Ping thread");
                    } else {
                        deadIbises.add(temp);
                    }
                }
            }

            while (p.pingers > 0) {
                try {
                    p.wait();
                } catch(Exception e) {
                    // ignored
                }
            }

            for (int j = 0; j < deadIbises.size(); j++) {
                IbisInfo temp = (IbisInfo) deadIbises.get(j);
                if (! kill && ! silent && logger.isInfoEnabled()) {
                    logger.info("NameServer: ibis " + temp.id + " seems dead");
                }

                p.remove(temp);
            }

            if (deadIbises.size() != 0) {
                // Put the dead ones in an array.
                IbisIdentifier[] ids = new IbisIdentifier[deadIbises.size()];
                IbisInfo[] ibisIds = new IbisInfo[ids.length];
                for (int j = 0; j < ids.length; j++) {
                    IbisInfo temp2 = (IbisInfo) deadIbises.get(j);
                    ids[j] = temp2.id;
                    ibisIds[j] = temp2;
                }

                // Pass the dead ones on to the election server
                p.electionKiller.addCorpses(ids);

                // ... and to all other ibis instances in this pool.
                elts = p.instances();
                for (int i = 0; i < elts.length; i++) {
                    IbisInfo ibisInf = elts[i];
                    if (ibisInf.needsUpcalls) {
                        forward(IBIS_DEAD, p, ibisInf, ibisIds, 0);
                    }
                }

                if (kill) {
                    for (int i = 0; i < ibisIds.length; i++) {
                        if (ibisIds[i].needsUpcalls) {
                            forward(IBIS_DEAD, p, ibisIds[i], ibisIds, 0);
                        }
                    }
                }

                while (p.forwarders != 0) {
                    try {
                        p.wait();
                    } catch(Exception ex) {
                        // ignored
                    }
                }
            }

            p.pingLimit = System.currentTimeMillis() + PINGER_TIMEOUT;

            if (p.pool.size() == 0) {
                pools.remove(poolId);
                if (! silent) {
                    logger.warn("pool " + poolId + " seems to be dead.");
                }
                killThreads(p);
            }
        }
    }

    private void handleIbisIsalive(boolean kill) throws IOException {
        String poolId = in.readUTF();
        String id = in.readUTF();

        if (! kill) {
            addPingerEntry(poolId, id);
            return;
        }

        RunInfo p = (RunInfo) pools.get(poolId);
        if (p != null) {
            checkPool(p, id, kill, poolId);
        }
    }

    private void handleCheck() throws IOException {
        String poolId = in.readUTF();
        if (! silent && logger.isInfoEnabled()) {
            logger.info("Got check for pool " + poolId);
        }
        addPingerEntry(poolId, null);
        out.writeByte(0);
        out.flush();
    }

    private void handleCheckAll() throws IOException {
        if (! silent && logger.isInfoEnabled()) {
            logger.info("Got checkAll");
        }
        addPingerEntry(null, null);
        out.writeByte(0);
        out.flush();
    }

    private void handleIbisJoin(long startTime) throws IOException {
        String poolId = in.readUTF();
        int len = in.readInt();
        byte[] buf = new byte[len];
        in.readFully(buf, 0, len);
        InetAddress address = null;
        try {
            address = (InetAddress) Conversion.byte2object(buf);
        } catch(ClassNotFoundException e) {
            throw new IOException("Could not read InetAddress");
        }
        int port = in.readInt();
    
        // System.out.println("After readInetAddress: " +
        //         (System.currentTimeMillis() - startTime));

        boolean needsUpcalls = in.readBoolean();
        len = in.readInt();
        byte[] data = new byte[len];
        in.readFully(data, 0, len);
        len = in.readInt();
        String[] cluster = null;
        if (len >= 0) {
            cluster = new String[len];
            for (int i = 0; i < len; i++) {
                cluster[i] = in.readUTF();
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("NameServer: join to pool " + poolId
                    + " requested by " + address + ":" + port);
        }


        RunInfo p = (RunInfo) pools.get(poolId);

        if (p == null) {
            // new run
            //
            if (singleRun && joined) {
                out.writeByte(IBIS_REFUSED);

                if (logger.isDebugEnabled()) {
                    logger.debug("NameServer: join to pool " + poolId
                            + " refused");
                }
                out.flush();
                return;
            }
            initiatePoolPinger();
            p = new RunInfo(silent);

            pools.put(poolId, p);
            joined = true;

            if (! silent && logger.isInfoEnabled()) {
                logger.info("NameServer: new pool " + poolId + " created");
            }
        }

        // System.out.println("before poolPinger: " +
        //         (System.currentTimeMillis() - startTime));
        initiatePoolPinger(poolId);
        // System.out.println("after poolPinger: " +
        //         (System.currentTimeMillis() - startTime));
        // Handle delayed leave messages before adding new members
        // to a pool, otherwise new members get leave messages from nodes
        // that they have never seen.

        out.writeByte(IBIS_ACCEPTED);
        out.writeInt(p.electionServer.getPort());

        if (logger.isDebugEnabled()) {
            logger.debug("NameServer: join to pool " + poolId + " accepted");
        }
        // System.out.println("before synchronized block: " +
        //         (System.currentTimeMillis() - startTime));

        IbisInfo info = new IbisInfo(address, port, needsUpcalls, p, data,
                cluster, poolId);

        synchronized(p) {
            sendLeavers(p);
            p.pool.put(info.id.idData, info);
            p.unfinishedJoins.add(info);
            p.arrayPool.add(info);
        }

        info.id.writeTo(out);
        // System.out.println("after synchronized block: " +
        //         (System.currentTimeMillis() - startTime));

        // first send all existing nodes (including the new one) to the
        // new one.
        if (needsUpcalls) {
            out.writeInt(p.pool.size());

            if (logger.isDebugEnabled()) {
                logger.debug("Sending " + p.pool.size() + " nodes");
            }

            int i = 0;
            while (i < p.arrayPool.size()) {
                IbisInfo temp = (IbisInfo) p.pool.get(((IbisInfo) p.arrayPool.get(i)).id.idData);

                if (temp != null) {
                    temp.id.writeTo(out);
                    i++;
                } else {
                    p.arrayPool.remove(i);
                }
            }
        }
        out.flush();

        if (! silent && logger.isInfoEnabled()) {
            logger.info("" + address + ":" + port + " JOINS  pool " + poolId
                    + " (" + p.pool.size() + " nodes)");
        }
        // System.out.println("after write answer: " +
        //         (System.currentTimeMillis() - startTime));
    }

    private void poolPinger(String poolId) {

        RunInfo p = (RunInfo) pools.get(poolId);

        if (p == null) {
            return;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("NameServer: ping pool " + poolId);
        }

        checkPool(p, null, false, poolId);
    }

    private void initiatePoolPinger(String poolId) {
        RunInfo p = (RunInfo) pools.get(poolId);

        if (p == null) {
            return;
        }

        long t = System.currentTimeMillis();

        // If the pool has not reached its ping-limit yet, return.
        if (t < p.pingLimit) {
            if (logger.isDebugEnabled()) {
                logger.debug("NameServer: ping timeout not reached yet for pool " + poolId);
            }
            return;
        }
        addPingerEntry(poolId, null);
    }

    private void initiatePoolPinger() {
        for (Enumeration e = pools.keys(); e.hasMoreElements();) {
            String poolId = (String) e.nextElement();
            initiatePoolPinger(poolId);
        }
    }

    /**
     * Checks all pools to see if they still are alive. If a pool is dead
     * (connect to all members fails), the pool is killed.
     */
    private void poolPinger() {
        for (Enumeration e = pools.keys(); e.hasMoreElements();) {
            String poolId = (String) e.nextElement();
            poolPinger(poolId);
        }
    }


    private void killThreads(RunInfo p) {
        Socket s3 = null;
        DataOutputStream out3 = null;

        p.electionKiller.quit();
        
        try {
            s3 = NameServerClient.socketFactory.createClientSocket(myAddress,
                    p.electionServer.getPort(), null);
            out3 = new DataOutputStream(new BufferedOutputStream(s3.getOutputStream()));
            out3.writeByte(ELECTION_EXIT);
        } catch (IOException e) {
            // ignore
        } finally {
            closeConnection(null, out3, s3);
        }
    }

    private void handleIbisLeave() throws IOException {
        String poolId = in.readUTF();
        String id = in.readUTF();

        RunInfo p = (RunInfo) pools.get(poolId);

        if (logger.isDebugEnabled()) {
            logger.debug("NameServer: leave from pool " + poolId
                    + " requested by " + id);
        }

        if (p == null) {
            // new run
            if (! silent) {
                logger.error("NameServer: unknown ibis " + id
                        + "/" + poolId + " tried to leave");
            }
        } else {
            IbisInfo iinf = (IbisInfo) p.pool.get(id);

            if (iinf != null) {
                // found it.
                if (logger.isDebugEnabled()) {
                    logger.debug("NameServer: leave from pool " + poolId
                            + " of ibis " + id + " accepted");
                }

                // Also forward the leave to the requester.
                // It is used as an acknowledgement, and
                // the leaver is only allowed to exit when it
                // has received its own leave message.
                synchronized(p) {
                    p.leavers.add(iinf);
                    p.remove(iinf);
                }

                if (! silent && logger.isInfoEnabled()) {
                    logger.info(id + " LEAVES pool " + poolId
                            + " (" + p.pool.size() + " nodes)");
                }

                if (p.pool.size() == 0) {
                    if (! silent && logger.isInfoEnabled()) {
                        logger.info("NameServer: removing pool " + poolId);
                    }

                    // Send leavers before removing this run
                    synchronized(p) {
                        sendLeavers(p);
                    }

                    pools.remove(poolId);
                    killThreads(p);
                }
            } else {
                if (! silent) {
                    logger.error("NameServer: unknown ibis " + id
                        + "/" + poolId + " tried to leave");
                }
            }
        }

        if (! silent && logger.isInfoEnabled()) {
            logger.info("NameServer: confirming LEAVE " + id);
        }
        out.writeByte(0);
        out.flush();
    }

    private void handleIbisMustLeave() throws IOException {
        String poolId = in.readUTF();
        RunInfo p = (RunInfo) pools.get(poolId);
        int count = in.readInt();
        String[] ids = new String[count];
        IbisInfo[] iinf = new IbisInfo[count];

        for (int i = 0; i < count; i++) {
            ids[i] = in.readUTF();
        }

        if (p == null) {
            if (! silent) {
                logger.error("NameServer: unknown pool " + poolId);
            }
            return;
        }

        int found = 0;

        synchronized(p) {
            for (int i = 0; i < count; i++) {
                IbisInfo info = (IbisInfo) p.pool.get(ids[i]);
                if (info != null) {
                    found++;
                    iinf[i] = info;
                }
            }

            // Obtain elements to send to first. The forward() method
            // may wait (and loose the lock).
            IbisInfo[] elts = p.instances();
            for (int i = 0; i < elts.length; i++) {
                IbisInfo ipp = elts[i];
                if (ipp.needsUpcalls) {
                    forward(IBIS_MUSTLEAVE, p, ipp, iinf, 0);
                }
            }

            while (p.forwarders != 0) {
                try {
                    p.wait();
                } catch(Exception ex) {
                    // ignored
                }
            }
        }

        out.writeByte(0);
        out.flush();
    }

    boolean stop = false;

    public class RequestHandler extends Thread {
        LinkedList jobs = new LinkedList();
        int maxSize;

        public RequestHandler(int maxSize) {
            this.maxSize = maxSize;
        }

        public synchronized void addJob(Socket s) {
            while (jobs.size() > maxSize) {
                try {
                    wait();
                } catch(Exception e) {
                    // ignored
                }
            }
            if (jobs.size() == 0) {
                notifyAll();
            }
            jobs.addLast(s);
        }

        public void run() {
            for (;;) {
                Socket s;
                synchronized(this) {
                    while (! stop && jobs.size() == 0) {
                        try {
                            this.wait();
                        } catch(Exception e) {
                        }
                    }
                    if (jobs.size() == 0) {
                        return;
                    }
                    if (jobs.size() >= maxSize) {
                        notifyAll();
                    }
                    s = (Socket) jobs.remove(0);
                }

                handleRequest(s);
            }
        }
    }

    public void run() {
        Socket s;

        RequestHandler reqHandler = new RequestHandler(256);
        reqHandler.start();
        ThreadPool.createNew(new Closer(silent), "Closer");

        while (!stop) {
            try {
                if (! silent && logger.isInfoEnabled()) {
                    logger.info("NameServer: accepting incoming connections... ");
                }

                s = serverSocket.accept();

                if (logger.isDebugEnabled()) {
                    logger.debug("NameServer: incoming connection from "
                            + s.toString());
                }
            } catch (Throwable e) {
                if (! silent) {
                    logger.error("NameServer got an error", e);
                }
                
                try {
                	Thread.sleep(1000);
                } catch (Exception x) {
                	//ignore
                }
                continue;
            }
            // reqHandler.addJob(s);
            handleRequest(s);
            // No separate reqHandler thread, because "stop" is not dealt
            // with correctly, then. Instead, TODO, make this whole loop
            // multithreaded.
        }
        try {
            serverSocket.close();
        } catch (Exception e) {
            throw new RuntimeException("NameServer got an error", e);
        }

        if (controlHub != null) {
            controlHub.waitForCount(1);
        }

        if (! silent && logger.isInfoEnabled()) {
            logger.info("NameServer: exit");
        }
    }

    private void handleSeqno() throws IOException {
        String name = in.readUTF();

        long l = seq.getSeqno(name);
        out.writeLong(l);
        out.flush();
    }

    public void handleRequest(Socket s) {
        int opcode = -1;
        out = null;
        in = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        long startTime = System.currentTimeMillis();

        try {

            in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(baos));

            opcode = in.readByte();

            logger.debug("NameServer got opcode: " + opcode);
            
            switch (opcode) {
            case SEQNO:
                handleSeqno();
                break;
            case (IBIS_ISALIVE):
            case (IBIS_DEAD):
                logger.debug("NameServer handling opcode IBIS_ISALIVE/IBIS_DEAD");
                handleIbisIsalive(opcode == IBIS_DEAD);
                break;
            case (IBIS_JOIN):
                handleIbisJoin(startTime);
                break;
            case (IBIS_MUSTLEAVE):
                handleIbisMustLeave();
                break;
            case (IBIS_LEAVE):
                handleIbisLeave();
                if (singleRun && pools.size() == 0) {
                    synchronized(this) {
                        if (joined) {
                            stop = true;
                        }
                    }
                    // ignore invalid leave req.
                }
                break;
            case (IBIS_CHECK):
                handleCheck();
                break;
            case (IBIS_CHECKALL):
                handleCheckAll();
                break;
            default:
                if (! silent) {
                    logger.error("NameServer got an illegal opcode: " + opcode);
                }
            }

        } catch (Exception e1) {
            if (! silent) {
                logger.error("Got an exception in NameServer.run", e1);
            }
        } finally {
            Closer.addJob(new CloseJob(in, out, baos, s, opcode, startTime));
        }
    }
    
    public int port() {
        return serverSocket.getLocalPort();
    }

    public static synchronized NameServer createNameServer(boolean singleRun,
            boolean retry, boolean poolserver, boolean controlhub,
            boolean silent) {
        NameServer ns = null;
        while (true) {
            try {
                ns = new NameServer(singleRun, poolserver, controlhub, silent);
                break;
            } catch (Throwable e) {
                if (retry) {
                    e.printStackTrace();
                    if (! silent) {
                        logger.warn("Nameserver: could not create server "
                                + "socket, retry in 1 second, cause = " + e);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (Exception ee) { /* do nothing */
                    }
                } else {
                    if (! silent) {
                        logger.warn("Nameserver: could not create server "
                                + "socket, cause = " + e, e);
                    }
                    return null;
                }
            }
        }
        return ns;
    }

    /**
     * Closes a socket and streams that are associated with it. These streams
     * are given as separate parameters, because they may be streams that are
     * built on top of the actual socket streams.
     * 
     * @param in
     *            the inputstream ot be closed
     * @param out
     *            the outputstream to be closed
     * @param s
     *            the socket to be closed
     */
    static void closeConnection(InputStream in, OutputStream out, Socket s) {
        if (out != null) {
            try {
                out.close();
            } catch (Exception e) {
                // ignore
            }
        }

        if (in != null) {
            try {
                in.close();
            } catch (Exception e) {
                // ignore
            }
        }

        if (s != null) {
            try {
                s.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        boolean single = false;
        boolean silent = false;
        boolean control_hub = false;
        boolean pool_server = true;
        boolean retry = true;
        NameServer ns = null;

        for (int i = 0; i < args.length; i++) {
            if (false) { /* do nothing */
            } else if (args[i].equals("-single")) {
                single = true;
            } else if (args[i].equals("-silent")) {
                silent = true;
            } else if (args[i].equals("-retry")) {
                retry = true;
            } else if (args[i].equals("-no-retry")) {
                retry = false;
            } else if (args[i].equals("-controlhub")) {
                control_hub = true;
            } else if (args[i].equals("-no-controlhub")) {
                control_hub = false;
            } else if (args[i].equals("-poolserver")) {
                pool_server = true;
            } else if (args[i].equals("-no-poolserver")) {
                pool_server = false;
            } else if (args[i].equals("-verbose") || args[i].equals("-v")) {
                if (logger.getEffectiveLevel().isGreaterOrEqual(org.apache.log4j.Level.INFO)) {
                    logger.setLevel(org.apache.log4j.Level.INFO);
                }
            } else {
                if (! silent) {
                    logger.fatal("No such option: " + args[i]);
                }
                System.exit(1);
            }
        }

        if (!single) {
            Properties p = System.getProperties();
            String singleS = p.getProperty(NSProps.s_single);

            single = (singleS != null && singleS.equals("true"));
        }

        ns = createNameServer(single, retry, pool_server, control_hub, silent);

        try {
            if (ns == null) {
                if (! silent) {
                    logger.error("No nameserver created");
                }
            } else {
                ns.run();
            }
            System.exit(0);
        } catch (Throwable t) {
            if (! silent) {
                logger.error("Nameserver got an exception", t);
            }
        }
    }
}
