package ibis.ipl.impl.tcp;

import java.io.IOException;

import ibis.ipl.PortType;
import ibis.ipl.StaticProperties;
import ibis.ipl.SendPort;
import ibis.ipl.ReceivePort;
import ibis.ipl.Upcall;
import ibis.ipl.IbisException;
import ibis.ipl.IbisError;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.ipl.SendPortConnectUpcall;
import ibis.ipl.Replacer;
import java.util.ArrayList;
import ibis.ipl.ReadMessage;

class TcpPortType implements PortType, Config { 

	StaticProperties p;
	String name;
	TcpIbis ibis;
	
	static final byte SERIALIZATION_SUN = 0;
	static final byte SERIALIZATION_IBIS = 1;

	byte serializationType = SERIALIZATION_SUN;

	private ArrayList receivePorts = new ArrayList();

	TcpPortType(TcpIbis ibis, String name, StaticProperties p) throws IbisException { 
		this.ibis = ibis;
		this.name = name;
		this.p = p;

		String ser = p.find("Serialization");
		if(ser == null) {
			p.add("Serialization", "sun");
			serializationType = SERIALIZATION_SUN;
		} else {
			if (ser.equals("sun")) {
				serializationType = SERIALIZATION_SUN;
//				System.err.println("serializationType = SERIALIZATION_SUN");
			} else if (ser.equals("ibis")) {

//				System.err.println("serializationType = SERIALIZATION_IBIS");
				serializationType = SERIALIZATION_IBIS;
			} else if (ser.equals("manta")) {
				// backwards compatibility ...

//				System.err.println("serializationType = SERIALIZATION_IBIS");
				serializationType = SERIALIZATION_IBIS;
			} else {
				throw new IbisException("Unknown Serialization type " + ser);
			}
		}
	} 

	public String name() { 
		if (name != null) {
			return name;
		}
		return "__anonymous__";
	} 

	private boolean equals(TcpPortType other) {
		return name().equals(other.name()) && ibis.equals(other.ibis);
	} 

	public boolean equals(Object other) {
		if (other == null) return false;
		if (! (other instanceof TcpPortType)) return false;
		return equals((TcpPortType) other);
	}

	public int hashCode() {
	    return name().hashCode() + ibis.hashCode();
	}

	public StaticProperties properties() { 
		return p;
	}

	public SendPort createSendPort() throws IOException {
		return createSendPort(null, null, false, null);
	}

	public SendPort createSendPort(Replacer r) throws IOException {
		return createSendPort(null, r, false, null);
	}

	public SendPort createSendPort(String portname, Replacer r) throws IOException {
		return createSendPort(portname, r, false, null);
	}

	public SendPort createSendPort(String portname) throws IOException {
		return createSendPort(portname, null, false, null);
	}

	public SendPort createSendPort(boolean connectionAdministration) throws IOException {
		return createSendPort(null, null, connectionAdministration, null);
	}

	public SendPort createSendPort(Replacer r, boolean connectionAdministration) throws IOException {
		return createSendPort(null, r, connectionAdministration, null);
	}

	public SendPort createSendPort(String portname, Replacer r, boolean connectionAdministration) throws IOException {
		return createSendPort(portname, r, connectionAdministration, null);
	}

	public SendPort createSendPort(String portname, boolean connectionAdministration) throws IOException {
		return createSendPort(portname, null, connectionAdministration, null);
	}

	public SendPort createSendPort(String name, SendPortConnectUpcall cU) throws IOException {
		return createSendPort(name, null, true, cU);
	}

	public SendPort createSendPort(Replacer r, SendPortConnectUpcall cU) throws IOException {
		return createSendPort(null, r, true, cU);
	}

	public SendPort createSendPort(String name, Replacer r, 
				       SendPortConnectUpcall cU) throws IOException {
		return createSendPort(name, r, true, cU);
	}

	private SendPort createSendPort(String name, Replacer r, 
				       boolean connectionAdministration, SendPortConnectUpcall cU) throws IOException {
		return new TcpSendPort(ibis, this, name, r, connectionAdministration, cU);
	}

	public ReceivePort createReceivePort(String name) throws IOException {
	    return createReceivePort(name, null, false, null);
	}

	public ReceivePort createReceivePort(String name, Upcall u)  throws IOException { 
	    return createReceivePort(name, u, false, null);
	}

	public ReceivePort createReceivePort(String name, boolean connectionAdministration) throws IOException {
	    return createReceivePort(name, null, connectionAdministration, null);
	}

	public ReceivePort createReceivePort(String name, Upcall u, boolean connectionAdministration)  throws IOException { 
		return createReceivePort(name, u, connectionAdministration, null);
	}

	public ReceivePort createReceivePort(String name, ReceivePortConnectUpcall cU) throws IOException {
	    return createReceivePort(name, null, true, cU);
	}

	public ReceivePort createReceivePort(String name, Upcall u, ReceivePortConnectUpcall cU)  throws IOException { 
		return createReceivePort(name, u, true, cU);
	}

	private ReceivePort createReceivePort(String name, Upcall u, 
					     boolean connectionAdministration, ReceivePortConnectUpcall cU)  throws IOException { 
		TcpReceivePort p = new TcpReceivePort(ibis, this, name, u, connectionAdministration, cU);

		if(DEBUG) {
			System.out.println(ibis.name() + ": Receiveport created name = '" + name() + "'");
		}

		ibis.tcpReceivePortNameServerClient.bind(name, p);

		if(DEBUG) {
			System.out.println(ibis.name() + ": Receiveport bound in registry, name = '" + name() + "'");
		}

		synchronized(this) {
			receivePorts.add(p);
		}

		return p;
	}

	void freeReceivePort(String name) throws IOException {
		synchronized(this) {
			for(int i=0; i<receivePorts.size(); i++) {
				ReceivePort rp = (ReceivePort) receivePorts.get(i);
				if(rp.equals(name)) {
					receivePorts.remove(i);
					ibis.tcpReceivePortNameServerClient.unbind(name);
					return;
				}
			}

			throw new IbisError("Internal error in TcpPortType: could not fine port with name: " + name);
		}
	}

	public String toString() {
		return ("(TcpPortType: name = " + name() + ")");
	}

	/* Only used interally to implement ibis.poll.
	   Poll all receiveports. */
	ReadMessage poll() throws IOException {
		Object[] a;

		synchronized(this) {
			a = receivePorts.toArray();
		}
		
		for(int i=0; i<a.length; i++) {
			TcpReceivePort r = (TcpReceivePort) a[i];
			ReadMessage m = r.poll(); // all exceptions are just forwared
			if(m != null) return m;
		}

		return null;
	}
}
