package ibis.connect.routedMessages;

import java.util.List;
import java.util.LinkedList;

import java.net.Socket;
import java.net.InetAddress;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.EOFException;

import ibis.connect.util.MyDebug;

public class RMSocket extends Socket
{
    private HubLink hub = null;
    private String  remoteHostname = null;
    private int     remotePort = -1;
    private int     localPort  = -1;
    private RMInputStream  in  = null;
    private RMOutputStream out = null;
    private LinkedList incomingFragments = new LinkedList(); // list of byte[]
    private byte[]  currentArray = null;
    private int     currentIndex = 0;

    static final int state_NONE       = 1;
    static final int state_CONNECTING = 2;
    static final int state_ACCEPTED   = 3;
    static final int state_REJECTED   = 4;
    static final int state_CONNECTED  = 5;
    static final int state_CLOSED     = 6;
    private int state;

    /* misc methods for the HubLink to feed us
     */
    protected synchronized void enqueueFragment(byte[] b) {
	incomingFragments.addLast(b);
	this.notifyAll();
    }
    protected synchronized void enqueueAccept(int servantPort) {
	MyDebug.out.println("# RMSocket.enqueueAccept()- servantPort="+servantPort);
	state = state_ACCEPTED;
	remotePort = servantPort;
	this.notifyAll();
    }
    protected synchronized void enqueueReject() {
	MyDebug.out.println("# RMSocket.enqueueReject()");
	state = state_REJECTED;
	this.notifyAll();
    }
    protected synchronized void enqueueClose() {
	MyDebug.out.println("# RMSocket.enqueueClose()- port = "+localPort);
	state = state_CLOSED;
	this.notifyAll();
    }
    /* Initialization
     */
    private void commonInit(String rHost)
    {
	try {
	    hub = HubLinkFactory.getHubLink();
	} catch(Exception e) {
	    throw new Error("Cannot initialize HubLink.");
	}
	remoteHostname = rHost;
	out = new RMOutputStream(this);
	in  = new RMInputStream(this);
	state = state_NONE;
	MyDebug.out.println("# RMSocket.commonInit()- rHost="+rHost);
    }

    // Incoming links constructor - reserved to RMServerSocket
    protected RMSocket(String rHost, int rPort, int lPort)
    {
	MyDebug.out.println("# RMSocket()");
	commonInit(rHost);
	remotePort = rPort;
	localPort = lPort;
	state = state_CONNECTED;
	hub.addSocket(this, localPort);
    }

    // Outgoing links constructor - public
    public RMSocket(InetAddress rAddr, int rPort)
	throws IOException
    {
	MyDebug.out.println("# RMSocket("+rAddr+", "+rPort+")");
	commonInit(rAddr.getCanonicalHostName());
	localPort = hub.newPort();
	hub.addSocket(this, localPort);

	MyDebug.out.println("# RMSocket()- sending CONNECT");
	state = state_CONNECTING;
	hub.sendPacket(remoteHostname, new HubProtocol.HubPacketConnect(rPort, hub.localHostName, localPort));
	synchronized(this)
	    {
		while(state == state_CONNECTING)
		    {
			MyDebug.out.println("# RMSocket()- waiting for ACCEPTED- port = "+localPort);
			try { this.wait(); } catch(InterruptedException e) { /* ignore */ }
			MyDebug.out.println("# RMSocket()- unlocked");
		    }
		if(state == state_ACCEPTED) {
		    state = state_CONNECTED;
		} else if(state == state_REJECTED) {
		    throw new IOException("connection refused");
		}
	    }
    }

    public OutputStream getOutputStream()
	throws IOException
    {
	return out;
    }
    public InputStream getInputStream()
	throws IOException
    {
	return in;
    }
    public void close()
	throws IOException
    {
	MyDebug.out.println("# RMSocket.close()");
	state = state_CLOSED;
	hub.sendPacket(remoteHostname, new HubProtocol.HubPacketClose(remotePort));
	hub.removeSocket(localPort);
    }

    /* InputStream for RMSocket
     */
    private class RMInputStream extends InputStream
    {
	private RMSocket socket = null;
	private int deliveredData = 0;
	private boolean  open = false;

