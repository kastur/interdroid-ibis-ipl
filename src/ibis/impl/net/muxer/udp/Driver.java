package ibis.impl.net.muxer.udp;

import ibis.impl.net.NetDriver;
import ibis.impl.net.NetIbis;
import ibis.impl.net.NetInput;
import ibis.impl.net.NetOutput;
import ibis.impl.net.NetPortType;

import java.io.IOException;

/**
 * The NetIbis Muxer/UDP driver.
 */
public final class Driver extends NetDriver {

	final static boolean DEBUG = false; // true;
	final static boolean DEBUG_HUGE = false; // DEBUG;
	final static boolean STATISTICS = false;

	/**
	 * The driver name.
	 */
	private final String name = "muxer.udp";

	/**
	 * Constructor.
	 *
	 * @param ibis the {@link NetIbis} instance.
	 */
	public Driver(NetIbis ibis) {
		super(ibis);
	}	

	/**
	 * Returns the name of the driver.
	 *
	 * @return The driver name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * Creates a new UDP input.
	 *
	 * @param sp the properties of the input's 
	 * {@link ibis.impl.net.NetReceivePort NetReceivePort}.
	 * @param input the controlling input.
	 * @return The new UDP input.
	 */
	public NetInput newInput(NetPortType pt, String context)
		throws IOException {
		return new UdpMuxInput(pt, this, context);
	}

	/**
	 * Creates a new UDP output.
	 *
	 * @param sp the properties of the output's 
	 * {@link ibis.impl.net.NetSendPort NetSendPort}.
	 * @param output the controlling output.
	 * @return The new UDP output.
	 */
	public NetOutput newOutput(NetPortType pt, String context)
		throws IOException {
		return new UdpMuxOutput(pt, this, context);
	}
}
