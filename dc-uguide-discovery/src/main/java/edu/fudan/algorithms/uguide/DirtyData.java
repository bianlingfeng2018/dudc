package edu.fudan.algorithms.uguide;


import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DirtyData {

  private final String dataPath;
  private final String excludedLinesPath;
  private final String headerPath;

  public DirtyData(String dataPath, String excludedLinesPath, String headerPath) {
    this.dataPath = dataPath;
    this.excludedLinesPath = excludedLinesPath;
    this.headerPath = headerPath;
  }
}
