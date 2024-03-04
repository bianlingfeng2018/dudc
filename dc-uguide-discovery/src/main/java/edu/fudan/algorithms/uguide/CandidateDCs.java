package edu.fudan.algorithms.uguide;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class CandidateDCs {

  private final String dcsPathForFCDC;
  private final String dcsPathForDCMiner;
  private final String evidencesPathForFCDC;
  private final String topKDCsPath;

  public CandidateDCs(String dcsPathForFCDC, String dcsPathForDCMiner, String evidencesPathForFCDC, String topKDCsPath) {
    this.dcsPathForFCDC = dcsPathForFCDC;
    this.dcsPathForDCMiner = dcsPathForDCMiner;
    this.evidencesPathForFCDC = evidencesPathForFCDC;
    this.topKDCsPath = topKDCsPath;
  }
}
