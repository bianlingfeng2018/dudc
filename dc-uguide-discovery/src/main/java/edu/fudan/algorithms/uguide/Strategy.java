package edu.fudan.algorithms.uguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author Lingfeng
 */
public class Strategy {

  // 从Set集合中随机选择指定数量的元素
  public static <T> List<T> getRandomElements(Set<T> set, int count) {
    // 转换为List以便进行随机访问
    List<T> list = new ArrayList<>(set);

    // 打乱顺序
    Collections.shuffle(list);

    // 获取前count个元素
    return list.subList(0, Math.min(count, list.size()));
  }

  /**
   * 根据行关联的对象集合的数量降序排序
   *
   * @param lineSetCountMap 行和关联对象集合的Map
   * @param <T>             关联的对象泛型
   * @return 排好序的Map
   */
  public static <T> ArrayList<Entry<Integer, Set<T>>> getSortedLines(
      Map<Integer, Set<T>> lineSetCountMap) {
    ArrayList<Entry<Integer, Set<T>>> sortedEntries = new ArrayList<>(
        lineSetCountMap.entrySet());
    sortedEntries.sort(Comparator.comparingInt(entry -> -entry.getValue().size()));
    return sortedEntries;
  }

  /**
   * 构建行和关联的对象集合
   *
   * @param lineSetCountMap 行和关联的对象集合Map
   * @param line            行
   * @param obj             待新增的关联的对象
   * @param <T>             关联的对象泛型
   */
  public static <T> void addToCountMap(Map<Integer, Set<T>> lineSetCountMap, int line, T obj) {
    if (lineSetCountMap.containsKey(line)) {
      Set<T> set = lineSetCountMap.get(line);
      set.add(obj);
    } else {
      HashSet<T> set = new HashSet<>();
      set.add(obj);
      lineSetCountMap.put(line, set);
    }
  }
}
