package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public enum CellQStrategy {
  /**
   * Choose cell which can form a potential false violation or relates to dcs with low confidence.
   */
  VIO_AND_CONF,
  /**
   * Random choose cells.
   */
  RANDOM_CELL
}
