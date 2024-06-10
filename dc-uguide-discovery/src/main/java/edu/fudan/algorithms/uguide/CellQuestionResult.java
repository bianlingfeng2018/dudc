package edu.fudan.algorithms.uguide;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CellQuestionResult {

  private Set<DenialConstraint> falseDCs;
  private int budgetUsed;

  @Override
  public String toString() {
    return String.format(
        "FalseDCs=%s, BudgetUsed=%s", falseDCs.size(), budgetUsed);
  }
}