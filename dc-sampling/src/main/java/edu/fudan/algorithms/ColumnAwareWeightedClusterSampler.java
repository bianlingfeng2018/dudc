package edu.fudan.algorithms;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
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
      Set<Integer> skippedColumns) {
    Set<Integer> lines = Sets.newHashSet();
    for (ParsedColumn<?> c : input.getColumns()) {
      if (skippedColumns.contains(c.getIndex())) {
        continue;
      }
      log.info("Sampling column " + c.getName());
      // 构建WeightedCluster
      Map<Integer, Integer> sizeFrequencyMap = Maps.newHashMap();
      Map<Object, WeightedCluster> valueMap = new HashMap<>();
      for (int i = 0; i < input.getLineCount(); ++i) {
        WeightedCluster cluster = valueMap.computeIfAbsent(c.getValue(i),
            (k) -> new WeightedCluster());
        // 自动更新size，即簇大小
        cluster.add(i);
        // 更新frequency，即簇大小的出现频率
        updateSizeFrequency(sizeFrequencyMap, cluster);
      }
      for (WeightedCluster cluster : valueMap.values()) {
        cluster.setFrequency(sizeFrequencyMap.get(cluster.size()));
      }

      List<WeightedCluster> sorted = valueMap.values().stream()
          .sorted()
          .collect(Collectors.toList());

//      List<WeightedCluster> selected = Lists.newArrayList();
//      int lastFrq = 0;
//      for (WeightedCluster cluster : sorted) {
//        int currFrq = cluster.getFrequency();
//        if (currFrq == lastFrq) {
//          continue;
//        }
//        lastFrq = currFrq;
//        selected.add(cluster);
//        if (selected.size() >= topKOfCluster) {
//          // 退出条件：遍历完所有sorted，或者达到topKOfCluster
//          break;
//        }
//      }
      List<WeightedCluster> selected = sorted.subList(0, Math.min(topKOfCluster, sorted.size()));

      for (OrderedCluster cluster : selected) {
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

  private void updateSizeFrequency(Map<Integer, Integer> sfmap, WeightedCluster cluster) {
    int currSize = cluster.size();
    int lastSize = currSize - 1;
    if (currSize == 1) {
      increaseOrCreate(sfmap, currSize);
    } else {
      // 新的size的频率值+1
      increaseOrCreate(sfmap, currSize);
      // 旧的size频率值-1
      decrease(sfmap, lastSize);
    }
  }

  private static void decrease(Map<Integer, Integer> sfmap, int oldSize) {
    Integer f = sfmap.get(oldSize);
    sfmap.put(oldSize, f - 1);
  }

  private static void increaseOrCreate(Map<Integer, Integer> sfmap, int newSize) {
    if (sfmap.containsKey(newSize)) {
      Integer f = sfmap.get(newSize);
      sfmap.put(newSize, f + 1);
    } else {
      sfmap.put(newSize, 1);
    }
  }
}
