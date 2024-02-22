package edu.fudan.algorithms.uguide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
}
