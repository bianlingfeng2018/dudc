package edu.fudan;

import static edu.fudan.CorrelationTest.correlationByUserPath;
import static edu.fudan.algorithms.BasicDCGenerator.getSortedDCs;
import static edu.fudan.algorithms.BasicDCGenerator.persistDCFinderDCs;
import static edu.fudan.algorithms.uguide.Strategy.addToCountMap;
import static edu.fudan.algorithms.uguide.Strategy.getSortedLines;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.predictArgs;
import static edu.fudan.conf.DefaultConf.sharedArgs;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.conf.DefaultConf.trainArgs;
import static edu.fudan.utils.CorrelationUtil.getDCScoreUniformMap;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;
import static edu.fudan.utils.DCUtil.genLineChangesMap;
import static edu.fudan.utils.DCUtil.getCellsOfChanges;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;
import static edu.fudan.utils.DCUtil.getCellsOfViolations;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.loadDirtyDataExcludedLines;
import static edu.fudan.utils.DCUtil.printDCViolationsMap;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.getRepairedLinesWithHeader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.Operator;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import edu.fudan.algorithms.BasicDCGenerator;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.PythonCaller;
import edu.fudan.algorithms.RLDCGenerator;
import edu.fudan.algorithms.TupleSampler;
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.CellQuestion;
import edu.fudan.algorithms.uguide.CellQuestionResult;
import edu.fudan.algorithms.uguide.CellQuestionV2;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
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
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscoveryTest {

  private static int dsIndex = 0;
  private static String[] dsNames = {"hospital", "stock", "tax"};
  private static String dsName = dsNames[dsIndex];
  public static String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";
  public static String headerPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + "_header.csv";
  public static String cleanDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + ".csv";
  public static String dirtyDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + "_dirty.csv";
  public static String changesPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + "_changes.csv";
  public static String excludedLinesPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + "_dirty_excluded.csv";
  public static String sampledDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_" + dsName + "_dirty_sample.csv";
  public static String universalDCsPath = baseDir + File.separator +
      "evidence_set\\dcs_fcdc_" + dsName + ".out";
  public static String dcsPathForDCMiner = baseDir + File.separator +
      "result_rules\\dcminer_5_" + dsName + ".csv";
  public static String evidencesPathForFCDC = baseDir + File.separator +
      "evidence_set\\evidence_set_fcdc_" + dsName + ".csv";
  public static String topKDCsPath = baseDir + File.separator +
      "result_rules\\dcs_" + dsName + ".out";
  public static String groundTruthDCsPath = baseDir + File.separator +
      "result_rules\\dcs_" + dsName + "_ground.out";
  public static String groundTruthDCsInjectErrorPath = baseDir + File.separator +
      "result_rules\\dcs_" + dsName + "_ground_inject_error.out";
  public static String candidateDCsPath = baseDir + File.separator +
      "result_rules\\dcs_" + dsName + "_candidate.out";
  public static String trueDCsPath = baseDir + File.separator +
      "result_rules\\dcs_" + dsName + "_candidate_true.out";
  public static String visitedDCsPath = baseDir + File.separator +
      "evidence_set\\excluded_rules_" + dsName + ".csv";
  public static String csvResultPath = baseDir + File.separator +
      "evaluation\\eval_error_detect_" + dsName + ".csv";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath,
        changesPath,
        dirtyDataPath,
        excludedLinesPath,
        sampledDataPath,
        universalDCsPath,
        dcsPathForDCMiner,
        evidencesPathForFCDC,
        topKDCsPath,
        groundTruthDCsPath,
        candidateDCsPath,
        trueDCsPath,
        visitedDCsPath,
        headerPath,
        csvResultPath);
    ud.guidedDiscovery();
  }

  /**
   * Discover dc using fdcd(2023)
   */
