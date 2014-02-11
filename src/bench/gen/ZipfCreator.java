/*
 * ZipfCreator.java Copyright (c) 2003 Timo Boehme, University of Leipzig, Database Group.
 * All rights reserved.
 *
 * This software is the Reference Implementation for the XMach-1 benchmark
 * (see http://dbs.uni-leipzig.de/en/projekte/XML/XmlBenchmarking.html).
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. If the software is used to implement the XMach-1 benchmark
 *    the algorithms for data generation should not be changed in
 *    order to ensure same data set and queries for all benchmark runs.
 *
 * 2. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 3. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bench.gen;

import java.util.Random;

/** 
 * Tool for creating collections distributed after Zipf's law
 * (integer k gets weight proportional to (1/k)^theta).
 * The algorithm stems from: Jim Gray et al.: Quickly Generating Billion-Record Synthetic Databases.
 * @author  Timo B&ouml;hme
 * @version 0.9 (incorporated in Sonic Test Harness, version 8.0)
 */
public class ZipfCreator{
  /** range from which to return random numbers */
  protected int numberOfElements = -1;
  /** the skew (0&lt;theta&lt;1) */
  private double theta = 0.9;
  /** constant derived from theta */
  private double alpha = Double.NaN;
  /** constant derived from theta */
  private double zetan = Double.NaN;
  /** constant derived from theta */
  private double eta = Double.NaN;
  /** local instance of random; to ensure strictly reproduceable sequences, each thread must create a separate 
    * instance of ZipfCreator. */
  private Random rnd_ = null;

  // -------------------------------------------------------------------------
  /** Simple constructor to set up parameters based on word list size.
   * The skew is set to 0.9.
   * @param _numberOfElements range from which random numbers are constructed ([0;_numberOfElements-1])
   */  
  public ZipfCreator(int _numberOfElements)   {
    numberOfElements = _numberOfElements;
    if (rnd_ == null)      rnd_ = new Random(RANDOM_SEED);
    calculateValues();
  }
  
  // -------------------------------------------------------------------------
  /** Returns sum of reciprocal for all values from range [1;n].
   * @param _n range for sum function
   * @return result from function sum((1/n)^theta) for n=1,...,_n.
   */  
  private double zeta(int _n) {
    double tmp = 0;
    for (int i=1; i<=_n; i++)
      tmp += Math.pow((double) 1 / i, theta);
    return tmp;
  }
  
  // -------------------------------------------------------------------------
  /** Calculates all constants depending on theta.
   */
  private void calculateValues() {
    alpha = 1 / (1 - theta);
    zetan = zeta(numberOfElements);
    eta = (1 - Math.pow(2.0 / numberOfElements, 1 - theta)) / (1 - zeta(2) / zetan);
  }
  
  // -------------------------------------------------------------------------
  /** Change the skew.
   * @param _theta new skew value (0 < _theta < 1)
   */
  public void setTheta(double _theta) {
    theta = _theta;
    calculateValues();
  }
  
  // -------------------------------------------------------------------------
  /**
   * Returns next random integer according to Zipf distribution with skew theta.
   * @return integer from range [0 ; numberOfElements-1]
   */
  public int nextInt() {
    double u = rnd_.nextDouble();
    double uz = u * zetan;
    if (uz < 1) return 0;
    if (uz < 1 + Math.pow(0.5, theta)) return 1;
    return (int) (numberOfElements * Math.pow(eta * u - eta + 1, alpha)) ;
  }

  /** An arbitrary large prime number to see Random generators */
  public static long RANDOM_SEED = 2147483647l;  // prime proved by Euler  /** random number generator with uniformly distributed numbers */
  
}
