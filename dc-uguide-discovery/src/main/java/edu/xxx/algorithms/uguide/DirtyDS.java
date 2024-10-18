package edu.xxx.algorithms.uguide;


import lombok.Getter;

/**
 * @author XXX
 */
@Getter
public class DirtyDS {

  private final String dataPath;
  private final String dataUnrepairedPath;
  private final String affectedPath;
  private final String excludedLinesPath;
  private final String headerPath;

  public DirtyDS(String dataPath, String dataUnrepairedPath, String affectedPath, String excludedLinesPath,
      String headerPath) {
    this.dataPath = dataPath;
    this.dataUnrepairedPath = dataUnrepairedPath;
    this.affectedPath = affectedPath;
    this.excludedLinesPath = excludedLinesPath;
    this.headerPath = headerPath;
  }
}
