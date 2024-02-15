package edu.fudan.algorithms;

import java.util.List;

/**
 * @author Lingfeng
 */
public class DCViolationSet {

  private List<DCViolation> dcViolationList;

  public DCViolationSet(List<DCViolation> dcViolationList) {
    this.dcViolationList = dcViolationList;
  }

  public List<DCViolation> getDcViolationList() {
    return dcViolationList;
  }
}
