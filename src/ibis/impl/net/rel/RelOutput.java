package ibis.ipl.impl.net.rel;

import ibis.ipl.impl.net.__;
import ibis.ipl.impl.net.NetDriver;
import ibis.ipl.impl.net.NetBufferedOutput;
import ibis.ipl.impl.net.NetIO;
import ibis.ipl.impl.net.NetOutput;
import ibis.ipl.impl.net.NetSendBuffer;

import ibis.ipl.IbisIOException;
import ibis.ipl.StaticProperties;

import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;

import java.io.ObjectInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import java.util.Hashtable;

/**
 * The REL output implementation.
 */
public class RelOutput extends NetBufferedOutput {

	/**
	 * The driver used for the 'real' output.
	 */
	private NetDriver subDriver = null;

	/**
	 * The 'real' output.
	 */
	private NetOutput subOutput = null;

	/**
	 * Constructor.
	 *
	 * @param sp the properties of the output's 
	 * {@link ibis.ipl.impl.net.NetSendPort NetSendPort}.
	 * @param driver the REL driver instance.
	 * @param output the controlling output.
	 */
	RelOutput(StaticProperties sp,
		  NetDriver   	   driver,
		  NetIO   	   up)
		throws IbisIOException {
		super(sp, driver, up);


		// The length of the header expressed in bytes
		headerLength = 0;
	}

	/*
	 * Sets up an outgoing REL connection.
	 *
	 * @param rpn {@inheritDoc}
	 * @param is {@inheritDoc}
	 * @param os {@inheritDoc}
	 */
	public void setupConnection(Integer            rpn,
				    ObjectInputStream  is,
				    ObjectOutputStream os)
		throws IbisIOException {
		NetOutput subOutput = this.subOutput;
		
		if (subOutput == null) {
			if (subDriver == null) {
				subDriver = driver.getIbis().getDriver(getProperty("Driver"));
			}

			subOutput = subDriver.newOutput(staticProperties, this);
			this.subOutput = subOutput;
		}

		subOutput.setupConnection(rpn, is, os);

		int _mtu = subOutput.getMaximumTransfertUnit();

		if ((mtu == 0)
		    ||
		    (mtu > _mtu)) {
			mtu = _mtu;
		}

		int _headersLength = subOutput.getHeadersLength();

		if (headerOffset < _headersLength) {
			headerOffset = _headersLength;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void initSend() throws IbisIOException {
                super.initSend();
		subOutput.initSend();
	}

	/**
	 * {@inheritDoc}
	 */
	public void writeByteBuffer(NetSendBuffer b) throws IbisIOException {
                subOutput.writeSubArrayByte(b.data, 0, b.length);
	}

	/**
	 * {@inheritDoc}
	 */
	public void finish() throws IbisIOException {
		subOutput.finish();
		super.finish();
	}

	/**
	 * {@inheritDoc}
	 */
	public void free() throws IbisIOException {
		if (subOutput != null) {
			subOutput.free();
			subOutput = null;
		}
		
		subDriver = null;

		super.free();
	}
	
}
