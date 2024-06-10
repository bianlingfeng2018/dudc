package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public enum TupleQStrategy {
  /**
   * Sort by violations size.
   */
  VIOLATIONS,
  /**
   * Sort by dcs size.
   */
  DCS,
  /**
   * Sort by dcs size first, then by violations size to break ties.
   */
  DCS_PRIOR,
  /**
   * Sort by violations size first, then by dcs size to break ties.
   */
  VIOLATIONS_PRIOR,
  /**
   * Random choose tuples(lines).
   */
  RANDOM
}
