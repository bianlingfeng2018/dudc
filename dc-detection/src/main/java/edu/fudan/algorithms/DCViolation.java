package edu.fudan.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.Predicate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
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

  @Override
  public int hashCode() {
    int result = Objects.hashCode(linePair);
    for (DenialConstraint dc : dcs) {
      result = 31 * result + Objects.hashCode(dc);
    }
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    DCViolation tarObj = (DCViolation) obj;
    if (!linePair.equals(tarObj.getLinePair())) {
      return false;
    }
    List<DenialConstraint> tarObjDcs = tarObj.getDcs();
    for (int i = 0; i < dcs.size(); i++) {
      DenialConstraint dc = dcs.get(i);
      DenialConstraint tarObjDc = tarObjDcs.get(i);
      if (!dc.equals(tarObjDc)) {
        return false;
      }
    }
    return true;
  }
}