//  @Test
//  public void testDiscoveryDCsUsingFDCD() {
//    DiscoveryEntry.doDiscovery(cleanDataPath, universalDCsPath);
//  }

  /**
   * Discover dc using dcFinder(2019)
   */
  @Test
  public void testDiscoveryDCsUsingDCFinderNoEvidence() throws IOException {
    // 1.当evidenceFile为null，则生成规则集合
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(cleanDataPath,
        0.0, null);
    log.info("DCs: {}", dcs.size());
  }

  /**
   * Discover dc using dcFinder(2019)
   */
  @Test
  public void testDiscoveryDCsUsingDCFinder() throws IOException {
    // 2.当evidenceFile不为null，则生成证据集（作为DCMiner训练模型的输入）
    log.info("cleanDataPath={}", cleanDataPath);
    log.info("evidencesPathForFCDC={}", evidencesPathForFCDC);
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(cleanDataPath,
        0.0, evidencesPathForFCDC);
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList =
        getSortedDCs(dcs);
    log.info("dcList={}", dcList.size());
    log.info("Persist to universalDCsPath={}", universalDCsPath);
    persistDCFinderDCs(dcList, universalDCsPath);
  }

  /**
   * Discover approximate dcs using dcFinder(2019)
   *
   * @throws IOException
   */
  @Test
  public void testDiscoveryADCsUsingDCFinder() throws IOException {
    // g1:0.0001 0.001 0.01
    log.info("dirtyDataPath={}", dirtyDataPath);
    log.info("evidencesPathForFCDC={}", evidencesPathForFCDC);
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(dirtyDataPath,
        0.001, evidencesPathForFCDC);
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList =
        getSortedDCs(dcs);
    log.info("dcList={}", dcList.size());
    log.info("Persist to universalDCsPath={}", universalDCsPath);
    persistDCFinderDCs(dcList, universalDCsPath);
  }

  /**
   * Discover dc using basicGenerator, which save all dcs to file and gen top-k dcs from that file
   */
  @Test
  public void testDiscoveryDCsUsingBasicGenerator() {
    BasicDCGenerator generator = new BasicDCGenerator(cleanDataPath, universalDCsPath, headerPath);
    generator.setExcludeDCs(new HashSet<>());
    generator.setErrorThreshold(0.0);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();
    log.info("DCs size={}", dcs.size());
  }

  /**
   * Discover approximate dc using basicGenerator
   */
  @Test
  public void testDiscoveryADCsUsingBasicGenerator() {
    BasicDCGenerator generator = new BasicDCGenerator(dirtyDataPath, universalDCsPath, headerPath);
    generator.setExcludeDCs(new HashSet<>());
    generator.setErrorThreshold(0.001);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();
    log.info("DCs size={}", dcs.size());
  }

  /**
   * Generate top-k dcs from file which contains all dcs
   */
  @Test
  public void testGenTopKDCs() {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(5, universalDCsPath, headerPath, null);
    DCUtil.persistTopKDCs(topKDCs, topKDCsPath);
  }

  /**
   * Test implication of mock dc
   */
  @Test
  public void testImplicationOfMockDC() {
    // 长规则能被短规则推断出来
    ColumnOperand<?> o1 = mock(ColumnOperand.class);
    ColumnOperand<?> o2 = mock(ColumnOperand.class);

    DenialConstraint dc1 = new DenialConstraint(
        new Predicate(Operator.GREATER, o1, o2), new Predicate(Operator.LESS, o1, o2));
    DenialConstraint dc2 = new DenialConstraint(
        new Predicate(Operator.GREATER, o1, o2));
    NTreeSearch tree = new NTreeSearch();
    tree.add(PredicateSetFactory.create(dc2.getPredicateSet()).getBitset());

    boolean equals = dc1.isImpliedBy(tree);
    assertTrue(equals);
  }

  /**
   * Test implication of dc loaded from file
   */
  @Test
  public void testImplicationOfDCLoadedFromFile() {
    List<DenialConstraint> dcs1 = DCLoader.load(headerPath, topKDCsPath);
    List<DenialConstraint> gtDCs1 = DCLoader.load(headerPath, groundTruthDCsPath);

    NTreeSearch gtTree1 = new NTreeSearch();
    for (DenialConstraint dc : gtDCs1) {
      gtTree1.add(PredicateSetFactory.create(dc.getPredicateSet()).getBitset());
    }

    assert dcs1.get(0).isImpliedBy(gtTree1);
//    assert gtDCs1.contains(dcs1.get(0));

  }

  /**
   * Detect on clean dataset to verify dcs correctness. We expect no violations on clean dataset.
   */
  @Test
  public void testVerifyDCsByDetectionUsingHydra() {
    HydraDetector detector = new HydraDetector(cleanDataPath, universalDCsPath, headerPath);
    DCViolationSet violationSet = detector.detect();
    log.info("violationSet={}", violationSet.size());

    printDCViolationsMap(violationSet);
  }

  /**
   * Test detect DCViolation using hydra, and print some logs
   */
  @Test
  public void testDetectDCViolationUsingHydra() {
    HydraDetector detector = new HydraDetector(dirtyDataPath, trueDCsPath, headerPath);
    detectUsingHydraDetector(detector);
  }

  /**
   * Test violations size compare
   */
  @Test
  public void testDCsViolationsSizeCompare() {
    // TODO: Violation size: tureDcs,candiDCs,gtDCs并无大小关系，因为冲突之间可能有元组对的重合，一对元组可能涉及多个冲突
    DCViolationSet vioSet1 =
        new HydraDetector(dirtyDataPath, candidateDCsPath, headerPath).detect();
    DCViolationSet vioSet2 =
        new HydraDetector(dirtyDataPath, trueDCsPath, headerPath).detect();
    DCViolationSet vioSet3 =
        new HydraDetector(dirtyDataPath, groundTruthDCsPath, headerPath).detect();
    log.info("candi={}, candiTure={}, gt={}", vioSet1.size(), vioSet2.size(), vioSet3.size());
  }

  /**
   * Test DCViolation's associated dcs size
   */
  @Test
  public void testAllViolationHasOnlyOneDC() {
    // 测试一个冲突在什么情况下才会关联多个DC？hydra的判断标准是什么？
    // 目前测一个vio就只对应一个DC
    HydraDetector detector = new HydraDetector(dirtyDataPath, groundTruthDCsPath, headerPath);
    DCViolationSet vioSet = detector.detect();
    log.info("VioSet = {}", vioSet.size());
    for (DCViolation vio : vioSet.getViosSet()) {
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      assertEquals(1, dcs.size());
    }
  }

  /**
   * Test print dc and it's violations
   */
  @Test
  public void testPrintDCViolationsMap() {
    HydraDetector detector = new HydraDetector(sampledDataPath, groundTruthDCsPath, headerPath);
    DCViolationSet violationSet = detector.detect();
    log.info("ViolationSet={}", violationSet.size());
    printDCViolationsMap(violationSet);
  }

  /**
   * Test all errors can be found by dcs
   */
  @Test
  public void testDCsErrorsEquality() {
    // TODO: Errors can all be found?
    // 对于BART注入错误，元组id(TupleOID)从1开始
    // 对于Hydra检测冲突，行号line(LinePair)从0开始
    List<TChange> changes = loadChanges(changesPath);
    Set<TCell> cellsOfChanges = getCellsOfChanges(changes);
    log.info("CellsOfChanges: {}, {}", cellsOfChanges.size(), cellsOfChanges.stream().findAny());

    // 检测冲突
    DCViolationSet vioSetOfGroundTruthDCs =
        new HydraDetector(dirtyDataPath, groundTruthDCsPath, headerPath).detect();
    DCViolationSet vioSetOfTrueDCs =
        new HydraDetector(dirtyDataPath, trueDCsPath, headerPath).detect();
    Input di = generateNewCopy(dirtyDataPath);

    // 转换成Cell
    Set<TCell> cellsGroundTruth =
        getCellsOfViolations(vioSetOfGroundTruthDCs.getViosSet(), di);
    Set<TCell> cellsTrue =
        getCellsOfViolations(vioSetOfTrueDCs.getViosSet(), di);
    log.info("CellsGroundTruth: {}, {}", cellsGroundTruth.size(),
        cellsGroundTruth.stream().findAny());
    log.info("CellsTrue: {}, {}", cellsTrue.size(),
        cellsTrue.stream().findAny());

    // 所有错误都能被发现
    assertTrue(cellsGroundTruth.containsAll(cellsOfChanges));
    assertTrue(cellsTrue.containsAll(cellsOfChanges));

    // 打印Cell样例
    cellsGroundTruth.stream().findAny().ifPresent(tCell ->
        log.info("Example1: {}", tCell));
    cellsTrue.stream().findAny().ifPresent(tCell ->
        log.info("Example2: {}", tCell));
  }

  /**
   * Test compare excluded dirty lines with true error lines
   */
  @Test
  public void testExcludedDirtyLinesAreTrueErrorLines() throws IOException {
    // TODO: All errorLines are in excluded dirtyLines, or vise versa?
    List<TChange> changes = loadChanges(changesPath);
    Set<Integer> changedLines = Sets.newHashSet();
    for (TChange change : changes) {
      changedLines.add(change.getLineIndex());
    }
    log.info("Changes={}", changedLines.size());

    Set<Integer> excludedLines = loadDirtyDataExcludedLines(excludedLinesPath);
    log.info("ExcludedLines={}", excludedLines.size());

    List<Integer> changesInExcludedLines = changedLines.stream()
        .filter(i -> excludedLines.contains(i))
        .collect(Collectors.toList());
    List<Integer> excludesInChangedLines = excludedLines.stream()
        .filter(i -> changedLines.contains(i))
        .collect(Collectors.toList());
    log.info("changesInExcludedLines={}, excludesInChangedLines={}", changesInExcludedLines.size(),
        excludesInChangedLines.size());
  }

  /**
   * Test error lines in sample
   */
  @Test
  public void testErrorLinesInSample()
      throws InputGenerationException, IOException, InputIterationException {
    // Load changes and error lines
    List<TChange> changes = loadChanges(changesPath);
    Set<Integer> errorLinesContainingChanges = getErrorLinesContainingChanges(changes);
    log.info("Changes={}, errorLinesContainingChanges={}", changes.size(),
        errorLinesContainingChanges.size());
    // Sample and get error lines
    log.info("Sampling...");
    SampleResult sampleResult = new TupleSampler()
        .sample(new File(dirtyDataPath), topKOfCluster, maxInCluster,
            null, true, null, null);
    List<List<String>> linesWithHeader = sampleResult.getLinesWithHeader();
    log.info("Write {} lines(with header line) to file: {}", linesWithHeader.size(),
        sampledDataPath);
    FileUtil.writeListLinesToFile(linesWithHeader, new File(sampledDataPath));
    // 比较
    Set<Integer> errorLinesInSample = sampleResult.getLineIndices().stream()
        .filter(i -> errorLinesContainingChanges.contains(i)).collect(
            Collectors.toSet());
    log.info("ErrorLinesInSample/SampledLineIndices: {}/{}",
        errorLinesInSample.size(), sampleResult.getLineIndices().size());
    log.info("ErrorLinesInSample: {}", errorLinesInSample);  // [2336, 2481, 2456, 2506]
  }

  /**
   * Test DCMiner train
   */
  @Test
  public void testTrainDCMiner() throws IOException, InterruptedException {
    String[] args4Train = (sharedArgs + " " + trainArgs).split(" ");
    PythonCaller.trainModel(args4Train);
  }

  /**
   * Test DCMiner predict
   */
  @Test
  public void testDCMinerPredict() throws IOException, InterruptedException {
    String[] args4Predict = (sharedArgs + " " + predictArgs).split(" ");
    PythonCaller.predict(args4Predict);
  }

  /**
   * Test discover dc using RLDCGenerator
   */
  @Test
  public void testDiscoverDCUsingRLDCGenerator() {
    List<DenialConstraint> excludeDCs = DCLoader.load(headerPath, visitedDCsPath, new HashSet<>());
    log.debug("Visited DCs size={}", excludeDCs.size());
    RLDCGenerator generator = new RLDCGenerator(sampledDataPath, evidencesPathForFCDC,
        dcsPathForDCMiner, headerPath);
    generator.setExcludeDCs(new HashSet<>(excludeDCs));
//    generator.setErrorThreshold(0.001);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();
    log.info("DCMiner DCs size={}", dcs.size());
    for (DenialConstraint dc : dcs) {
      log.debug(DCFormatUtil.convertDC2String(dc));
    }
  }

  /**
   * Test dc minimize
   */
  @Test
  public void testMinimizeDCs() {
    // 测试准备注入错误的20条DCs已经是最小化的、没有重复的。
    String DCsPath = baseDir + File.separator +
        "result_rules\\dcs_hospital_ground_inject_error_20.out";
    List<DenialConstraint> dcs = DCLoader.load(headerPath, DCsPath, new HashSet<>());
    de.hpi.naumann.dc.denialcontraints.DenialConstraintSet set = new de.hpi.naumann.dc.denialcontraints.DenialConstraintSet();
    for (DenialConstraint dc : dcs) {
      set.add(dc);
    }
    log.debug("Before size = {}", set.size());
    set.minimize();
    log.debug("After size = {}", set.size());
  }

  // Question strategy
  @Test
  public void testTuplePairDCsMapping() {
    // 测试一个LinePair可能关联多个DC
    HydraDetector detector = new HydraDetector(dirtyDataPath, groundTruthDCsPath, headerPath);
    DCViolationSet vioSet = detector.detect();
    Map<LinePair, Set<DenialConstraint>> linePairDCsMap = Maps.newHashMap();
    for (DCViolation vio : vioSet.getViosSet()) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      if (dcs.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcs.get(0);
      if (linePairDCsMap.containsKey(linePair)) {
        Set<DenialConstraint> set = linePairDCsMap.get(linePair);
        set.add(dc);
      } else {
        linePairDCsMap.put(linePair, Sets.newHashSet(dc));
      }
    }
    int size1 = vioSet.size();  // 冲突个数
    int size2 = linePairDCsMap.size();  // 元组对个数
    log.debug("Violations = {}, LinePairs = {}", size1, size2);
    assertTrue(size1 >= size2);

    // 打印 LinePair -> DCs
    Set<DenialConstraint> next = linePairDCsMap.values().iterator().next();
    log.debug("DCs size = {}", next.size());
  }

  @Test
  public void testCellQuestion() {
    // TODO: 目前发现一个BART的大bug，注入错误后，输出的dirty版本数据单引号变成两个单引号'->''
    // CellQ算法
    Set<DCViolation> vios =
        new HydraDetector(dirtyDataPath, topKDCsPath, headerPath).detect().getViosSet();
    Input di = generateNewCopy(dirtyDataPath);
    Set<TCell> cellsOfChanges = getCellsOfChanges(loadChanges(changesPath));
    List<DenialConstraint> dcs = DCLoader.load(headerPath, topKDCsPath);
    log.debug("DCs={}", dcs.size());
    log.debug("Violations={}", vios.size());
    log.debug("CellsOfChanges={}", cellsOfChanges.size());
    log.debug("CellQBudgets={}", maxCellQuestionBudget);
    CellQuestion selector = new CellQuestionV2(di, cellsOfChanges, new HashSet<>(dcs), vios);

    selector.simulate();
    CellQuestionResult result = selector.getResult();

    log.debug(result.toString());
  }

  // Tuple strategy
  @Test
  public void testTupleQuestion() {
    // TODO: 测试优先选择关联冲突多的元组，以及优先选择关联冲突规则多的元组，以及在脏数据集还是采样数据集上效果更好？
    Set<DCViolation> vios =
        new HydraDetector(dirtyDataPath, universalDCsPath, headerPath).detect().getViosSet();
    Set<Integer> errorLines = getErrorLinesContainingChanges(loadChanges(changesPath));
    Map<Integer, Set<DCViolation>> lineViosCountMap = Maps.newHashMap();
    Map<Integer, Set<DenialConstraint>> lineDCsCountMap = Maps.newHashMap();
    Set<Integer> lines = Sets.newHashSet();
    for (DCViolation vio : vios) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcList = vio.getDenialConstraintsNoData();
      int line1 = linePair.getLine1();
      int line2 = linePair.getLine2();
      addToCountMap(lineViosCountMap, line1, vio);
      addToCountMap(lineViosCountMap, line2, vio);
      for (DenialConstraint dc : dcList) {
        addToCountMap(lineDCsCountMap, line1, dc);
        addToCountMap(lineDCsCountMap, line2, dc);
      }

      lines.add(line1);
      lines.add(line2);
    }

    // 单一排序1:关联vio数量多的在前
    ArrayList<Entry<Integer, Set<DCViolation>>> sortedLineVioMap = getSortedLines(
        lineViosCountMap);
    // 单一排序2:关联DC数量多的在前
    ArrayList<Entry<Integer, Set<DenialConstraint>>> sortedLineDCMap = getSortedLines(
        lineDCsCountMap);
    // 混合排序1:以DC数量排序为主
    List<Entry<Integer, Set<DCViolation>>> sortedComposeDCCountPrior = sortedLineVioMap.stream()
        .sorted(Comparator.comparingInt(entry -> {
          int size = lineDCsCountMap.get(entry.getKey()).size();
          return -size;
        })).collect(Collectors.toList());
    // 混合排序2:以vio数量排序位置
    List<Entry<Integer, Set<DenialConstraint>>> sortedComposeVioCountPrior = sortedLineDCMap.stream()
        .sorted(Comparator.comparingInt(entry -> {
          int size = lineViosCountMap.get(entry.getKey()).size();
          return -size;
        })).collect(Collectors.toList());
    int limit = 100;
    // 随机
    double errorPerByVioCount = getErrorPercent(sortedLineVioMap, errorLines, limit);
    double errorPerByDCCount = getErrorPercent(sortedLineDCMap, errorLines, limit);
    double errorPerByComposeDCPrior = getErrorPercent(sortedComposeDCCountPrior, errorLines, limit);
    double errorPerByComposeVioPrior = getErrorPercent(sortedComposeVioCountPrior, errorLines,
        limit);
    double errorPerByRandom = getErrorPercent(lines, errorLines, limit);
    log.debug("ErrorPerByVioCount = {}", errorPerByVioCount);
    log.debug("ErrorPerByDCCount = {}", errorPerByDCCount);
    log.debug("ErrorPerByComposeDCPrior = {}", errorPerByComposeDCPrior);
    log.debug("ErrorPerByComposeVioPrior = {}", errorPerByComposeVioPrior);
    log.debug("ErrorPerByRandom = {}", errorPerByRandom);
  }

  // DC strategy
  @Test
  public void testDCsQuestion() throws IOException {
    // TODO:考虑什么DC最有可能是真DC
    //  同时考虑DC如何给出上下文辅助用户判断正误，因为直接判断比较难，同时这个上下文可以用来训练相关性打分矩阵
    // 1.简洁性 + 覆盖率 = interesting
    // 2.关联冲突个数，希望真冲突的个数越多越好；怎么判断真冲突？
    // DC-Violation置信度 Line-Violations 如果一个DC是真DC，那么真的错误会出现一个Line关联非常多的Vios，但是假的DC这种情况会减少，不是少数Line cover所有Vios，而是大家比较平均？
    // 冲突数量不能判断规则真假，冲突多少只取决于反例的个数
    int minLenOfDC = 2;
//    double succinctFactor = 0.8;
    List<DenialConstraint> testDCs = DCLoader.load(headerPath, universalDCsPath);
    Set<DenialConstraint> trueDCs = Sets.newHashSet();
    List<DenialConstraint> gtDCs = DCLoader.load(headerPath, groundTruthDCsPath);
    NTreeSearch gtTree = new NTreeSearch();
    for (DenialConstraint gtDC : gtDCs) {
      gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    }
    for (DenialConstraint dc : testDCs) {
      if (dc.isImpliedBy(gtTree)) {
        // 是真DC
        trueDCs.add(dc);
      }
    }
    log.debug("TrueDCs: {}", trueDCs.size());
    for (DenialConstraint dc : trueDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    // 计算冲突个数
    Set<DCViolation> vios = new HydraDetector(dirtyDataPath, universalDCsPath, headerPath).detect()
        .getViosSet();
    Map<DenialConstraint, Set<DCViolation>> dcViosMap = Maps.newHashMap();
    for (DCViolation vio : vios) {
      List<DenialConstraint> dcList = vio.getDenialConstraintsNoData();
      for (DenialConstraint dc : dcList) {
        if (dcViosMap.containsKey(dc)) {
          Set<DCViolation> viosOfDC = dcViosMap.get(dc);
          viosOfDC.add(vio);
        } else {
          dcViosMap.put(dc, Sets.newHashSet(vio));
        }
      }
    }
    ArrayList<Entry<DenialConstraint, Set<DCViolation>>> sortedEntries = new ArrayList<>(
        dcViosMap.entrySet());
    log.debug("Test DCs: {}", sortedEntries.size());
    // 读取相关性矩阵
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(correlationByUserPath);
    // 计算综合分数 排序 打印
//    int size = testDCs.size();
//    for (int j = 1; j <= 20; j++) {
//      int limit = Math.min(size, j * 5);
//      log.debug("====== dcs = {} ======", limit);
//
//      for (int i = 0; i <= 10; i++) {
//        double succinctFactor = i * 0.1;
//        log.debug("------ succinctFactor = {} ------", succinctFactor);
//        printSortResult(testDCs, columnsCorrScoreMap, minLenOfDC, succinctFactor, sortedEntries, limit,
//            trueDCs);
//      }
//    }
    printSortResult(testDCs, columnsCorrScoreMap, minLenOfDC, 0.0, sortedEntries, 20,
        trueDCs);

  }

  private static void printSortResult(List<DenialConstraint> testDCs,
      Map<String, Double> columnsCorrScoreMap, int minLenOfDC, double succinctFactor,
      ArrayList<Entry<DenialConstraint, Set<DCViolation>>> sortedEntries, int limit,
      Set<DenialConstraint> trueDCs) {
    Map<DenialConstraint, Double> dcScoreUniformMap = getDCScoreUniformMap(
        testDCs, columnsCorrScoreMap, minLenOfDC, succinctFactor);
    // 排序
    sortDCsByScore(sortedEntries, dcScoreUniformMap);
    int totalDCNum = 0;
    int trueDCNum = 0;

    for (Entry<DenialConstraint, Set<DCViolation>> entry : sortedEntries) {
      totalDCNum++;
      if (totalDCNum > limit) {
        break;
      }
      DenialConstraint dc = entry.getKey();
      Set<DCViolation> violations = entry.getValue();
      boolean isTrueDC = trueDCs.contains(dc);
      if (isTrueDC) {
        trueDCNum++;
      }
      log.debug("{},(len={},score={},vios={})({})", DCFormatUtil.convertDC2String(dc),
          dc.getPredicateCount(),
          dcScoreUniformMap.get(dc),
          violations.size(),
          isTrueDC);
    }
    log.debug("TrueDC percent = {}, limit = {}, succinctFactor = {}",
        (double) trueDCNum / limit,
        limit, succinctFactor);
  }

  // Dynamic g1 strategy
  @Test
  public void testDynamicG1() {
    // TODO:如果待发现规则长，g1有生成太通用规则的风险，如果待发现规则短，g1有生成太特殊规则的风险
  }

  /**
   * 测试修复脏数据
   */
  @Test
  public void testRepairingDirty()
      throws IOException, InputGenerationException, InputIterationException {
    // 修复前检测
    List<DenialConstraint> dcsWithoutData = DCLoader.load(headerPath, universalDCsPath);
    HydraDetector detector = new HydraDetector(dirtyDataPath, new HashSet<>(dcsWithoutData));
    detectUsingHydraDetector(detector);

    // 修复脏数据指定的行（例如所有有错误的行）
    String dataPath = dirtyDataPath;
    File dataFile = new File(dataPath);
    List<TChange> changes = loadChanges(changesPath);
    Map<Integer, Map<Integer, String>> lineChangesMap = genLineChangesMap(dataPath, changes);
    Set<Integer> errorLinesOfChanges = getErrorLinesContainingChanges(changes);
//    Set<Integer> errorLinesOfChanges = Sets.newHashSet(6467);

    // 修复
    List<List<String>> repairedLinesWithHeader = getRepairedLinesWithHeader(errorLinesOfChanges,
        lineChangesMap, dataFile);

    // 保存
    log.debug("Write to file: {}", dataPath);
    FileUtil.writeListLinesToFile(repairedLinesWithHeader, dataFile);

    // 修复后检测
    detectUsingHydraDetector(detector);
  }

  /**
   * 测试将规则转换成字符串时，谓词顺序永远保持一致
   */
  @Test
  public void testDC2String() {
    List<DenialConstraint> dcs = DCLoader.load(headerPath, universalDCsPath);
    for (DenialConstraint dc : dcs) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
  }

  private static void sortDCsByScore(
      ArrayList<Entry<DenialConstraint, Set<DCViolation>>> sortedEntries,
      Map<DenialConstraint, Double> dcScoreUniformMap) {
    // 根据简洁性+相关性和冲突个数排序
    sortedEntries.sort(
        Comparator
            .comparingDouble(
                (Entry<DenialConstraint, Set<DCViolation>> entry) -> -dcScoreUniformMap.get(
                    entry.getKey()))
//            .reversed()  // 综合打分（简洁性+相关性）高的在前
            .thenComparingInt(
                (Entry<DenialConstraint, Set<DCViolation>> entry) -> -entry.getValue().size())
//            .reversed()  // 综合打分相同的情况下，冲突数量多的在前
    );
  }

  private static <T> double getErrorPercent(
      Set<Integer> lines, Set<Integer> errorLines, int limit) {
    int errorFound = 0;
    int i = 0;
    for (Integer line : lines) {
      i++;
      if (i > limit) {
        break;
      }
      if (errorLines.contains(line)) {
        errorFound++;
      }
    }
    log.debug("Error lines {} in {} limit {}", errorFound, errorLines.size(), limit);
    double per = (double) errorFound / limit;
    return per;
  }

  private static <T> double getErrorPercent(
      List<Entry<Integer, Set<T>>> entries, Set<Integer> errorLines, int limit) {
    int errorFound = 0;
    int i = 0;
    for (Entry<Integer, Set<T>> entry : entries) {
      i++;
      if (i > limit) {
        break;
      }
      Integer line = entry.getKey();
      if (errorLines.contains(line)) {
        errorFound++;
      }
    }
    log.debug("Error lines {} in {} limit {}", errorFound, errorLines.size(), limit);
    double per = (double) errorFound / limit;
    return per;
  }

  private static void detectUsingHydraDetector(HydraDetector detector) {
    DCViolationSet violationSet = detector.detect();
    log.info("ViolationSet={}", violationSet.size());

    printDCViolationsMap(violationSet);

    List<TChange> changes = loadChanges(changesPath);
    Set<TCell> cellsOfChanges = getCellsOfChanges(changes);

    // 每个DC只打印一个冲突样例
    Set<DenialConstraint> visitedDCs = Sets.newHashSet();
    Input di = generateNewCopy(dirtyDataPath);
    for (DCViolation v : violationSet.getViosSet()) {
//      List<DenialConstraint> dcs = v.getConstraints();
      List<DenialConstraint> dcs = v.getDenialConstraintsNoData();
      if (dcs.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcs.get(0);
      String dcStr = DCFormatUtil.convertDC2String(dc);
      if (!visitedDCs.contains(dc)) {
        // 打印一个冲突中所有的Cell
        LinePair linePair = v.getLinePair();
        visitedDCs.add(dc);
        log.debug("{}", dcStr);
        log.debug("LinePair = {}", linePair);
//        Set<TCell> cells = getCellsOfViolation(dc, linePair);
        Set<TCell> cells = getCellsOfViolation(di, dc, linePair);
        for (TCell cell : cells) {
          boolean contains = cellsOfChanges.contains(cell);
          log.debug("cell={}, contains={}", cell, contains);
        }
      }
    }
  }

}
