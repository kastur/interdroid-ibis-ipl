package ibis.ipl.impl.messagePassing;

import java.io.IOException;

import ibis.ipl.IbisException;

final class ReceivePortIdentifier
	implements ibis.ipl.ReceivePortIdentifier,
		   java.io.Serializable {

    String name;
    String type;
    int cpu;
    int port;
    ibis.ipl.IbisIdentifier ibisIdentifier;
    transient byte[] serialForm;


    ReceivePortIdentifier(String name, String type) {

	synchronized (Ibis.myIbis) {
	    port = Ibis.myIbis.receivePort++;
	}
	cpu = Ibis.myIbis.myCpu;
	this.name = name;
	this.type = type;
	ibisIdentifier = Ibis.myIbis.identifier();
	makeSerialForm();
    }


    private void makeSerialForm() {
	try {
	    serialForm = SerializeBuffer.writeObject(this);
	} catch (IOException e) {
	    throw new Error("Cannot serialize myself", e);
	}
    }


    byte[] getSerialForm() {
	if (serialForm == null) {
	    makeSerialForm();
	}
	return serialForm;
    }


    public boolean equals(Object other) {
	if (other == null) return false;
	if (other == this) return true;

	if (!(other instanceof ReceivePortIdentifier)) {
	    return false;
	}

	ReceivePortIdentifier temp = (ReceivePortIdentifier)other;
	return (cpu == temp.cpu && port == temp.port);
    }


    //gosia
    public int hashCode() {
	return name.hashCode() + type.hashCode() + cpu + port;
    }


    public String name() {
	return name;
    }


    public String type() {
	return type;
    }


    public ibis.ipl.IbisIdentifier ibis() {
	return ibisIdentifier;
    }


    public String toString() {
	return ("(RecPortIdent: name \"" + ( name == null ? "null" : name ) +
		"\" type \"" + ( type == null ? "null" : type ) +
		"\" cpu " + cpu + " port " + port +
		" ibis \"" + ( (ibisIdentifier == null || ibisIdentifier.name() == null) ? "null" : ibisIdentifier.name()) + "\")");
    }

}
