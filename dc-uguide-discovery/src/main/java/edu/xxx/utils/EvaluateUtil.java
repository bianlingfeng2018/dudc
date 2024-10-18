package edu.xxx.utils;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import edu.xxx.algorithms.DCViolation;
import edu.xxx.algorithms.HydraDetector;
import edu.xxx.transformat.DCFormatUtil;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author XXX
 */
@Slf4j
public class EvaluateUtil {

  public static Double[] eval(Set<DenialConstraint> gtDCs, Set<DenialConstraint> discDCs,
      String dataPath) {
    Set<DCViolation> vGT = new HydraDetector(dataPath, new HashSet<>(gtDCs)).detect().getViosSet();
    Set<DCViolation> vDisc = new HydraDetector(dataPath, new HashSet<>(discDCs)).detect()
        .getViosSet();
    log.debug("VGT={}, VDisc={}", vGT.size(), vDisc.size());
    return eval(new HashSet<>(gtDCs), new HashSet<>(discDCs), vGT, vDisc);
  }

  public static Double[] eval(Set<DenialConstraint> gtDCs, Set<DenialConstraint> discDCs,
      Set<DCViolation> vGT, Set<DCViolation> vDisc) {
    log.info("Evaluating precision, recall and f-measure.");
    /**
     * GTDCs -> None repeated linePairs of GTDCs.
     */
    Map<String, Set<LinePair>> lpMap4GT = Maps.newHashMap();
    /**
     * GTDCs -> None repeated linePairs of DiscDCs.
     */
    Map<String, Set<LinePair>> lpMap4Disc = Maps.newHashMap();
    /**
     * GTDCs -> [vGT, none-repeated and true violations of vDisc]([V1, V2]). Recall=V2/V1.
     */
    Map<String, Integer[]> dcGTEvalMap = Maps.newHashMap();
    /**
     * DiscDCs -> [vDisc, true violations of vDisc]([V3, V4]). Precision=V4/V3.
     */
    Map<String, Integer[]> dcDiscEvalMap = Maps.newHashMap();

    // 初始化
    log.debug("Indexing...");
    for (DenialConstraint gtDC : gtDCs) {
      String sGT = DCFormatUtil.convertDC2String(gtDC);
      // V1 V2
      dcGTEvalMap.put(sGT, new Integer[]{0, 0});
      // 为了验证每个gtDC产生的冲突的linePair都唯一
      lpMap4GT.put(sGT, Sets.newHashSet());
      // 为了对每个gtDC相应的discDCs产生的冲突的linePair进行去重
      lpMap4Disc.put(sGT, Sets.newHashSet());
    }
    for (DenialConstraint discDC : discDCs) {
      String sDisc = DCFormatUtil.convertDC2String(discDC);
      // V3 V4
      dcDiscEvalMap.put(sDisc, new Integer[]{0, 0});
    }
    // 建立包含关系索引
    Map<String, Set<String>> discImpliedMap = Maps.newHashMap();
    Map<String, Set<String>> discImplyingMap = Maps.newHashMap();
    for (DenialConstraint gtDC : gtDCs) {
      for (DenialConstraint discDC : discDCs) {
        String sDisc = DCFormatUtil.convertDC2String(discDC);
        String sGT = DCFormatUtil.convertDC2String(gtDC);
        // DiscDC更短，记录它可以推出的gtDCs
        if (implied(gtDC, discDC)) {
          if (discImplyingMap.containsKey(sDisc)) {
            discImplyingMap.get(sDisc).add(sGT);
          } else {
            discImplyingMap.put(sDisc, Sets.newHashSet(sGT));
          }
        }
        // DiscDC更长，记录可以推出它的gtDCs
        if (implied(discDC, gtDC)) {
          if (discImpliedMap.containsKey(sDisc)) {
            discImpliedMap.get(sDisc).add(sGT);
          } else {
            discImpliedMap.put(sDisc, Sets.newHashSet(sGT));
          }
        }
      }
    }

    // 遍历vGT
    log.debug("Iterating vGT and vDisc...");
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
      // 注入的 冲突 V1
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      integersGT[0] = integersGT[0] + 1;
    }
    // 遍历vDisc
    for (DCViolation v : vDisc) {
      DenialConstraint dc = v.getDenialConstraintsNoData().get(0);
      LinePair lpDisc = v.getLinePair();
      String sDisc = DCFormatUtil.convertDC2String(dc);

      Integer[] integersDisc = dcDiscEvalMap.get(sDisc);
      // 发现的 冲突 V3
      integersDisc[0] = integersDisc[0] + 1;
      if (discImpliedMap.containsKey(sDisc)) {
        // 真冲突
        String sGT = discImpliedMap.get(sDisc).stream().findAny().get();
        tryUpdateV(lpMap4Disc, lpDisc, dcGTEvalMap, integersDisc, sGT);
      } else {
        if (discImplyingMap.containsKey(sDisc)) {
          // 进一步判断当前元组是否是真冲突，此时，discDC相对于gtDC更general，若gtDC关联的元组包含当前元组，则当前元组为真冲突
          // 注意可能有多个gtDC与当前discDC满足这种蕴含关系，但当前元组只能给其中一个gtDC算一次真冲突
          String sGT = discImplyingMap.get(sDisc).stream().findAny().get();
          Set<LinePair> linePairs = lpMap4GT.get(sGT);
          if (linePairs.contains(lpDisc)) {
            // 真冲突
            tryUpdateV(lpMap4Disc, lpDisc, dcGTEvalMap, integersDisc, sGT);
          }
        }
      }
    }

