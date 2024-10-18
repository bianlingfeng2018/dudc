package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.addToCountMap;
import static edu.fudan.algorithms.uguide.Strategy.getSortedLines;
import static edu.fudan.conf.DefaultConf.userProb_tupleq;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.fudan.algorithms.DCViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class TupleQuestion {

  private final Set<Integer> errorLinesOfChanges;
  private final Set<DCViolation> vios;
  private final TupleQStrategy strategy;
  private final int budget;

  public TupleQuestion(Set<Integer> errorLinesOfChanges, Set<DCViolation> vios,
      TupleQStrategy strategy, int budget) {
    this.errorLinesOfChanges = errorLinesOfChanges;
    this.vios = vios;
    this.strategy = strategy;
    this.budget = budget;
  }

  public TupleQuestionResult simulate() {
    log.debug("Simulating tuple question...");
    log.debug("Using strategy = {}, budget = {}", strategy, budget);
    Map<Integer, Set<DCViolation>> lineViosCountMap = Maps.newHashMap();
    Map<Integer, Set<DenialConstraint>> lineDCsCountMap = Maps.newHashMap();
    Set<Integer> lines = Sets.newHashSet();
    for (DCViolation vio : vios) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcList = vio.getDenialConstraintsNoData();
      int line1 = linePair.getLine1();
      int line2 = linePair.getLine2();
      addToCountMap(lineViosCountMap, line1, vio);
      addToCountMap(lineViosCountMap, line2, vio);
      for (DenialConstraint dc : dcList) {
        addToCountMap(lineDCsCountMap, line1, dc);
        addToCountMap(lineDCsCountMap, line2, dc);
      }

      lines.add(line1);
      lines.add(line2);
    }

    List<Integer> sorted = null;
    double errorRate = 0.0;
    switch (strategy) {
      case DCS:
        sorted = getSortedLines(lineDCsCountMap, null);
        break;
      case VIOLATIONS:
        sorted = getSortedLines(lineViosCountMap, null);
        break;
      case DCS_PRIOR:
        sorted = getSortedLines(lineViosCountMap, lineDCsCountMap);
        break;
      case VIOLATIONS_PRIOR:
        sorted = getSortedLines(lineDCsCountMap, lineViosCountMap);
        break;
      case RANDOM_TUPLE:
        sorted = new ArrayList<>(lines);
        break;
      default:
        log.error("Unknown strategy: {}", strategy);
        break;
    }
    List<Integer> subList = sorted.subList(0, Math.min(budget, sorted.size()));
    // Simulate check error tuples(lines)!!!
    List<Integer> errorsTuples = subList.stream().filter(l -> isError(l))
        .collect(Collectors.toList());
    long errorFound = errorsTuples.size();
    errorRate = (double) errorFound / subList.size();
    log.debug("ErrorFound={}, Budget={}, ErrorRate={}", errorFound, budget, errorRate);

    return new TupleQuestionResult(errorsTuples, subList.size(), errorRate);
  }

  private boolean isError(Integer l) {
//    return errorLinesOfChanges.contains(l);
    // TODO:用户判断正确率
    double r = Math.random();  // 生成0到1之间的随机数

    if (r < userProb_tupleq) {
      // 60%的概率进入逻辑A
      return errorLinesOfChanges.contains(l);
    } else {
      // 40%的概率进入逻辑B
      return !errorLinesOfChanges.contains(l);
    }
  }

}
