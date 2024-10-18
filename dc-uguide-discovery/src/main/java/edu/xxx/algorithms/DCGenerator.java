package edu.xxx.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Set;

/**
 * @author XXX
 */
interface DCGenerator {

  /**
   * Generate DCs to ask user DC questions.
   * @return Generated DCs
   */
  Set<DenialConstraint> generateDCs();

}