    // 确认数量关系正确
    log.debug("Checking...");
    check(gtDCs, discDCs, dcGTEvalMap, lpMap4GT, lpMap4Disc, dcDiscEvalMap);

    // 计算precision recall f-measure
    log.debug("Calculating precision, recall and f-measure.");
    int allDiscVs = 0;
    int allTrueDiscVs = 0;
    int allTrueNoDupDiscVs = 0;
    int allGTVs = 0;
    for (String gtS : dcGTEvalMap.keySet()) {
      Integer[] integersGT = dcGTEvalMap.get(gtS);
      // V1
      allGTVs += integersGT[0];
      // V2
      allTrueNoDupDiscVs += integersGT[1];
    }
    for (String sDisc : dcDiscEvalMap.keySet()) {
      Integer[] integersDisc = dcDiscEvalMap.get(sDisc);
      // V3
      allDiscVs += integersDisc[0];
      // V4
      allTrueDiscVs += integersDisc[1];
    }
    // V2/V1
    double recall = allTrueNoDupDiscVs / (double) allGTVs;
    // V4/V3
    double precision = allTrueDiscVs / (double) allDiscVs;
    double f1 = (2 * precision * recall) / (precision + recall);
    Double[] result = new Double[3];
    result[0] = precision;
    result[1] = recall;
    result[2] = f1;
    log.debug("Precision = {}, Recall = {}, F1 = {}", result[0], result[1], result[2]);
    return result;
  }

  private static void check(Set<DenialConstraint> gtDCs, Set<DenialConstraint> discDCs,
      Map<String, Integer[]> dcGTEvalMap, Map<String, Set<LinePair>> lpMap4GT,
      Map<String, Set<LinePair>> lpMap4Disc, Map<String, Integer[]> dcDiscEvalMap) {
    int allV2InGT = 0;
    int allV2InDisc = 0;
    for (DenialConstraint gtDC : gtDCs) {
      String sGT = DCFormatUtil.convertDC2String(gtDC);
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      // 所有真冲突数量（默认不重复） 大于等于 发现的
      boolean b1 = integersGT[0] >= integersGT[1];

      int lpSizeGT = lpMap4GT.get(sGT).size();
      int lpSizeDisc = lpMap4Disc.get(sGT).size();
      // 所有真冲突数量（默认不重复） 大于等于 发现的不重复的
      boolean b2 = lpSizeGT >= lpSizeDisc;
      // 所有真冲突数量（默认不重复） 等于 对应LinePairs大小
      boolean b3 = lpSizeGT == integersGT[0];
      // 所有发现的不重复的真冲突数量 等于 对应LinePairs大小
      boolean b4 = lpSizeDisc == integersGT[1];

      if (!b1 || !b2 || !b3 || !b4) {
        log.error("b1 = {}, b2 = {}, b3 = {}, b4 = {}", b1, b2, b3, b4);
//        throw new RuntimeException("Size relation error, b1-3-4-5");
      }
      allV2InGT = allV2InGT + integersGT[1];
    }
    for (DenialConstraint discDC : discDCs) {
      String sDisc = DCFormatUtil.convertDC2String(discDC);
      Integer[] integersDisc = dcDiscEvalMap.get(sDisc);
      // 所有发现的冲突数量 大于等于 确定为真的
      boolean b2 = integersDisc[0] >= integersDisc[1];

      if (!b2) {
        throw new RuntimeException("Size relation error, b2");
      }
      allV2InDisc = allV2InDisc + integersDisc[1];
    }
    // 所有发现的真冲突数量大于等于去重后的大小
    boolean b6 = allV2InDisc >= allV2InGT;
    if (!b6) {
      throw new RuntimeException("Size error, b6");
    }
  }

  private static void tryUpdateV(Map<String, Set<LinePair>> lpMap4Disc, LinePair lpDisc,
      Map<String, Integer[]> dcGTEvalMap, Integer[] integersDisc, String sGT) {
    // 发现的 真冲突 V4
    integersDisc[1] = integersDisc[1] + 1;
    Set<LinePair> linePairs = lpMap4Disc.get(sGT);
    if (!linePairs.contains(lpDisc)) {
      // 发现的 不重复的 真冲突 V2
      Integer[] integersGT = dcGTEvalMap.get(sGT);
      integersGT[1] = integersGT[1] + 1;

      linePairs.add(lpDisc);
    }
  }

  private static boolean implied(DenialConstraint discDC, DenialConstraint gtDC) {
    NTreeSearch gtTree = new NTreeSearch();
    gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    return discDC.isImpliedBy(gtTree);
  }


}
