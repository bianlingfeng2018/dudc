package edu.xxx.algorithms;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import java.util.Random;
import lombok.Getter;
import lombok.Setter;

/**
 * @author XXX
 */
@Getter
@Setter
public class MyOrderedCluster implements Comparable<MyOrderedCluster> {

  private final TIntArrayList array;

  public MyOrderedCluster() {
    this.array = new TIntArrayList();
  }

  public void randomize() {
    for (int i = array.size(); i > 1; i--) {
      swap(array, i - 1, r.nextInt(i));
    }
  }

  public void add(int i) {
    array.add(i);
  }

  private void swap(TIntArrayList l, int i, int j) {
    l.set(i, l.set(j, l.get(i)));
  }

  private static Random r = new Random();

  public int size() {
    return array.size();
  }

  public int nextLine() {
//		return array.get(next++ % size());
    return array.get(r.nextInt(size()));
  }

  public TIntIterator iterator() {
    return array.iterator();
  }

  @Override
  public int compareTo(MyOrderedCluster o) {
    // 按照簇内元组数量比较，数量少(+)/多(-)的在前
    return -Integer.compare(this.size(), o.size());
  }


}
