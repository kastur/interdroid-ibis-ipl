package ibis.ipl.impl.net.rel;

import ibis.ipl.impl.net.__;
import ibis.ipl.impl.net.NetDriver;
import ibis.ipl.impl.net.NetBufferedInput;
import ibis.ipl.impl.net.NetInput;
import ibis.ipl.impl.net.NetIO;
import ibis.ipl.impl.net.NetReceiveBuffer;
import ibis.ipl.impl.net.NetSendPortIdentifier;

import ibis.ipl.IbisException;
import ibis.ipl.IbisIOException;
import ibis.ipl.StaticProperties;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.SocketException;

/* Only for java >= 1.4 
import java.net.SocketTimeoutException;
*/
import java.io.InterruptedIOException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import java.util.Hashtable;


/**
 * The REL input implementation.
 */
public class RelInput extends NetBufferedInput {

	/**
	 * The driver used for the 'real' input.
	 */
	private NetDriver subDriver = null;

	/**
	 * The 'real' input.
	 */
	private NetInput  subInput  = null;

	/**
	 * Constructor.
	 *
	 * @param sp the properties of the input's 
	 * {@link ibis.ipl.impl.net.NetSendPort NetSendPort}.
	 * @param driver the REL driver instance.
	 * @param input the controlling input.
	 */
	RelInput(StaticProperties sp,
		 NetDriver        driver,
		 NetIO            up)
		throws IbisIOException {
		super(sp, driver, up);

		// The length of the header expressed in bytes
		headerLength = 0;
	}

	/*
	 * Sets up an incoming REL connection.
	 *
	 * @param rpn {@inheritDoc}
	 * @param is {@inheritDoc}
	 * @param os {@inheritDoc}
	 */
	public void setupConnection(Integer            rpn,
				    ObjectInputStream  is,
				    ObjectOutputStream os)
		throws IbisIOException {
		NetInput subInput = this.subInput;
		if (subInput == null) {
			if (subDriver == null) {
				subDriver = driver.getIbis().getDriver(getProperty("Driver"));
			}

			subInput = subDriver.newInput(staticProperties, this);
			this.subInput = subInput;
		}
		
		subInput.setupConnection(rpn, is, os);
		 
		int _mtu = subInput.getMaximumTransfertUnit();

		if ((mtu == 0)
		    ||
		    (mtu > _mtu)) {
			mtu = _mtu;
		}

		int _headersLength = subInput.getHeadersLength();

		if (headerOffset < _headersLength) {
			headerOffset = _headersLength;
		}
	}

	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: This REL polling implementation uses the
	 * {@link java.io.InputStream#available()} function to test whether at least one
	 * data byte may be extracted without blocking.
	 *
	 * @return {@inheritDoc}
	 */
	public Integer poll() throws IbisIOException {
                if (subInput == null)
                        return null;
                
                Integer result = subInput.poll();
                if (result != null) {
                        initReceive();
                }

		return result;
	}
	
	/**
	 * {@inheritDoc}
	 *
	 * <BR><B>Note</B>: this function may block if the expected data is not there.
	 *
	 * @return {@inheritDoc}
	 */
	public void readByteBuffer(NetReceiveBuffer b)
		throws IbisIOException {
                subInput.readSubArrayByte(b.data, 0, b.length);
	}

	/**
	 * {@inheritDoc}
	 */
	public void finish() throws IbisIOException {
		subInput.finish();
		super.finish();
	}

	/**
	 * {@inheritDoc}
	 */
	public void free() throws IbisIOException {
		if (subInput != null) {
			subInput.free();
			subInput = null;
		}

		subDriver = null;
		
		super.free();
	}
	
}
