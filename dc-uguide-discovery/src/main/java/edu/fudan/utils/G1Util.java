package edu.fudan.utils;

import java.util.ArrayList;
import java.util.List;

public class G1Util {

  public static <T>List<List<T>> generateSubLists(List<T> list) {
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
