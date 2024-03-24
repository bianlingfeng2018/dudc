package edu.fudan.algorithms;

import static edu.fudan.utils.FileUtil.getLinesWithHeader;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class TupleSampler {

  // 最少取3个cluster，一个cluster内最少取2个元素，保证大于、小于、等于的情况都存在
  private final int minK = 2;
  private final int minM = 2;

  public SampleResult sample(File dataF, int topKOfCluster, int maxInCluster,
      Set<Integer> skippedColumns, boolean requireHeader, Set<Integer> excludedLines,
      Map<DenialConstraint, Set<LinePair>> falseDCLinePairMap)
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    log.info("Input file: {}", dataF.toString());
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(dataF);
    Input input = new Input(actualGenerator.generateNewCopy());
    log.info("Input size: {}", input.getLineCount());
    int k = Math.max(minK, topKOfCluster);
    int m = Math.max(minM, maxInCluster);

    long startSample = System.currentTimeMillis();
    ColumnAwareWeightedClusterSampler sampler = new ColumnAwareWeightedClusterSampler();
    Set<Integer> sampled = sampler.sampling(input, k, m, skippedColumns, excludedLines,
        falseDCLinePairMap);
    long timeSample = System.currentTimeMillis() - startSample;
    log.debug("Sample time = {} ms", timeSample);
    log.info("Sampled size = {}", sampled.size());

//    log.debug("Get lines according to sampled line indices...");
    List<List<String>> lines = getLinesWithHeader(actualGenerator, sampled, requireHeader);
    return new SampleResult(sampled, lines);
  }

  @Getter
  @Setter
  @AllArgsConstructor
  public static class SampleResult {

    private Set<Integer> lineIndices;
    private List<List<String>> linesWithHeader;
  }
}
