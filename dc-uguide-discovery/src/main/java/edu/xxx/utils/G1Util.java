package edu.xxx.utils;

import static edu.xxx.algorithms.DCLoader.loadHeader;
import static edu.xxx.utils.FileUtil.generateNewCopy;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.metanome.algorithm_integration.Operator;
import edu.xxx.algorithms.HydraDetector;
import edu.xxx.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class G1Util {

  public static List<G1RangeResult> calculateG1Ranges(String headerPath, String dsPath,
      Set<DenialConstraint> dcs) {
    log.debug("Calculate g1 ranges of {} dcs", dcs.size());
    ArrayList<DenialConstraint> dcList = new ArrayList<>(dcs);
    // 防止每次顺序不一样
    dcList.sort(Comparator.comparing(dc -> DCFormatUtil.convertDC2String(dc)));
    List<G1RangeResult> result = new ArrayList<>();
    for (DenialConstraint dc : dcList) {
      G1RangeResult rr = calculateG1Range(headerPath, dsPath, dc, false);
      result.add(rr);
    }
    return result;
  }

  /**
   * Calculate g1 range for each dc.
   *
   * @param headerPath    Header file path
   * @param dsPath        Dataset file path
   * @param dc            DC
   * @param excludeNegEvi If we should exclude the evidence which consists entirely of unequal
   *                      predicates, e.g., not(xxx!=xxx^xxx!=xxx^xxx!=xxx)
   * @return G1 range result
   */
  public static G1RangeResult calculateG1Range(String headerPath, String dsPath,
      DenialConstraint dc, boolean excludeNegEvi) {
    Double[] doubles = new Double[2];
    // Size1
    int excludeSize = Integer.MAX_VALUE;
    if (excludeNegEvi) {
      String excludeEvi = buildExcludeEvi(headerPath);
      DenialConstraint excludeDC = DCFormatUtil.convertString2DC(excludeEvi,
          loadHeader(headerPath));
      excludeSize = new HydraDetector(dsPath, Sets.newHashSet(excludeDC)).detect().size();
//      log.debug("ExcludeSize = {}, dc = {}", excludeSize, DCFormatUtil.convertDC2String(excludeDC));
    }

    // Size2
    int size = new HydraDetector(dsPath, Sets.newHashSet(dc)).detect().size();
    String dcStr = DCFormatUtil.convertDC2String(dc);
//    log.debug("Size = {}, dc = {}", size, dcStr);

    // Size3
    int subSizeMin = Integer.MAX_VALUE;
    // 共享索引，加速计算过程
    Map<DenialConstraint, Integer> subDCSizeMap = new HashMap<>();
    DenialConstraint subDCMin = null;
    ArrayList<Predicate> list = new ArrayList<>();
    for (Predicate predicate : dc.getPredicateSet()) {
      list.add(predicate);
    }
    List<List<Predicate>> lists = generateSubLists(list);
    for (List<Predicate> predicates : lists) {
      // TODO: add-hoc trick to prevent expensive validation, need to be improved later...
      if (allNeg(predicates)) {
//        log.debug("All neg predicates, skip...");
        continue;
      }
//      if (hasNeg(predicates)) {  // 有不等谓词，计算较慢
//        log.debug("Has unequal predicates, skip...");
//        continue;
//      }
//      if (hasNoEqual(predicates)) {  // 没有相等谓词，计算较慢
//        log.debug("Has no equal predicates, skip...");
//        continue;
//      }

      // SubDC
      PredicateBitSet ps = new PredicateBitSet();
      for (Predicate predicate : predicates) {
        ps.add(predicate);
      }
      DenialConstraint subDC = new DenialConstraint(ps);

      int subSize = 0;
      if (subDCSizeMap.containsKey(subDC)) {
        subSize = subDCSizeMap.get(subDC);
      } else {
        subSize = new HydraDetector(dsPath, Sets.newHashSet(subDC)).detect().size();
        subDCSizeMap.put(subDC, subSize);
      }
//      log.debug("SubSize = {}, dc = {}", subSize, DCFormatUtil.convertDC2String(subDC));

      if (subSize < subSizeMin) {
        subSizeMin = subSize;
        subDCMin = subDC;
      }
    }
//    log.debug("SubSizeMin = {}, dc = {}", subSizeMin, DCFormatUtil.convertDC2String(subDCMin));
    Input input = generateNewCopy(dsPath);
    int lineCount = input.getLineCount();
//    log.debug("LineCount = {}", lineCount);

    // Size2
    int low = size >= excludeSize ? (size - excludeSize) : size;
    // Size1 Size3
    int high = subSizeMin >= excludeSize ? (subSizeMin - excludeSize) : subSizeMin;
    // Important!!!
    // In hydra, if g1*combinations = 1.x, then it will allow 2 violations.
    // So we need revise low high before calculating g1.
    int lowRev = Math.max(0, low - 1);
    int highRev = Math.max(0, high - 1);
//    log.debug("Low = {}, High = {}, LowRev = {}, HighRev = {}", low, high, lowRev, highRev);
    // Size4
    int combinations = lineCount * (lineCount - 1);
//    log.debug("Tuple pairs combinations = {}", combinations);

    double leftG1 = lowRev / (double) combinations;
    double rightG1 = highRev / (double) combinations;
//    log.debug("LeftG1 = {}, RightG1 = {}", leftG1, rightG1);
    doubles[0] = leftG1;
    doubles[1] = rightG1;
    return new G1RangeResult(doubles, dcStr);
  }

  private static boolean allNeg(List<Predicate> predicates) {
    for (Predicate predicate : predicates) {
      Operator op = predicate.getOperator();
      if (op != Operator.UNEQUAL) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasNeg(List<Predicate> predicates) {
    for (Predicate predicate : predicates) {
      Operator op = predicate.getOperator();
      if (op == Operator.UNEQUAL) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasNoEqual(List<Predicate> predicates) {
    for (Predicate predicate : predicates) {
      Operator op = predicate.getOperator();
      if (op == Operator.EQUAL) {
        return false;
      }
    }
    return true;
  }

  private static String buildExcludeEvi(String headerPath) {
    String header = loadHeader(headerPath);
    String[] split = header.split(",");
    StringBuilder sb = new StringBuilder();
    sb.append("not(");
    for (String s : split) {
      String column = s.substring(0, s.indexOf("("));
      String p = "t1." + column + "!=t2." + column;
      sb.append(p).append("^");
    }
    sb.deleteCharAt(sb.lastIndexOf("^"));
    sb.append(")");
    return sb.toString();
  }

  public static <T> List<List<T>> generateSubLists(List<T> list) {
    List<List<T>> result = new ArrayList<>();

    // 列表大小为 n
    int n = list.size();

    // 遍历列表中的每个元素
    for (int i = 0; i < n; i++) {
      // 生成一个新的子列表，不包含当前元素 list.get(i)
      List<T> sublist = new ArrayList<>(list.subList(0, i));
      sublist.addAll(list.subList(i + 1, n));

      // 将生成的子列表加入结果列表中
      result.add(sublist);
    }

    return result;
  }

}
