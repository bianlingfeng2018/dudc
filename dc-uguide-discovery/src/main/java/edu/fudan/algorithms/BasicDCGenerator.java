package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.minimumSharedValue;
import static edu.fudan.conf.DefaultConf.noCrossColumn;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.utils.DCUtil.convertDCFinderDC2Str;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithms.dcfinder.DCFinder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.predicates.PredicateBuilder;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.DCMinderToolsException;
import edu.fudan.utils.DCUtil;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
      Input input = new Input(
          new DefaultFileInputGenerator(new File(this.inputDataPath)).generateNewCopy());
      PredicateBuilder predicatesSpace = new PredicateBuilder(input, noCrossColumn,
          minimumSharedValue);
      log.info("Size of the predicate space:" + predicatesSpace.getPredicates().size());

      DenialConstraintSet dcs = new DCFinder().run(input, predicatesSpace, errorThreshold);
      log.info("Result size: " + dcs.size());

      log.debug("Saving DCs into: " + this.dcsPathForFCDC);
      BufferedWriter writer = new BufferedWriter(new FileWriter(this.dcsPathForFCDC));
      for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcs) {
        // ----- 适配输出 -----
        String dcStr = convertDCFinderDC2Str(dc);
        writer.write(dcStr);
        writer.newLine();
      }
      writer.close();
    } catch (InputIterationException | InputGenerationException | DCMinderToolsException |
             IOException e) {
      throw new RuntimeException(e);
    }
    // 取前k个规则
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(topK, this.dcsPathForFCDC,
        this.headerPath, this.excludeDCs);
    return new HashSet<>(topKDCs);
  }

  public void setErrorThreshold(double errorThreshold) {
    this.errorThreshold = errorThreshold;
  }
}
