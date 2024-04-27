package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.utils.DCUtil.convertDCFinderDC2Str;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
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
  private final String dcsPathForFCDC;
  private final String headerPath;
  @Setter
  private Set<DenialConstraint> excludeDCs = Sets.newHashSet();
  @Setter
  private double errorThreshold = 0.0;

  public BasicDCGenerator(String inputDataPath, String dcsPathForFCDC, String headerPath) {
    this.inputDataPath = inputDataPath;
    this.dcsPathForFCDC = dcsPathForFCDC;
    this.headerPath = headerPath;
  }

//  @Override
//  public Set<DenialConstraint> generateDCsForUser() {
//    log.info("Generate top-{} DCs, exclude: {}", topK, this.excludeDCs.size());
//    // 从经过优化的采样数据中发现规则
//    DiscoveryEntry.doDiscovery(this.inputDataPath, this.dcsPathForFCDC);
//    // 取前k个规则
//    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(topK, this.dcsPathForFCDC,
//        this.headerPath, this.excludeDCs);
//    return new HashSet<>(topKDCs);
//  }

  @Override
  public Set<DenialConstraint> generateDCsForUser() {
    try {
      DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(this.inputDataPath,
          this.errorThreshold);
      List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList =
          getSortedDCs(dcs);
      log.info("Result size: " + dcList.size());

      log.debug("Saving DCs into: " + this.dcsPathForFCDC);
      persistDCFinderDCs(dcList, this.dcsPathForFCDC);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // 取前k个规则
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(topK, this.dcsPathForFCDC,
        this.headerPath, this.excludeDCs);
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

  public static List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint>
  getSortedDCs(DenialConstraintSet dcs) {
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = Lists.newArrayList();
    for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcs) {
      dcList.add(dc);
    }
    dcList.sort(
        new Comparator<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint>() {
          @Override
          public int compare(
              de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint o1,
              de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint o2) {
            return Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
          }
        });
    return dcList;
  }

}
