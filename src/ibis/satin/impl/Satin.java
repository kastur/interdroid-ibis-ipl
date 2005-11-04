/* $Id$ */

package ibis.satin.impl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisError;
import ibis.ipl.IbisException;
import ibis.ipl.IbisIdentifier;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.Registry;
import ibis.ipl.ResizeHandler;
import ibis.ipl.SendPortConnectUpcall;
import ibis.ipl.StaticProperties;
import ibis.util.IPUtils;
import ibis.util.messagecombining.MessageCombiner;

import java.net.InetAddress;

/*
 * One important invariant: there is only one thread per machine that spawns
 * work. Another: there is only one lock: the global satin object. invariant:
 * all jobs in the queue and outstandingJobsList have me as owner. invariant:
 * all invocations records in use are in one of these lists: - onStack (being
 * worked on) - OutstandingJobs (stolen) - q (spawned but not yet running)
 * 
 * invariant: When running java code, the parentStamp, parentOwner and parent
 * contain the spawner of the work. (parent may be null when running the root
 * node, or when the spawner is a remote machine).
 * 
 * Satin.spawn gets the satin wrapper. This can be serialized, and run may be
 * called.
 * 
 * When a job is spawned, the RTS put a stamp on it. When a job is stolen the
 * RTS puts an entry in a table. The runRemote method creates a return wrapper
 * containing the return value. The runtime system sends the return value back,
 * together with the original stamp. The victim can do a lookup to find the
 * entry (containing the spawn counter and result pointer) that corresponds with
 * the job.
 */

/**
 * This is the main class of the Ibis Satin implementation. Its public methods
 * are called by code generated by the Satin frontend, not directly by the user.
 */
