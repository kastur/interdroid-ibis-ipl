package ibis.ipl;

public class IbisException extends java.lang.Exception {
	// this space was intensionally left blank, but is now taken...

	Throwable cause = null;

	public IbisException() {
		super();
	}

	public IbisException(String name) {
		super(name);
	}

	public IbisException(String name, Throwable cause) {
		super(name);
		initCause(cause);
	}

	public IbisException(Throwable cause) {
		super();
		initCause(cause);
	}

	public Throwable initCause(Throwable t) {
		return cause = t;
	}

	public Throwable getCause() {
		return cause;
	}

	public String getMessage() {
		String res = super.getMessage();
		if(cause != null) {
			res += ": " + cause.getMessage();
		}

		return res;
	}

	public void printStackTrace() {
		if(cause != null) {
			cause.printStackTrace();
		}

		super.printStackTrace();
	}
}
