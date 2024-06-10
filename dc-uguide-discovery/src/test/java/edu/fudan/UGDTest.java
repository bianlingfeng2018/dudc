package edu.fudan;

import static edu.fudan.conf.DefaultConf.canBreakEarly;
import static edu.fudan.conf.DefaultConf.delta;
import static edu.fudan.conf.DefaultConf.excludeLinePercent;
import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;
import static edu.fudan.utils.DCUtil.getCellsOfChanges;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.printDCVioMap;
import static edu.fudan.utils.FileUtil.generateNewCopy;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.BasicDCGenerator;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.TupleSampler;
import edu.fudan.algorithms.uguide.CellQStrategy;
import edu.fudan.algorithms.uguide.CellQuestionResult;
import edu.fudan.algorithms.uguide.CellQuestionV2;
import edu.fudan.algorithms.uguide.DCsQStrategy;
import edu.fudan.algorithms.uguide.DCsQuestion;
import edu.fudan.algorithms.uguide.DCsQuestionResult;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.algorithms.uguide.TupleQStrategy;
import edu.fudan.algorithms.uguide.TupleQuestion;
import edu.fudan.algorithms.uguide.TupleQuestionResult;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

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
    log.info("TopK dcs size={}", dcs.size());
    DCUtil.persistTopKDCs(new ArrayList<>(dcs), params.topKDCsPath);
  }

  /**
   * Test detect violations.
   */
  @Test
  public void testDetectViolations() {
    DCViolationSet vios = new HydraDetector(params.dirtyDataPath, params.topKDCsPath,
        params.headerPath).detect();
    log.debug("Vios size={}", vios.size());

    printDCVioMap(vios);
  }


  /**
   * Test cell question.
   */
  @Test
  public void testCellQuestion() {
    // TODO: 目前发现一个BART的BUG:
    //  注入错误后，输出的xxx_dirty.csv中单引号(')变成两个单引号('')，如果出现这种情况，需要手动替换一下。
    CellQStrategy strategy = CellQStrategy.VIO_AND_CONF;
    int budget = 100;
    Set<DCViolation> vios = new HydraDetector(params.dirtyDataPath, params.topKDCsPath,
        params.headerPath).detect().getViosSet();
    Input di = generateNewCopy(params.dirtyDataPath);
    Set<TCell> cellsOfChanges = getCellsOfChanges(loadChanges(params.changesPath));
    List<DenialConstraint> dcs = DCLoader.load(params.headerPath, params.topKDCsPath);
    log.debug("DCs={}, Violations={}, CellsOfChanges={}, CellQBudgets={}", dcs.size(), vios.size(),
        cellsOfChanges.size(), budget);

    CellQuestionV2 selector = new CellQuestionV2(di, cellsOfChanges, new HashSet<>(dcs), vios,
        budget, delta, canBreakEarly, strategy, excludeLinePercent);
    CellQuestionResult result = selector.simulate();

    log.debug(result.toString());
  }

  /**
   * Test tuple question.
   */
  @Test
  public void testTupleQuestion() {
    // RANDOM, DCS, VIOLATIONS, DCS_PRIOR, VIOLATIONS_PRIOR
    // 0.02, 0.26, 0.715, 0.47, 0.725
    TupleQStrategy strategy = TupleQStrategy.VIOLATIONS_PRIOR;
    int budget = 200;
    Set<DCViolation> vios = new HydraDetector(params.dirtyDataPath, params.topKDCsPath,
        params.headerPath).detect().getViosSet();

    Set<Integer> errorLines = getErrorLinesContainingChanges(loadChanges(params.changesPath));
    TupleQuestion selector = new TupleQuestion(errorLines, vios, strategy, budget);
    TupleQuestionResult result = selector.simulate();

    log.debug(result.toString());
  }

  /**
   * Test dcs question
   *
   * @throws IOException
   */
  @Test
  public void testDCsQuestion() throws IOException {
    // TODO:考虑什么DC最有可能是真DC
    //  同时考虑DC如何给出上下文辅助用户判断正误，因为直接判断比较难，同时这个上下文可以用来训练相关性打分矩阵
    // 1.简洁性 + 覆盖率 = interesting
    // 2.关联冲突个数，希望真冲突的个数越多越好；怎么判断真冲突？
    // DC-Violation置信度 Line-Violations 如果一个DC是真DC，那么真的错误会出现一个Line关联非常多的Vios，但是假的DC这种情况会减少，不是少数Line cover所有Vios，而是大家比较平均？
    // 冲突数量不能判断规则真假，冲突多少只取决于反例的个数
    int minLenOfDC = 2;
    double succinctFactor = 0.8;
    int budget = 10;
    DCsQStrategy strategy = DCsQStrategy.RANDOM_DC;
    Set<DCViolation> vios = new HydraDetector(params.dirtyDataPath, params.topKDCsPath,
        params.headerPath).detect().getViosSet();

    List<DenialConstraint> testDCs = DCLoader.load(params.headerPath, params.topKDCsPath);
    List<DenialConstraint> gtDCs = DCLoader.load(params.headerPath, params.groundTruthDCsPath);
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(params.correlationByUserPath);

    DCsQuestion selector = new DCsQuestion(gtDCs, testDCs, vios, columnsCorrScoreMap, minLenOfDC,
        succinctFactor, strategy, budget);

    DCsQuestionResult result = selector.simulate();
    log.debug(result.toString());
  }

}
