package edu.fudan.algorithms.uguide;


import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DirtyDS {

  private final String dataPath;
  private final String excludedLinesPath;
  private final String headerPath;

  public DirtyDS(String dataPath, String excludedLinesPath, String headerPath) {
    this.dataPath = dataPath;
    this.excludedLinesPath = excludedLinesPath;
    this.headerPath = headerPath;
  }
}
