package edu.fudan.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.fudan.transformat.DCFormatUtil;
import java.util.List;
import java.util.Objects;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DCViolation {

  private List<DenialConstraint> denialConstraintList;

  private LinePair linePair;

  public DCViolation(List<DenialConstraint> denialConstraintList, LinePair linePair) {
    this.denialConstraintList = denialConstraintList;
    this.linePair = linePair;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[dcs=");
    for (DenialConstraint dc : denialConstraintList) {
      String dcStr = DCFormatUtil.convertDC2String(dc);
      sb.append(dcStr)
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
    for (DenialConstraint dc : denialConstraintList) {
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
    List<DenialConstraint> tarObjDcs = tarObj.getDenialConstraintList();
    for (int i = 0; i < denialConstraintList.size(); i++) {
      DenialConstraint dc = denialConstraintList.get(i);
      DenialConstraint tarObjDc = tarObjDcs.get(i);
      if (!dc.equals(tarObjDc)) {
        return false;
      }
    }
    return true;
  }
}
