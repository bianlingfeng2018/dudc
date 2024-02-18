package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class CandidateDCs {

  private final String dcsPathForFCDC;
  private final String evidencesPathForFCDC;

  public CandidateDCs(String dcsPathForFCDC, String evidencesPathForFCDC) {
    this.dcsPathForFCDC = dcsPathForFCDC;
    this.evidencesPathForFCDC = evidencesPathForFCDC;
  }
}
