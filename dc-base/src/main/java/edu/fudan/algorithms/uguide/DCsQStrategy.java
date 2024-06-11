package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public enum DCsQStrategy {
  /**
   * Rank dc by combined score of succinctness and correlation, violations size.
   */
  SUC_AND_COR_VIOS,
  /**
   * Rank dc by combined score of succinctness and correlation.
   */
  SUC_AND_COR,
  /**
   * Random choose dcs.
   */
  RANDOM_DC
}
