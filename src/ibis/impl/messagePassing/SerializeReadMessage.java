package ibis.impl.messagePassing;

import java.io.ObjectInput;
import java.io.IOException;

final class SerializeReadMessage extends ReadMessage {

    java.io.ObjectInput obj_in;

    SerializeReadMessage(ibis.ipl.SendPort origin,
			 ReceivePort port) {
	super(origin, port);
	if (Ibis.DEBUG) {
	    System.err.println("~~~~~~~~~ A new -sun- ReadMessage " + this);
	}
	obj_in = ((SerializeShadowSendPort)origin).obj_in;
    }

    public boolean readBoolean() throws IOException {
	return obj_in.readBoolean();
    }

    public byte readByte() throws IOException {
	return obj_in.readByte();
    }

    public char readChar() throws IOException {
	return obj_in.readChar();
    }

    public short readShort() throws IOException {
	return obj_in.readShort();
    }

    public int  readInt() throws IOException {
	return obj_in.readInt();
    }

    public long readLong() throws IOException {
	return obj_in.readLong();
    }

    public float readFloat() throws IOException {
	return obj_in.readFloat();
    }

    public double readDouble() throws IOException {
	return obj_in.readDouble();
    }

    public String readString() throws IOException {
	try {
	    return (String) obj_in.readObject();
	} catch (ClassNotFoundException e) {
	    throw new Error("class String not found", e);
	}
    }

    public Object readObject() throws IOException, ClassNotFoundException {
// System.err.println("SerializeReadMessage.readObject() called " + this);
	return obj_in.readObject();
    }

    public void readArray(boolean[] destination) throws IOException {
	try {
	    boolean[] temp = (boolean[])obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require boolean[]", e3);
	}
    }

    public void readArray(byte[] destination) throws IOException {
	try {
	    byte[] temp = (byte[])obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require byte[]", e3);
	}
    }

    public void readArray(char[] destination) throws IOException {
	try {
	    char[] temp = (char[]) obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require char[]", e3);
	}
    }

    public void readArray(short[] destination) throws IOException {

	try {
	    short[] temp = (short[]) obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require short[]", e3);
	}
    }

    public void readArray(int[] destination) throws IOException {
	// manta.runtime.RuntimeSystem.DebugMe(this, destination);
	try {
	    int[] temp = (int[]) obj_in.readObject();
	    // manta.runtime.RuntimeSystem.DebugMe(temp, destination);
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require int[]", e3);
	}
    }

    public void readArray(long[] destination) throws IOException {
	try {
	    long[] temp = (long[]) obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require long[]", e3);
	}
    }

    public void readArray(float[] destination) throws IOException {
	try {
	    float[] temp = (float[]) obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require float[]", e3);
	}
    }

    public void readArray(double[] destination) throws IOException {
	try {
	    double[] temp = (double[]) obj_in.readObject();
	    if (temp.length != destination.length) {
		throw new ArrayIndexOutOfBoundsException("Destination has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, 0, temp.length);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require double[]", e3);
	}
    }

    public void readArray(boolean[] destination, int offset, int size)
	    throws IOException {
	try {
	    boolean[] temp = (boolean[])obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require boolean[]", e3);
	}
    }

    public void readArray(byte[] destination, int offset, int size)
	    throws IOException {
	try {
	    byte[] temp = (byte[])obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require byte[]", e3);
	}
    }

    public void readArray(char[] destination, int offset, int size)
	    throws IOException {

	try {
	    char[] temp = (char[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require char[]", e3);
	}

    }

    public void readArray(short[] destination, int offset, int size)
	    throws IOException {

	try {
	    short[] temp = (short[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require short[]", e3);
	}

    }

    public void readArray(int[] destination, int offset, int size)
	    throws IOException {

	try {
	    int[] temp = (int[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require int[]", e3);
	}

    }

    public void readArray(long[] destination, int offset, int size)
	    throws IOException {
	try {
	    long[] temp = (long[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require long[]", e3);
	}

    }

    public void readArray(float[] destination, int offset, int size)
	    throws IOException {
	try {
	    float[] temp = (float[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require float[]", e3);
	}

    }

    public void readArray(double[] destination, int offset, int size)
	    throws IOException {
	try {
	    double[] temp = (double[]) obj_in.readObject();
	    if (temp.length != size) {
		throw new ArrayIndexOutOfBoundsException("Received sub array has wrong size");
	    }
	    System.arraycopy(temp, 0, destination, offset, size);

	} catch (ClassNotFoundException e3) {
	    throw new Error("require double[]", e3);
	}
    }

}
