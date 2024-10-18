package edu.xxx.utils;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.xxx.algorithms.DCViolation;
import java.util.List;
import java.util.Set;

/**
 * @author XXX
 */
public class DataUtil {

  public static Set<DenialConstraint> getDCsSetFromViolations(Set<DCViolation> vioSet) {
    Set<DenialConstraint> result = Sets.newHashSet();
    for (DCViolation vio : vioSet) {
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      if (dcs.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcs.get(0);
      result.add(dc);
    }
    return result;
  }

}
