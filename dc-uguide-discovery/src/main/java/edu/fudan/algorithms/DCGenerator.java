package edu.fudan.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Set;

/**
 * @author Lingfeng
 */
interface DCGenerator {

  /**
   * Generate DCs to be refined by asking user questions.
   * @return Generated DCs
   */
  Set<DenialConstraint> generateDCsForUser();

}
