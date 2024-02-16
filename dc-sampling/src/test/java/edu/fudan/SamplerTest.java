package edu.fudan;

import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.TupleSampler;
import edu.fudan.utils.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class SamplerTest {

  private final int topKOfCluster = 2;
  private final int maxInCluster = 2;
  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";

  @Test
  public void testSampling()
      throws IOException, InputGenerationException, InputIterationException {
    String dataFile = baseDir + File.separator + "preprocessed_data" + File.separator
        + "preprocessed_hospital_dirty.csv";
    File dataF = new File(dataFile);
    String sampledDataFile = baseDir + File.separator + "preprocessed_data" + File.separator
        + "preprocessed_hospital_dirty_sample.csv";
    File sampledF = new File(sampledDataFile);
    // Sampling
//    HashSet<Integer> skippedColumns = new HashSet<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));
    HashSet<Integer> skippedColumns = new HashSet<>();
    List<List<String>> sampled = new TupleSampler().sample(dataF, topKOfCluster, maxInCluster, skippedColumns, true);
    // Write to file
    log.debug("Write to file: {}", sampledDataFile);
    FileUtil.writeLinesToFile(sampled, sampledF);
  }
}
