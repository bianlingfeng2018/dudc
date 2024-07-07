package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.maxDCLen;
import static edu.fudan.utils.DCUtil.convertDCFinderDC2Str;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import edu.fudan.utils.DCUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class BasicDCGenerator implements DCGenerator {

  private final String inputDataPath;
  private final String fullDCsPath;
  private final String topKDCsPath;
  private final String headerPath;
  private final Set<DenialConstraint> excludeDCs;
  private final double errorThreshold;
  private final int topK;
  @Setter
  private String evidencePath;

  public BasicDCGenerator(String inputDataPath, String fullDCsPath, String topKDCsPath,
      String headerPath, Set<DenialConstraint> excludeDCs, double errorThreshold, int topK) {
    this.inputDataPath = inputDataPath;
    this.fullDCsPath = fullDCsPath;
    this.topKDCsPath = topKDCsPath;
    this.headerPath = headerPath;
    this.excludeDCs = excludeDCs;
    this.errorThreshold = errorThreshold;
    this.topK = topK;
  }

  @Override
  public Set<DenialConstraint> generateDCs() {
    try {
      DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(this.inputDataPath,
          this.errorThreshold, this.evidencePath);
      List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = getSortedDCs(
          dcs);
      log.info("Result size: " + dcList.size());

      log.debug("Saving DCs into: " + this.fullDCsPath);
      persistDCFinderDCs(dcList, this.fullDCsPath);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // 取前k个规则
    // TODO: 删除特别长的规则，否则检测冲突的时间会很长
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(this.topK, this.fullDCsPath,
        this.headerPath, this.excludeDCs, maxDCLen);
    if (topKDCsPath != null) {
      DCUtil.persistTopKDCs(topKDCs, this.topKDCsPath);
    }
    return new HashSet<>(topKDCs);
  }

  public static void persistDCFinderDCs(
      List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList,
      String dcsPathForFCDC) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(dcsPathForFCDC));
    for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcList) {
      // ----- 适配输出 -----
      String dcStr = convertDCFinderDC2Str(dc);
      writer.write(dcStr);
      writer.newLine();
    }
    writer.close();
  }

  public static List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> getSortedDCs(
      DenialConstraintSet dcs) {
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = Lists.newArrayList();
    for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcs) {
      dcList.add(dc);
    }
    dcList.sort(Comparator.comparingInt(
            de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint::getPredicateCount)
        .thenComparing(System::identityHashCode));
    return dcList;
  }

}
