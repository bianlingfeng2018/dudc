package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DirtyData {

  private int[] markedExcludedLines;

  private final String dataPath;

  public DirtyData(String dataPath) {
    this.dataPath = dataPath;
  }
}
