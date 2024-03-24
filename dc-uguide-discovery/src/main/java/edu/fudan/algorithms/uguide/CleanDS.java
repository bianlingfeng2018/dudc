package edu.fudan.algorithms.uguide;


import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class CleanDS {

  private final String dataPath;
  private final String headerPath;
  private final String changesPath;

  public CleanDS(String dataPath, String headerPath, String changesPath) {
    this.dataPath = dataPath;
    this.headerPath = headerPath;
    this.changesPath = changesPath;
  }

}
