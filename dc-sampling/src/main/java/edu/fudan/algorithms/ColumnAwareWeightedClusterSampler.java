package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.optimizeWithCounter;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.paritions.LinePair;
import gnu.trove.iterator.TIntIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColumnAwareWeightedClusterSampler {

  private final boolean randomCluster = false;

  /**
   * @param input          所有元组
   * @param topKOfCluster  采样多少个簇
   * @param maxInCluster   每个簇采样多少个元组
   * @param skippedColumns 跳过的列
   * @return 采样得到的元组
   */
  public Set<Integer> sampling(Input input, int topKOfCluster, int maxInCluster,
      Set<Integer> skippedColumns, Set<Integer> excludedLines,
      Map<DenialConstraint, Set<LinePair>> falseDCLinePairMap) {
    Set<Integer> lines = Sets.newHashSet();
    if (optimizeWithCounter) {
      // TODO: 这里有个矛盾，作为反例的元组，可能是脏数据(冲突内的Cell都是干净的，错误在其他Cell上)
      //  规则越长，或者错误率越高，这种风险越大
      for (Entry<DenialConstraint, Set<LinePair>> entry : falseDCLinePairMap.entrySet()) {
        Set<LinePair> linePairs = entry.getValue();
        // 至少取一对反例，且都是干净元组
        boolean leastOneLinePairAdded = false;
        for (LinePair linePair : linePairs) {
          if (leastOneLinePairAdded) {
            break;
          }
          int line1 = linePair.getLine1();
          int line2 = linePair.getLine2();

          // TODO: 目前加入修复策略后，可以保证excludedLines为空，从而假DC的反例一定不会被排除，但尚不能保证该反例修复后仍然还是反例
          //  即，当修复假DC反例时，若修复与这个DC相关的cell，则可能修复后就不再是反例，因此下一步可以考虑限制只能修复与这个DC无关的cell
          if (!excludedLines.contains(line1) && !excludedLines.contains(line2)) {
            leastOneLinePairAdded = true;
            lines.add(line1);
            lines.add(line2);
          }
        }
//      if (!leastOneLinePairAdded) {
//        log.debug("No one linePair found, because all pairs contain errors. {}", dc2String);
//      }
      }
    }
    log.debug("Add counter-example lines : {}", lines.size());
    for (ParsedColumn<?> c : input.getColumns()) {
      if (skippedColumns != null && skippedColumns.contains(c.getIndex())) {
        // 排除列
        continue;
      }
//      log.debug("Sampling column " + c.getName());
      // 构建valueMap
      Map<Object, MyOrderedCluster> valueMap = new HashMap<>();
      for (int i = 0; i < input.getLineCount(); ++i) {
        if (excludedLines != null && excludedLines.contains(i)) {
          // 排除行（已经被用户判断是脏数据）
          continue;
        }
        MyOrderedCluster cluster = valueMap.computeIfAbsent(c.getValue(i),
            (k) -> new MyOrderedCluster());
        // 自动更新size，即簇大小
        cluster.add(i);
      }

      List<MyOrderedCluster> clusters = getClusterList(valueMap, randomCluster);

      // TODO: 如何考虑有错误混入size最大的cluster中这种情况？
      List<MyOrderedCluster> selected = clusters.subList(0,
          Math.min(topKOfCluster, clusters.size()));

      // TODO: Cluster内部采用随机策略比较好吧？避免一直得到同一个结果
      for (MyOrderedCluster cluster : selected) {
        // cluster内部随机化
        cluster.randomize();
        TIntIterator it = cluster.iterator();
        int cnt = 0;
        while (it.hasNext() && cnt < maxInCluster) {
          int line1 = it.next();
          lines.add(line1);
          cnt++;
        }
      }
    }
    return lines;
  }

  private static List<MyOrderedCluster> getClusterList(
      Map<Object, MyOrderedCluster> valueMap, boolean randomCluster) {
    List<MyOrderedCluster> clusters;
    if (randomCluster) {
      clusters = new ArrayList<>(valueMap.values());
      // 打乱顺序
      Collections.shuffle(clusters);
    } else {
      clusters = valueMap.values().stream()
          .sorted()
          .collect(Collectors.toList());
    }
    return clusters;
  }

//  private void updateSizeFrequency(Map<Integer, Integer> sfmap, WeightedCluster cluster) {
//    int currSize = cluster.size();
//    int lastSize = currSize - 1;
//    if (currSize == 1) {
//      increaseOrCreate(sfmap, currSize);
//    } else {
//      // 新的size的频率值+1
//      increaseOrCreate(sfmap, currSize);
//      // 旧的size频率值-1
//      decrease(sfmap, lastSize);
//    }
//  }
//
//  private static void decrease(Map<Integer, Integer> sfmap, int oldSize) {
//    Integer f = sfmap.get(oldSize);
//    sfmap.put(oldSize, f - 1);
//  }
//
//  private static void increaseOrCreate(Map<Integer, Integer> sfmap, int newSize) {
//    if (sfmap.containsKey(newSize)) {
//      Integer f = sfmap.get(newSize);
//      sfmap.put(newSize, f + 1);
//    } else {
//      sfmap.put(newSize, 1);
//    }
//  }
}
