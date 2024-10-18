package edu.xxx.utils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author XXX
 */
public class CorrelationUtil {

  public static Map<String, Double> readColumnCorrScoreMap(String correlationByUserPath)
      throws IOException {
    BufferedReader br = new BufferedReader(new FileReader(correlationByUserPath));
    br.readLine();
    Map<String, Double> columnsCorrScoreMap = Maps.newHashMap();
    String line = null;
    while ((line = br.readLine()) != null) {
      String[] split = line.split(",");
      if (split.length != 5) {
        throw new RuntimeException("Illegal correlation line");
      }
      String col1 = split[0];
      String col2 = split[2];
      double score = Double.parseDouble(split[4]);
      String key = col1 + "_" + col2;
      if (columnsCorrScoreMap.containsKey(key)) {
        throw new RuntimeException("Illegal correlation key");
      }
      columnsCorrScoreMap.put(key, score);
    }
    return columnsCorrScoreMap;
  }

  public static Map<DenialConstraint, Double> getDCScoreUniformMap(
      Set<DenialConstraint> dcs,
      Map<String, Double> columnsCorrScoreMap,
      int minLenOfDC,
      double succinctFactor
  ) {
    double minCorrScore = Double.MAX_VALUE;
    double maxCorrScore = -Double.MAX_VALUE;
    Map<DenialConstraint, Double> dcScoreMap = Maps.newHashMap();
    for (DenialConstraint dc : dcs) {
      List<String> colList = getColList(dc);
      int cnt = 0;
      double sum = 0.0;
      for (int i = 0; i < colList.size(); i++) {
        for (int j = 0; j < colList.size(); j++) {
          if (i == j) {
            continue;
          }
          String col1 = colList.get(i);
          String col2 = colList.get(j);
          String key = col1 + "_" + col2;
          double score = columnsCorrScoreMap.get(key);
          sum = sum + score;
          cnt++;
        }
      }
      double corrScore = sum / cnt;
      // 统计所有规则相关性的最大、最小分数
      if (corrScore < minCorrScore) {
        minCorrScore = corrScore;
      }
      if (corrScore > maxCorrScore) {
        maxCorrScore = corrScore;
      }
      dcScoreMap.put(dc, corrScore);
    }

    // 分数区间
    double corrScoreRange = maxCorrScore - minCorrScore;
    Map<DenialConstraint, Double> dcScoreUniformMap = Maps.newHashMap();
    // 归一化分数
    for (Entry<DenialConstraint, Double> entry : dcScoreMap.entrySet()) {
      DenialConstraint dc = entry.getKey();
      Double corrScore = entry.getValue();
      // 相关性分数
      double normalizedCorrScore = (corrScore - minCorrScore) / corrScoreRange;
      // 简洁性分数
      double succinctScore = (double) minLenOfDC / dc.getPredicateCount();
      // 综合打分
      double composeScore = succinctFactor * succinctScore + (1 - succinctFactor) * normalizedCorrScore;
      dcScoreUniformMap.put(dc, composeScore);
    }
    return dcScoreUniformMap;
  }

  private static List<String> getColList(DenialConstraint dc) {
    PredicateBitSet predicateSet = dc.getPredicateSet();
    Set<String> cols = Sets.newHashSet();
    for (Predicate predicate : predicateSet) {
      String col1 = predicate.getOperand1().getColumn().getName();
      String col2 = predicate.getOperand2().getColumn().getName();
      cols.add(col1);
      cols.add(col2);
    }
    List<String> colList = new ArrayList<>(cols);
    return colList;
  }

}
