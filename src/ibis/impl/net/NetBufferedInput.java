package ibis.ipl.impl.net;

import ibis.ipl.StaticProperties;

/**
 * Provides an abstraction of a buffered network input.
 */
public abstract class NetBufferedInput extends NetInput {

	/**
	 * The current buffer.
	 */
        private NetReceiveBuffer buffer          = null;

	/**
	 * The current memory block allocator.
	 */
        private NetAllocator     bufferAllocator = null;

	/**
	 * The buffer offset of the payload area.
	 */
	protected int dataOffset = 0;

	/**
	 * The current buffer offset for extracting user data.
	 */
	private int bufferOffset = 0;

        /**
         * Flag used to detect circular references in readByteBuffer default implementations.
         */
        private boolean circularCheck = false;

	/**
	 * Constructor.
	 *
	 * @param staticProperties the properties of the corresponing
	 *                         {@linkplain NetReceivePort receive port}.
	 * @param driver the input's driver.
	 * @param input the controlling input or <code>null</code> if this input is a
	 *              root input.
	 */
	protected NetBufferedInput(NetPortType      portType,
			   NetDriver 	    driver,
			   NetIO  	    up,
                           String           context) {
		super(portType, driver, up, context);
	}

	/**
	 * @inheritDoc
	 * {@link NetBufferFactory}.
	 */
	public NetReceiveBuffer createReceiveBuffer(int length)
		throws NetIbisException {
	    if (factory == null) {
		byte[] data;
		if (bufferAllocator == null) {
		    data = new byte[length];
		} else {
		    data = bufferAllocator.allocate();
		}
		return new NetReceiveBuffer(data, length, bufferAllocator);
	    }
	    return (NetReceiveBuffer)createBuffer(length);
	}

        /**
         * Optional method for zero-copy reception.
         * Note: at least one 'receiveByteBuffer' method must be implemented.
         */
        protected void receiveByteBuffer(NetReceiveBuffer buffer) throws NetIbisException {
                int offset = dataOffset;
                int length = buffer.length - offset;

                if (circularCheck)
                        throw new NetIbisException("circular reference");

                circularCheck = true;
                while (length > 0) {
                        NetReceiveBuffer b = receiveByteBuffer(length);
                        int copyLength = Math.min(length, b.length);
                        System.arraycopy(b.data, 0, buffer.data, dataOffset, copyLength);
                        offset += copyLength;
                        length -= copyLength;
                        b.free();
                }
                circularCheck = false;
        }

        /**
         * Optional method for static buffer reception.
         * Note: at least one 'receiveByteBuffer' method must be implemented.
         */
        protected NetReceiveBuffer receiveByteBuffer(int expectedLength) throws NetIbisException {
                NetReceiveBuffer b = null;

                if (circularCheck)
                        throw new NetIbisException("circular reference");

                circularCheck = true;
                if (mtu != 0) {
                        b = createReceiveBuffer(mtu, 0);
                } else {
                        b = createReceiveBuffer(dataOffset + expectedLength, 0);
                }

                receiveByteBuffer(b);
                circularCheck = false;
                return b;
        }



        protected void initReceive() {
                //System.err.println("initReceive -->");
                if (mtu != 0) {
			if (bufferAllocator == null || bufferAllocator.getBlockSize() != mtu) {
				bufferAllocator = new NetAllocator(mtu);
			}
			if (factory == null) {
			    factory = new NetBufferFactory(mtu, new NetReceiveBufferFactoryDefaultImpl(), bufferAllocator);
			} else {
			    factory.setMaximumTransferUnit(mtu);
			}
		}

                dataOffset = getHeadersLength();
                //System.err.println("initReceive <--");
        }

        private void pumpBuffer(NetReceiveBuffer buffer) throws NetIbisException {
                receiveByteBuffer(buffer);
                buffer.free();
        }

	private void pumpBuffer(int length) throws NetIbisException {
		buffer       = receiveByteBuffer(dataOffset+length);
		bufferOffset = dataOffset;
	}

	protected void freeBuffer() throws NetIbisException {
		if (buffer != null) {
			buffer.free();
			buffer       = null;
                        bufferOffset =    0;
		}
	}

	public void finish() throws NetIbisException {
                super.finish();
 		freeBuffer();
	}

        public NetReceiveBuffer readByteBuffer(int expectedLength) throws NetIbisException {
                freeBuffer();
                return receiveByteBuffer(expectedLength);
        }


        public void readByteBuffer(NetReceiveBuffer b) throws NetIbisException {
                freeBuffer();
                receiveByteBuffer(b);
        }


	public byte readByte() throws NetIbisException {
                //System.err.println("readbyte -->");
		byte value = 0;

		if (buffer == null) {
			pumpBuffer(1);
		}

		value = buffer.data[bufferOffset++];

		if ((buffer.length - bufferOffset) == 0) {
			freeBuffer();
		}

                //System.err.println("readbyte <--");

		return value;
	}

	public void readArraySliceByte(byte [] userBuffer,
				       int     offset,
				       int     length)
		throws NetIbisException {
		//System.err.println("read: "+offset+", "+length);
		if (length == 0)
			return;

                if (dataOffset == 0) {
                        if (buffer != null) {
                                freeBuffer();
                        }

			// Here, the NetReceiveBuffer provides a view into a
			// pre-existing Buffer at a varying offset. For that,
			// we cannot use the BufferFactory.
                        if (mtu != 0) {
                                do {
                                        int copyLength = Math.min(mtu, length);
                                        pumpBuffer(new NetReceiveBuffer(userBuffer, offset, copyLength));
                                        offset += copyLength;
                                        length -= copyLength;
                                } while (length != 0);
                        } else {
                                pumpBuffer(new NetReceiveBuffer(userBuffer, offset, length));
                        }

                } else {
                        while (length > 0) {
				if (buffer == null) {
					pumpBuffer(length);
				}

                                int bufferLength = buffer.length - bufferOffset;
                                int copyLength   = Math.min(bufferLength, length);

                                System.arraycopy(buffer.data, bufferOffset, userBuffer, offset, copyLength);

                                bufferOffset += copyLength;
                                bufferLength -= copyLength;
                                offset       += copyLength;
                                length       -= copyLength;

                                if (bufferLength == 0) {
                                        freeBuffer();
                                }
                        }
                }

		//System.err.println("read: "+offset+", "+length+": ok");
	}

}
