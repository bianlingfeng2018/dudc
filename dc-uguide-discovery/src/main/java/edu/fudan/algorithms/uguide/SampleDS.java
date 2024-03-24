package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class SampleDS {

  private final String dataPath;
  private final String headerPath;

  public SampleDS(String dataPath, String headerPath) {
    this.dataPath = dataPath;
    this.headerPath = headerPath;
  }

}
