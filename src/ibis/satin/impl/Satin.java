package ibis.satin.impl;

import ibis.ipl.Ibis;
import ibis.ipl.IbisError;
import ibis.ipl.IbisException;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.ReceivePortIdentifier;
import ibis.ipl.Registry;
import ibis.ipl.ResizeHandler;
import ibis.ipl.SendPortConnectUpcall;
import ibis.ipl.StaticProperties;
import ibis.util.IPUtils;
import ibis.util.PoolInfo;

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
public final class Satin
	extends APIMethods
	implements ResizeHandler, ReceivePortConnectUpcall, SendPortConnectUpcall {

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

	StaticProperties ibisProperties = createIbisProperties(requestedProperties);

	PoolInfo pool = null;
	int poolSize = 0; /* Only used with closed world. */

	if (COMM_DEBUG) {
	    out.println("SATIN '" + hostName + "': init ibis");
	}

	try {
	    ibis = Ibis.createIbis(ibisProperties, this);
	} catch (IbisException e) {
	    System.err.println("SATIN '" + hostName
		    + "': Could not start ibis: " + e.getMessage());
	    //			e.printStackTrace();
	    System.exit(1);
	}

	if (closed) {
	    pool = PoolInfo.createPoolInfo();

	    poolSize = pool.size();
	}

	ident = ibis.identifier();

	parentOwner = ident;

	victims = new VictimTable(this); //victimTable accesses ident..

	if (COMM_DEBUG) {
	    out.println("SATIN '" + hostName + "': init ibis DONE, "
		    + "my cluster is '" + ident.cluster() + "'");
	}

	try {
	    Registry r = ibis.registry();

	    if (closed && pool.rank() != 0) {
		masterIdent = r.getElectionResult("satin master");
	    }
	    else {
		masterIdent = r.elect("satin master");
	    }


	    if (masterIdent.equals(ident)) {
		/* I an the master. */
		if (COMM_DEBUG) {
		    out.println("SATIN '" + hostName
			    + "': init ibis: I am the master");
		}
		// System.out.println("master is " + masterIdent);
		master = true;
	    } else {
		if (COMM_DEBUG) {
		    out.println("SATIN '" + hostName
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
			System.out.println("cluster coordinator for cluster "
				+ ident.cluster() + " is " + clusterCoordinatorIdent);
		    }
		}
	    }

	    portType = createSatinPortType(requestedProperties);
	    tuplePortType = createTuplePortType(requestedProperties);
	    barrierPortType = createBarrierPortType(requestedProperties);
	    globalResultTablePortType = createGlobalResultTablePortType(requestedProperties);
	    // do the same for tuple space @@@@
	    // but this needs a seperate receive port... --Rob

	    messageHandler = new MessageHandler(this);

	    if (FAULT_TOLERANCE) {
		if (upcalls) {
		    receivePort = portType.createReceivePort("satin port on "
			    + ident.name(), messageHandler, this);
		} else {
		    if (master) {
			System.err.println("SATIN: using blocking receive");
		    }
		    receivePort = portType.createReceivePort("satin port on "
			    + ident.name(), this);
		}
	    }
	    else {
		if (upcalls) {
		    receivePort = portType.createReceivePort("satin port on "
			    + ident.name(), messageHandler);
		} else {
		    if (master) {
			System.err.println("SATIN: using blocking receive");
		    }
		    receivePort = portType.createReceivePort("satin port on "
			    + ident.name());
		}
	    }

	    if(SUPPORT_TUPLE_MULTICAST) {
		tupleReceivePort = tuplePortType.createReceivePort("satin tuple port on " + ident.name(), new MessageHandler(this));
	    }
	    else tupleReceivePort = receivePort;

	    if (master) {
		barrierReceivePort = barrierPortType
		    .createReceivePort("satin barrier receive port on "
			    + ident.name());
		barrierReceivePort.enableConnections();
	    } else {
		barrierSendPort = barrierPortType
		    .createSendPort("satin barrier send port on "
			    + ident.name());
		ReceivePortIdentifier barrierIdent = lookup("satin barrier receive port on "
			+ masterIdent.name());
		connect(barrierSendPort, barrierIdent);
	    }

	    // Create a multicast port to bcast tuples.
	    // Connections are established in the join upcall.
	    if (SUPPORT_TUPLE_MULTICAST) {
		tuplePort = tuplePortType.createSendPort("satin tuple port on "
			+ ident.name());
	    }
	} catch (Exception e) {
	    System.err.println("SATIN '" + hostName
		    + "': Could not start ibis: " + e);
	    e.printStackTrace();
	    System.exit(1);
	}

	if (COMM_DEBUG) {
	    out.println("SATIN '" + ident.name() + "': init ibis DONE2");
	}

	if (stats && master) {
	    totalStats = new StatsMessage();
	}

	if (master) {
	    if (closed) {
		System.out.println("SATIN '" + hostName
			+ "': running with closed world, " + poolSize
			+ " host(s)");
	    } else {
		System.err.println("SATIN '" + hostName
			+ "': running with open world");
	    }
	}

	if (COMM_DEBUG) {
	    out.println("SATIN '" + hostName + "': algorithm created");
	}

	if (upcalls)
	    receivePort.enableUpcalls();
	receivePort.enableConnections();

	if(SUPPORT_TUPLE_MULTICAST) {
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

	if (COMM_DEBUG) {
	    out.println("SATIN '" + hostName + "': pre barrier");
	}

	if (closed) {
	    synchronized (this) {
		while (victims.size() != poolSize - 1) {
		    try {
			wait();
		    } catch (InterruptedException e) {
			System.err.println("eek: " + e);
			// Ignore.
		    }
		}
		if (COMM_DEBUG) {
		    out.println("SATIN '" + hostName
			    + "': barrier, everybody has joined");
		}

		//				ibis.closeWorld();
	    }

	    barrier();

	}

	if (COMM_DEBUG) {
	    out.println("SATIN '" + hostName + "': post barrier");
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

	if (FAULT_TOLERANCE) {
	    if (FT_NAIVE) {
		System.out.println("naive FT on");
	    } else {
		System.out.println("FT on, GRT " + 
			(GLOBAL_RESULT_TABLE_REPLICATED ? "replicated" : "distributed, message combining ") +
			(GRT_MESSAGE_COMBINING ? "on" : "off"));
	    }
	} else {
	    // System.out.println("FT off");
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
}
