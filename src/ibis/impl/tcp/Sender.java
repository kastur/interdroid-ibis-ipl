package ibis.impl.tcp;

import java.io.IOException;
import java.io.OutputStream;

abstract class Sender {
	abstract ibis.ipl.WriteMessage newMessage() throws IOException;
	abstract void connect(TcpReceivePortIdentifier ri, OutputStream sout, int id) throws IOException;
	abstract void free();	
	abstract void reset() throws IOException;
}
