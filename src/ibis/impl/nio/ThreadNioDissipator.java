package ibis.impl.nio;

import ibis.ipl.IbisError;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * Dissipator which reads from a single channel. Reads whenever
 * the SendReceiveThread askes it to.
 */
final class ThreadNioDissipator extends NioDissipator implements Config {

    final ThreadNioReceivePort port;
    final SendReceiveThread sendReceiveThread;

    SelectionKey key;
    boolean reading = false;

    int minimum = 0;

    IOException error = null; // used to notify user of exceptions

    ThreadNioDissipator(SendReceiveThread sendReceiveThread,
			ThreadNioReceivePort port, 
			NioSendPortIdentifier peer,
			NioReceivePortIdentifier rpi,
			ReadableByteChannel channel,
			NioPortType type) throws IOException {
	super(peer, rpi, channel, type);

	this.sendReceiveThread = sendReceiveThread;
	this.port = port;

	if(!(channel instanceof SelectableChannel)) {
	    throw new IbisError("wrong type of channel given on creation of"
		    + " ThreadNioDissipator");
	}
	key = sendReceiveThread.register((SelectableChannel) channel, this);
	sendReceiveThread.enableReading(key);
	reading = true;
    }

    synchronized void receive() throws IOException {
	super.receive();

	if (!reading) {
	    sendReceiveThread.enableReading(key);
	    reading = true;
	}
    }

    synchronized boolean messageWaiting() throws IOException {
	if (key == null) {
	    //this dissipator is already closed
	    return false;
	}
	return super.messageWaiting();
    }

    /**
     * Called by the send/receive thread to indicate we may read from the
     * channel now.
     */
    synchronized void read() {
	boolean bufferWasEmpty;

	synchronized(this) {
	    try {
		bufferWasEmpty = (unUsedLength() == 0);

		readFromChannel();

		//no use reading anymore, it's already full
		if (!buffer.hasRemaining()) {
		    key.interestOps(0);
		    reading = false;
		}


		if(minimum != 0 && unUsedLength() >= minimum) {
		    notifyAll();
		}
	    } catch (IOException e) {
		error = e;
		key.interestOps(0);
		reading = false;
		try {
		    channel.close();
		} catch (IOException e2) {
		    //IGNORE
		}
		return;
	    }
	}
	//signal the port data is available in this dissipator now
	if (bufferWasEmpty) {
	    port.addToReadyList(this);
	}
    }

    /*
     * fills the buffer upto at least "minimum" bytes.
     *
     */
    synchronized protected void fillBuffer(int minimum) throws IOException {
	//since the send/receive thread actually receives,
	//we can only wait for it to put the data in the buffer
	
	if(!reading) {
	    sendReceiveThread.enableReading(key);
	    reading = true;
	}

	while(unUsedLength() < minimum) {
	    if(!reading) {
		sendReceiveThread.enableReading(key);
		reading = true;
	    }
	    if (error != null) {
		//an exception occured while receiving, throw exception now
		throw error;
	    }
	    try {
		this.minimum = minimum;
		wait();
	    } catch (InterruptedException e) {
		//IGNORE
	    }
	    this.minimum = 0;
	}
    }

    synchronized public long byteRead() {
	return super.bytesRead();
    }

    synchronized public void resetBytesRead() {
	super.resetBytesRead();
    }

    public void close() throws IOException {
	key = null;
	channel.close();
    }
}
