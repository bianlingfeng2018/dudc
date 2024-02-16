package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class CandidateDCs {
  private String dcsPath;
  private String evidencesPath;

  public CandidateDCs(String dcsPath, String evidencesPath) {
    this.dcsPath = dcsPath;
    this.evidencesPath = evidencesPath;
  }
}
