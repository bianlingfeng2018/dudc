package edu.fudan;

import edu.fudan.algorithms.WeightedCluster;
import gnu.trove.iterator.TIntIterator;
import org.junit.Test;

public class RandomTest {

  @Test
  public void testRandomElements() {
    WeightedCluster cluster = new WeightedCluster();
    cluster.add(1);
    cluster.add(2);
    cluster.add(3);
    cluster.add(4);
    cluster.add(5);
    cluster.add(6);
    cluster.add(7);
    cluster.add(8);
    cluster.add(9);
    cluster.add(10);
    int k = 3;
    int i = 0;
    cluster.randomize();
    TIntIterator iterator = cluster.iterator();
    while (iterator.hasNext() && i < k) {
      int next = iterator.next();
      System.out.println(next);
      i++;
    }
  }
}
