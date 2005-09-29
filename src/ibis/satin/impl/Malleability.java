/* $Id$ */

package ibis.satin.impl;

import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.SendPort;

import java.io.IOException;

public abstract class Malleability extends FaultTolerance {
    private void handleJoin(IbisIdentifier joiner) {
        try {
            ReceivePortIdentifier r = null;
            SendPort s = portType.createSendPort("satin sendport");

            r = lookup("satin port on " + joiner.name());

            if (FAULT_TOLERANCE) {
                if (!connect(s, r, connectTimeout)) {
                    if (commLogger.isDebugEnabled()) {
                        commLogger.debug("SATIN '" + ident
                                + "': unable to connect to " + joiner
                                + ", might have crashed");
                    }
                    return;
                }
            } else {
                connect(s, r);
            }

            synchronized (this) {
                if (FAULT_TOLERANCE && !FT_NAIVE) {
                    globalResultTable.addReplica(joiner);
                }
                victims.add(joiner, s);
                notifyAll();
            }

            if (commLogger.isDebugEnabled()) {
                commLogger.debug("SATIN '" + ident + "': "
                        + joiner + " JOINED");
            }
        } catch (Exception e) {
            System.err.println("SATIN '" + ident
                    + "': got an exception in Satin.join: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public void joined(IbisIdentifier joiner) {

        // System.err.println("SATIN '" + ident + "': '" + joiner
        //         + " is joining");

        if (joiner.name().equals("ControlCentreIbis")) {
            return;
        }

        allIbises.add(joiner);

        if (joiner.equals(ident)) {
            return;
        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + ident + "': '" + joiner
                    + "' from cluster '" + joiner.cluster()
                    + "' is trying to join");
        }
        // if (!victims.contains(joiner)) {
        handleJoin(joiner);

        /*
         * synchronized (this) {
         *     System.err.println("SATIN '" + ident + "': '"
         *             + victims.size() + " hosts joined");
         * }
         */
    }

    public void died(IbisIdentifier corpse) {
        left(corpse);
    }

    public void left(IbisIdentifier leaver) {
        if (leaver.equals(ident)) {
            return;
        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + ident + "': " + leaver
                    + " left");
        }

        Victim v;

        synchronized (this) {
            /*
             * if (FAULT_TOLERANCE && !FT_NAIVE) {
             *     globalResultTable.removeReplica(leaver);
             * }
             */
            if (FAULT_TOLERANCE) {
                /* 
                 * master and cluster coordinators will be reelected
                 * only if their crash was confirmed by the nameserver
                 */
                if (leaver.equals(masterIdent)) {
                    masterHasCrashed = true;
                    gotCrashes = true;
                }
                if (leaver.equals(clusterCoordinatorIdent)) {
                    clusterCoordinatorHasCrashed = true;
                    gotCrashes = true;
                }
            } 
             
            v = victims.remove(leaver);
            notifyAll();

            if (v != null && v.s != null) {
                try {
                    v.s.close();
                } catch (IOException e) {
                    System.err.println("port.close() throws " + e);
                }
            }
        }
    }
}
