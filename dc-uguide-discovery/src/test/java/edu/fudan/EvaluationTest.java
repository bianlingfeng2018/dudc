package edu.fudan;

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
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluate the precision, recall and f-measure of the algorithm
 *
 * @author Lingfeng
 */
@Slf4j
public class EvaluationTest {
  private static int dsIndex = 0;
  private static String headerPath;
  private static String cleanDataPath;
  private static String dirtyDataPath;
  private static String changesPath;
  private static String excludedLinesPath;
  private static String sampledDataPath;
  private static String fullDCsPath;
  private static String dcsPathForDCMiner;
  private static String evidencesPath;
  private static String topKDCsPath;
  private static String groundTruthDCsPath;
  private static String candidateDCsPath;
  private static String candidateTrueDCsPath;
  private static String excludedDCsPath;
  private static String csvResultPath;

  @Before
  public void setUp() throws Exception {
    UGDParams params = UGDRunner.buildParams(dsIndex);
    headerPath = params.headerPath;
    cleanDataPath = params.cleanDataPath;
    dirtyDataPath = params.dirtyDataPath;
    changesPath = params.changesPath;
    excludedLinesPath = params.excludedLinesPath;
    sampledDataPath = params.sampledDataPath;
    fullDCsPath = params.fullDCsPath;
    dcsPathForDCMiner = params.dcsPathForDCMiner;
    evidencesPath = params.evidencesPath;
    topKDCsPath = params.topKDCsPath;
    groundTruthDCsPath = params.groundTruthDCsPath;
    candidateDCsPath = params.candidateDCsPath;
    candidateTrueDCsPath = params.candidateTrueDCsPath;
    excludedDCsPath = params.excludedDCsPath;
    csvResultPath = params.csvResultPath;
  }

