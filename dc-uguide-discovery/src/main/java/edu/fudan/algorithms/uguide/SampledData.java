package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class SampledData {

  private final String dataPath;
  private final String headerPath;

  public SampledData(String dataPath, String headerPath) {
    this.dataPath = dataPath;
    this.headerPath = headerPath;
  }

}
