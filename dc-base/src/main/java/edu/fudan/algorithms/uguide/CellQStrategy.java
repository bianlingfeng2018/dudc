package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public enum CellQStrategy {
  /**
   * Choose cell if it: 1.can form a potential false violation; 2.relates to dcs with low
   * confidence.
   */
  VIO_AND_CONF,
  /**
   * Random choose cells.
   */
  RANDOM_CELL
}
