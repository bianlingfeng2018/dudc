package edu.xxx.algorithms.uguide;

import lombok.Getter;

/**
 * @author XXX
 */
@Getter
public class CandidateDCs {

  private final String fullDCsPath;
  private final String dcsPathForDCMiner;
  private final String evidencesPath;
  private final String topKDCsPath;

  public CandidateDCs(String fullDCsPath, String dcsPathForDCMiner, String evidencesPath, String topKDCsPath) {
    this.fullDCsPath = fullDCsPath;
    this.dcsPathForDCMiner = dcsPathForDCMiner;
    this.evidencesPath = evidencesPath;
    this.topKDCsPath = topKDCsPath;
  }
}
