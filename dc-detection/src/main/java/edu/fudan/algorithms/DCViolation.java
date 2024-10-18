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

  /**
   * MyDC with no data
   */
  private List<DenialConstraint> denialConstraintsNoData;
  /**
   * Hydra DC with parsed column data
   */
  @Deprecated
  private List<DenialConstraint> constraints;

  private LinePair linePair;

  public DCViolation(List<DenialConstraint> denialConstraintsNoData,
      List<DenialConstraint> constraints, LinePair linePair) {
    this.denialConstraintsNoData = denialConstraintsNoData;
    this.constraints = constraints;
    this.linePair = linePair;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("[dcs=");
    for (DenialConstraint dc : denialConstraintsNoData) {
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
    for (DenialConstraint dc : denialConstraintsNoData) {
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
    List<DenialConstraint> tarObjDCVOs = tarObj.getDenialConstraintsNoData();
    for (int i = 0; i < denialConstraintsNoData.size(); i++) {
      DenialConstraint dc = denialConstraintsNoData.get(i);
      DenialConstraint tarObjDC = tarObjDCVOs.get(i);
      if (!dc.equals(tarObjDC)) {
        return false;
      }
    }
    return true;
  }
}
