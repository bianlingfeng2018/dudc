package edu.fudan.algorithms;

import de.hpi.naumann.dc.evidenceset.build.sampling.OrderedCluster;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Lingfeng
 */
@Getter
@Setter
public class WeightedCluster extends OrderedCluster implements Comparable<WeightedCluster>{

  private int frequency;
  @Override
  public int compareTo(WeightedCluster o) {
    // 先按照频率比较，频率低的在前
    int freqComparison = Integer.compare(this.frequency, o.getFrequency());
    if (freqComparison != 0) {
      return freqComparison;
    }
    // 再按照簇内元组数量比较，数量少的在前
    return Integer.compare(this.size(), o.size());
  }


}
