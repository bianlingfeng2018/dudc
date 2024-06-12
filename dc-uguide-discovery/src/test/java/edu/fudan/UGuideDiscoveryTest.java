package edu.fudan;

import static edu.fudan.algorithms.BasicDCGenerator.getSortedDCs;
import static edu.fudan.algorithms.BasicDCGenerator.persistDCFinderDCs;
import static edu.fudan.conf.DefaultConf.predictArgs;
import static edu.fudan.conf.DefaultConf.sharedArgs;
import static edu.fudan.conf.DefaultConf.trainArgs;
import static edu.fudan.utils.DCUtil.genLineChangesMap;
import static edu.fudan.utils.DCUtil.getCellsOfChanges;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;
import static edu.fudan.utils.DCUtil.getCellsOfViolations;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.loadDirtyDataExcludedLines;
import static edu.fudan.utils.DCUtil.printDCVioMap;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.getRepairedLinesWithHeader;
import static edu.fudan.utils.GlobalConf.baseDir;
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
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.PythonCaller;
import edu.fudan.algorithms.RLDCGenerator;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscoveryTest {

  private int dsIndex = 0;
  private String headerPath;
  private String cleanDataPath;
  private String dirtyDataPath;
  private String changesPath;
  private String excludedLinesPath;
  private String sampledDataPath;
  private String fullDCsPath;
  private String dcsPathForDCMiner;
  private String evidencesPath;
  private String topKDCsPath;
  private String groundTruthDCsPath;
  private String candidateDCsPath;
  private String candidateTrueDCsPath;
  private String excludedDCsPath;
  private String csvResultPath;
  private String correlationByUserPath;

  @Before
  public void setUp() throws Exception {
    UGDParams params = UGDRunner.buildParams(dsIndex);
    headerPath = params.headerPath;
    cleanDataPath = params.cleanDataPath;
    dirtyDataPath = params.dirtyDataPath;
    changesPath = params.changesPath;
    excludedLinesPath = params.excludedLinesPath;
    sampledDataPath = params.sampledDataPath;
    fullDCsPath = params.fullDCsPath;
    dcsPathForDCMiner = params.dcsPathForDCMiner;
    evidencesPath = params.evidencesPath;
    topKDCsPath = params.topKDCsPath;
    groundTruthDCsPath = params.groundTruthDCsPath;
    candidateDCsPath = params.candidateDCsPath;
    candidateTrueDCsPath = params.candidateTrueDCsPath;
    excludedDCsPath = params.excludedDCsPath;
    csvResultPath = params.csvResultPath;
    correlationByUserPath = params.correlationByUserPath;
  }

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath, changesPath, dirtyDataPath,
        excludedLinesPath, sampledDataPath, fullDCsPath, dcsPathForDCMiner, evidencesPath,
        topKDCsPath, groundTruthDCsPath, candidateDCsPath, candidateTrueDCsPath, excludedDCsPath,
        headerPath, csvResultPath, correlationByUserPath);
    ud.guidedDiscovery();
  }

