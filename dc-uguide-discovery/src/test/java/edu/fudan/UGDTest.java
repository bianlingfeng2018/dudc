package edu.fudan;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.BasicDCGenerator;
import edu.fudan.algorithms.TupleSampler;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.utils.FileUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;

@Slf4j
public class UGDTest {

  /**
   * Parameters used in User-guided-DC-Detection process.
   */
  UGDParams params;

  @Before
  public void setUp() throws Exception {
    int dsIndex = 0;
    params = UGDRunner.buildParams(dsIndex);
  }

  /**
   * Test sampling.
   */
  @Test
  public void testSampling() throws InputGenerationException, IOException, InputIterationException {
    boolean addCounterExampleS = false;
    boolean randomClusterS = false;

    // Load changes and error lines
    List<TChange> changes = loadChanges(params.changesPath);
    Set<Integer> errorLines = getErrorLinesContainingChanges(changes);
    log.info("Changes={}, errorLines={}", changes.size(), errorLines.size());

    // Sampling
    log.info("Sampling...");
    TupleSampler.SampleResult sampleResult = new TupleSampler().sample(
        new File(params.dirtyDataPath), topKOfCluster, numInCluster, null, true, null, null,
        addCounterExampleS, randomClusterS);

    // Persist sample result
    List<List<String>> linesWithHeader = sampleResult.getLinesWithHeader();
    log.info("Write {} lines(with header line) to file: {}", linesWithHeader.size(),
        params.sampledDataPath);
    FileUtil.writeListLinesToFile(linesWithHeader, new File(params.sampledDataPath));

    // Calculate the ratio
    Set<Integer> errorLinesInSample = sampleResult.getLineIndices().stream()
        .filter(i -> errorLines.contains(i)).collect(Collectors.toSet());
    log.info("ErrorLinesInSample/SampledLines: {}/{}", errorLinesInSample.size(),
        sampleResult.getLineIndices().size());

    log.info("ErrorLinesInSample: {}", errorLinesInSample);  // [2336, 2481, 2456, 2506]
  }

  /**
   * Test discovering DCs.
   */
  @Test
  public void testDiscoveringDCs() {
    double g1 = 0.001;
    int topK = 5;  // Integer.MAX_VALUE means get all DCs

    log.debug("Params = {}", params.toString());
    BasicDCGenerator generator = new BasicDCGenerator(params.sampledDataPath, params.fullDCsPath,
        params.headerPath, new HashSet<>(), g1, topK);

    Set<DenialConstraint> dcs = generator.generateDCs();
    log.info("DCs size={}", dcs.size());
  }
}
