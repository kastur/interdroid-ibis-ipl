/**************************************************************************
*                                                                         *
*             Java Grande Forum Benchmark Suite - MPJ Version 1.0         *
*                                                                         *
*                            produced by                                  *
*                                                                         *
*                  Java Grande Benchmarking Project                       *
*                                                                         *
*                                at                                       *
*                                                                         *
*                Edinburgh Parallel Computing Centre                      *
*                                                                         *
*                email: epcc-javagrande@epcc.ed.ac.uk                     *
*                                                                         *
*      Original version of this code by Hon Yau (hwyau@epcc.ed.ac.uk)     *
*                                                                         *
*      This version copyright (c) The University of Edinburgh, 2001.      *
*                         All rights reserved.                            *
*                                                                         *
**************************************************************************/
/**************************************************************************
* Ported to MPJ:                                                          *
* Markus Bornemann                                                        * 
* Vrije Universiteit Amsterdam Department of Computer Science             *
* 19/06/2005                                                              *
**************************************************************************/


package montecarlo;
/**
  * Class for defining a task, for the applications demonstrator.
  *
  * @author H W Yau
  * @version $Revision$ $Date$
  */
public class ToTask implements java.io.Serializable {
  private String header;
  private long randomSeed;

  public ToTask(String header, long randomSeed) {
    this.header         = header;
    this.randomSeed     = randomSeed;
  }
  //------------------------------------------------------------------------
  // Accessor methods for class ToTask.
  // Generated by 'makeJavaAccessor.pl' script.  HWY.  20th January 1999.
  //------------------------------------------------------------------------
  /**
    * Accessor method for private instance variable <code>header</code>.
    *
    * @return Value of instance variable <code>header</code>.
    */
  public String get_header() {
    return(this.header);
  }
  /**
    * Set method for private instance variable <code>header</code>.
    *
    * @param header the value to set for the instance variable <code>header</code>.
    */
  public void set_header(String header) {
    this.header = header;
  }
  /**
    * Accessor method for private instance variable <code>randomSeed</code>.
    *
    * @return Value of instance variable <code>randomSeed</code>.
    */
  public long get_randomSeed() {
    return(this.randomSeed);
  }
  /**
    * Set method for private instance variable <code>randomSeed</code>.
    *
    * @param randomSeed the value to set for the instance variable <code>randomSeed</code>.
    */
  public void set_randomSeed(long randomSeed) {
    this.randomSeed = randomSeed;
  }
  //------------------------------------------------------------------------
}