//  /**
//   * Discover dc using fdcd(2023)
//   */
//  @Test
//  public void testDiscoveryDCsUsingFDCD() {
//    DiscoveryEntry.doDiscovery(cleanDataPath, fullDCsPath);
//  }

  /**
   * Discover dc using dcFinder(2019)
   */
  @Test
  public void testDiscoveryDCsUsingDCFinderNoEvidence() {
    // 1.当evidenceFile为null，则生成规则集合
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(cleanDataPath, 0.0, null);
    log.info("DCs: {}", dcs.size());
  }

  /**
   * Discover dc using dcFinder(2019)
   */
  @Test
  public void testDiscoveryDCsUsingDCFinder() throws IOException {
    // 2.当evidenceFile不为null，则生成证据集（作为DCMiner训练模型的输入）
    log.info("cleanDataPath={}", cleanDataPath);
    log.info("evidencesPathForFCDC={}", evidencesPath);
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(cleanDataPath, 0.0,
        evidencesPath);
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = getSortedDCs(
        dcs);
    log.info("dcList={}", dcList.size());
    log.info("Persist to universalDCsPath={}", fullDCsPath);
    persistDCFinderDCs(dcList, fullDCsPath);
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
    log.info("evidencesPathForFCDC={}", evidencesPath);
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(dirtyDataPath, 0.001,
        evidencesPath);
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = getSortedDCs(
        dcs);
    log.info("dcList={}", dcList.size());
    log.info("Persist to universalDCsPath={}", fullDCsPath);
    persistDCFinderDCs(dcList, fullDCsPath);
  }

  /**
   * Generate top-k dcs from file which contains all dcs
   */
  @Test
  public void testGenTopKDCs() {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(5, fullDCsPath, headerPath, null);
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

    DenialConstraint dc1 = new DenialConstraint(new Predicate(Operator.GREATER, o1, o2),
        new Predicate(Operator.LESS, o1, o2));
    DenialConstraint dc2 = new DenialConstraint(new Predicate(Operator.GREATER, o1, o2));
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
    HydraDetector detector = new HydraDetector(cleanDataPath, fullDCsPath, headerPath);
    DCViolationSet violationSet = detector.detect();
    log.info("violationSet={}", violationSet.size());

    printDCVioMap(violationSet);
  }

  /**
   * Test violations size compare
   */
  @Test
  public void testDCsViolationsSizeCompare() {
    // TODO: Violation size: tureDcs,candiDCs,gtDCs并无大小关系，因为冲突之间可能有元组对的重合，一对元组可能涉及多个冲突
    DCViolationSet vioSet1 = new HydraDetector(dirtyDataPath, candidateDCsPath,
        headerPath).detect();
    DCViolationSet vioSet2 = new HydraDetector(dirtyDataPath, candidateTrueDCsPath,
        headerPath).detect();
    DCViolationSet vioSet3 = new HydraDetector(dirtyDataPath, groundTruthDCsPath,
        headerPath).detect();
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
    printDCVioMap(violationSet);
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
    DCViolationSet vioSetOfGroundTruthDCs = new HydraDetector(dirtyDataPath, groundTruthDCsPath,
        headerPath).detect();
    DCViolationSet vioSetOfTrueDCs = new HydraDetector(dirtyDataPath, candidateTrueDCsPath,
        headerPath).detect();
    Input di = generateNewCopy(dirtyDataPath);

    // 转换成Cell
    Set<TCell> cellsGroundTruth = getCellsOfViolations(vioSetOfGroundTruthDCs.getViosSet(), di);
    Set<TCell> cellsTrue = getCellsOfViolations(vioSetOfTrueDCs.getViosSet(), di);
    log.info("CellsGroundTruth: {}, {}", cellsGroundTruth.size(),
        cellsGroundTruth.stream().findAny());
    log.info("CellsTrue: {}, {}", cellsTrue.size(), cellsTrue.stream().findAny());

    // 所有错误都能被发现
    assertTrue(cellsGroundTruth.containsAll(cellsOfChanges));
    assertTrue(cellsTrue.containsAll(cellsOfChanges));

    // 打印Cell样例
    cellsGroundTruth.stream().findAny().ifPresent(tCell -> log.info("Example1: {}", tCell));
    cellsTrue.stream().findAny().ifPresent(tCell -> log.info("Example2: {}", tCell));
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
        .filter(i -> excludedLines.contains(i)).collect(Collectors.toList());
    List<Integer> excludesInChangedLines = excludedLines.stream()
        .filter(i -> changedLines.contains(i)).collect(Collectors.toList());
    log.info("changesInExcludedLines={}, excludesInChangedLines={}", changesInExcludedLines.size(),
        excludesInChangedLines.size());
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
    List<DenialConstraint> excludeDCs = DCLoader.load(headerPath, excludedDCsPath, new HashSet<>());
    log.debug("Visited DCs size={}", excludeDCs.size());
    RLDCGenerator generator = new RLDCGenerator(sampledDataPath, evidencesPath, dcsPathForDCMiner,
        headerPath);
    generator.setExcludeDCs(new HashSet<>(excludeDCs));
//    generator.setErrorThreshold(0.001);
    Set<DenialConstraint> dcs = generator.generateDCs();
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
    String DCsPath =
        baseDir + File.separator + "result_rules\\dcs_hospital_ground_inject_error_20.out";
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

}
