package ibis.ipl.impl.messagePassing;

import ibis.ipl.IbisIOException;

import java.util.Hashtable;

final class ReceivePortNameServer implements
    ReceivePortNameServerProtocol {

    private Hashtable ports;

    protected ReceivePortNameServer() throws IbisIOException {
	ports = new Hashtable();
    }

    native void bind_reply(int ret, int tag, int client);

    /* Called from native */
    private void bind(byte[] serialForm, int tag, int client)
	    throws IbisIOException {
	// Ibis.myIbis.checkLockOwned();
	ReceivePortIdentifier ri = null;
	try {
	    ri = (ReceivePortIdentifier)SerializeBuffer.readObject(serialForm);
	} catch (IbisIOException e) {
	    bind_reply(PORT_REFUSED, tag, client);
	    return;
	}

	if (ReceivePortNameServerProtocol.DEBUG) {
	    System.err.println(Thread.currentThread() + "" + this + ": bind receive port " + ri + " client " + client);
	}

	ReceivePortIdentifier storedId;

	/* Check wheter the name is in use.*/
	storedId = (ReceivePortIdentifier)ports.get(ri.name);

	if (storedId != null) {
	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println(Thread.currentThread() + "Don't bind existing port name \"" + ri.name + "\"");
	    }
	    bind_reply(PORT_REFUSED, tag, client);
	} else {
	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println(Thread.currentThread() + "Bound new port name \"" + ri.name + "\"" + " ibis " + ri.ibis().name());
	    }
	    bind_reply(PORT_ACCEPTED, tag, client);
	    ports.put(ri.name, ri);
	}
    }

    native void lookup_reply(int ret, int tag, int client, byte[] rcvePortId);

    /* Called from native */
    private void lookup(String name, int tag, int client) throws ClassNotFoundException {
	// Ibis.myIbis.checkLockOwned();

	ReceivePortIdentifier storedId;

	storedId = (ReceivePortIdentifier)ports.get(name);

	if (storedId != null) {
	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println(Thread.currentThread() + "Give this client his ReceivePort \"" + name + "\"; cpu " + storedId.cpu + " port " + storedId.port);
	    }
	    try {
		byte[] sf = storedId.getSerialForm();
		lookup_reply(PORT_KNOWN, tag, client, sf);
	    } catch (IbisIOException e) {
		lookup_reply(PORT_UNKNOWN, tag, client, null);
	    }
	} else {
	    if (ReceivePortNameServerProtocol.DEBUG) {
		System.err.println(Thread.currentThread() + "Cannot give this client his ReceivePort \"" + name + "\"");
	    }
	    lookup_reply(PORT_UNKNOWN, tag, client, null);
	}
    }

    /* Called from native */
    private void unbind(String name) throws ClassNotFoundException {
	// Ibis.myIbis.checkLockOwned();
	ports.remove(name);
    }

}
