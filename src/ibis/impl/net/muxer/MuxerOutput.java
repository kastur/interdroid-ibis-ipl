package ibis.ipl.impl.net.muxer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import ibis.ipl.impl.net.NetConvert;
import ibis.ipl.impl.net.NetPortType;
import ibis.ipl.impl.net.NetDriver;
import ibis.ipl.impl.net.NetIO;
import ibis.ipl.impl.net.NetBufferedOutput;
import ibis.ipl.impl.net.NetIbisException;

public abstract class MuxerOutput extends NetBufferedOutput {

    public abstract void disconnect(MuxerKey key)
	    throws NetIbisException;


    /**
     * Constructor. Call this from all subclass constructors.
     */
    protected MuxerOutput(NetPortType portType,
			  NetDriver   driver,
			  String      context) {
	super(portType, driver, context);
	headerLength = NetConvert.INT_SIZE;
	mtu          =    0;
    }


    public MuxerKey getKey(Integer spn) throws NetIbisException {
	MuxerKey key = locateKey(spn.intValue());
	return key;
    }


    private MuxerKeyHash keyHash = new MuxerKeyHash();

    protected void registerKey(MuxerKey q) {
	keyHash.registerKey(q);
    }

    protected MuxerKey locateKey(int n) {
	return keyHash.locateKey(n);
    }

    protected void releaseKey(MuxerKey key) throws NetIbisException {
	keyHash.releaseKey(key);
    }

}
