package edu.fudan.algorithms;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.input.Input;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class TupleSampler {

  // 最少取3个cluster，一个cluster内最少取2个元素，保证大于、小于、等于的情况都存在
  private final int minK = 2;
  private final int minM = 2;

  public ArrayList<List<String>> sample(File dataF, int topKOfCluster, int maxInCluster,
      Set<Integer> skippedColumns, boolean requireHeader)
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    log.info("Sampling: {}", dataF.toString());
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(dataF);
    Input input = new Input(actualGenerator.generateNewCopy());
    log.info("Input size: {}", input.getLineCount());
    int k = Math.max(minK, topKOfCluster);
    int m = Math.max(minM, maxInCluster);

    long startSample = System.currentTimeMillis();
    ColumnAwareWeightedClusterSampler sampler = new ColumnAwareWeightedClusterSampler();
    Set<Integer> sampled = sampler.sampling(input, k, m, skippedColumns);
    long timeSample = System.currentTimeMillis() - startSample;
    log.debug("Sample time = {} ms", timeSample);
    log.info("Sampled size = {}", sampled.size());

    log.info("Get lines according to sampled line indices...");
    ArrayList<List<String>> lines = convertInt2Str(actualGenerator, sampled, requireHeader);
    log.info("Sampling done");
    return lines;
  }

  private static ArrayList<List<String>> convertInt2Str(DefaultFileInputGenerator actualGenerator,
      Set<Integer> sampled, boolean requireHeader)
      throws InputGenerationException, InputIterationException {
    ArrayList<List<String>> lines = Lists.newArrayList();
    RelationalInput ri = actualGenerator.generateNewCopy();
    if (requireHeader) {
      List<String> columnNames = ri.columnNames();
      lines.add(columnNames);
    }
    int i = 0;
    while (ri.hasNext()) {
      List<String> next = ri.next();
      if (sampled.contains(i)) {
        lines.add(next);
      }
      i++;
    }
    return lines;
  }
}
