package ibis.impl.net.s_ibis;

import ibis.impl.net.NetDriver;
import ibis.impl.net.NetInputUpcall;
import ibis.impl.net.NetPortType;
import ibis.impl.net.NetSerializedInput;
import ibis.io.Dissipator;
import ibis.io.IbisSerializationInputStream;
import ibis.io.SerializationInputStream;

import java.io.IOException;

/**
 * Input part of the s_ibis driver used for serialization. Uses the
 * IbisSerializationInputStream to actually do the serialization.
 */
public final class SIbisInput extends NetSerializedInput {

    public SIbisInput(NetPortType pt, NetDriver driver, String context,
            NetInputUpcall inputUpcall) throws IOException {
        super(pt, driver, context, inputUpcall);
        requiresStreamReinit = false;
    }

    public SerializationInputStream newSerializationInputStream()
            throws IOException {
        Dissipator id = new DummyDissipator();

        return new IbisSerializationInputStream(id);
    }

    protected void handleEmptyMsg() throws IOException {
        // super.handleEmptyMsg();
        readShort();
    }

    /*
     * Dissipator used to get the data from the driver below. Actually
     * does almost nothing, just passes the data up to the serialization.
     */
    private final class DummyDissipator extends Dissipator {

        public int available() throws IOException {
            return 0; // no way to tell if anything is available */
        }

        public void close() throws IOException {
            // NOTHING
        }

        /*
         * Since the NetInput interface has no way to get to this
         * information, we don't implement this here
         */
        public long bytesRead() {
            return 0;
        }

        public void resetBytesRead() {
            // NOTHING
        }

        public boolean readBoolean() throws IOException {
            return subInput.readBoolean();
        }

        public byte readByte() throws IOException {
            return subInput.readByte();
        }

        public char readChar() throws IOException {
            return subInput.readChar();
        }

        public short readShort() throws IOException {
            return subInput.readShort();
        }

        public int readInt() throws IOException {
            return subInput.readInt();
        }

        public long readLong() throws IOException {
            return subInput.readLong();
        }

        public float readFloat() throws IOException {
            return subInput.readFloat();
        }

        public double readDouble() throws IOException {
            return subInput.readDouble();
        }

        public void readArray(boolean[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(byte[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(short[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(char[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(int[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(long[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(float[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

        public void readArray(double[] a, int off, int len) throws IOException {
            subInput.readArray(a, off, len);
        }

    }
}