	private void checkOpen()
	    throws IOException
	{
	    if((!open || state != state_CONNECTED) && 
	       (socket.currentArray == null && socket.incomingFragments.isEmpty()))
		{
		    MyDebug.out.println("# Detected EOF! open="+open+"; state="+state+"; socket.currentArray="+socket.currentArray+"; incomingFragment: "+socket.incomingFragments.isEmpty());
		    throw new EOFException();
		}
	}

	private void waitFragment()
	    throws IOException
	{
	    if(socket.currentArray == null)
		{
		    while(incomingFragments.size() == 0)
			{
			    try {
				checkOpen();
				socket.wait();
			    } catch(InterruptedException e) { 
				/* ignored */
			    }
			}
		    socket.currentArray = (byte[])socket.incomingFragments.removeFirst();
		    socket.currentIndex = 0;
		}
	}
	private void pumpFragment(int amount)
	{
	    socket.currentIndex += amount;
	    if(socket.currentIndex >= socket.currentArray.length)
		{
		    socket.currentArray = null;
		}
	}

	public RMInputStream(RMSocket s)
	{
	    super();
	    socket = s;
	    open = true;
	}
	public int read(byte[] b)
	    throws IOException
	{
	    int rc = this.read(b, 0, b.length);
	    return rc;
	}
	public int read(byte[] b, int off, int len)
	    throws IOException
	{
	    int rc = -1;
	    synchronized(socket)
		{
		    checkOpen();
		    int j = 0;
		    waitFragment();
		    if(len <= socket.currentArray.length - socket.currentIndex)
			j = len;
		    else
			j = socket.currentArray.length - socket.currentIndex;
		    System.arraycopy(socket.currentArray, socket.currentIndex,
				     b, off, j);
		    pumpFragment(j);
		    rc = j;
		    MyDebug.out.println("# RMInputStream: reading- port="+socket.localPort+" size="+rc);
		}
	    return rc;
	}
	public int read()
	    throws IOException
	{
	    int r = -1;
	    synchronized(socket)
		{
		    checkOpen();
		    waitFragment();
		    r = socket.currentArray[socket.currentIndex];
		    pumpFragment(1);
		    deliveredData++;
		    MyDebug.out.println("# RMInputStream: reading- port="+socket.localPort+" size=1");
		}
	    return r;
	}
	public int available()
	    throws IOException
	{
	    MyDebug.out.println("# RMInputStream: available()");
	    checkOpen();
	    return socket.currentArray==null?0:socket.currentArray.length;
	}
	public void close()
	    throws IOException
	{
	    MyDebug.out.println("# RMInputStream: close()");
	    synchronized(socket) {
		in = null;
		open = false;
		socket.notifyAll();
	    }
	}
    }
    
    /* OutputStream for RMSocket
     */
    private class RMOutputStream extends OutputStream
    {
	private RMSocket socket;
	private boolean open = false;

	private void checkOpen()
	    throws IOException
	{
	    if(!open || state != state_CONNECTED)
		throw new EOFException();
	}
	public RMOutputStream(RMSocket s)
	{
	    super();
	    socket = s;
	    open = true;
	}
	public void write(int v)
	    throws IOException
	{
	    checkOpen();
	    byte[] b = new byte[1];
	    b[0] = (byte)v;
	    MyDebug.out.println("# RMOutputStream: writing- port="+socket.remotePort+" size=1");
	    hub.sendPacket(remoteHostname, new HubProtocol.HubPacketData(remotePort, b));
	}
	public void write(byte[] b)
	    throws IOException
	{
	    checkOpen();
	    MyDebug.out.println("# RMOutputStream: writing- port="+socket.remotePort+" size="+b.length);
	    hub.sendPacket(remoteHostname, new HubProtocol.HubPacketData(remotePort, b));
	}
	public void write(byte[] b, int off, int len)
	    throws IOException
	{
	    checkOpen();
	    byte[] a = new byte[len];
	    System.arraycopy(b, off, a, 0, len);
	    MyDebug.out.println("# RMOutputStream: writing- port="+socket.remotePort+" size="+len+" offset="+off);
	    hub.sendPacket(remoteHostname, new HubProtocol.HubPacketData(remotePort, a));
	}
	public void flush()
	    throws IOException
	{
	    //	    checkOpen();
	    MyDebug.out.println("# RMOutputStream: flush()");
	}
	public void close()
	    throws IOException
	{
	    MyDebug.out.println("# RMOutputStream: close()");
	    synchronized(socket) {
		out = null;
		open = false;
		socket.notifyAll();
	    }
	}
    }

}
