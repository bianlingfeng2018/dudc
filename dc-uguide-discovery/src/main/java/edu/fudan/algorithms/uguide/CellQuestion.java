package edu.fudan.algorithms.uguide;

/**
 * @author Lingfeng
 */
public interface CellQuestion {

  void simulate();

  CellQuestionResult getResult();

  int getBudgetUsed();
}
