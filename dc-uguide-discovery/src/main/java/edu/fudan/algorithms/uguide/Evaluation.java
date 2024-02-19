package edu.fudan.algorithms.uguide;

import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import edu.fudan.algorithms.DCViolationSet;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class Evaluation {
  // 模拟的事实
  private final String groundTruthDCsPath;
  private final DenialConstraintSet groundTruthDCs = new DenialConstraintSet();

  // 全局的优化结果
  private final String candidateDCsPath;
  private final DenialConstraintSet candidateDCs = new DenialConstraintSet();
  private final DCViolationSet candidateViolations = new DCViolationSet();

  public Evaluation(String groundTruthDCsPath, String candidateDCsPath) {
    this.groundTruthDCsPath = groundTruthDCsPath;
    this.candidateDCsPath = candidateDCsPath;
  }
}
