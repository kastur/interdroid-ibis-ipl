// $Id$

/** A single clause in a symbolic Boolean expression. */

import java.io.PrintStream;

class Clause implements java.io.Serializable, Comparable {
    int label;
    int pos[];		// The positive terms
    int neg[];		// The negative terms

    /**
     * @param p the positive terms of the clause
     * @param n the negative terms of the clause
     * @param l the labels of the clause
     */
    public Clause( int p[], int n[], int l )
    {
        pos = p;
	neg = n;
	label = l;
    }

    /**
     * Note: this comparator imposes orderings that are inconsistent
     * with equals.
     */
    public int compareTo( Object other )
    {
	Clause co = (Clause) other;
	int nthis = pos.length + neg.length;
	int nother = co.pos.length + co.neg.length;

	if( nthis>nother ){
	    return 1;
	}
	if( nthis<nother ){
	    return -1;
	}
	return 0;
    }

    /**
     * Returns true iff 'l' contains 'n'.
     * @param l the list to search in
     * @param n the value to search for
     */
    static boolean memberIntList( int l[], int n )
    {
        for( int ix=0; ix<l.length; ix++ ){
	    if( l[ix] == n ){
		return true;
	    }
	}
	return false;
    }

    /**
     * Returns true iff lb contains all symbols in la.
     * @param la the reference list
     * @param lb the list that is being tested
     */
    static boolean isSubsetIntList( int la[], int lb[] )
    {
	for( int ix=0; ix<la.length; ix++ ){
	    if( !memberIntList( lb, la[ix] ) ){
		return false;
	    }
	}
	return true;
    }

    /**
     * Returns true iff clause 'cy' is subsumed by this clause.
     * @param cy the clause we compare to.
     */
    boolean isSubsumed( Clause cy )
    {
	return isSubsetIntList( this.pos, cy.pos ) &&
	    isSubsetIntList( this.neg, cy.neg );
    }

    /**
     * Given an array 'a', an array 'marks', and a flag 'flag', set the
     * given bits of all elements in 'marks' that have an index mentioned
     * in `a'.
     */
    private void setMarks( int marks[], int a[], int flag )
    {
        for( int ix=0; ix<a.length; ix++ ){
	    marks[a[ix]] |= flag;
	}
    }

    /**
     * Given two int arrays 'large' and 'small', where 'large' is exactly
     * one element longer than the small one, check whether all elements
     * in 'small' are in 'large'. Return the index of the one 
     * extra element, or -1 if there are more differences.
     * @param large the large array
     * @param small the small array
     */
    private int joinHalf( int large[], int small[], int varCount )
    {
	// We implement this function by marking all elements in `large'
	// and `small' in an array, and then searching for an element with
	// a special marking.
	int marks[] = new int[varCount];

	if( large.length != small.length+1 ){
	    System.err.println( "Large array has length " + large.length + ", but small array has length " + small.length );
	    return -1;
	}
	setMarks( marks, large, 1 );
	setMarks( marks, small, 2 );

	int special = -1;
	for( int ix=0; ix<varCount; ix++ ){
	    int v = marks[ix];

	    if( v == 2 ){
		// This element only occurs in `small', and not in `large'.
		// That can't be right.
	        return -1;
	    }
	    if( v == 1 ){
		// This element only occurs in `large', and not in `small'.
		// This is probably the one we're looking for.
		if( special != -1 ){
		    // There already is an element that only occurs in
		    // 'small', this can't be right.
		    return -1;
		}
		special = ix;
	    }
	}
	if( special != -1 ){
	    for( int ix=0; ix<large.length; ix++ ){
	        if( large[ix] == special ){
		    return ix;
		}
	    }
	}
	return -1;
    }

    /**
     * Returns true iff the given clause `cy' can be joined with this
     * clause to form a more general clause. This clause is updated to
     * the more general version.
     * @param cy the clause we try to join with
     */
    boolean join( Clause cy, int varCount )
    {
        int posa[] = pos;
        int posb[] = cy.pos;
        int nega[] = neg;
        int negb[] = cy.neg;

	// TODO: do something useful.
	// Looking at the sizes of the lists, there are only two
	// interesting cases:
	if( (posa.length+1 == posb.length) && (nega.length == negb.length+1) ){
	    int posix = joinHalf( posb, posa, varCount );
	    if( posix<0 ){
		// No join possible on the positive array.
	        return false;
	    }
	    int negix = joinHalf( nega, negb, varCount );
	    if( negix<0 ){
	        // No join possible on the negative array.
		return false;
	    }
	    if( posb[posix] != nega[negix] ){
		return false;
	    }
	    // The two clauses only differ in a single variable, that
	    // occurs negatively in our clause, and positively in `cy'.
	    // We can generalize by ommiting this variable from the clause.

	    // Fill the hole created by ommitting this variable by the
	    // last element in the array. If negix == nega.length-1, this
	    // is in essence a no-op.
	    nega[negix] = nega[nega.length-1];

	    // Now replace the neg array.
	    neg = Helpers.cloneIntArray( nega, nega.length-1 );
	    return true;
	}
	else if( (posa.length == posb.length+1) && (nega.length+1 == negb.length) ){
	    int posix = joinHalf( posa, posb, varCount );
	    if( posix<0 ){
		// No join possible on the positive array.
	        return false;
	    }
	    int negix = joinHalf( negb, nega, varCount );
	    if( negix<0 ){
	        // No join possible on the negative array.
		return false;
	    }
	    if( posa[posix] != negb[negix] ){
		return false;
	    }
	    // The two clauses only differ in a single variable, that
	    // occurs negatively in our clause, and positively in `cy'.
	    // We can generalize by ommiting this variable from the clause.

	    // Fill the hole created by ommitting this variable by the
	    // last element in the array. If negix == nega.length-1, this
	    // is in essence a no-op.
	    posa[negix] = posa[nega.length-1];

	    // Now replace the pos array.
	    pos = Helpers.cloneIntArray( posa, posa.length-1 );
	    return true;
	}
	return false;
    }

