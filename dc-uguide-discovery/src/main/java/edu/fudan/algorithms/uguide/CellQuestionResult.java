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
  private Set<DenialConstraint> trueDCs;
  private Set<DenialConstraint> falseDCs;
  private Set<DCViolation> trueVios;
  private Set<DCViolation> falseVios;
  private Set<Integer> excludedLines;

  @Override
  public String toString() {
    return String.format(
        "SelectedCells=%s, selectedViolations=%s, trueDCs=%s, falseDCs=%s, trueVios=%s, falseVios=%s, excludedLines=%s",
        selectedCells.size(),
        selectedViolations.size(),
        trueDCs.size(),
        falseDCs.size(),
        trueVios.size(),
        falseVios.size(),
        excludedLines.size());
  }
}