public final class Satin extends APIMethods implements ResizeHandler,
        ReceivePortConnectUpcall, SendPortConnectUpcall {

    /**
     * Creates a Satin instance and also an Ibis instance to run Satin on. This
     * constructor gets called by the rewritten main() from the application, and
     * the argument array from main is passed to this constructor. Which ibis is
     * chosen depends, a.o., on these arguments.
     */
    public Satin(String[] args) {
        if (this_satin != null) {
            throw new IbisError(
                    "multiple satin instances are currently not supported");
        }
        this_satin = this;

        init(args);
    }

    void init(String[] args) {
        q = (ABORTS || FAULT_TOLERANCE) ? ((DEQueue) new DEQueueNormal(this))
                : ((DEQueue) new DEQueueDijkstra(this));

        outstandingJobs = new IRVector(this);
        resultList = new IRVector(this);
        onStack = new IRStack(this);
        exceptionList = new IRVector(this);

        String hostName = null;

        InetAddress address = IPUtils.getLocalHostAddress();
        hostName = address.getHostName();

        StaticProperties requestedProperties = new StaticProperties();

        // parse the satin commandline options, and the user properties that are
        // set.
        mainArgs = parseArguments(args, requestedProperties, this, hostName);

        StaticProperties ibisProperties
                = createIbisProperties(requestedProperties);

        int poolSize = 0; /* Only used with closed world. */

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + hostName + "': init ibis");
        }

        try {
            ibis = Ibis.createIbis(ibisProperties, this);
        } catch (IbisException e) {
            commLogger.fatal("SATIN '" + hostName
                    + "': Could not start ibis: " + e, e);
	    e.printStackTrace();
            System.exit(1);
        }

        if (closed) {
            poolSize = ibis.totalNrOfIbisesInPool();
        }

        ident = ibis.identifier();

        parentOwner = ident;

        victims = new VictimTable(this); //victimTable accesses ident..

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + hostName + "': init ibis DONE, "
                    + "my cluster is '" + ident.cluster() + "'");
        }

        try {
            Registry r = ibis.registry();
            String canonicalMasterHost = null;

            if (MASTER_HOST != null) {
                try {
                    InetAddress a = InetAddress.getByName(MASTER_HOST);
                    canonicalMasterHost = a.getCanonicalHostName();
                } catch(Exception e) {
                    commLogger.warn("satin.masterhost is set to an unknown "
                            + "name: " + MASTER_HOST);
                    commLogger.warn("continuing with default master election");
                }
            }

            if (canonicalMasterHost == null
                    || canonicalMasterHost.equals(address.getCanonicalHostName())) {
                masterIdent = r.elect("satin master");
            } else {
                masterIdent = r.getElectionResult("satin master");
            }

            if (masterIdent.equals(ident)) {
                /* I an the master. */
                if (commLogger.isInfoEnabled()) {
                    commLogger.info("SATIN '" + hostName
                            + "': init ibis: I am the master");
                }
                master = true;
            } else {
                if (commLogger.isInfoEnabled()) {
                    commLogger.info("SATIN '" + hostName
                            + "': init ibis I am slave");
                }
            }

            if (FAULT_TOLERANCE) {
                if (algorithm instanceof ClusterAwareRandomWorkStealing) {
                    clusterCoordinatorIdent = r.elect("satin "
                            + ident.cluster() + " cluster coordinator");
                    if (clusterCoordinatorIdent.equals(ident)) {
                        /* I am the cluster coordinator */
                        clusterCoordinator = true;
                        if (ftLogger.isInfoEnabled()) {
                            ftLogger.info("cluster coordinator for cluster "
                                    + ident.cluster() + " is "
                                    + clusterCoordinatorIdent);
                        }
                    }
                }
            }

            portType = createSatinPortType(requestedProperties);
            tuplePortType = createTuplePortType(requestedProperties);
            barrierPortType = createBarrierPortType(requestedProperties);
            globalResultTablePortType
                    = createGlobalResultTablePortType(requestedProperties);
            soPortType = createSOPortType(requestedProperties);
            // do the same for tuple space @@@@
            // but this needs a seperate receive port... --Rob

	    MessageHandler messageHandler = new MessageHandler(this);

            if (FAULT_TOLERANCE) {
                if (upcalls) {
                    receivePort = portType.createReceivePort("satin port on "
                            + ident.name(), messageHandler, this);
                } else {
                    if (commLogger.isInfoEnabled() && master) {
                        commLogger.info("SATIN: using blocking receive");
                    }
                    receivePort = portType.createReceivePort("satin port on "
                            + ident.name(), this);
                }
            } else {
                if (upcalls) {
                    receivePort = portType.createReceivePort("satin port on "
                            + ident.name(), messageHandler);
                } else {
                    if (commLogger.isInfoEnabled() && master) {
                        commLogger.info("SATIN: using blocking receive");
                    }
                    receivePort = portType.createReceivePort("satin port on "
                            + ident.name());
                }
            }

            if (SUPPORT_TUPLE_MULTICAST) {
                tupleReceivePort = tuplePortType.createReceivePort(
                        "satin tuple port on " + ident.name(),
                        new MessageHandler(this));
            } else {
                tupleReceivePort = receivePort;
            }

            if (master) {
                barrierReceivePort = barrierPortType.createReceivePort(
                        "satin barrier receive port on " + ident.name());
                barrierReceivePort.enableConnections();
            } else {
                barrierSendPort = barrierPortType.createSendPort(
                        "satin barrier send port on " + ident.name());
                ReceivePortIdentifier barrierIdent = lookup(
                        "satin barrier receive port on " + masterIdent.name());
                connect(barrierSendPort, barrierIdent);
            }

            // Create a multicast port to bcast tuples.
            // Connections are established in the join upcall.
            if (SUPPORT_TUPLE_MULTICAST) {
                tuplePort = tuplePortType.createSendPort("satin tuple port on "
                        + ident.name());
            }
            
	    if (SHARED_OBJECTS) {
		/* SOInvocationHandler soInvocationHandler = */ new SOInvocationHandler(this);
		/*soReceivePort 
		    = soPortType.createReceivePort("satin so receive port on " 
						   + ident.name(),
						   soInvocationHandler);*/

		//Create a multicast port to bcast shared object invocations
		//Connections are established in the join upcall
		soSendPort = soPortType.createSendPort("satin so port on "
						       + ident.name());
		
		if (soInvocationsDelay > 0) {
		    StaticProperties s = new StaticProperties();
		    s.add("serialization", "ibis");  
		    /*soInvocationHandler.setMessageSplitter(new MessageSplitter(s, soReceivePort));*/
		    soMessageCombiner = new MessageCombiner(s, soSendPort);
		}

		/*soReceivePort.enableConnections();
		  soReceivePort.enableUpcalls();*/


	    }

	} catch (Exception e) {
	    commLogger.fatal("SATIN '" + hostName
                    + "': Could not start ibis: " + e);
	    e.printStackTrace();
            System.exit(1);
        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + ident + "': init ibis DONE2");
        }

        if (stats && master) {
            totalStats = new StatsMessage();
        }

        if (commLogger.isInfoEnabled() && master) {
            if (closed) {
                commLogger.info("SATIN '" + hostName
                        + "': running with closed world, " + poolSize
                        + " host(s)");
            } else {
                commLogger.info("SATIN '" + hostName
                        + "': running with open world");
            }
        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + hostName + "': algorithm created");
        }

        if (upcalls) {
            receivePort.enableUpcalls();
        }
        receivePort.enableConnections();

        if (SUPPORT_TUPLE_MULTICAST) {
            tupleReceivePort.enableConnections();
            tupleReceivePort.enableUpcalls();
        }

        if (FAULT_TOLERANCE) {
            globalResultTable = new GlobalResultTable(this);
            abortAndStoreList = new StampVector();
            if (master) {
                getTable = false;
            }
        }

        ibis.enableResizeUpcalls();

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + hostName + "': pre barrier");
        }

        if (closed) {
            synchronized (this) {
                while (victims.size() != poolSize - 1) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
                if (commLogger.isDebugEnabled()) {
                    commLogger.debug("SATIN '" + hostName
                            + "': barrier, everybody has joined");
                }

                // ibis.closeWorld();
            }

            barrier();

        }

        if (commLogger.isDebugEnabled()) {
            commLogger.debug("SATIN '" + hostName + "': post barrier");
        }

        if (killTime > 0) {
            (new KillerThread(killTime)).start();
        }
        if (deleteTime > 0) {
            (new DeleteThread(deleteTime)).start();
        }
        if (deleteClusterTime > 0) {
            (new DeleteClusterThread(deleteClusterTime)).start();
        }
        if (dump) {
            DumpThread dumpThread = new DumpThread(this);
            Runtime.getRuntime().addShutdownHook(dumpThread);
        }

        if (ADD_REPLICA_TIMING) {
            if (!master) {
                addReplicaTimer.start();
            }
        }

        if (FAULT_TOLERANCE && ftLogger.isInfoEnabled()) {
            if (FT_NAIVE) {
                ftLogger.info("naive FT on");
            } else {
                ftLogger.info("FT on, GRT "
                        + (GLOBAL_RESULT_TABLE_REPLICATED ? "replicated"
                                : "distributed, message combining ")
                        + (GRT_MESSAGE_COMBINING ? "on" : "off"));
            }
        } else {
            ftLogger.info("FT off");
        }

        totalTimer.start();
    }

    /**
     * Returns true if this is the instance that is running main().
     * 
     * @return <code>true</code> if this is the instance running main().
     */
    public boolean isMaster() {
        return master;
    }

    /**
     * Returns the argument list that is left when Satin has stripped its
     * arguments from it.
     */
    public String[] getMainArgs() {
        String[] r = new String[mainArgs.length];
        for (int i = 0; i < r.length; i++) {
            r[i] = mainArgs[i];
        }
        return r;
    }

    /**
     * Returns the invocation record that is the "parent" of the current work.
     * May return <code>null</code> when not processing spawned work yet.
     * 
     * @return the "current" invocation record.
     */
    public InvocationRecord getParent() {
        return parent;
    }

    /**
     * Returns the current Satin instance.
     * 
     * @return the current Satin instance.
     */
    public static Satin getSatin() {
        return this_satin;
    }
    
    public static IbisIdentifier getSatinIdent() {
        return this_satin.ident;
    }
}
