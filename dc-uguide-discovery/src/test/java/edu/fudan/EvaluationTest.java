package edu.fudan;

import static edu.fudan.UGuideDiscoveryTest.candidateDCsPath;
import static edu.fudan.UGuideDiscoveryTest.dirtyDataPath;
import static edu.fudan.UGuideDiscoveryTest.groundTruthDCsPath;
import static edu.fudan.UGuideDiscoveryTest.headerPath;
import static edu.fudan.UGuideDiscoveryTest.topKDCsPath;
import static edu.fudan.UGuideDiscoveryTest.trueDCsPath;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.transformat.DCFormatUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * Evaluate the precision, recall and f-measure of the algorithm
 *
 * @author Lingfeng
 */
@Slf4j
public class EvaluationTest {

  /**
   * Evaluation of the discovered DCs wrt. ground truth DCs
   */
  @Test
  public void testEvaluationDiscoveredDCsAgainstGroundTruthDCs() {
    List<DenialConstraint> discoveredDCs = DCLoader.load(headerPath, trueDCsPath);
    List<DenialConstraint> gtDCs = DCLoader.load(headerPath, groundTruthDCsPath);
    Set<DCViolation> vGT =
        new HydraDetector(dirtyDataPath, new HashSet<>(gtDCs)).detect()
            .getViosSet();
    Set<DCViolation> vTarget =
        new HydraDetector(dirtyDataPath, new HashSet<>(discoveredDCs)).detect()
            .getViosSet();

    log.info("vGT={}, vTarget={}", vGT.size(), vTarget.size());

    Map<String, Set<LinePair>> lpMap4GT = Maps.newHashMap();
    Map<String, Set<LinePair>> lpMap4Target = Maps.newHashMap();
    // key规则 -> V1所有冲突，V2真冲突
    Map<String, Integer[]> dcGTEvalMap = Maps.newHashMap();
    // key规则 -> V1所有冲突，V2真冲突
    Map<String, Integer[]> dcTargetEvalMap = Maps.newHashMap();

    // 初始化
    for (DenialConstraint gtDC : gtDCs) {
      String sGT = DCFormatUtil.convertDC2String(gtDC);
      dcGTEvalMap.put(sGT, new Integer[]{0, 0});  // V1 V2
      lpMap4GT.put(sGT, Sets.newHashSet());  // 为了验证每个GTDC产生的冲突的linePair都唯一
      lpMap4Target.put(sGT, Sets.newHashSet());  // 为了对每个GTDC相应的TargetDC产生的冲突的linePair进行去重
    }
    for (DenialConstraint targetDC : discoveredDCs) {
      String sTarget = DCFormatUtil.convertDC2String(targetDC);
      dcTargetEvalMap.put(sTarget, new Integer[]{0, 0});  // V1 V2
    }

    // GT
    for (DCViolation v : vGT) {
      DenialConstraint dc = v.getDenialConstraintsNoData().get(0);
      LinePair lpGT = v.getLinePair();
      String sGT = DCFormatUtil.convertDC2String(dc);
      Set<LinePair> linePairs = lpMap4GT.get(sGT);
      if (linePairs.contains(lpGT)) {
        // 确认每个规则的冲突对应的linePair是唯一的
        throw new RuntimeException("Duplicate line pair: " + sGT);
      }
      linePairs.add(lpGT);
      // Update V1 in GT
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      integersGT[0] = integersGT[0] + 1;
    }

    // Target
    for (DCViolation v : vTarget) {
      DenialConstraint dc = v.getDenialConstraintsNoData().get(0);
      LinePair lpTarget = v.getLinePair();
      String sTarget = DCFormatUtil.convertDC2String(dc);

      Integer[] integersTarget = dcTargetEvalMap.get(sTarget);
      // 所有冲突
      // Update V1 in Target
      integersTarget[0] = integersTarget[0] + 1;
      DenialConstraint impliedGTDC = null;
      for (DenialConstraint gtDC : gtDCs) {
        boolean b = implied(dc, gtDC);
        if (b) {
          // 找到一个符合的gtDC
          impliedGTDC = gtDC;
          break;
        }
      }
      if (impliedGTDC != null) {
        // 真冲突
        // Update V2 in Target
        integersTarget[1] = integersTarget[1] + 1;
        String sGT = DCFormatUtil.convertDC2String(impliedGTDC);
        Set<LinePair> linePairs = lpMap4Target.get(sGT);
        if (!linePairs.contains(lpTarget)) {
          // 不重复的真冲突
          // Update V2 in GT
          Integer[] integersGT = dcGTEvalMap.get(sGT);
          integersGT[1] = integersGT[1] + 1;

          linePairs.add(lpTarget);
        }
      }
    }

    // Calculate precision and recall
    int allTrueViolations = 0;
    int allDTVs = 0;
    int allNoDuplicateDTVs = 0;
    int allDVs = 0;
    for (String targetS : dcTargetEvalMap.keySet()) {
      Integer[] integersTarget = dcTargetEvalMap.get(targetS);
      allDVs += integersTarget[0];  // V1 发现的所有冲突
      allDTVs += integersTarget[1];  // V2 发现的真冲突
    }
    for (String gtS : dcGTEvalMap.keySet()) {
      Integer[] integersGT = dcGTEvalMap.get(gtS);
      allTrueViolations += integersGT[0];  // V1 所有真冲突
      allNoDuplicateDTVs += integersGT[1];  // V2 无重复的发现的真冲突
    }
    double precision = allDTVs / (double) allDVs;
    double recall = allNoDuplicateDTVs / (double) allTrueViolations;

    log.info("Precision={}, recall={}", precision, recall);
  }

  private boolean implied(DenialConstraint targetDC, DenialConstraint gtDC) {
    NTreeSearch gtTree = new NTreeSearch();
    gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    return targetDC.isImpliedBy(gtTree);
  }
}
