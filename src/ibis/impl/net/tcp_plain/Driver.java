package ibis.ipl.impl.net.tcp_plain;

import ibis.ipl.impl.net.*;

import java.io.IOException;

/**
 * The NetIbis TCP driver with pipelined block transmission.
 */
public final class Driver extends NetDriver {

	/**
	 * The driver name.
	 */
	private final String name = "tcp_plain";


	/**
	 * Constructor.
	 *
	 * @param ibis the {@link NetIbis} instance.
	 */
	public Driver(NetIbis ibis) {
		super(ibis);
	}	

	/**
	 * {@inheritDoc}
	 */
	public String getName() {
		return name;
	}

	/**
	 * {@inheritDoc}
	 */
	public NetInput newInput(NetPortType pt, String context) throws IOException {
		return new TcpInput(pt, this, context);
	}

	/**
	 * {@inheritDoc}
	 */
	public NetOutput newOutput(NetPortType pt, String context) throws IOException {
		return new TcpOutput(pt, this, context);
	}
}
