package edu.fudan.algorithms;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.evidenceset.build.sampling.OrderedCluster;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import gnu.trove.iterator.TIntIterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ColumnAwareWeightedClusterSampler {

  /**
   * @param input          所有元组
   * @param topKOfCluster  采样多少个簇
   * @param maxInCluster   每个簇采样多少个元组
   * @param skippedColumns
   * @return 采样得到的元组
   */
  public Set<Integer> sampling(Input input, int topKOfCluster, int maxInCluster,
      Set<Integer> skippedColumns, Set<Integer> excludedLines) {
    Set<Integer> lines = Sets.newHashSet();
    for (ParsedColumn<?> c : input.getColumns()) {
      if (skippedColumns != null && skippedColumns.contains(c.getIndex())) {
        // 排除列
        continue;
      }
      log.info("Sampling column " + c.getName());
      // 构建valueMap
      Map<Object, WeightedCluster> valueMap = new HashMap<>();
      for (int i = 0; i < input.getLineCount(); ++i) {
        if (excludedLines != null && excludedLines.contains(i)) {
          // 排除行（已经被用户判断是脏数据）
          continue;
        }
        WeightedCluster cluster = valueMap.computeIfAbsent(c.getValue(i),
            (k) -> new WeightedCluster());
        // 自动更新size，即簇大小
        cluster.add(i);
      }

      List<WeightedCluster> sorted = valueMap.values().stream()
          .sorted()
          .collect(Collectors.toList());

      // TODO: 如何考虑有错误混入size最大的cluster中这种情况？
      List<WeightedCluster> selected = sorted.subList(0, Math.min(topKOfCluster, sorted.size()));

      // TODO: Cluster内部采用随机策略比较好吧？避免一直得到同一个结果
      for (OrderedCluster cluster : selected) {
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
