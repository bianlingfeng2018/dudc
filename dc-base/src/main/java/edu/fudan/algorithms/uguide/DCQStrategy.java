package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public enum DCQStrategy {
  /**
   * Rank dc by combined score of succinctness and correlation, violations size.
   */
  SUC_COR_VIOS,
  /**
   * Rank dc by combined score of succinctness and correlation.
   */
  SUC_COR,
  /**
   * Random choose dcs.
   */
  RANDOM_DC
}
