package edu.fudan.algorithms.uguide;

import static edu.fudan.utils.CorrelationUtil.getDCScoreUniformMap;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class DCsQuestion {

  private final NTreeSearch gtTree;
  private final Set<DenialConstraint> testDCs;
  private final Set<DCViolation> vios;
  private final Map<String, Double> columnsCorrScoreMap;
  private final int minLenOfDC;
  private final double succinctFactor;
  private final DCQStrategy strategy;
  private final int budget;

  public DCsQuestion(NTreeSearch gtTree, Set<DenialConstraint> testDCs, Set<DCViolation> vios,
      Map<String, Double> columnsCorrScoreMap, int minLenOfDC, double succinctFactor,
      DCQStrategy strategy, int budget) {
    this.gtTree = gtTree;
    this.testDCs = testDCs;
    this.vios = vios;
    this.columnsCorrScoreMap = columnsCorrScoreMap;
    this.minLenOfDC = minLenOfDC;
    this.succinctFactor = succinctFactor;
    this.strategy = strategy;
    this.budget = budget;
  }

  public DCsQuestionResult simulate() {
    log.debug("Simulating dc question...");
    log.debug("Using strategy = {}, budget = {}", strategy, budget);
    log.debug("SuccinctFactor = {}, minLenOfDC = {}", succinctFactor, minLenOfDC);

    Map<DenialConstraint, Integer> dcViosMap = Maps.newHashMap();
    for (DCViolation vio : vios) {
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      DenialConstraint dc = dcs.get(0);
      if (dcViosMap.containsKey(dc)) {
        int i = dcViosMap.get(dc);
        dcViosMap.put(dc, i + 1);
      } else {
        dcViosMap.put(dc, 1);
      }
    }
    for (DenialConstraint dc : testDCs) {
      if (!dcViosMap.containsKey(dc)) {
        dcViosMap.put(dc, 0);
      }
    }
    List<Entry<DenialConstraint, Integer>> entries = new ArrayList<>(dcViosMap.entrySet());
    log.debug("TestDCs={}, DCViosMap={}", testDCs.size(), entries.size());

    Map<DenialConstraint, Double> dcScoreUniformMap = getDCScoreUniformMap(testDCs,
        columnsCorrScoreMap, minLenOfDC, succinctFactor);
    // 综合打分:
    // 1.简洁性+相关性打分低（更可能是假规则）的在前
    // 2.冲突数量多的在前
    switch (strategy) {
      case SUC_COR_VIOS:
        // 打分低的在前
        // 冲突多的在前面
        // 按字典序，防止每次顺序不一样
        entries.sort(Comparator.comparingDouble(
                (Entry<DenialConstraint, Integer> entry) -> dcScoreUniformMap.get(entry.getKey()))
            .thenComparingInt((Entry<DenialConstraint, Integer> entry) -> -entry.getValue())
            .thenComparing(
                (Entry<DenialConstraint, Integer> entry) -> DCFormatUtil.convertDC2String(
                    entry.getKey())));
        break;
      case SUC_COR:
        // 打分低的在前
        // 按字典序，防止每次顺序不一样
        entries.sort(Comparator.comparingDouble(
                (Entry<DenialConstraint, Integer> entry) -> dcScoreUniformMap.get(entry.getKey()))
            .thenComparing(
                (Entry<DenialConstraint, Integer> entry) -> DCFormatUtil.convertDC2String(
                    entry.getKey())));
        break;
      case RANDOM_DC:
        Collections.shuffle(entries);
        break;
      default:
        log.error("Unknown strategy: {}", strategy);
        break;
    }

    Set<DenialConstraint> trueDCs = new HashSet<>();
    Set<DenialConstraint> falseDCs = new HashSet<>();
    List<Entry<DenialConstraint, Integer>> subList = entries.subList(0,
        Math.min(budget, entries.size()));
    int totalViosSize = 0;
    for (Entry<DenialConstraint, Integer> e : subList) {
      DenialConstraint dc = e.getKey();
      Integer viosSize = e.getValue();
      // Simulate checking if it is true dc!!!
      // TODO: Here true dc means useful dc.
      //  Useful dc: 1.All true violations and can be inferred by gtDCs.
      //  Unuseful dc: 1.No violations; 2.Any false violations; 3.All true violations but can not be inferred by gtDCs.
      if (isTrueDC(dc, gtTree)) {
        trueDCs.add(dc);
        // True violations size
        totalViosSize += viosSize;
      } else {
        falseDCs.add(dc);
      }
    }

    int budgetUsed = subList.size();
    double trueDCRate = (double) trueDCs.size() / budgetUsed;
    double falseDCRate = (double) falseDCs.size() / budgetUsed;
    log.debug("TrueDCRate = {}, falseDCRate = {}", trueDCRate, falseDCRate);
    return new DCsQuestionResult(falseDCs, trueDCs, trueDCRate, totalViosSize, budgetUsed);
  }

  private boolean isTrueDC(DenialConstraint dc, NTreeSearch gtTree) {
    return dc.isImpliedBy(gtTree);
  }

}
