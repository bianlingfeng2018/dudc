package edu.xxx.algorithms.uguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author XXX
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
   * 根据行关联的对象集合的数量降序排序，先按第一个Map排序，再按第二个Map排序，后者优先级更高。
   *
   * @param map1 第一个Map
   * @param map2 第二个Map
   * @param <T1> 对象1泛型
   * @param <T2> 对象2泛型
   * @return 排好序的Lines
   */
  public static <T1, T2> List<Integer> getSortedLines(Map<Integer, Set<T1>> map1,
      Map<Integer, Set<T2>> map2) {
    Set<Integer> set1 = map1.keySet();
    List<Integer> result = new ArrayList<>(set1);
    // Sort by natural order.
    result.sort(Comparator.naturalOrder());  // 防止每次顺序不一样。但默认已经是有序的？
    // Sort by map1 in descending order.
    result.sort(Comparator.comparingInt(l -> -map1.get(l).size()));
    if (map2 != null) {
      Set<Integer> set2 = map2.keySet();
      if (!set1.equals(set2)) {
        throw new IllegalArgumentException("Not equal: set1, set2.");
      }
      // Sort by map2 in descending order.
      result.sort(Comparator.comparingInt(l -> -map2.get(l).size()));
    }
    return result;
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
