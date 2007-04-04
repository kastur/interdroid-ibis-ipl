/* $Id$ */

package ibis.ipl;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Properties;

/**
 * An instance of this interface can only be created by the
 * {@link ibis.ipl.IbisFactory#createIbis(CapabilitySet, CapabilitySet,
 * Properties, RegistryEventHandler)} method, and is the starting point
 * of all Ibis communication.
 */
public interface Ibis extends Managable {

    /**
     * When running closed-world, returns the total number of Ibis instances
     * involved in the run. Otherwise returns -1.
     * @return the number of Ibis instances
     * @exception NumberFormatException is thrown when the property
     *   ibis.pool.total_hosts is not defined or does not represent a number.
     * @exception IbisConfigurationException is thrown when this is not a
     * closed-world run.
     */
    public int totalNrOfIbisesInPool();

    /**
     * When running closed-world, wait for all Ibis instances
     * involved in the run.
     * @exception IbisConfigurationException is thrown when this is not a
     * closed-world run, or when registry events are not enabled yet.
     */
    public void waitForAll();

    /**
     * Allows reception of {@link ibis.ipl.RegistryEventHandler RegistryEventHandler}
     * upcalls.
     */
    public void enableRegistryEvents();

    /**
     * Disables reception of
     * {@link ibis.ipl.RegistryEventHandler RegistryEventHandler} upcalls.
     */
    public void disableRegistryEvents();

    /**
     * Returns all Ibis recources to the system.
     * @exception IOException is thrown when an error occurs.
     */
    public void end() throws IOException;

    /** 
     * Returns the Ibis {@linkplain ibis.ipl.Registry Registry}.
     * @return the Ibis registry.
     */
    public Registry registry();

    /**
     * Returns the capabilities of this Ibis implementation.
     * @return the capabilities of this Ibis implementation.
     */
    public CapabilitySet capabilities();

    /**
     * Polls the network for new messages.
     * An upcall may be generated by the poll. 
     * There is one poll for the entire Ibis, as this
     * can sometimes be implemented more efficiently than polling per
     * port. Polling per port is provided in the receiveport itself.
     * @exception IOException is thrown when a communication error occurs.
     */
    public void poll() throws IOException;

    /**
     * Returns an Ibis {@linkplain ibis.ipl.IbisIdentifier identifier} for
     * this Ibis instance.
     * An Ibis identifier identifies an Ibis instance in the network.
     * @return the Ibis identifier of this Ibis instance.
     */
    public IbisIdentifier identifier();

    /**
     * Returns the current Ibis version.
     * @return the ibis version.
     */
    public String getVersion();

    /**
     * May print Ibis-implementation-specific statistics.
     * @param out the stream to print to.
     */
    public void printStatistics(PrintStream out);

    /**
     * Returns the properties as provided when instantiating Ibis.
     * @return the properties.
     */
    public Properties properties();

    /**
     * Returns the Ibis instances that joined the run.
     * Returns the changes since the last joinedIbises call,
     * or, if this is the first call, all Ibis instances that joined.
     * This call only works if this Ibis is configured to support
     * resize downcalls.
     * If no Ibis instances joined, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when the port was
     * not configured to support resize downcalls.
     * @return the joined Ibises.
     */
    public IbisIdentifier[] joinedIbises();

    /**
     * Returns the Ibis instances that left the run (or died).
     * Returns the changes since the last leftIbises call,
     * or, if this is the first call, all Ibis instances that left.
     * This call only works if this Ibis is configured to support
     * resize downcalls.
     * If no Ibis instances left, an array with 0 entries is returned.
     * @exception IbisConfigurationException is thrown when the port was
     * not configured to support resize downcalls.
     * @return the left Ibises.
     */
    public IbisIdentifier[] leftIbises();

    /**
     * Creates a anonymous {@link SendPort} of the specified port type.
     * 
     * @param tp the port type.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     */
    public SendPort createSendPort(CapabilitySet tp) throws IOException;

    /**
     * Creates a named {@link SendPort} of the specified port type.
     * The name does not have to be unique.
     *
     * @param tp the port type.
     * @param name the name of this sendport.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public SendPort createSendPort(CapabilitySet tp, String name)
            throws IOException;

    /** 
     * Creates a named {@link SendPort} of the specified port type.
     * The name does not have to be unique.
     * When a connection is lost, a ConnectUpcall is performed.
     *
     * @param tp the port type.
     * @param name the name of this sendport.
     * @param cU object implementing the
     * {@link SendPortDisconnectUpcall#lostConnection(SendPort,
     * ReceivePortIdentifier, Throwable)} method.
     * @return the new sendport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public SendPort createSendPort(CapabilitySet tp, String name,
        SendPortDisconnectUpcall cU) throws IOException;

    /**
     * Creates a named {@link ReceivePort} of the specified port type.
     * with explicit receipt communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     *
     * @param tp the port type.
     * @param name the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(CapabilitySet tp, String name)
        throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with upcall-based communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * Upcalls will not be invoked until
     * {@link ReceivePort#enableMessageUpcalls()} has been called.
     * This is done to avoid message upcalls during initialization.
     *
     * @param tp the port type.
     * @param name the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param u the upcall handler.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(CapabilitySet tp, String name,
            MessageUpcall u) throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with explicit receipt communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * When a new connection request arrives, or when a connection is lost,
     * a ConnectUpcall is performed.
     *
     * @param tp the port type.
     * @param name the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param cU object implementing <code>gotConnection</code>() and
     * <code>lostConnection</code>() upcalls.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(CapabilitySet tp, String name,
            ReceivePortConnectUpcall cU) throws IOException;

    /** 
     * Creates a named {@link ReceivePort} of the specified port type.
     * with upcall-based communication.
     * New connections will not be accepted until
     * {@link ReceivePort#enableConnections()} is invoked.
     * This is done to avoid connection upcalls during initialization.
     * When a new connection request arrives, or when a connection is lost,
     * a ConnectUpcall is performed.
     * Upcalls will not be invoked until
     * {@link ReceivePort#enableMessageUpcalls()} has been called.
     * This is done to avoid message upcalls during initialization.
     *
     * @param tp the port type.
     * @param name the unique name of this receiveport (or <code>null</code>,
     *    in which case the port is created anonymously).
     * @param u the upcall handler.
     * @param cU object implementing <code>gotConnection</code>() and
     * <code>lostConnection</code>() upcalls.
     * @return the new receiveport.
     * @exception java.io.IOException is thrown when the port could not be
     * created.
     * @exception ibis.ipl.IbisConfigurationException is thrown when the port
     * type does not match what is required here.
     */
    public ReceivePort createReceivePort(CapabilitySet tp, String name,
            MessageUpcall u, ReceivePortConnectUpcall cU) throws IOException;
}
