package edu.fudan.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.Predicate;
import java.util.Collection;
import java.util.List;

/**
 * @author Lingfeng
 */
public class DCViolation {

  private List<DenialConstraint> dcs;

  private LinePair linePair;

  public DCViolation(List<DenialConstraint> dcs, LinePair linePair) {
    this.dcs = dcs;
    this.linePair = linePair;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[dcs=");
    for (DenialConstraint dc : dcs) {
      de.metanome.algorithm_integration.results.DenialConstraint dcResult = dc.toResult();
      sb.append(dcResult.toString())
          .append(",");
    }
    if (sb.length() > 1) {
      sb.deleteCharAt(sb.length() - 1);
    }
    sb.append(", linePair=")
        .append(linePair.toString())
        .append("]");
    return sb.toString();
  }
}