    /**
     * Returns true iff variable 'v' occurs as a positive term in this clause.
     */
    boolean occursPos( int var )
    {
        return memberIntList( pos, var );
    }

    /**
     * Return true iff variable 'v' occurs as a negative term in this clause.
     */
    boolean occursNeg( int var )
    {
        return memberIntList( neg, var );
    }

    /**
     * Registers that the specified variable is known to be true.
     * Returns true iff the clause is now satisfied.
     * @param var the variable that is known to be true
     * @return wether the clause is now satisfied
     */
    public boolean propagatePosAssignment( int var )
    {
        if( memberIntList( pos, var ) ){
	    // Clause is now satisfied.
	    return true;
	}
	// Now remove any occurence of 'var' in the 'neg' terms, since
	// they cannot satisfy the clause.
	for( int ix=0; ix<neg.length; ix++ ){
	    if( neg[ix] == var ){
		int nneg[] = Helpers.cloneIntArray( neg, neg.length-1 );
		if( ix<nneg.length ){
		    nneg[ix] = neg[neg.length-1];
		}
		neg = nneg;
	    }
	}
	return false;
    }

    /**
     * Registers that the specified variable is known to be false.
     * Returns true iff the clause is now satisfied.
     * @param var the variable that is known to be false
     * @return wether the clause is now satisfied
     */
    public boolean propagateNegAssignment( int var )
    {
        if( memberIntList( neg, var ) ){
	    // Clause is now satisfied.
	    return true;
	}
	// Now remove any occurence of 'var' in the 'pos' terms, since
	// they cannot satisfy the clause.
	for( int ix=0; ix<pos.length; ix++ ){
	    if( pos[ix] == var ){
		int npos[] = Helpers.cloneIntArray( pos, pos.length-1 );
		if( ix<npos.length ){
		    npos[ix] = pos[pos.length-1];
		}
		pos = npos;
	    }
	}
	return false;
    }

    /**
     * Given an array of assignments, return true iff this clause is
     * satisfied by these assignments.
     * @param assignments the assignments
     * @return wether the clause is now satisfied
     */
    public boolean isSatisfied( int assignments[] )
    {
	for( int ix=0; ix<pos.length; ix++ ){
	    int v = pos[ix];

	    if( assignments[v] == 1 ){
		return true;
	    }
	}
	for( int ix=0; ix<neg.length; ix++ ){
	    int v = neg[ix];

	    if( assignments[v] == 0 ){
		return true;
	    }
	}
	return false;
    }

    /**
     * If this clause is not a positive unit clause, return -1,
     * else return the variable that constitutes this clause.
     * @return The variable if this is a positive unit clause, or else -1.
     */
    public int getPosUnitVar()
    {
        if( neg.length != 0 ){
	    return -1;
	}
        if( pos.length != 1 ){
	    return -1;
	}
	return pos[0];
    }

    /**
     * If this clause is not a negative unit clause, return -1,
     * else return the variable that constitutes this clause.
     * @return The variable if this is a negative unit clause, or else -1.
     */
    public int getNegUnitVar()
    {
        if( pos.length != 0 ){
	    return -1;
	}
        if( neg.length != 1 ){
	    return -1;
	}
	return neg[0];
    }

    /**
     * Given an array of assignments, return true iff this clause conflicts
     * with these assignments.
     * @param assignments the assignments
     * @return wether the assignments conflict with this clause
     */
    public boolean isConflicting( int assignments[] )
    {
	// Search for any term of the clause that has an agreeing assignment
	// or is uncommitted.
	for( int ix=0; ix<pos.length; ix++ ){
	    int v = pos[ix];

	    if( assignments[v] != 0 ){
		return false;
	    }
	}
	for( int ix=0; ix<neg.length; ix++ ){
	    int v = neg[ix];

	    if( assignments[v] != 1 ){
		return false;
	    }
	}
	return true;
    }

    /**
     * Given an output stream, print the clause to it in DIMACS format.
     * @param s the stream to print to
     */
    public void printDIMACS( PrintStream s )
    {
	for( int ix=0; ix<pos.length; ix++ ){
	    s.print( (pos[ix]+1) + " " );
	}
	for( int ix=0; ix<neg.length; ix++ ){
	    s.print( "-" + (neg[ix]+1) + " " );
	}
	s.println( "0" );
    }

    /** Returns a string representation of this clause. */
    public String toString()
    {
        String res = "";
	boolean first = true;

	for( int ix=0; ix<pos.length; ix++ ){
	    if( !first ){
	        res += " ";
	    }
	    else {
	        first = false;
	    }
	    res += pos[ix];
	}
	for( int ix=0; ix<neg.length; ix++ ){
	    if( !first ){
	        res += " ";
	    }
	    else {
	        first = false;
	    }
	    res += "!" + neg[ix];
	}
	res += " (" + label + ")";
	return res;
    }
}
