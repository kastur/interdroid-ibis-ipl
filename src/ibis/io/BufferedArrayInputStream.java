package ibis.io;

import java.io.InputStream;
import java.io.IOException;

import ibis.ipl.IbisIOException;

/**
 *
 * Extends OutputStream with read of array of primitives and readSingleInt
 */

public final class BufferedArrayInputStream
	extends ibis.io.ArrayInputStream {

//	static {
//		ArrayOutputStream.classInit();
//	}

    public static final boolean DEBUG = false;

    private InputStream in;

    private static final int BUF_SIZE = 4*1024;
    private byte [] buffer;
    private int index, buffered_bytes;

    public BufferedArrayInputStream(InputStream in) {
	    this.in = in;
	    buffer = new byte[BUF_SIZE];
    }

    private void dump(byte[] buffer, int off, int len, String caller) {
	System.err.print(caller + " buffer read[" + off + ":" + len + "] = [");
	for (int i = off; i < min(off + len, 256); i++) {
	    System.err.print(Integer.toHexString(buffer[i] & 0xff) + " ");
	}
	System.err.println("]");
    }

	private static final int min(int a, int b) {
		return (a > b) ? b : a;
	}

	public final int read() throws IbisIOException {
		throw new IbisIOException("int read() has no meaning for typed stream");
	}

	private final void fillbuffer(int len) throws IbisIOException {

		// This ensures that there are at least 'len' bytes in the buffer
		// PRECONDITION: 'index + buffered_bytes' should never be larger than BUF_SIZE!!

	    try {
		while ((buffered_bytes) < len) {
//			System.out.println("buffer -> filled from " + index + " with " + buffered_bytes + " size " + BUF_SIZE + " read " + len);
			buffered_bytes += in.read(buffer, index + buffered_bytes, BUF_SIZE-(index+buffered_bytes));
		}
//		System.out.println(buffered_bytes + " in buffer");
	    } catch (IOException e) {
		throw new IbisIOException(e);
	    }
	}

	public final int available() throws IbisIOException {
	    try {
		return (buffered_bytes + in.available());
	    } catch (IOException e) {
		throw new IbisIOException(e);
	    }
	}

	public void readArray(boolean[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(boolean[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_BOOLEAN;



		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_BOOLEAN;
				Conversion.byte2boolean(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_BOOLEAN;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2boolean(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;
	}

	public void readArray(byte[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(byte[" + off + " ... " + (off+len) + "])");
		}

		if (buffered_bytes >= len) {

//System.out.println("IN BUF");

			// data is already in the buffer.
//			System.out.println("Data is in buffer -> copying " + index + " ... " + (index+len) + " to " + off);

			System.arraycopy(buffer, index, a, off, len);
			index += len;
			buffered_bytes -= len;

//System.out.println("DONE");

		} else {
		    try {
			if (buffered_bytes == 0) {
//System.out.println("EEK2");
//System.out.println("NOT IN BUF");
				int rd = 0;
				index = 0;
				do {
					rd += in.read(a, off + rd, len - rd);
				} while (rd < len);
			} else {
//System.out.println("PARTLY IN BUF " + buffered_bytes + " " + len);
//System.out.println("EEK3");
				// first, copy the data we do have to 'a' .
				System.arraycopy(buffer, index, a, off, buffered_bytes);
				index = 0;

				// next, read the rest.
				int rd = buffered_bytes;
				do {
					rd += in.read(a, off + rd, len - rd);
				} while (rd < len);

				buffered_bytes = 0;
			}
		    } catch (IOException e) {
			throw new IbisIOException(e);
		    }
		}

//		System.out.print("result -> byte[");
//		for (int i=0;i<len;i++) { 
//			System.out.print(a[off+i] + ",");
//		}
//		System.out.println("]");
	}


//	static int R = 0;
//	static int W = 0;

	public void readArray(short[] a, int off, int len)
		throws IbisIOException {

		int useable, converted;
		int to_convert = len * SIZEOF_SHORT;

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_SHORT;
				Conversion.byte2short(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_SHORT;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2short(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;

		if (DEBUG) {
//			System.out.print("readArray(short " + (R++) + " [");
			for (int i=0;i<len;i++) {
				System.out.print(a[off+i] + ",");
			}
			System.out.println("]");
		}
	}

	public void readArray(char[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(char[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_CHAR;

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_CHAR;
				Conversion.byte2char(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_CHAR;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2char(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;
	}

	public void readArray(int[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(int[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_INT;

//		System.out.println("To convert " + to_convert);
//		System.out.println("Buffered " + buffered_bytes);

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_INT;

//				System.out.println("converting " + useable + " ints from " + off);
				Conversion.byte2int(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_INT;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

//				System.out.println("Leftover " + len + " ints to convert, " + buffered_bytes + " bytes buffered" + 
//						   to_convert + " bytes to convert");

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
//		System.out.println("converting " + len + " ints from " + index + " to " + off);
	
		Conversion.byte2int(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;

	//	System.out.println("Done converting int [], buffer contains " + buffered_bytes + " bytes (starting at " + index + ")");


	}

	public void readArray(long[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(long[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_LONG;

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_LONG;
				Conversion.byte2long(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_LONG;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2long(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;
	}


	public void readArray(float[] a, int off, int len)
		throws IbisIOException {
		if (DEBUG) {
			System.out.println("readArray(float[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_FLOAT;

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_FLOAT;
				Conversion.byte2float(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_FLOAT;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2float(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;
	}

	public void readArray(double[] a, int off, int len)
		throws IbisIOException {

		if (DEBUG) {
			System.out.println("readArray(double[" + off + " ... " + (off+len) + "])");
		}

		int useable, converted;
		int to_convert = len * SIZEOF_DOUBLE;

		while (buffered_bytes < to_convert) {
			// not enough data in the buffer

			if (buffered_bytes == 0) {
				index = 0;
				fillbuffer(min(BUF_SIZE, to_convert));
			} else {
				// first, copy the data we do have to 'a' .
				useable = buffered_bytes / SIZEOF_DOUBLE;
				Conversion.byte2double(buffer, index, a, off, useable);

				len -= useable;
				off += useable;

				converted = useable * SIZEOF_DOUBLE;
				index += converted;
				buffered_bytes -= converted;
				to_convert -= converted;

				// second, copy the leftovers to the start of the buffer.
				for (int i=0;i<buffered_bytes;i++) {
					buffer[i] = buffer[index+i];
				}
				index = 0;

				// third, fill the buffer as far as possible.
				fillbuffer(min(BUF_SIZE, to_convert));
			}
		}

		// enough data in the buffer
		Conversion.byte2double(buffer, index, a, off, len);
		buffered_bytes -= to_convert;
		index += to_convert;
	}
}

