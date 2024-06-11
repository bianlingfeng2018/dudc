package edu.fudan;

import static edu.fudan.conf.DefaultConf.canBreakEarly;
import static edu.fudan.conf.DefaultConf.delta;
import static edu.fudan.conf.DefaultConf.excludeLinePercent;
import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;
import static edu.fudan.utils.DCUtil.genLineChangesMap;
import static edu.fudan.utils.DCUtil.getCellsOfChanges;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.printDCVioMap;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.getRepairedLinesWithHeader;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
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
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
    // TODO:考虑什么DC最有可能是真DC，同时考虑:
    //  1.如何给出数据集的语法概要(Syntactic Profile)，辅助用户间接判断DC是否为真
    //  2.训练相关性打分矩阵
    // 1.简洁性+相关性/简洁性+覆盖率(interesting)
    // 2.关联冲突个数，希望真冲突的个数越多越好
    int minLenOfDC = 2;
    double succinctFactor = 0.5;
    int budget = 10;
    DCsQStrategy strategy = DCsQStrategy.SUC_AND_COR;
    Set<DCViolation> vios = new HydraDetector(params.dirtyDataPath, params.topKDCsPath,
        params.headerPath).detect().getViosSet();

    List<DenialConstraint> testDCs = DCLoader.load(params.headerPath, params.topKDCsPath);
    List<DenialConstraint> gtDCs = DCLoader.load(params.headerPath, params.groundTruthDCsPath);
    Map<String, Double> corrMap = readColumnCorrScoreMap(params.correlationByUserPath);

    printCorrMap(corrMap, 10);

    NTreeSearch gtTree = new NTreeSearch();
    for (DenialConstraint gtDC : gtDCs) {
      gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    }
    DCsQuestion selector = new DCsQuestion(gtTree, new HashSet<>(testDCs), vios, corrMap,
        minLenOfDC, succinctFactor, strategy, budget);

    // SUC_AND_COR_VIOS:
    // succinctFactor = 0.5
    //  FalseDCs=3, TrueDCs=7, TrueDCRate=0.7, TotalViosSize=108256, BudgetUsed=10
    // succinctFactor = 0.0
    //  FalseDCs=3, TrueDCs=7, TrueDCRate=0.7, BudgetUsed=10
    // succinctFactor = 1.0
    //  FalseDCs=8, TrueDCs=2, TrueDCRate=0.2, BudgetUsed=10

    // SUC_AND_COR:
    // succinctFactor = 0.5
    //  FalseDCs=3, TrueDCs=7, TrueDCRate=0.7, TotalViosSize=108256, BudgetUsed=10
    // succinctFactor = 0.0
    //  FalseDCs=3, TrueDCs=7, TrueDCRate=0.7, BudgetUsed=10
    // succinctFactor = 1.0
    //  FalseDCs=8, TrueDCs=2, TrueDCRate=0.2, BudgetUsed=10

    // RANDOM_DC:
    // succinctFactor = 0.5
    //  FalseDCs=8, TrueDCs=2, TrueDCRate=0.2, BudgetUsed=10
    DCsQuestionResult result = selector.simulate();
    log.debug(result.toString());
  }


  /**
   * Test repair lines.
   */
  @Test
  public void testRepairLines()
      throws IOException, InputGenerationException, InputIterationException {
    File dataF = new File(params.dirtyDataPath);
    List<TChange> changes = loadChanges(params.changesPath);
    List<DenialConstraint> dcs = DCLoader.load(params.headerPath, params.topKDCsPath);
    HydraDetector detector = new HydraDetector(params.dirtyDataPath, new HashSet<>(dcs));

    // Detect violations before repairing lines.
    log.debug("Detect before repairing.");
    detectAndPrintViosWithCells(detector, params.dirtyDataPath, changes);

    // Repair specified lines(e.g. all the lines containing errors.)
    Map<Integer, Map<Integer, String>> lineChangesMap = genLineChangesMap(params.dirtyDataPath,
        changes);
    log.debug("Building index for changes={}", lineChangesMap.size());

    // Specify some lines.
    // Sets.newHashSet(6467)
    Set<Integer> errorLinesOfChanges = getErrorLinesContainingChanges(changes);
    log.debug("Specify error lines={}", errorLinesOfChanges.size());

    // Repair lines.
    List<List<String>> repairedLinesWithHeader = getRepairedLinesWithHeader(errorLinesOfChanges,
        lineChangesMap, dataF);
    log.debug("Repair lines={}", repairedLinesWithHeader.size());

    // Persist.
    log.debug("Write to file: {}", params.dirtyDataPath);
    FileUtil.writeListLinesToFile(repairedLinesWithHeader, dataF);

    // Detect violations after repairing lines.
    log.debug("Detect after repairing.");
    detectAndPrintViosWithCells(detector, params.dirtyDataPath, changes);
  }

  /**
   * Detect and print violations with cells contained in it.
   *
   * @param detector      Hydra detector
   * @param dirtyDataPath Dirty data path
   * @param changes       Changes loaded from file
   */
  private void detectAndPrintViosWithCells(HydraDetector detector, String dirtyDataPath,
      List<TChange> changes) {
    DCViolationSet vios = detector.detect();

    printDCVioMap(vios);

    Set<TCell> cellsOfChanges = getCellsOfChanges(changes);

    // 每个DC只打印一个冲突样例
    log.debug("Print example violation with cells contained in it.");
    Set<DenialConstraint> visitedDCs = Sets.newHashSet();
    Input di = generateNewCopy(dirtyDataPath);
    for (DCViolation v : vios.getViosSet()) {
      List<DenialConstraint> dcs = v.getDenialConstraintsNoData();
      DenialConstraint dc = dcs.get(0);
      String dcStr = DCFormatUtil.convertDC2String(dc);
      if (!visitedDCs.contains(dc)) {
        // 打印一个冲突中所有的Cell
        LinePair linePair = v.getLinePair();
        visitedDCs.add(dc);
        log.debug("DC = {}", dcStr);
        log.debug("LinePair = {}", linePair);
        Set<TCell> cells = getCellsOfViolation(di, dc, linePair);
        for (TCell cell : cells) {
          boolean contains = cellsOfChanges.contains(cell);
          log.debug("Cell={}, ContainedInChanges={}", cell, contains);
        }
      }
    }
  }

  /**
   * Print top-k column pairs with correlation score.
   *
   * @param columnsCorrScoreMap Correlation score map
   * @param topK                Top-k
   */
  private void printCorrMap(Map<String, Double> columnsCorrScoreMap, int topK) {
    log.debug("ColumnsCorrScoreMap={}", columnsCorrScoreMap.size());
    ArrayList<Entry<String, Double>> entries = new ArrayList<>(columnsCorrScoreMap.entrySet());
    entries.sort(Comparator.comparingDouble(e -> -e.getValue()));
    List<Entry<String, Double>> subList = entries.subList(0, topK);
    for (Entry<String, Double> entry : subList) {
      log.debug("k={}, v={}", entry.getKey(), entry.getValue());
    }
  }

}
