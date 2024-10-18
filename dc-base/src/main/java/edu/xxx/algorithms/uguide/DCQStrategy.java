package edu.xxx.algorithms.uguide;

/**
 * @author XXX
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
