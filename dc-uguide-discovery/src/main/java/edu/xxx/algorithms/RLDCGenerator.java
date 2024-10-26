package edu.xxx.algorithms;

import static edu.xxx.conf.DefaultConf.predictArgs;
import static edu.xxx.conf.DefaultConf.sharedArgs;
import static edu.xxx.conf.DefaultConf.trainArgs;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Setter;

/**
 * @author XXX
 */
public class RLDCGenerator implements DCGenerator {

  private final String inputDataPath;
  private final String evidencesPath;
  private final String dcsPathForDCMiner;
  private final String headerPath;
  @Setter
  private Set<DenialConstraint> excludeDCs = Sets.newHashSet();
  @Setter
  private double errorThreshold = 0.0;

  public RLDCGenerator(String inputDataPath, String evidencesPath, String dcsPathForDCMiner,
      String headerPath) {
    this.inputDataPath = inputDataPath;
    this.evidencesPath = evidencesPath;
    this.dcsPathForDCMiner = dcsPathForDCMiner;
    this.headerPath = headerPath;
  }

  @Override
  public Set<DenialConstraint> generateDCs() {
    try {
      // TODO: java调用python，训练模型并预测得到top-k规则
      // 先用DCFinder生成证据集，这里errorThreshold并不起什么作用
      DiscoveryEntry.discoveryDCsDCFinder(inputDataPath, -1,
          evidencesPath);
      // 再用DCMiner训练+预测
      String[] args4Train = (sharedArgs + " " + trainArgs).split(" ");
      String[] args4Predict = (sharedArgs + " " + predictArgs).split(" ");
      PythonCaller.trainModel(args4Train);
      PythonCaller.predict(args4Predict);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    // 读取k个规则
    List<DenialConstraint> dcList = DCLoader.load(headerPath, dcsPathForDCMiner, excludeDCs);
    return new HashSet<>(dcList);
  }
}
