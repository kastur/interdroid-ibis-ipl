// File: $Id$

/**
 * A sequential SAT solver. Given a symbolic boolean equation in CNF, find a set
 * of assignments that make this equation true.
 * 
 * This implementation tries to do all the things a professional SAT
 * solver would do, although we are limited by implementation time and
 * the fact that we need to parallelize the stuff.
 * 
 * @author Kees van Reeuwijk
 * @version $Revision$
 */

import java.io.File;

public final class SeqSolver {
    private static final boolean traceSolver = false;
    private static final boolean printSatSolutions = true;
    private static final boolean traceNewCode = true;
    private static int label = 0;
    private int decisions = 0;

    /**
     * Solve the leaf part of a SAT problem.
     * The method throws a SATResultException if it finds a solution,
     * or terminates normally if it cannot find a solution.
     * @param level branching level
     * @param p the SAT problem to solve
     * @param ctx the changable context of the solver
     * @param var the next variable to assign
     * @param val the value to assign
     */
    public void leafSolve(
	int level,
	SATProblem p,
	SATContext ctx,
	int var,
	boolean val
    ) throws SATResultException, SATRestartException
    {
	if( traceSolver ){
	    System.err.println( "ls" + level + ": trying assignment var[" + var + "]=" + val );
	}
	ctx.assignment[var] = val?(byte) 1:(byte) 0;
	int res;
	if( val ){
	    res = ctx.propagatePosAssignment( p, var, level );
	}
	else {
	    res = ctx.propagateNegAssignment( p, var, level );
	}
	if( res == SATProblem.CONFLICTING ){
	    if( traceSolver ){
		System.err.println( "ls" + level + ": propagation found a conflict" );
	    }
	    return;
	}
	if( res == SATProblem.SATISFIED ){
	    // Propagation reveals problem is satisfied.
	    SATSolution s = new SATSolution( ctx.assignment );

	    if( traceSolver | printSatSolutions ){
		System.err.println( "ls" + level + ": propagation found a solution: " + s );
	    }
	    if( !p.isSatisfied( ctx.assignment ) ){
		System.err.println( "Error: " + level + ": solution does not satisfy problem." );
	    }
	    throw new SATResultException( s );
	}
	int nextvar = ctx.getDecisionVariable();
	if( nextvar<0 ){
	    // There are no variables left to assign, clearly there
	    // is no solution.
	    if( traceSolver ){
		System.err.println( "ls" + level + ": nothing to branch on" );
	    }
	    return;
	}
        decisions++;

	boolean firstvar = ctx.posDominant( nextvar );
	SATContext subctx = (SATContext) ctx.clone();
        try {
            leafSolve( level+1, p, subctx, nextvar, firstvar );
        }
        catch( SATRestartException x ){
	    if( x.level<level ){
		//System.err.println( "RestartException passes level " + level + " heading for level " + x.level );
		throw x;
	    }
        }
	// Since we won't be using our context again, we may as well
	// give it to the recursion.
	// Also note that this call is a perfect candidate for tail
	// call elimination.
        // However, we must update the administration with any
        // new clauses that we've learned recently.
        ctx.update( p );
	leafSolve( level+1, p, ctx, nextvar, !firstvar );
    }

    /**
     * Given a SAT problem, returns a solution, or <code>null</code> if
     * there is no solution.
     * @param p The problem to solve.
     * @return a solution of the problem, or <code>null</code> if there is no solution
     */
    SATSolution solveSystem( final SATProblem p )
    {
	SATSolution res = null;

	if( p.isConflicting() ){
	    return null;
	}
	if( p.isSatisfied() ){
	    return new SATSolution( p.buildInitialAssignments() );
	}
	int oldClauseCount = p.getClauseCount();

        // Now recursively try to find a solution.
	try {
	    SATContext ctx = SATContext.buildSATContext( p );

	    ctx.assignment = p.buildInitialAssignments();

	    int r = ctx.optimize( p );
	    if( r == SATProblem.SATISFIED ){
		if( !p.isSatisfied( ctx.assignment ) ){
		    System.err.println( "Error: solution does not satisfy problem." );
		}
		return new SATSolution( ctx.assignment );
	    }
	    if( r == SATProblem.CONFLICTING ){
		return null;
	    }

	    int nextvar = ctx.getDecisionVariable();
	    if( nextvar<0 ){
		// There are no variables left to assign, clearly there
		// is no solution.
		if( traceSolver | traceNewCode ){
		    System.err.println( "top: nothing to branch on" );
		}
		return null;
	    }
	    if( traceSolver ){
		System.err.println( "Top level: branching on variable " + nextvar );
	    }
            decisions++;

	    SATContext negctx = (SATContext) ctx.clone();
	    boolean firstvar = ctx.posDominant( nextvar );
            try {
                leafSolve( 0, p, negctx, nextvar, firstvar );
            }
            catch( SATRestartException x ){
                // Restart the search here, since we have an untried
                // value.
            }
            ctx.update( p );
            leafSolve( 0, p, ctx, nextvar, !firstvar );
	}
	catch( SATResultException r ){
	    if( r.s == null ){
		System.err.println( "A null solution thrown???" );
	    }
	    res = r.s;
	}
        catch( SATException x ){
            System.err.println( "Uncaught " + x + "???" );
        }

	int newClauseCount = p.getClauseCount();
	System.err.println( "Learned " + (newClauseCount-oldClauseCount) + " clauses." );
	return res;
    }

    /**
     * Allows execution of the class.
     * @param args The command-line arguments.
     */
    public static void main( String args[] ) throws java.io.IOException
    {
	if( args.length != 1 ){
	    System.err.println( "Exactly one filename argument required." );
	    System.exit( 1 );
	}
	File f = new File( args[0] );
	if( !f.exists() ){
	    System.err.println( "File does not exist: " + f );
	    System.exit( 1 );
	}
	SATProblem p = SATProblem.parseDIMACSStream( f );
	p.setReviewer( new CubeClauseReviewer() );
	p.report( System.out );
	p.optimize();
	p.report( System.out );
        SeqSolver s = new SeqSolver();
	long startTime = System.currentTimeMillis();
	SATSolution res = s.solveSystem( p );

	long endTime = System.currentTimeMillis();
	double time = ((double) (endTime - startTime))/1000.0;

	p.report( System.out );
	System.out.println( "ExecutionTime: " + time );
        System.out.println( "Decisions: " + s.decisions );
	if( res == null ){
	    System.out.println( "There are no solutions" );
	}
	else {
	    System.out.println( "There is a solution: " + res );
	}
    }
}
