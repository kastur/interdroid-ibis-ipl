package ibis.ipl;

public interface Registry {	

	/**
	 Locate the ReceivePortIdentifier that has been bound with name name.
	 The registry is polled regularly until a ReceivePortIdentifier with
	 name name is returned.
	 */
	public ReceivePortIdentifier lookup(String name) throws IbisException;

	/**
	 Locate the ReceivePortIdentifier that has been bound with name name.
	 The registry is polled regularly until a ReceivePortIdentifier with
	 name name is returned or until timeout milliseconds have passed.
	 If timeout is 0, lookup does not time out.
	 If the ReceivePortIdentifier has not been found within timeout
	 milliseconds, an IbisException with corresponding message is thrown.
	 */
	public ReceivePortIdentifier lookup(String name, long timeout) throws IbisException;

	/**
	 The registry is polled regularly until an Ibis with name name
	 is returned.
	 */
	public IbisIdentifier locate(String name) throws IbisException;

	/**
	 Locate the IbisIdentifier that has been registered with name name.
	 The registry is polled regularly until an IbisIdentifier with name name
	 is returned or until timeout milliseconds have passed.
	 If timeout is 0, locate does not time out.
	 If the IbisIdentifier has not been found within timeout milliseconds,
	 an IbisException with corresponding message is thrown.
	 */
	public IbisIdentifier locate(String name, long timeout) throws IbisException;

	public ReceivePortIdentifier[] query(IbisIdentifier ident)  throws IbisException;

	public Object elect(String election, Object candidate) throws IbisException;
}
