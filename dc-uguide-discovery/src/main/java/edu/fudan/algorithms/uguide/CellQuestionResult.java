package edu.fudan.algorithms.uguide;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCViolation;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class CellQuestionResult {

  private Set<TCell> selectedCells;
  private Set<DCViolation> selectedViolations;
  private Set<DenialConstraint> possibleTrueDCs;
  private Set<DenialConstraint> falseDCs;
  private Set<DCViolation> trueVios;
  private Set<DCViolation> falseVios;
  private Set<Integer> excludedLines;

  @Override
  public String toString() {
    return String.format(
        "SelectedCells=%s, SelectedViolations=%s, PossibleTrueDCs=%s, FalseDCs=%s, TrueVios=%s, FalseVios=%s, ExcludedLines=%s",
        selectedCells.size(),
        selectedViolations.size(),
        possibleTrueDCs.size(),
        falseDCs.size(),
        trueVios.size(),
        falseVios.size(),
        excludedLines.size());
  }
}