  /**
   * Evaluation of the discovered DCs wrt. ground truth DCs
   */
  @Test
  public void testEvaluationDiscoveredDCsAgainstGroundTruthDCs() {
    List<DenialConstraint> discoveredDCs = DCLoader.load(headerPath, candidateTrueDCsPath);
    List<DenialConstraint> gtDCs = DCLoader.load(headerPath, groundTruthDCsPath);
    Set<DCViolation> vGT =
        new HydraDetector(dirtyDataPath, new HashSet<>(gtDCs)).detect()
            .getViosSet();
    Set<DCViolation> vTarget =
        new HydraDetector(dirtyDataPath, new HashSet<>(discoveredDCs)).detect()
            .getViosSet();

    log.info("vGT={}, vTarget={}", vGT.size(), vTarget.size());

    // GtDC->关联的不重复的LinePairs
    Map<String, Set<LinePair>> lpMap4GT = Maps.newHashMap();
    // GtDC->关联的TargetDCs的不重复的LinePairs
    Map<String, Set<LinePair>> lpMap4Target = Maps.newHashMap();
    // GtDC->第一个元素为所有真冲突V1，第二个元素为发现的不重复的真冲突V2，V2/V1为recall
    Map<String, Integer[]> dcGTEvalMap = Maps.newHashMap();
    // TargetDC->第一个元素为发现的冲突V1，第二个元素为发现的真冲突V2，V2/V1为precision
    Map<String, Integer[]> dcTargetEvalMap = Maps.newHashMap();

    // 初始化
    for (DenialConstraint gtDC : gtDCs) {
      String sGT = DCFormatUtil.convertDC2String(gtDC);
      dcGTEvalMap.put(sGT, new Integer[]{0, 0});  // V1 V2
      lpMap4GT.put(sGT, Sets.newHashSet());  // 为了验证每个GtDC产生的冲突的linePair都唯一
      lpMap4Target.put(sGT, Sets.newHashSet());  // 为了对每个GtDC相应的TargetDCs产生的冲突的linePair进行去重
    }
    for (DenialConstraint targetDC : discoveredDCs) {
      String sTarget = DCFormatUtil.convertDC2String(targetDC);
      dcTargetEvalMap.put(sTarget, new Integer[]{0, 0});  // V1 V2
    }
    // 建立包含关系索引
    Map<String, Set<String>> targetImpliedMap = Maps.newHashMap();
    Map<String, Set<String>> targetImplyingMap = Maps.newHashMap();
    for (DenialConstraint gtDC : gtDCs) {
      for (DenialConstraint targetDC : discoveredDCs) {
        String sTarget = DCFormatUtil.convertDC2String(targetDC);
        String sGT = DCFormatUtil.convertDC2String(gtDC);
        // TargetDC更短，记录它可以推出的GtDCs
        if (implied(gtDC, targetDC)) {
          if (targetImplyingMap.containsKey(sTarget)) {
            targetImplyingMap.get(sTarget).add(sGT);
          } else {
            targetImplyingMap.put(sTarget, Sets.newHashSet(sGT));
          }
        }
        // TargetDC更长，记录可以推出它的GtDCs
        if (implied(targetDC, gtDC)) {
          if (targetImpliedMap.containsKey(sTarget)) {
            targetImpliedMap.get(sTarget).add(sGT);
          } else {
            targetImpliedMap.put(sTarget, Sets.newHashSet(sGT));
          }
        }
      }
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
      if (targetImpliedMap.containsKey(sTarget)) {
        // 真冲突
        String sGT = targetImpliedMap.get(sTarget).stream().findAny().get();
        tryUpdateV2(lpMap4Target, lpTarget, dcGTEvalMap, integersTarget, sGT);
      } else {
        if (targetImplyingMap.containsKey(sTarget)) {
          // 进一步判断当前元组是否是真冲突，此时，TargetDC相对于GtDC更general，若GtDC关联的元组包含当前元组，则当前元组为真冲突
          // 注意可能有多个GtDC与当前TargetDC满足这种蕴含关系，但当前元组只能给其中一个GtDC算一次真冲突
          String sGT = targetImplyingMap.get(sTarget).stream().findAny().get();
          Set<LinePair> linePairs = lpMap4GT.get(sGT);
          if (linePairs.contains(lpTarget)) {
            // 真冲突
            tryUpdateV2(lpMap4Target, lpTarget, dcGTEvalMap, integersTarget, sGT);
          }
        }
      }
    }

    // 确认数量关系正确
    int allV2InGT = 0;
    int allV2InTarget = 0;
    for (DenialConstraint gtDC : gtDCs) {
      String sGT = DCFormatUtil.convertDC2String(gtDC);
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      // 所有真冲突数量（默认不重复） 大于等于 发现的
      boolean b1 = integersGT[0] >= integersGT[1];

      int lpSizeGT = lpMap4GT.get(sGT).size();
      int lpSizeTarget = lpMap4Target.get(sGT).size();
      // 所有真冲突数量（默认不重复） 大于等于 发现的不重复的
      boolean b3 = lpSizeGT >= lpSizeTarget;
      // 所有真冲突数量（默认不重复） 等于 对应LinePairs大小
      boolean b4 = lpSizeGT == integersGT[0];
      // 所有发现的不重复的真冲突数量 等于 对应LinePairs大小
      boolean b5 = lpSizeTarget == integersGT[1];

      if (!b1 || !b3 || !b4 || !b5) {
        throw new RuntimeException("Size relation error, b1-3-4-5");
      }
      allV2InGT = allV2InGT + integersGT[1];
    }
    for (DenialConstraint targetDC : discoveredDCs) {
      String sTarget = DCFormatUtil.convertDC2String(targetDC);
      Integer[] integersTarget = dcTargetEvalMap.get(sTarget);
      // 所有发现的冲突数量 大于等于 确定为真的
      boolean b2 = integersTarget[0] >= integersTarget[1];

      if (!b2) {
        throw new RuntimeException("Size relation error, b2");
      }
      allV2InTarget = allV2InTarget + integersTarget[1];
    }
    // 所有发现的真冲突数量大于等于去重后的大小
    boolean b6 = allV2InTarget >= allV2InGT;
    if (!b6) {
      throw new RuntimeException("Size error, b6");
    }

    // 计算precision recall
    int allDisVs = 0;
    int allDisTrueVs = 0;
    int allDisTrueNoDupVs = 0;
    int allGtVs = 0;
    for (String targetS : dcTargetEvalMap.keySet()) {
      Integer[] integersTarget = dcTargetEvalMap.get(targetS);
      allDisVs += integersTarget[0];  // V1
      allDisTrueVs += integersTarget[1];  // V2
    }
    for (String gtS : dcGTEvalMap.keySet()) {
      Integer[] integersGT = dcGTEvalMap.get(gtS);
      allGtVs += integersGT[0];  // V1
      allDisTrueNoDupVs += integersGT[1];  // V2
    }
    double precision = allDisTrueVs / (double) allDisVs;
    double recall = allDisTrueNoDupVs / (double) allGtVs;
    double f1 = (2 * precision * recall) / (precision + recall);

    log.info("Precision={}, recall={}, f1={}", precision, recall, f1);
  }

  private static void tryUpdateV2(Map<String, Set<LinePair>> lpMap4Target,
      LinePair lpTarget, Map<String, Integer[]> dcGTEvalMap, Integer[] integersTarget,
      String sGT) {
    // 真冲突
    // Update V2 in Target
    integersTarget[1] = integersTarget[1] + 1;
    Set<LinePair> linePairs = lpMap4Target.get(sGT);
    if (!linePairs.contains(lpTarget)) {
      // 不重复的真冲突
      // Update V2 in GT
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      integersGT[1] = integersGT[1] + 1;

      linePairs.add(lpTarget);
    }
  }

  private boolean implied(DenialConstraint targetDC, DenialConstraint gtDC) {
    NTreeSearch gtTree = new NTreeSearch();
    gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    return targetDC.isImpliedBy(gtTree);
  }
}
