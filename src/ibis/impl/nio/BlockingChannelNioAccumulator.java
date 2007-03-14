/* $Id: BlockingChannelNioAccumulator.java 2974 2005-04-29 15:30:11Z ceriel $ */

package ibis.impl.nio;

import ibis.impl.ReceivePortIdentifier;
import ibis.impl.SendPortConnectionInfo;

import java.io.IOException;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.SelectableChannel;

import org.apache.log4j.Logger;

final class BlockingChannelNioAccumulator extends NioAccumulator {

    private static Logger logger = Logger.getLogger(
            BlockingChannelNioAccumulator.class);

    SendPortConnectionInfo[] connections = null;

    public BlockingChannelNioAccumulator(NioSendPort port) {
        super(port);
    }

    NioAccumulatorConnection newConnection(GatheringByteChannel channel,
            ReceivePortIdentifier peer) throws IOException {
        NioAccumulatorConnection result;

        logger.debug("registering new connection");

        int nConnections = port.connections().length;

        if (nConnections != 0) {
            logger.warn("" + (nConnections + 1)
                    + " connections from a blocking send port");
        }

        SelectableChannel sChannel = (SelectableChannel) channel;

        sChannel.configureBlocking(true);

        result = new NioAccumulatorConnection(port, channel, peer);

        logger.debug("registered new connection");

        connections = null;

        return result;
    }

    void removeConnection(SendPortConnectionInfo c) {
        connections = null;
    }

    /**
     * Sends out a buffer to multiple channels. Doesn't buffer anything
     */
    boolean doSend(SendBuffer buffer) throws IOException {
        if (logger.isDebugEnabled()) {
            logger.debug("sending a buffer");
        }

        if (connections == null) {
            connections = port.connections();
        }
        buffer.mark();

        int nrOfConnections = connections.length;

        for (int i = 0; i < nrOfConnections; i++) {
            NioAccumulatorConnection connection = (NioAccumulatorConnection)
                connections[i];
            try {
                buffer.reset();
                while (buffer.hasRemaining()) {
                    connection.channel.write(buffer.byteBuffers);
                }
            } catch (IOException e) {
                // inform the SendPort
                port.lostConnection(connection.target, e);

                // remove connection
                nrOfConnections--;
                connections[i] = connections[nrOfConnections];
                connections[nrOfConnections] = null;
                i--;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("done sending a buffer");
        }
        if (nrOfConnections != connections.length) {
            connections = null;
        }
        return true; // signal we are done with the buffer now
    }

    void doFlush() throws IOException {
        // NOTHING
    }
}
