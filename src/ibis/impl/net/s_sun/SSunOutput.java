package ibis.ipl.impl.net.s_sun;

import ibis.io.ArrayOutputStream;
import ibis.io.SunSerializationOutputStream;
import ibis.io.SerializationOutputStream;

import ibis.ipl.impl.net.*;

import ibis.ipl.IbisIOException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * The ID output implementation.
 */
public final class SSunOutput extends NetSerializedOutput {
        public SSunOutput(NetPortType pt, NetDriver driver, NetIO up, String context) throws IbisIOException {
		super(pt, driver, up, context);
	}
        public SerializationOutputStream newSerializationOutputStream() throws IbisIOException {
                OutputStream os = new DummyOutputStream();
                return new SunSerializationOutputStream(os);
        }
        
        private final class DummyOutputStream extends OutputStream {
                public void write(int b) throws IOException {
                        try {
                                subOutput.writeByte((byte)b);
                        } catch (IbisIOException e) {
                                throw new IOException(e.getMessage());
                        }
                }
        }

}
