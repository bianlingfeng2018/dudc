package edu.fudan;

import static edu.fudan.utils.EvaluateUtil.eval;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

/**
 * Evaluate the precision, recall and f-measure of the algorithm
 *
 * @author Lingfeng
 */
@Slf4j
public class EvaluationTest {

  /**
   * Parameters used in User-guided-DC-Detection process.
   */
  UGDParams params;

  @Before
  public void setUp() throws Exception {
    int dsIndex = 0;
    params = UGDRunner.buildParams(dsIndex);
  }

  /**
   * Evaluation of the discovered DCs against ground truth DCs
   */
  @Test
  public void testEvalDiscoveredDCsAgainstGTDCs() {
    List<DenialConstraint> gtDCs = DCLoader.load(params.headerPath, params.groundTruthDCsPath);
    List<DenialConstraint> discDCs = DCLoader.load(params.headerPath, params.candidateTrueDCsPath);
    log.debug("Load gtDCs = {}, discDCs = {}", gtDCs.size(), discDCs.size());

    Set<DCViolation> vGT = new HydraDetector(params.dirtyDataPath, new HashSet<>(gtDCs))
        .detect().getViosSet();
    Set<DCViolation> vDisc = new HydraDetector(params.dirtyDataPath, new HashSet<>(discDCs))
        .detect().getViosSet();
    log.debug("VGT={}, VDisc={}", vGT.size(), vDisc.size());

    Double[] result = eval(new HashSet<>(gtDCs), new HashSet<>(discDCs), vGT, vDisc);
    log.debug("Precision={}, Recall={}, F-measure={}", result[0], result[1], result[2]);
  }

}
