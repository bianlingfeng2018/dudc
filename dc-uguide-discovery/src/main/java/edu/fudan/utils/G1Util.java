package edu.fudan.utils;

import static edu.fudan.algorithms.DCLoader.loadHeader;
import static edu.fudan.utils.FileUtil.generateNewCopy;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class G1Util {

  public static G1RangeResult calculateG1Range(String headerPath, String dsPath,
      DenialConstraint dc) {
    Double[] doubles = new Double[2];
    // Size1
    String excludeEvi = buildExcludeEvi(headerPath);
    DenialConstraint excludeDC = DCFormatUtil.convertString2DC(excludeEvi, loadHeader(headerPath));
    int excludeSize = new HydraDetector(dsPath, Sets.newHashSet(excludeDC)).detect().size();
    log.debug("ExcludeSize = {}, dc = {}", excludeSize, DCFormatUtil.convertDC2String(excludeDC));

    // Size2
    int size = new HydraDetector(dsPath, Sets.newHashSet(dc)).detect().size();
    String dcStr = DCFormatUtil.convertDC2String(dc);
    log.debug("Size = {}, dc = {}", size, dcStr);

    // Size3
    int subSizeMin = Integer.MAX_VALUE;
    DenialConstraint subDCMin = null;
    ArrayList<Predicate> list = new ArrayList<>();
    for (Predicate predicate : dc.getPredicateSet()) {
      list.add(predicate);
    }
    List<List<Predicate>> lists = generateSubLists(list);
    for (List<Predicate> predicates : lists) {
      // SubDC
      PredicateBitSet ps = new PredicateBitSet();
      for (Predicate predicate : predicates) {
        ps.add(predicate);
      }
      DenialConstraint subDC = new DenialConstraint(ps);
      int subSize = new HydraDetector(dsPath, Sets.newHashSet(subDC)).detect().size();
      log.debug("SubSize = {}, dc = {}", subSize, DCFormatUtil.convertDC2String(subDC));

      if (subSize < subSizeMin) {
        subSizeMin = subSize;
        subDCMin = subDC;
      }
    }
    log.debug("SubSizeMin = {}, dc = {}", subSizeMin, DCFormatUtil.convertDC2String(subDCMin));
    Input input = generateNewCopy(dsPath);
    int lineCount = input.getLineCount();
    log.debug("LineCount = {}", lineCount);

    // Size2
    int low = size >= excludeSize ? (size - excludeSize) : size;
    // Size1 Size3
    int high = subSizeMin >= excludeSize ? (subSizeMin - excludeSize) : subSizeMin;
    // Important!!!
    // In hydra, if g1*combinations = 1.x, then it will allow 2 violations.
    // So we need revise low high before calculating g1.
    int lowRev = Math.max(0, low - 1);
    int highRev = Math.max(0, high - 1);
    log.debug("Low = {}, High = {}, LowRev = {}, HighRev = {}", low, high, lowRev, highRev);
    // Size4
    int combinations = lineCount * (lineCount - 1);
    log.debug("Tuple pairs combinations = {}", combinations);

    double leftG1 = lowRev / (double) combinations;
    double rightG1 = highRev / (double) combinations;
    log.debug("LeftG1 = {}, RightG1 = {}", leftG1, rightG1);
    doubles[0] = leftG1;
    doubles[1] = rightG1;
    return new G1RangeResult(doubles, dcStr);
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
