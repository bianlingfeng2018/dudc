package edu.fudan.algorithms;

import de.hpi.naumann.dc.evidenceset.build.sampling.OrderedCluster;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Lingfeng
 */
@Getter
@Setter
public class WeightedCluster extends OrderedCluster implements Comparable<WeightedCluster> {

  @Override
  public int compareTo(WeightedCluster o) {
    // 按照簇内元组数量比较，数量少(+)/多(-)的在前
    return -Integer.compare(this.size(), o.size());
  }


}
