package edu.fudan.algorithms.uguide;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCViolation;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CellQuestionResult {

  private Set<DenialConstraint> falseDCs;
  private Set<DenialConstraint> possibleTrueDCs;
  private Set<DCViolation> falseVios;
  private int budgetUsed;

  @Override
  public String toString() {
    return String.format("FalseDCs = %s, FalseVios = %s, BudgetUsed = %s", falseDCs.size(),
        falseVios.size(), budgetUsed);
  }
}