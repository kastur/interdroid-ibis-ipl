/* $Id$ */

import java.util.*;

/**
 * This oct tree is designed as follows: A node has two modes: - cell node, with
 * zero to eight children (children != null) - a leaf node (children == null)
 * The 'children' field must be used to distinguish between the two modes.
 * 
 * When children == null, the body field can also still be null. This indicates
 * an empty tree (which could be the result of a cut off in the necessary tree
 * contruction).
 */

/*
 * TODO: - boom-maak-fase optimaliseren door alleen bodies die van plek
 * verwisselen te verplaatsen Let Op! Als deeltjes buiten de ruimte komen moet
 * (eigenlijk) de hele boom opnieuw gebalanceerd worden.. Alternatief: de root
 * van te voren heel wijd maken (* 1.5 oid) (hij is al ietsje wijder vanwege
 * precisieproblemen)
 */

/*strictfp*/final class BodyTreeNode extends ibis.satin.SatinObject implements
        BodyTreeNodeInterface {

    BodyTreeNode children[];

    Body[] bodies;

    /*
     * bodyCount is only used during (initial) tree building. After trimming,
     * bodies.length must be used (also in the necessaryTree-constructor)
     */
    int bodyCount;

    /*
     * The part of space this node represents. The fields are used during tree
     * construction, and by the necessaryTree-constructor so they can not be
     * made transient. In an alternative barneshut implementation, these fields
     * could be used (in a leaf node) to find out if a body has moved out of the
     * represented part
     */
    private double center_x, center_y, center_z;

    private double halfSize;

    /* these 4 variables are used during the force calculation */
    private double maxTheta; //set during initialisation

    private double com_x, com_y, com_z; //set during CoM computation

    private double totalMass; //set during CoM computation

    //usual potential softening value, copied from splash2-barnes
    //	static final double SOFT = 0.05;

    // this value is copied from Suel --Rob
    static final double SOFT = 0.0000025;

    static final double SOFT_SQ = SOFT * SOFT;

    // Extra margin of space used around the bodies.
    private static final double DIM_SLACK = 0.00001;

    /** Runtime paremeters. */
    RunParameters params;

    /**
     * creates a totally empty tree.
     */
    public BodyTreeNode() {
    }

    /**
     * Initializes center, halfSize and maxTheta
     * 
     * @param max
     *            the maximum point the tree should represent
     * @param min
     *            the minimum point the tree should represent
     */
    private void initCenterSizeMaxtheta(double max_x, double max_y,
        double max_z, double min_x, double min_y, double min_z) {

        double size;

        size = Math.max(max_x - min_x, max_y - min_y);
        size = Math.max(size, max_z - min_z);

        halfSize = size / 2.0;

        // center is the real center (without added DIM_SLACK)
        center_x = min_x + halfSize;
        center_y = min_y + halfSize;
        center_z = min_z + halfSize;

        // make size a little bigger to compensate for very small floating point
        size *= 1 + 2.0 * DIM_SLACK;

        halfSize = size / 2.0;
        maxTheta = params.THETA * params.THETA * halfSize * halfSize;

        //        System.out.println("theta = " + params.THETA + ", halfSize = " + halfSize + ", maxx = " + max_x + ", minx = " + min_x + ", maxTheta = " + maxTheta);
    }

    //constructor to create an empty tree, used during tree contruction
    private BodyTreeNode(RunParameters params, double centerX, double centerY,
            double centerZ, double halfSize, double maxTheta) {
        //children = null and bodies = null by default
        this.center_x = centerX;
        this.center_y = centerY;
        this.center_z = centerZ;
        this.halfSize = halfSize;
        this.maxTheta = maxTheta;
        this.params = params;
    }

    /**
     * Generates a new tree with dimensions exactly large enough to contain all
     * bodies.
     * 
     * @param bodyArray
     *            the bodies to add
     * @param maxLeafBodies
     *            the maximum number of bodies to put in a leaf node
     * @param params
     *            The run parameters
     */
    public BodyTreeNode(Body[] bodyArray, int maxLeafBodies, RunParameters params) {
        int i;
        this.params = params;
        double max_x = -1000000.0, max_y = -1000000.0, max_z = -1000000.0, min_x = 1000000.0, min_y = 1000000.0, min_z = 1000000.0;

        for (i = 0; i < bodyArray.length; i++) {
            max_x = Math.max(max_x, bodyArray[i].pos_x);
            max_y = Math.max(max_y, bodyArray[i].pos_y);
            max_z = Math.max(max_z, bodyArray[i].pos_z);
            min_x = Math.min(min_x, bodyArray[i].pos_x);
            min_y = Math.min(min_y, bodyArray[i].pos_y);
            min_z = Math.min(min_z, bodyArray[i].pos_z);
        }

        initCenterSizeMaxtheta(max_x, max_y, max_z, min_x, min_y, min_z);

        for (i = 0; i < bodyArray.length; i++) {
            addBody(bodyArray[i], maxLeafBodies);
        }

//        trim();
    }

    private static double calcSquare(double com, double center, double halfSize) {
        if (com > center + halfSize) {
            double dist1D = com - (center + halfSize);
            return dist1D * dist1D;
        } else if (com < center - halfSize) {
            double dist1D = (center - halfSize) - com;
            return dist1D * dist1D;
        }

        // centerOfMass is in this dimension between the limits of job
        return 0.0;
    }

    /**
     * Necessary Tree Constructor: Creates a recursive copy of 'original',
     * containing exactly the parts that are needed to compute the interactions
     * with the bodies in 'job'
     */
    private BodyTreeNode(BodyTreeNode original, BodyTreeNode job) {
        center_x = original.center_x;
        center_y = original.center_y;
        center_z = original.center_z;
        halfSize = original.halfSize;

        maxTheta = original.maxTheta;
        com_x = original.com_x;
        com_y = original.com_y;
        com_z = original.com_z;
        totalMass = original.totalMass;
        params = original.params;
        bodyCount = 0;

        // calculate if original can be cut off

        double distsq = calcSquare(com_x, job.center_x, job.halfSize);
        distsq += calcSquare(com_y, job.center_y, job.halfSize);
        distsq += calcSquare(com_z, job.center_z, job.halfSize);

        if (distsq >= maxTheta) return; // cutoff IS possible, don't copy the original 

        // no cutoff possible, copy the necessary parts of original

        if (original.children == null) {
            bodies = original.bodies;
            bodyCount = original.bodyCount;
        } else {
            // cell node, recursively create/copy necessary parts
            children = new BodyTreeNode[8];
            for (int i = 0; i < 8; i++) {
                if (original.children[i] != null) {
                    if (original.children[i] == job) {
                        // don't copy job, as it is fully necessary ;-)
                        children[i] = job;
                    } else {
                        children[i] = new BodyTreeNode(original.children[i],
                            job);
                    }
                    bodyCount += children[i].bodyCount;
                }
            }
        }
    }

    /**
     * determines if the point indicated by pos is in or outside this node
     */
    private boolean outOfRange(double pos_x, double pos_y, double pos_z) {
        if (Math.abs(pos_x - center_x) > halfSize
            || Math.abs(pos_y - center_y) > halfSize
            || Math.abs(pos_z - center_z) > halfSize) {
            return true;
        } else {
            return false;
        }
    }

    private void printOutOfRange(java.io.PrintStream out, double pos_x,
        double pos_y, double pos_z) {
        double xdiff = Math.abs(pos_x - center_x) - halfSize;
        double ydiff = Math.abs(pos_y - center_y) - halfSize;
        double zdiff = Math.abs(pos_z - center_z) - halfSize;
        if (xdiff > 0.0) out.println("x : " + xdiff);
        if (ydiff > 0.0) out.println("y : " + ydiff);
        if (zdiff > 0.0) out.println("z : " + zdiff);
    }

    private double[] computeChildCenter(int childIndex, double[] newCenter) {
        double newHalfSize = halfSize / 2.0;

        if ((childIndex & 1) != 0) { //lower bit: x dimension
            newCenter[0] = center_x + newHalfSize;
        } else {
            newCenter[0] = center_x - newHalfSize;
        }
        if ((childIndex & 2) != 0) { //middle bit: y dimension
            newCenter[1] = center_y + newHalfSize;
        } else {
            newCenter[1] = center_y - newHalfSize;
        }
        if ((childIndex & 4) != 0) { //upper bit: z dimension
            newCenter[2] = center_z + newHalfSize;
        } else {
            newCenter[2] = center_z - newHalfSize;
        }

        return newCenter;
    }

    /**
     * Adds 'b' to 'this' or its children
     */
    private void addBody(Body b, int maxLeafBodies) {
        if (children != null) { // cell node
            addBody2Cell(b, maxLeafBodies);
            bodyCount++;
            return;
        }

        // leaf node
        if (BarnesHut.ASSERTS && outOfRange(b.pos_x, b.pos_y, b.pos_z)) {
            System.err.println("EEK! Adding out-of-range body! "
                + "Body position: " + b.pos_x + ", " + b.pos_y + ", " + b.pos_z
                + " id: " + b.number);

            System.err.println("     Center: " + center_x + ", " + center_y
                + ", " + center_z + " halfSize: " + halfSize);
            printOutOfRange(System.err, b.pos_x, b.pos_y, b.pos_z);
            throw new IndexOutOfBoundsException("Out-of-range body!");
        }

        if (bodyCount < maxLeafBodies) { // we have room left
            if (bodyCount == 0) bodies = new Body[maxLeafBodies];
            bodies[bodyCount] = b;
            bodyCount++;
            totalMass += b.mass;
            return;
        }

        // we'll have to convert ourselves to a cell
        children = new BodyTreeNode[8];
        addBody2Cell(b, maxLeafBodies);
        for (int i = 0; i < bodyCount; i++) {
            addBody2Cell(bodies[i], maxLeafBodies);
        }
        bodyCount++;
        bodies = null;
        // totalMass is overwritten in the CoM computation
    }

    /**
     * This method is used if 'this' is a cell, to add a body to the appropiate child.
     */
    private void addBody2Cell(Body b, int maxLeafBodies) {
        int child = 0;

        if (b.pos_x - center_x >= 0.0) child |= 1;
        if (b.pos_y - center_y >= 0.0) child |= 2;
        if (b.pos_z - center_z >= 0.0) child |= 4;

        if (children[child] != null) {
            children[child].addBody(b, maxLeafBodies);
            return;
        }

        /*
         * We could compute 'newCenter' directly during the calculation of
         * 'child', but with a large tree we would do it at every depth we
         * pass while adding the node..
         */
        double newCenterX, newCenterY, newCenterZ;
        double newHalfSize = halfSize / 2.0;

        if ((child & 1) != 0) { //lower bit: x dimension
            newCenterX = center_x + newHalfSize;
        } else {
            newCenterX = center_x - newHalfSize;
        }
        if ((child & 2) != 0) { //middle bit: y dimension
            newCenterY = center_y + newHalfSize;
        } else {
            newCenterY = center_y - newHalfSize;
        }
        if ((child & 4) != 0) { //upper bit: z dimension
            newCenterZ = center_z + newHalfSize;
        } else {
            newCenterZ = center_z - newHalfSize;
        }

        children[child] = new BodyTreeNode(params, newCenterX, newCenterY,
                newCenterZ, halfSize / 2.0, maxTheta / 4.0);
        children[child].bodies = new Body[maxLeafBodies];
        children[child].bodies[0] = b;
        children[child].bodyCount = 1;
    }

    /**
     * Makes the tree smaller by: - replacing the 'bodies' array in leaf nodes
     * by one that is exactly large enough to hold all bodies (The array is
     * initialised with 'maxLeafBodies' elements
     * 
     * During force calculation, trimming has only been useful for the job since
     * the copy-constructor automatically trims original
     */
    public void trim() {
        if (children == null) {
            //leaf node
            if (bodies.length != bodyCount) {
                Body[] newBodies = new Body[bodyCount];
                System.arraycopy(bodies, 0, newBodies, 0, bodyCount);
                bodies = newBodies;
            }
        } else {
            //cell node, process all children
            for (int i = 0; i < 8; i++) {
                if (children[i] != null) children[i].trim();
            }
        }
    }

    public void computeCentersOfMass() {
        com_x = com_y = com_z = 0.0;
        totalMass = 0.0;

        if (children == null) { //leaf node
            if (BarnesHut.ASSERTS && (bodies == null || bodies.length == 0)) {
                System.err.println("computeCoM: Found empty leaf node!");
                return;
            }

            for (int i = 0; i < bodies.length; i++) {
                com_x += bodies[i].pos_x * bodies[i].mass;
                com_y += bodies[i].pos_y * bodies[i].mass;
                com_z += bodies[i].pos_z * bodies[i].mass;
                totalMass += bodies[i].mass;
            }

            com_x /= totalMass;
            com_y /= totalMass;
            com_z /= totalMass;
            return;
        }

        // cell node
        // -> first process all children, then compute my center-of-mass
        // maybe satinize this later, then the loop has to be split up
        for (int i = 0; i < 8; i++) {
            if (children[i] != null) {
                children[i].computeCentersOfMass();

                com_x += children[i].com_x * children[i].totalMass;
                com_y += children[i].com_y * children[i].totalMass;
                com_z += children[i].com_z * children[i].totalMass;
                totalMass += children[i].totalMass;
            }
        }
        // then here comes a sync() and a loop to compute my CoM;
        com_x /= totalMass;
        com_y /= totalMass;
        com_z /= totalMass;

        //        System.out.println("set com to: " + com_x + ", " + com_y + ", " + com_z);
    }

    /**
     * Computes the acceleration which the bodies in 'this' give to a body at
     * position 'pos' 'this' can be a tree which is the result of necessaryTree
     * construction, some parts may be cut off. We exploit this by first
     * checking if the tree below 'this' has been cut off. Then the distance
     * calculation is already done during necessarryTree construction
     */
    public double[] barnesBody(double pos_x, double pos_y, double pos_z) {
        double diff_x, diff_y, diff_z;
        double[] totalAcc = new double[3];
        double dist, distsq, factor;
        int i;

        diff_x = com_x - pos_x;
        diff_y = com_y - pos_y;
        diff_z = com_z - pos_z;

        distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z;

        //        System.out.println("distsq = " + distsq + ", maxTheta = " + maxTheta);        
        /*
         * In the if-statement below we could only check if we are cut off
         * (children == null && bodies == null), but then the 'invalid cutoff'
         * ASSERT statement doesn't make sense anymore, and: The (square)
         * distance computed here is *LARGER* than the distance computed by the
         * necessaryTree construction (which uses the boundary of the job we're
         * working on), so we can still test if the distance is large enough to
         * use my CoM
         */

        if (distsq >= maxTheta) {
            distsq += params.SOFT_SQ;
            dist = Math.sqrt(distsq);
            factor = totalMass / (distsq * dist);

            totalAcc[0] = diff_x * factor;
            totalAcc[1] = diff_y * factor;
            totalAcc[2] = diff_z * factor;

            return totalAcc;
        }

        if (children == null) {
            // Leaf node, compute interactions with all my bodies

            if (BarnesHut.ASSERTS && (bodies == null || bodies.length == 0)) {
                System.err.println("EEK! invalid cutoff in barnes(vec3)");
                System.exit(1);
            }

            for (i = 0; i < bodies.length; i++) {
                diff_x = bodies[i].pos_x - pos_x;
                diff_y = bodies[i].pos_y - pos_y;
                diff_z = bodies[i].pos_z - pos_z;

                distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z
                    + params.SOFT_SQ;

                dist = Math.sqrt(distsq);
                factor = bodies[i].mass / (distsq * dist);

                totalAcc[0] += diff_x * factor;
                totalAcc[1] += diff_y * factor;
                totalAcc[2] += diff_z * factor;
            }
            return totalAcc;
        }

        // Cell node
        for (i = 0; i < 8; i++) {
            double[] childresult;
            if (children[i] != null) {
                childresult = children[i].barnesBody(pos_x, pos_y, pos_z);
                totalAcc[0] += childresult[0];
                totalAcc[1] += childresult[1];
                totalAcc[2] += childresult[2];
            }
        }

        return totalAcc;
    }

    /**
     * Computes the acceleration which the bodies in 'this' give to a body at
     * position 'pos' 'this' can be a tree which is the result of necessaryTree
     * construction, some parts may be cut off. We exploit this by first
     * checking if the tree below 'this' has been cut off. Then the distance
     * calculation is already done during necessarryTree construction
     */
    public void barnesBodyRob(double pos_x, double pos_y, double pos_z,
        double[] dest_x, double[] dest_y, double[] dest_z, int destPos) {

        double diff_x = com_x - pos_x;
        double diff_y = com_y - pos_y;
        double diff_z = com_z - pos_z;

        double distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z;

        /*
         * In the if-statement below we could only check if we are cut off
         * (children == null && bodies == null), but then the 'invalid cutoff'
         * ASSERT statement doesn't make sense anymore, and: The (square)
         * distance computed here is *LARGER* than the distance computed by the
         * necessaryTree construction (which uses the boundary of the job we're
         * working on), so we can still test if the distance is large enough to
         * use my CoM
         */

        if (distsq >= maxTheta) {
            distsq += params.SOFT_SQ;
            double dist = Math.sqrt(distsq);
            double factor = totalMass / (distsq * dist);

            dest_x[destPos] += diff_x * factor;
            dest_y[destPos] += diff_y * factor;
            dest_z[destPos] += diff_z * factor;

            return;
        }

        if (children == null) {
            // Leaf node, compute interactions with all my bodies

            if (BarnesHut.ASSERTS && (bodies == null || bodies.length == 0)) {
                System.err.println("EEK! invalid cutoff in barnes(vec3)");
                System.exit(1);
            }

            for (int i = 0; i < bodies.length; i++) {
                diff_x = bodies[i].pos_x - pos_x;
                diff_y = bodies[i].pos_y - pos_y;
                diff_z = bodies[i].pos_z - pos_z;

                distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z
                    + params.SOFT_SQ;

                double dist = Math.sqrt(distsq);
                double factor = bodies[i].mass / (distsq * dist);

                dest_x[destPos] += diff_x * factor;
                dest_y[destPos] += diff_y * factor;
                dest_z[destPos] += diff_z * factor;
            }
            
            return;
        }

        // Cell node
        for (int i = 0; i < 8; i++) {
            if (children[i] != null) {
                children[i].barnesBodyRob(pos_x, pos_y, pos_z, dest_x, dest_y,
                    dest_z, destPos);
            }
        }
    }

    /**
     * debug version of barnesBody(pos_x, pos_y, pos_z)
     */
    public double[] barnesBodyDbg(double pos_x, double pos_y, double pos_z,
        boolean debug) {
        double diff_x, diff_y, diff_z;
        double[] totalAcc = new double[3];
        double dist, distsq, factor;
        int i;

        diff_x = com_x - pos_x;
        diff_y = com_y - pos_y;
        diff_z = com_z - pos_z;

        distsq = diff_x * diff_x + diff_y * diff_y + diff_z * diff_z;

        if (debug) {
            System.out.println();
            System.out.println("Barnes: new level:");
            System.out.println(" CoM pos = (" + com_x + ", " + com_y + ", "
                + com_z + ")");
            System.out.println(" pos = (" + pos_x + ", " + pos_y + ", " + pos_z
                + ")");
            System.out.println(" diff = (" + diff_x + ", " + diff_y + ", "
                + diff_z + ")");
            System.out.println(" distsq = " + distsq + " maxTheta = "
                + maxTheta);
        }

        if (distsq >= maxTheta) {
            /*
             * The distance was large enough to use my centerOfMass instead of
             * iterating my children
             */
            distsq += params.SOFT_SQ;
            dist = Math.sqrt(distsq);
            factor = totalMass / (distsq * dist);

            totalAcc[0] = diff_x * factor;
            totalAcc[1] = diff_y * factor;
            totalAcc[2] = diff_z * factor;

            if (debug) {
                System.out.println("  CoM interaction:");
                System.out.println("  added (" + totalAcc[0] + ", "
                    + totalAcc[1] + ", " + totalAcc[2] + ")");
            }
            return totalAcc;
        }

        // else

        if (children == null) {
            // Leaf node, compute interactions with all my bodies
            if (BarnesHut.ASSERTS && (bodies == null || bodies.length == 0)) {
                System.err.println("EEK! invalid cutoff in "
                    + "barnes(vec3)(debug version)");
                System.exit(1);
            }

            for (i = 0; i < bodies.length; i++) {
                if (debug) {
                    System.out
                        .println("  Interaction with " + bodies[i].number);
                }

                diff_x = bodies[i].pos_x - pos_x;
                diff_y = bodies[i].pos_y - pos_y;
                diff_z = bodies[i].pos_z - pos_z;

                distsq = diff_x * diff_x + diff_y * diff_y;
                distsq += diff_z * diff_z + params.SOFT_SQ;

                dist = Math.sqrt(distsq);
                factor = bodies[i].mass / (distsq * dist);

                totalAcc[0] += diff_x * factor;
                totalAcc[1] += diff_y * factor;
                totalAcc[2] += diff_z * factor;

                if (debug) {
                    System.out.println("  distsq, dist, factor: " + distsq
                        + ", " + dist + ", " + factor);
                    System.out.println("  added (" + diff_x * factor + ", "
                        + diff_y * factor + ", " + diff_z * factor + ")");
                }
            }
        } else { // Cell node
            for (i = 0; i < 8; i++) {
                double[] childresult;
                if (children[i] != null) {
                    childresult = children[i].barnesBodyDbg(pos_x, pos_y,
                        pos_z, debug);
                    totalAcc[0] += childresult[0];
                    totalAcc[1] += childresult[1];
                    totalAcc[2] += childresult[2];
                }
            }
        }
        return totalAcc;
    }

    /**
     * computes the iteractions between [ the bodies in 'this' ] and [
     * 'interactTree' ], by recursively splitting up 'this', and calling
     * interactTree.barnes(bodies[i].pos) for all the bodies when 'this' is a
     * leaf node.
     */
    // @@@ one optimization that we can still do: don't use three arrays for x, y, z, but just one, and interleave the values. --Rob
    public void barnesSequential(BodyTreeNode interactTree, ArrayList results) {
        if (children != null) { // cell node -> call children[].barnes()
            for (int i = 0; i < 8; i++) {
                if (children[i] != null) {
                    children[i].barnesSequential(interactTree, results);
                }
            }

            return;
        }

        //leaf node

        if (BarnesHut.ASSERTS && (bodies == null || bodies.length == 0)) {
            System.err.println("barnes(interactTree): "
                + "found empty leafnode!");
            return;
        }

        int[] bodyNumbers = new int[bodies.length];
        double[] accs_x = new double[bodies.length];
        double[] accs_y = new double[bodies.length];
        double[] accs_z = new double[bodies.length];

        for (int i = 0; i < bodies.length; i++) {
            bodyNumbers[i] = bodies[i].number;
            if (true) {
                interactTree.barnesBodyRob(bodies[i].pos_x, bodies[i].pos_y,
                    bodies[i].pos_z, accs_x, accs_y, accs_z, i);
            } else {
                double[] acc = interactTree.barnesBody(bodies[i].pos_x,
                    bodies[i].pos_y, bodies[i].pos_z);
                accs_x[i] = acc[0];
                accs_y[i] = acc[1];
                accs_z[i] = acc[2];
            }
        }

        results.add(bodyNumbers);
        results.add(accs_x);
        results.add(accs_y);
        results.add(accs_z);
    }

    public ArrayList doBarnes(BodyTreeNode tree, int low, int high) {
        if (children == null) {
            // leaf node, let barnesSequential handle this
            // (using optimizeList isn't useful for leaf nodes)
            ArrayList res = new ArrayList();
            barnesSequential(tree, res);
            return res;
        }

        //cell node -> call children[].barnes()
        ArrayList res[] = new ArrayList[8];
        int lastValidChild = -1;
        boolean spawned = false;

        for (int i = 0; i < 8; i++) {
            BodyTreeNode ch = children[i];
            if (ch != null) {
                if (ch.bodyCount < low) {
                    res[i] = new ArrayList();
                    ch.barnesSequential(tree, res[i]);
                } else {
                    if (ch.bodyCount > high) {
                        BodyTreeNode n = tree;
                        if (BarnesHut.impl == BarnesHut.IMPL_NTC) {
                            n = ch == tree ? tree : new BodyTreeNode(tree, ch);
                        }
                        res[i] = ch.doBarnes(n, low, high);
                    } else {
                        spawned = true;
                        // System.out.println("doBarnes spawning, count = " + ch.bodyCount);
                        BodyTreeNode necessaryTree = ch == tree ? tree
                            : new BodyTreeNode(tree, ch);
                        res[i] = ch.barnesNTC(necessaryTree, low);
                    }
                }
                lastValidChild = i;
            }
        }
        if (spawned) {
            sync();
            return combineResults(res, lastValidChild);
        }
        //this was a sequential job, optimize!
        return optimizeList(combineResults(res, lastValidChild));
    }

    /**
     * Does the same as barnesSequential, but spawnes itself until a threshold
     * is reached. Before a subjob is spawned, the necessary tree for that
     * subjob is created to be passed to the subjob.
     * 
     * @param threshold
     *            the recursion depth at which work shouldn't be spawned anymore
     */
    public ArrayList barnesNTC(BodyTreeNode interactTree, int threshold) {
        if (children == null || bodyCount < threshold) {
            // leaf node, let barnesSequential handle this
            // (using optimizeList isn't useful for leaf nodes)
            ArrayList res = new ArrayList();
            barnesSequential(interactTree, res);
            return res;
        }

        //cell node -> call children[].barnes()
        ArrayList res[] = new ArrayList[8];
        int lastValidChild = -1;
        boolean spawned = false;

        for (int i = 0; i < 8; i++) {
            BodyTreeNode ch = children[i];
            if (ch != null) {
                if (ch.children == null) {
                    res[i] = new ArrayList();
                    ch.barnesSequential(interactTree, res[i]);
                } else {
                    //necessaryTree creation
                    BodyTreeNode necessaryTree = ch == interactTree
                        ? interactTree : new BodyTreeNode(interactTree, ch);
                    res[i] = ch.barnesNTC(necessaryTree, threshold);
                    spawned = true;

                    //alternative: copy whole tree
                    //res[i] = children[i].barnesNTC(interactTree,
                    //		       threshold-1);
                }
                lastValidChild = i;
            }
        }
        if (spawned) {
            sync();
            return combineResults(res, lastValidChild);
        }
        //this was a sequential job, optimize!
        return optimizeList(combineResults(res, lastValidChild));
    }

    /**
     * adds all items in results[x] (x < lastValidIndex) to
     * results[lastValidIndex]
     * 
     * @return a reference to results[lastValidIndex], for convenience
     */
    private static ArrayList combineResults(ArrayList[] results,
        int lastValidIndex) {

        if (BarnesHut.ASSERTS && lastValidIndex < 0) {
            System.err.println("BodyTreeNode.combineResults: EEK! "
                + "lvi < 0! All children are null in caller!");
            System.exit(1);
        }

        for (int i = 0; i < lastValidIndex; i++) {
            if (results[i] != null) {
                results[lastValidIndex].addAll(results[i]);
            }
        }

        return results[lastValidIndex];
    }

    /**
     * @param suboptimal
     *            A list with (a lot of) bodyNumber and acc arrays
     * @return A list with only one bodyNumber and acc array, containing all the
     *         elements of these arrays in 'suboptimal' If all arrays in
     *         suboptimal are empty, an empty list is returned
     */
    private static ArrayList optimizeList(ArrayList suboptimal) {
        int totalElements = 0;
        for (int i = 0; i < suboptimal.size(); i += 4) { // 4 elts in the list: x, y, z and body nr
            totalElements += ((int[]) suboptimal.get(i)).length;
        }

        if (totalElements == 0) {
            return new ArrayList(); //nothing to optimize
        }

        int[] allBodyNrs = new int[totalElements];
        double[] allAccs_x = new double[totalElements];
        double[] allAccs_y = new double[totalElements];
        double[] allAccs_z = new double[totalElements];

        int[] bodyNrs = null;
        double[] accs_x = null, accs_y = null, accs_z = null;
        int position = 0;

        for (int i = 0; i < suboptimal.size(); i += 4) { // 4 elts in the list: x, y, z and body nr
            bodyNrs = (int[]) suboptimal.get(i);
            accs_x = (double[]) suboptimal.get(i + 1);
            accs_y = (double[]) suboptimal.get(i + 2);
            accs_z = (double[]) suboptimal.get(i + 3);

            int len = bodyNrs.length;
            System.arraycopy(bodyNrs, 0, allBodyNrs, position, len);
            System.arraycopy(accs_x, 0, allAccs_x, position, len);
            System.arraycopy(accs_y, 0, allAccs_y, position, len);
            System.arraycopy(accs_z, 0, allAccs_z, position, len);

            position += len;
        }

        ArrayList optimal = new ArrayList();
        optimal.add(allBodyNrs);
        optimal.add(allAccs_x);
        optimal.add(allAccs_y);
        optimal.add(allAccs_z);

        return optimal;
    }

    public void print(java.io.PrintStream out, int level) {
        int i, j;

        if (level == 0) {
            out.println("halfSize = " + halfSize);
            out.print("center");
        }

        out.println(" at " + center_x + ", " + center_y + ", " + center_z);

        out.print("CoM at " + com_x + ", " + com_y + ", " + com_z);

        if (children == null) {
            out.print(": leaf, ");

            if (bodies == null) {
                out.println("has been cut off");
            } else {
                out.println(bodies.length + " bodies");

                for (j = 0; j < bodies.length; j++) {
                    for (i = 0; i < level + 1; i++)
                        out.print(" ");
                    if (bodies[j] != null) {
                        out.println("body #" + bodies[j].number + " at "
                            + bodies[j].pos_x + ", " + bodies[j].pos_y + ", "
                            + bodies[j].pos_z);
                    } else {
                        out.println("body: null");
                    }
                }
            }
        } else {
            out.println();
            for (j = 0; j < 8; j++) {
                if (children[j] != null) {
                    for (i = 0; i < level + 1; i++)
                        out.print(" ");
                    out.print("child #" + j);
                    //the child will print its status + the newline
                    children[j].print(out, level + 1);
                }
            }
        }
    }
}
