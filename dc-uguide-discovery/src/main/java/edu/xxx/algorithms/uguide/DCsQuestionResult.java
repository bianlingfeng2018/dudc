package edu.xxx.algorithms.uguide;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Set;
import lombok.Getter;

@Getter
public class DCsQuestionResult {

  private final Set<DenialConstraint> falseDCs;
  private final Set<DenialConstraint> trueDCs;
  private final double trueDCRate;
  private final int totalViosSize;
  private final int budgetUsed;

  public DCsQuestionResult(Set<DenialConstraint> falseDCs, Set<DenialConstraint> trueDCs,
      double trueDCRate, int totalViosSize, int budgetUsed) {
    this.falseDCs = falseDCs;
    this.trueDCs = trueDCs;
    this.trueDCRate = trueDCRate;
    this.totalViosSize = totalViosSize;
    this.budgetUsed = budgetUsed;
  }

  @Override
  public String toString() {
    return String.format("FalseDCs=%S, TrueDCs=%s, TrueDCRate=%s, TotalViosSize=%s, BudgetUsed=%s",
        falseDCs.size(), trueDCs.size(), trueDCRate, totalViosSize, budgetUsed);
  }
}