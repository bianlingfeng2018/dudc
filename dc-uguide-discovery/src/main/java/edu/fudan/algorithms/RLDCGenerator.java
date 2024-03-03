package edu.fudan.algorithms;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Set;
import lombok.Setter;

/**
 * @author Lingfeng
 */
public class RLDCGenerator implements DCGenerator {
  private final String inputDataPath;
  private final String headerPath;
  private final String topKDCsPath;
  @Setter
  private Set<DenialConstraint> excludeDCs = Sets.newHashSet();
  @Setter
  private double errorThreshold = 0.0;

  public RLDCGenerator(String inputDataPath, String headerPath, String topKDCsPath) {
    this.inputDataPath = inputDataPath;
    this.headerPath = headerPath;
    this.topKDCsPath = topKDCsPath;
  }

  @Override
  public Set<DenialConstraint> generateDCsForUser() {
    // TODO: java调用python，训练模型并预测得到top-k规则
    return null;
  }
}
