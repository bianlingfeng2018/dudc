package edu.xxx.algorithms;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.xxx.conf.DefaultConf.minNumInCluster;
import static edu.xxx.conf.DefaultConf.minTopKOfCluster;
import static edu.xxx.utils.FileUtil.getLinesWithHeader;

/**
 * @author XXX
 */
@Slf4j
public class TupleSampler {

  public SampleResult sample(File dataF,
      int topKOfCluster,
      int numInCluster,
      Set<Integer> skippedColumns,
      boolean requireHeader,
      Set<Integer> excludedLines,
      Map<DenialConstraint, Set<LinePair>> falseDCLinePairMap,
      boolean addCounterExampleS,
      boolean randomClusterS) throws FileNotFoundException, InputGenerationException, InputIterationException {
    log.info("Sample input file: {}", dataF.toString());

    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(dataF);
    Input input = new Input(actualGenerator.generateNewCopy());
    log.info("Sample input size: {}", input.getLineCount());

    int k = Math.max(minTopKOfCluster, topKOfCluster);
    int n = Math.max(minNumInCluster, numInCluster);
    log.info("Sample parameters: k={}, n={}, skippedColumns={}, excludedLines={}, falseDCLinePairMap={}, addCounterExampleS={}, randomClusterS={}",
        k, n, skippedColumns == null ? null : skippedColumns.size(),
        falseDCLinePairMap == null ? null : falseDCLinePairMap.size(),
        excludedLines == null ? null : excludedLines.size(),
        addCounterExampleS, randomClusterS);

    long t1 = System.currentTimeMillis();
    Set<Integer> sampled = new ColumnAwareWeightedClusterSampler()
        .sampling(input, k, n, skippedColumns, excludedLines, falseDCLinePairMap, addCounterExampleS, randomClusterS);
    long t2 = System.currentTimeMillis();
    log.debug("Sample time = {} ms", (t2 - t1));
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
