package ibis.ipl.impl.messagePassing;

import java.io.IOException;

// Make this final, make inlining possible
final class IbisIdentifier
	implements ibis.ipl.IbisIdentifier,
		   java.io.Serializable
{

    String name;
    int cpu;

    IbisIdentifier(String name, int cpu) {
	this.name = name;
	this.cpu  = cpu;
    }

    // Compare ranks here, much faster. This is method critical for Satin. --Rob
    public boolean equals(ibis.ipl.impl.messagePassing.IbisIdentifier other) {
	return cpu == other.cpu;
    }

    public boolean equals(Object o) {
	if(o == this) return true;

	if (o instanceof ibis.ipl.impl.messagePassing.IbisIdentifier) {
	    ibis.ipl.impl.messagePassing.IbisIdentifier other = (ibis.ipl.impl.messagePassing.IbisIdentifier)o;
	    // there is only one PandaIbis per cpu, so this should be ok
	    return cpu == other.cpu;
	}
	return false;
    }

    public String toString() {
	return ("(IbisIdent: name = " + name + ")");
    }

    public String name() {
	return name;
    }

    public int hashCode() {
	return name.hashCode();
    }
}
