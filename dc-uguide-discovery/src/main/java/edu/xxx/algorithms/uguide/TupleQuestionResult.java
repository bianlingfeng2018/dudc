package edu.xxx.algorithms.uguide;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TupleQuestionResult {

  private List<Integer> excludedTuples;
  private int budgetUsed;
  private double errorRate;

  @Override
  public String toString() {
    return String.format("ExcludedTuples=%S, BudgetUsed=%s, ErrorRate=%s",
        excludedTuples.size(), budgetUsed, errorRate);
  }
}