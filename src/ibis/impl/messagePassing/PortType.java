package ibis.ipl.impl.messagePassing;

import java.io.IOException;

import ibis.ipl.IbisException;
import ibis.ipl.SendPortConnectUpcall;
import ibis.ipl.ReceivePortConnectUpcall;
import ibis.io.Replacer;

public class PortType implements ibis.ipl.PortType {

    ibis.ipl.StaticProperties p;
    String name;
    Ibis myIbis;

    public static final byte SERIALIZATION_NONE = 0;
    public static final byte SERIALIZATION_SUN = 1;
    public static final byte SERIALIZATION_IBIS = 2;

    public byte serializationType = SERIALIZATION_SUN;

    PortType(Ibis myIbis,
	     String name,
	     ibis.ipl.StaticProperties p) throws IbisException {
	this.myIbis = myIbis;
	this.name = name;
	this.p = p;

	String ser = p.find("Serialization");
	if (ser == null) {
	    p.add("Serialization", "sun");
	    serializationType = SERIALIZATION_SUN;
	} else if (ser.equals("none")) {
	    serializationType = SERIALIZATION_NONE;
	} else if (ser.equals("sun")) {
	    serializationType = SERIALIZATION_SUN;
	} else if (ser.equals("manta")) {
	    // For backwards compatibility ...
	    serializationType = SERIALIZATION_IBIS;
	} else if (ser.equals("ibis")) {
	    serializationType = SERIALIZATION_IBIS;
	} else {
	    throw new IbisException("Unknown Serialization type " + ser);
	}
    }

    public String name() {
	return name;
    }

    public boolean equals(Object other) {
	if (other == null) {
	    return false;
	}
	if (!(other instanceof PortType))
	    return false;

	PortType temp = (PortType)other;

	return name.equals(temp.name);
    }

    public ibis.ipl.StaticProperties properties() {
	return p;
    }


    public ibis.ipl.SendPort createSendPort() throws IOException {
	return createSendPort("noname", null, null);
    }

    public ibis.ipl.SendPort createSendPort(String portname) throws IOException {
	return createSendPort(portname, null, null);
    }

    public ibis.ipl.SendPort createSendPort(String portname, SendPortConnectUpcall cU) throws IOException {
	return createSendPort(portname, null, cU);
    }

    public ibis.ipl.SendPort createSendPort(Replacer r) throws IOException {
	return createSendPort("noname", r, null);
    }

    public ibis.ipl.SendPort createSendPort(Replacer r, SendPortConnectUpcall cU) throws IOException {
	return createSendPort("noname", r, cU);
    }

    public ibis.ipl.SendPort createSendPort(String portname, Replacer r) throws IOException {
	return createSendPort(portname, r, null);
    }

    public ibis.ipl.SendPort createSendPort(String portname, Replacer r, SendPortConnectUpcall cU) throws IOException {

	if (cU != null) {
	    System.err.println(this + ": createSendPort with ConnectUpcall. UNIMPLEMENTED");
	}

	SendPort s;

	switch (serializationType) {
        case PortType.SERIALIZATION_NONE:
// System.err.println("MSG: NO SER, name = " + portname);
	    s = new SendPort(this, portname, new OutputConnection());
	    break;

	case PortType.SERIALIZATION_SUN:
// System.err.println("MSG: SUN SER, name = " + portname);
	    s = new SerializeSendPort(this, portname, new OutputConnection(), r);
	    break;

	case PortType.SERIALIZATION_IBIS:
// System.err.println("MSG: IBIS SER, name = " + portname);
	    s = new IbisSendPort(this, portname, new OutputConnection(), r);
	    break;

	default:
	    throw new Error("No such serialization type " + serializationType);
	}

	if (Ibis.DEBUG) {
	    System.out.println(Ibis.myIbis.name() + ": Sendport " + portname +
				" created of of type '" + this + "'" +
				" cpu " + s.ident.cpu +
				" port " + s.ident.port);
	}
	
	return s;
    }


    public ibis.ipl.ReceivePort createReceivePort(String name)
	    throws IOException {
	return createReceivePort(name, null, null);
    }

    public ibis.ipl.ReceivePort createReceivePort(String name, ibis.ipl.Upcall u)
	    throws IOException {
	return createReceivePort(name, u, null);
    }

    public ibis.ipl.ReceivePort createReceivePort(String name, ibis.ipl.ReceivePortConnectUpcall cU)
	    throws IOException {
	return createReceivePort(name, null, cU);
    }

    public ibis.ipl.ReceivePort createReceivePort(
					String name,
					ibis.ipl.Upcall u,
					ibis.ipl.ReceivePortConnectUpcall cU)
	    throws IOException {

	ReceivePort p = new ReceivePort(this, name, u, cU);

	if (Ibis.DEBUG) {
	    System.out.println(myIbis.name() + ": Receiveport created of type '" +
			       this.name + "', name = '" + name + "'" +
			       " cpu " + p.ident.cpu + " port " + p.ident.port);
	}

	if (Ibis.DEBUG) {
	    System.out.println(myIbis.name() +
			       ": Receiveport bound in registry, type = '" +
			       this.name + "', name = '" + name + "'");
	}

	return p;
    }

    void freeReceivePort(String name) throws IOException {
	((Registry)myIbis.registry()).unbind(name);
    }

    public String toString() {
	return ("(PortType: name = " + name + ")");
    }

}
