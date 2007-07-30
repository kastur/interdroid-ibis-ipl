package ibis.ipl.impl.registry.newCentral;

import ibis.ipl.impl.IbisIdentifier;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

final class Event implements Serializable, Comparable<Event> {

    private static final long serialVersionUID = 1L;

    // event types

    static final int JOIN = 1;

    static final int LEAVE = 2;

    static final int DIED = 3;

    static final int SIGNAL = 4;

    static final int ELECT = 5;

    static final int UN_ELECT = 6;

    private final int time;

    private final int type;

    private final String description;

    private final IbisIdentifier[] ibisses;

    Event(int time, int type, String description, IbisIdentifier... ibisses ) {
        this.time = time;
        this.type = type;
        this.ibisses = ibisses.clone();
        if (description == null) {
            this.description = "";
        } else {
            this.description = description;
        }
    }

    Event(DataInput in) throws IOException {
        time = in.readInt();
        type = in.readInt();
        description = in.readUTF();
        ibisses = new IbisIdentifier[in.readInt()];
        for (int i = 0; i < ibisses.length; i++) {
            ibisses[i] = new IbisIdentifier(in);
        }
    }

    void writeTo(DataOutput out) throws IOException {
        out.writeInt(time);
        out.writeInt(type);
        out.writeUTF(description);
        out.writeInt(ibisses.length);
        for (int i = 0; i < ibisses.length; i++) {
            ibisses[i].writeTo(out);
        }

    }

    int getTime() {
        return time;
    }

    String getDescription() {
        return description;
    }

    IbisIdentifier getFirstIbis() {
        if (ibisses.length == 0) {
            return null;
        }
        return ibisses[0];
    }

    IbisIdentifier[] getIbises() {
        return ibisses.clone();
    }

    int getType() {
        return type;
    }

    private String typeString() {
        switch (type) {
        case JOIN:
            return "JOIN";
        case LEAVE:
            return "LEAVE";
        case DIED:
            return "DIED";
        case SIGNAL:
            return "SIGNAL";
        case ELECT:
            return "ELECT";
        case UN_ELECT:
            return "UN_ELECT";
        default:
            return "UNKNOWN";
        }
    }

    public String toString() {
        return typeString() + "@" + time;
    }

	public int compareTo(Event other) {
		return time - other.time;
	}

}
