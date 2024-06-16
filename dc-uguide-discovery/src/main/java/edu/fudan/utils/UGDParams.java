package edu.fudan.utils;

import lombok.ToString;

@ToString
public class UGDParams {
  public String changesPath;
  public String cleanDataPath;
  public String dirtyDataPath;
  public String dirtyDataUnrepairedPath;
  public String excludedLinesPath;
  public String sampledDataPath;
  public String fullDCsPath;
  public String dcsPathForDCMiner;
  public String evidencesPath;
  public String topKDCsPath;
  public String groundTruthDCsPath;
  public String candidateDCsPath;
  public String candidateTrueDCsPath;
  public String excludedDCsPath;
  public String headerPath;
  public String csvResultPath;
  public String correlationByUserPath;
}
