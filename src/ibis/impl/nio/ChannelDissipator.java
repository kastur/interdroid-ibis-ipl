package ibis.impl.nio;

import java.io.IOException;

import java.nio.channels.ReadableByteChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.BufferUnderflowException;

import ibis.io.Dissipator;

/**
 * Reads data from a channel.
 */
public final class ChannelDissipator extends Dissipator implements Config {
    public static final int SIZEOF_BYTE = 1;
    public static final int SIZEOF_CHAR = 2;
    public static final int SIZEOF_SHORT = 2;
    public static final int SIZEOF_INT = 4;
    public static final int SIZEOF_LONG = 8;
    public static final int SIZEOF_FLOAT = 4;
    public static final int SIZEOF_DOUBLE = 8;

    static final int BUFFER_SIZE = 1024;

    private ReadableByteChannel channel;
    private ByteBuffer buffer;
    private long count = 0;

    public ChannelDissipator(ReadableByteChannel channel) {
	buffer = ByteBuffer.allocate(BUFFER_SIZE).order(ByteOrder.BIG_ENDIAN);
	//make the buffer apear empty
	buffer.limit(0);
	this.channel = channel;
    }

    public int available() throws IOException {
	return buffer.remaining();
    }

    public void close() throws IOException {
	channel = null;
	buffer = null;
    }

    public long bytesRead() {
	return count;
    }

    public void resetBytesRead() {
	count = 0;
    }

    private void readAtLeast(int minimum) throws IOException {
	int count;
	buffer.compact();

	while(buffer.position() < minimum) {
	    count = channel.read(buffer);
	    if(count == -1) {
		throw new IOException("eos read on reading from channel");
	    }
	    this.count += count;
	}
	buffer.flip();
    }

    public boolean readBoolean() throws IOException {
	return (readByte() == ((byte) 1));
    }

    public byte readByte() throws IOException {
	try {
	    return buffer.get();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_BYTE); 
	    return buffer.get();
	}
    }

    public char readChar() throws IOException {
	try {
	    return buffer.getChar();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_CHAR); 
	    return buffer.getChar();
	}
    }

    public short readShort() throws IOException {
	try {
	    return buffer.getShort();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_SHORT);
	    return buffer.getShort();
	}
    }


    public int readInt() throws IOException {
	try {
	    return buffer.getInt();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_INT);
	    return buffer.getInt();
	}
    }

    public long readLong() throws IOException {
	try {
	    return buffer.getLong();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_LONG);
	    return buffer.getLong();
	}
    }

    public float readFloat() throws IOException {
	try {
	    return buffer.getFloat();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_FLOAT);
	    return buffer.getFloat();
	}
    }

    public double readDouble() throws IOException {
	try {
	    return buffer.getDouble();
	} catch (BufferUnderflowException e) {
	    readAtLeast(SIZEOF_DOUBLE);
	    return buffer.getDouble();
	}
    }


    public void readArray(boolean [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readBoolean();
	}
    }

    public void readArray(byte [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readByte();
	}
    }

    public void readArray(char [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readChar();
	}
    }

    public void readArray(short [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readShort();
	}
    }

    public void readArray(int [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readInt();
	}
    }

    public void readArray(long [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readLong();
	}
    }

    public void readArray(float [] destination, int offset, int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readFloat();
	}
    }

    public void readArray(double [] destination, int offset, 
				   int length) 
	    throws IOException {
	for (int i = offset; i < (offset + length); i++) {
	    destination[i] = readDouble();
	}
    }
}
