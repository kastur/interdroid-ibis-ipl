package ibis.connect.routedMessages;

import java.util.Map;
import java.util.Hashtable;

import java.net.Socket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.SocketException;

import java.io.IOException;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.BufferedInputStream;

import ibis.connect.util.MyDebug;

// HubLink manages the link with the control hub
public class HubLink extends Thread
{
    private HubProtocol.HubWire wire;
    public final String localHostName;

    private boolean hubRunning = true;

    private int serverPortCounter = 1;
    private Map serverSockets    = new Hashtable();
    private Map connectedSockets = new Hashtable();

    protected synchronized int newPort() {
	serverPortCounter++;
	return serverPortCounter;
    }

    /* ServerSocket list management
     */
    protected synchronized void addServer(RMServerSocket s, int port) {
	serverSockets.put(new Integer(port), s);
    }
    protected synchronized void removeServer(int port) {
	serverSockets.remove(new Integer(port));
    }
    private synchronized RMServerSocket resolveServer(int port) {
	RMServerSocket s = (RMServerSocket)serverSockets.get(new Integer(port));
	if(s == null)
	    throw new Error("HubLink: bad server- port="+port);
	return s;
    }
    /* Socket list management
     */
    protected synchronized void addSocket(RMSocket s, int port) {
	connectedSockets.put(new Integer(port), s);
    }
    protected synchronized void removeSocket(int port) {
	connectedSockets.remove(new Integer(port));
    }
    private synchronized RMSocket resolveSocket(int port)
	throws IOException {
	RMSocket s = (RMSocket)connectedSockets.get(new Integer(port));
	if(s == null)
	    {
		throw new IOException("HubLink: bad socket- port="+port);
	    }
	return s;
    }
    
    public HubLink(String host, int port)
	throws IOException, ClassNotFoundException {
	MyDebug.out.println("# HubLink()");
	Socket s = new Socket(host, port);
	wire = new HubProtocol.HubWire(s);
	localHostName = wire.getLocalName();
	MyDebug.out.println("# HubLink() done.");
    }

    protected synchronized void stopHub()
    {
	if(hubRunning) {
	    hubRunning = false;
	    try {
		wire.close();
	    } catch(IOException e) { /* discard exception */ }
	}
    }

    protected synchronized void sendPacket(String destHost, HubProtocol.HubPacket packet)
	throws IOException {
	wire.sendMessage(destHost, packet);
    }

    public void run()
    {
	while(hubRunning) {
	    try {
		HubProtocol.HubPacket packet = wire.recvPacket();
		int action      = packet.getType();
		String destHost = packet.getHost();
		if(!destHost.equals(localHostName))
		    {
			System.out.println("# HubLink.run()- received wrong data (host="+destHost+")");
			throw new Error("HubLink: bad data");
		    }
		switch(action) {
		case HubProtocol.CONNECT:
		    {
			HubProtocol.HubPacketConnect p = (HubProtocol.HubPacketConnect)packet;
			MyDebug.out.println("# HubLink.run()- Received CONNECT for host="+p.destHost+"; port="+p.serverPort+
					    "; from host="+p.clientHost+"; port="+p.clientPort);
			RMServerSocket s = resolveServer(p.serverPort);
			boolean accepted = false;
			if(s != null) {  // AD: TODO- investigate concurrency in CONNECT/ACCEPT
			    accepted = s.enqueueConnect(p.clientHost, p.clientPort);
			}
			if(!accepted) {
			    HubProtocol.HubPacketReject pr =
				new HubProtocol.HubPacketReject(p.clientPort, localHostName);
			    wire.sendMessage(p.clientHost, pr);
			}
		    }
		    break;
		case HubProtocol.ACCEPT:
		    {
			HubProtocol.HubPacketAccept p = (HubProtocol.HubPacketAccept)packet;
			MyDebug.out.println("# HubLink.run()- Received ACCEPT for clientPort="+p.clientPort+" from serverHost="+p.serverHost+"; servantPort="+p.servantPort);
			try {
			    RMSocket s = resolveSocket(p.clientPort);
			    s.enqueueAccept(p.servantPort);
			} catch(Exception e) {
			    /* Exception may be discarded (socket has been closed while the 
			     * CONNECT/ACCEPT were on the wire). Trace it anyway for pathologic
			     * behavior diagnosis.
			     */
			    System.out.println("# HubLink.run()- exception while resolving socket for ACCEPT!");
			}
		    }
		    break;
		case HubProtocol.REJECT:
		    {
			HubProtocol.HubPacketReject p = (HubProtocol.HubPacketReject)packet;
			System.out.println("# Received REJECT for port "+p.clientPort+" from host "+p.serverHost);
			try {
			    RMSocket s = resolveSocket(p.clientPort);
			    s.enqueueReject();
			} catch(Exception e) { /* ignore */ }
		    }
		    break;
		case HubProtocol.DATA:
		    {
			HubProtocol.HubPacketData p = (HubProtocol.HubPacketData)packet;
			try {
			    RMSocket s = resolveSocket(p.port);
			    s.enqueueFragment(p.b);
			} catch(Exception e) {
			    System.out.println("HubLink: received DATA on closed connection.");
			}
		    }
		    break;
		case HubProtocol.CLOSE:
		    {
			HubProtocol.HubPacketClose p = (HubProtocol.HubPacketClose)packet;
			try {
			    RMSocket s = resolveSocket(p.port);
			    MyDebug.out.println(" HubLink.run()- Received CLOSE for port = "+p.port);
			    s.enqueueClose();
			} catch(IOException e) { /* ignore */ }
		    }
		    break;
		default:
		    MyDebug.out.println("# Received unknown action: "+action);
		    throw new Error("HubLink: bad data");
		}
	    } catch(EOFException e) {
		System.out.println("# HubLink: EOF detected- exiting.");
		hubRunning = false;
	    } catch(SocketException e) {
		System.out.println("# HubLink: Socket closed- exiting.");
		hubRunning = false;
	    } catch(Exception e) {
		System.out.println("# HubLink: unexpected exception.");
		hubRunning = false;
		throw new Error(e);
	    }
	}
    }
}
