package ibis.rmi;

import ibis.ipl.*;
import ibis.rmi.server.*;

import java.util.Hashtable;
import java.util.Properties;
import java.util.ArrayList;

import java.net.InetAddress;

public final class RTS {

	public final static boolean DEBUG = false; // true;

	//keys - impl objects, values - skeletons for those objects
	protected static Hashtable skeletons;
	protected static Hashtable stubs;

	protected static String hostname;
	protected static PortType portType;

	private static Ibis ibis;
        private static IbisIdentifier localID;
	private static ibis.ipl.Registry ibisRegistry;

	private static ThreadLocal clientHost;

	static {
                try {
                        skeletons = new Hashtable();
                        stubs = new Hashtable();

			hostname = InetAddress.getLocalHost().getHostName();
			InetAddress adres = InetAddress.getByName(hostname);
			adres = InetAddress.getByName(adres.getHostAddress());
			hostname = adres.getHostName();

                        if (DEBUG) {
                                System.out.println(hostname + ": init RMI RTS");
				System.out.println(hostname + ": creating ibis");
                        }

			ibis = Ibis.createIbis(null);

			Properties p = System.getProperties();
			String ibis_name = p.getProperty("ibis.name");

                        StaticProperties s = new StaticProperties();
			String ibis_serialization = p.getProperty("ibis.serialization");
			if (ibis_serialization != null) {
			    System.out.println("Setting Serialization to " + ibis_serialization);
			    s.add("Serialization", ibis_serialization);
			} else {
			    System.out.println("Setting Serialization to ibis");
			    s.add("Serialization", "ibis");
			}

			if (ibis_name != null && ibis_name.startsWith("net.")) {
			    String driver = ibis_name.substring("net.".length());
			    String path = "/";
			    while (true) {
				int dot = driver.indexOf('.');
				int end = dot;
				if (end == -1) {
				    end = driver.length();
				}
				String top = driver.substring(0, end);
// System.err.println("Now register static property \"" + (path + ":Driver") + "\" as \"" + top + "\"");
				s.add(path + ":Driver", top);
				if (dot == -1) {
				    break;
				}
				if (path.equals("/")) {
				    path = path + top;
				} else {
				    path = path + "/" + top;
				}
				driver = driver.substring(dot + 1);
			    }

			}

			if (DEBUG) {
			    System.out.println(hostname + ": ibis created");
			}

                        localID      = ibis.identifier();
                        ibisRegistry = ibis.registry();

                        portType = ibis.createPortType("RMI", s);

			clientHost = new ThreadLocal();

                        if(DEBUG) {
                                System.out.println(hostname + ": RMI RTS init done");
                        }

                } catch (Exception e) {
                        System.err.println(hostname + ": Could not init RMI RTS " + e);
                        e.printStackTrace();
                        System.exit(1);
                }

		/****
		 * This is only supported in SDK 1.4 and upwards. Comment out
		 * if you run an older SDK.
		 */
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
			ibis.end();
			// System.err.println("Ended Ibis");
		    }
		});
		/* End of 1.4-specific code */
        }


	public static synchronized RemoteStub exportObject(Remote obj)
	    throws Exception
	{
	    Skeleton skel;
	    Stub stub;
	    Class c = obj.getClass();
	    ReceivePort rec;


	    if ((skel = (Skeleton)skeletons.get(obj)) == null ) {

		//create a skeleton

	        if (DEBUG) {
		    System.out.println(hostname + ": creating skeleton of type rmi_skeleton_" + c.getName());
	        }

	        Class skel_c = Class.forName("rmi_skeleton_" + c.getName());
		skel = (Skeleton) skel_c.newInstance();

		String skel_rec_port_name = "//" + hostname + "/rmi_skeleton" + (new java.rmi.server.UID()).toString();
		rec = portType.createReceivePort(skel_rec_port_name, skel);

		skel.init(rec, obj);

		rec.enableConnections();
		rec.enableUpcalls();

		skeletons.put(obj, skel);
	    } else {
		throw new ExportException("object already exported");
	    }

	    //create a stub
	    Class stub_c = Class.forName("rmi_stub_" + c.getName());
	    stub = (Stub) stub_c.newInstance();

	    SendPort s = portType.createSendPort(new RMIReplacer());
	    s.connect(rec.identifier());


	    String stub_rec_port_name = "//" + hostname + "/rmi_stub" + (new java.rmi.server.UID()).toString();
	    ReceivePort r = portType.createReceivePort(stub_rec_port_name);
	    r.enableConnections();

	    if (DEBUG) {
		System.out.println(hostname + ": Created receiveport for stub " + stub_rec_port_name + " -> id = " + r.identifier());
	    }

	    WriteMessage wm = s.newMessage();
	    wm.writeInt(-1);
	    wm.writeInt(0);
	    wm.writeObject(r.identifier());
	    wm.send();
// System.err.println(hostname + ": sent ID for receiveport stub");
	    wm.finish();
// System.err.println(hostname + ": finished ID for receiveport stub");

	    ReadMessage rm = r.receive();
// System.err.println(hostname + ": received reply for receiveport stub");
	    int stubID = rm.readInt();
	    String stubType = (String) rm.readObject();
	    rm.finish();
// System.err.println(hostname + ": finished reply for receiveport stub");
	    //ignore the stubType information -- stub already created


	    stub.init(s, r, stubID, rec.identifier());

	    if (DEBUG) {
		System.out.println(hostname + ": Created stub of type rmi_stub_" + c.getName());
	    }

	    stubs.put(obj, stub);

	    return (RemoteStub) stub;
	}

	public static Object getStub(Object o) {
	    return stubs.get(o);
	}



	public static synchronized void bind(String url, Remote o)
	    throws AlreadyBoundException, IbisException, IbisIOException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
	//	String url = "//" + RTS.hostname + "/" + name;

		if (DEBUG) {
			System.out.println(hostname + ": Trying to bind object to " + url);
		}

		/*if (skeletons.containsKey(url)) {
			return -1;
		}*/

		ReceivePortIdentifier dest = null;

		try {
		    dest = ibisRegistry.lookup(url, 1);
		} catch(IbisIOException e) {
		}

		if (dest != null) {
			throw new AlreadyBoundException(url + " already bound");
		}

		Skeleton skel = (Skeleton) skeletons.get(o);
		if (skel == null) {
//		    throw new RemoteException("object not exported");
		    //or just export it???

		    Class c = o.getClass();
	    	    if (DEBUG) {
			System.out.println(hostname + ": creating skeleton of type rmi_skeleton_" + c.getName());
	    	    }

	    	    Class skel_c = Class.forName("rmi_skeleton_" + c.getName());
		    skel = (Skeleton) skel_c.newInstance();

		    String skel_rec_port_name = "//" + hostname + "/rmi_skeleton" + (new java.rmi.server.UID()).toString();
		    ReceivePort rec = portType.createReceivePort(skel_rec_port_name, skel);

		    skel.init(rec, o);

		    rec.enableConnections();
		    rec.enableUpcalls();

		    skeletons.put(o, skel);

		}

		//new method
		ibisRegistry.bind(url, skel.receivePort().identifier());

		if (DEBUG) {
		    System.out.println(hostname + ": Bound to object " + url);
		}

	}

	public static synchronized void rebind(String url, Remote o)
	    throws IbisException, IbisIOException, ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		if (DEBUG) {
			System.out.println(hostname + ": Trying to bind object to " + url);
		}

		Skeleton skel = (Skeleton) skeletons.get(o);
		if (skel == null) {
//		    throw new RemoteException("object not exported");
		    //or just export it???

		    Class c = o.getClass();
	    	    if (DEBUG) {
			System.out.println(hostname + ": creating skeleton of type rmi_skeleton_" + c.getName());
	    	    }

	    	    Class skel_c = Class.forName("rmi_skeleton_" + c.getName());
		    skel = (Skeleton) skel_c.newInstance();

		    String skel_rec_port_name = "//" + hostname + "/rmi_skeleton" + (new java.rmi.server.UID()).toString();
		    ReceivePort rec = portType.createReceivePort(skel_rec_port_name, skel);

		    skel.init(rec, o);

		    rec.enableConnections();
		    rec.enableUpcalls();

		    skeletons.put(o, skel);

		}

		//new method
		ibisRegistry.rebind(url, skel.receivePort().identifier());
	}

	public static void unbind(String url)
	    throws NotBoundException, IbisException, IbisIOException
	{
		if (DEBUG) {
			System.out.println(hostname + ": Trying to unbind object from " + url);
		}

		ReceivePortIdentifier dest = null;

		try {
		    dest = ibisRegistry.lookup(url, 1);
		} catch (IbisIOException e) {
		}

		if (dest == null) {
			throw new NotBoundException(url + " not bound");
		}

		//new method
		ibisRegistry.unbind(url);
	}


	public static synchronized Remote lookup(String url)  throws NotBoundException, IbisException, IbisIOException {

		Stub result;
		SendPort s = null;

		if (DEBUG) {
			System.out.println(hostname + ": Trying to lookup object " + url);
		}

		ReceivePortIdentifier dest = null;

		try {
		    dest = ibisRegistry.lookup(url, 1);
// System.err.println("ibisRegistry.lookup(" + url + ". 1) is " + dest);
		} catch(IbisIOException e) {
// System.err.println("ibisRegistry.lookup(" + url + ". 1) throws " + e);
		}

		if (dest == null) {
			throw new NotBoundException(url + " not bound");
		}

		if (DEBUG) {
			System.out.println(hostname + ": Found skeleton " + url + " connecting");
		}

		s = portType.createSendPort(new RMIReplacer());

		if (DEBUG) {
			System.out.println(hostname + ": Created sendport for stub");
		}
		s.connect(dest);
		if (DEBUG) {
			System.out.println(hostname + ": Connected the sendport of the stub to the receive port of the skeleton");
		}

		ReceivePort r = portType.createReceivePort("//" + hostname + "/rmi_stub" + (new java.rmi.server.UID()).toString());
		r.enableConnections();

		if (DEBUG) {
			System.out.println(hostname + ": Created receiveport for stub  -> id = " + r.identifier());
		}

		WriteMessage wm = s.newMessage();

		if (DEBUG) {
			System.out.println(hostname + ": Created new WriteMessage");
		}

		wm.writeInt(-1);
		wm.writeInt(0);
		wm.writeObject(r.identifier());
		wm.send();
		wm.finish();

		if (DEBUG) {
			System.out.println(hostname + ": Sent new WriteMessage");
		}

		ReadMessage rm = r.receive();

		if (DEBUG) {
			System.out.println(hostname + ": Received readMessage");
		}

		int stubID = rm.readInt();
		String stubType = (String) rm.readObject();
		rm.finish();

		try {
			result = (Stub) Class.forName(stubType).newInstance();
		} catch (Exception e) {
			s.free();
			r.free();
			throw new IbisException("stub class " + stubType + " not found" + e);
		}

		result.init(s, r, stubID, dest);

		if (DEBUG) {
			System.out.println(hostname + ": Found object " + url);
		}
		return (Remote) result;
	}

	public static String[] list(String url) throws IbisIOException
	{
		int urlLength = url.length();
		String[] names = ibisRegistry.list(url /*+ ".*"*/);
		for (int i=0; i<names.length; i++) {
		    names[i] = names[i].substring(urlLength);
		}
		return names;
	}

	public static SendPort createSendPort()
	    throws IbisIOException
	{
	    return portType.createSendPort(new RMIReplacer());
	}

	public static ReceivePort createReceivePort()
	    throws IbisIOException
	{
	    return  portType.createReceivePort("//" + hostname + "/rmi_stub" + (new java.rmi.server.UID()).toString());
	}

	public static void createRegistry(int port) throws RemoteException
	{
	    String url = "registry://" + hostname + ":" + port;
	    try {
		portType.createReceivePort(url);
	    } catch (IbisIOException e) {
		throw new RemoteException("there already is a registry running on port " + port);
	    }
	}


	public static String getHostname() {
	    return hostname;
	}

	public static void setClientHost(java.net.InetAddress host) {
	    clientHost.set(host);
	}

	public static String getClientHost() {
	    Object o = clientHost.get();
	    if (o == null) return "0.0.0.0";
	    java.net.InetAddress a = (java.net.InetAddress) o;
	    return a.getHostAddress();
	}

}
