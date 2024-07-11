package edu.fudan;

import static edu.fudan.conf.DefaultConf.canBreakEarly;
import static edu.fudan.conf.DefaultConf.delta;
import static edu.fudan.conf.DefaultConf.excludeLinePercent;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.repairErrors;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.conf.GlobalConf.baseDir;
import static edu.fudan.conf.GlobalConf.dsNames;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;
import static edu.fudan.utils.DCUtil.genLineChangesMap;
import static edu.fudan.utils.DCUtil.getCellsOfChanges;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;
import static edu.fudan.utils.DCUtil.getCellsOfViolations;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.printDCVioMap;
import static edu.fudan.utils.EvaluateUtil.eval;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.getRepairedLinesWithHeader;
import static edu.fudan.utils.FileUtil.writeStringLinesToFile;
import static edu.fudan.utils.G1Util.calculateG1Ranges;
import static org.junit.Assert.assertTrue;

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
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.CellQStrategy;
import edu.fudan.algorithms.uguide.CellQuestionResult;
import edu.fudan.algorithms.uguide.CellQuestionV2;
import edu.fudan.algorithms.uguide.DCQStrategy;
import edu.fudan.algorithms.uguide.DCsQuestion;
import edu.fudan.algorithms.uguide.DCsQuestionResult;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.algorithms.uguide.TupleQStrategy;
import edu.fudan.algorithms.uguide.TupleQuestion;
import edu.fudan.algorithms.uguide.TupleQuestionResult;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.FileUtil;
import edu.fudan.utils.G1RangeResult;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
import picocli.CommandLine;

@Slf4j
public class UGDTest {

  /**
   * Parameters used in User-guided-DC-Detection process.
   */
  UGDParams params;

  @Before
  public void setUp() throws Exception {
    int dsIndex = Arrays.asList(dsNames).indexOf("flights");
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
    double g1 = 0.0;
    int topK = Integer.MAX_VALUE;  // 5 / Integer.MAX_VALUE means get all DCs

    log.debug("Params = {}", params.toString());
    BasicDCGenerator generator = new BasicDCGenerator(params.cleanDataPath, params.fullDCsPath,
        params.topKDCsPath, params.headerPath, new HashSet<>(), g1, topK);

    Set<DenialConstraint> dcs = generator.generateDCs();
    log.info("TopK dcs size={}", dcs.size());
  }

  /**
   * Test detect violations.
   */
  @Test
  public void testDetectViolations() {
    DCViolationSet vios = new HydraDetector(params.cleanDataPath, params.groundTruthDCsPath,
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
    double succinctFactor = 1.0;
    int budget = 10;
    DCQStrategy strategy = DCQStrategy.SUC_COR;
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
//    String dsPath = params.dirtyDataPath;
//    String changesPath = params.changesPath;
//    String headerPath = params.headerPath;
//    String dcsPath = params.topKDCsPath;
    String dsPath = "../data/ds_dirty.txt";
    String changesPath = "../data/changes.txt";
    String headerPath = "../data/header.txt";
    String dcsPath = "../data/dc_gt.txt";

    File dataF = new File(dsPath);
    List<TChange> changes = loadChanges(changesPath);

    // Detect violations before repairing lines.
    log.debug("Detect before repairing.");
    detectAndPrintViosWithCells(headerPath, dcsPath, dsPath, changes);

    // Repair specified lines(e.g. all the lines containing errors.)
    Map<Integer, Map<Integer, String>> lineChangesMap = genLineChangesMap(dsPath, changes);
    log.debug("Building index, lineChangesMap={}", lineChangesMap.size());

    // Specify some lines.
    // Sets.newHashSet(6467)
    Set<Integer> errorLinesOfChanges = getErrorLinesContainingChanges(changes);
    log.debug("ErrorLinesOfChanges={}", errorLinesOfChanges.size());

    // Repair lines.
    List<List<String>> repairedLinesWithHeader = getRepairedLinesWithHeader(errorLinesOfChanges,
        lineChangesMap, dataF);
    log.debug("RepairedLinesWithHeader={}", repairedLinesWithHeader.size());

    // Persist.
    log.debug("Write to file: {}", dsPath);
    FileUtil.writeListLinesToFile(repairedLinesWithHeader, dataF);

    // Detect violations after repairing lines.
    log.debug("Detect after repairing.");
    detectAndPrintViosWithCells(headerPath, dcsPath, dsPath, changes);
  }

  /**
   * Detect and print violations with cells contained in it.
   *
   * @param headerPath Header path
   * @param dcsPath    DCs path
   * @param dsPath     Dataset path
   * @param changes    Changes loaded from file
   */
  private void detectAndPrintViosWithCells(String headerPath, String dcsPath, String dsPath,
      List<TChange> changes) {
    List<DenialConstraint> dcs = DCLoader.load(headerPath, dcsPath);
    DCViolationSet vios = new HydraDetector(dsPath, new HashSet<>(dcs)).detect();

    printDCVioMap(vios);

    Set<TCell> cellsOfChanges = getCellsOfChanges(changes);

    // 每个DC只打印一个冲突样例
    log.debug("Print example violation with cells contained in it.");
    Set<DenialConstraint> visitedDCs = Sets.newHashSet();
    Input di = generateNewCopy(dsPath);
    for (DCViolation v : vios.getViosSet()) {
      DenialConstraint dc = v.getDenialConstraintsNoData().get(0);
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

  /**
   * Test dynamic g1. Vary g1 to compare effectiveness of DC discovery.
   */
  @Test
  public void testDynamicG1() {
    // TODO: g1的设定到底取决于什么？什么情况下需要更小的g1，什么情况下需要更大的g1？
    //  发现的DC在脏数据集上产生的真冲突尽可能多（反之DC太特化，g1偏小），假冲突尽可能少（反之DC太泛化，g1偏大），说明此时g1比较合理。
    //  不同的规则合理的g1范围不同；不同的错误率合理的g1范围不同；不同数据分布合理的g1范围不同；
    //  规则不包含的属性的错误不影响这个规则的发现；
    //  1.如何估计g1？
    //  估计错误率->计算冲突元组对->计算临界值->估计g1（估计原则为让g1比临界值大一点点，即刚好容纳所有冲突，再小可能产生特化的规则，再大可能产生泛化的规则）
    //  例如：n条元组，估计错误x条，冲突元组对组合数a=x!/(x-2)!，所有元组对组合数为b=n!/(n-2)!，占比为r=a/b；g1≈r+d（d为一个较小的值）
    //  2.如何计算理论上合理的g1范围？
    //  当规则属性都没有错误时，g1=0
    //  当规则检测无冲突时，g1=0
    //  当前规则的g1 <= 合理的g1 < 减一个谓词的所有泛化规则的g1的最小值
    String dsPath = "../data/ds_dirty.txt";
    String headerPath = "../data/header.txt";
    String gtDCsPath = "../data/dc_gt.txt";
    String fullDCsPath = "../data/dc_full.txt";
    String topKDCsPath = "../data/dc_top_k.txt";
    String evidencePath = "../data/evidence.txt";
//    String dsPath = params.dirtyDataPath;
//    String headerPath = params.headerPath;
//    String gtDCsPath = params.groundTruthDCsPath;
//    String fullDCsPath = params.fullDCsPath;
//    String topKDCsPath = params.topKDCsPath;
//    String evidencePath = params.evidencesPath;
    double g1 = 0.188;
    log.debug("Discover dcs using g1 = {}.", g1);

    List<DenialConstraint> gtDCs = DCLoader.load(headerPath, gtDCsPath);
    log.debug("Load dcs = {}", gtDCs.size());

    DCViolationSet vios = new HydraDetector(dsPath, new HashSet<>(gtDCs)).detect();
    log.debug("Detect violations = {}", vios.size());

    // not(t1.A=t2.A^t1.B!=t2.B)->4
    printDCVioMap(vios);

    // TODO: 为什么证据集（体现多重性的）数量不等于元组对组合数量？
    //  好像是因为生成证据集时，没有把所有谓词都不等的情况算进去？
    // 90 * 0.011 = 0.99 --- 1 violations (max=38) × not(t1.Abbr!=t2.Abbr^t1.City=t2.City)
    // 90 * 0.012 = 1.08 --- 2 violations (max=38) √ not(t1.Abbr!=t2.Abbr^t1.City=t2.City)
    // 90 * 0.18 = 16.2 --- 17 violations (max=38) × not(t1.City=t2.City)
    // 90 * 0.19 = 17.1 --- 18 violations (max=38) √ not(t1.City=t2.City)
    // 90 * 0.81 = 72.9 --- 73 violations (max=38) ×
    // 90 * 0.82 = 73.8 --- 74 violations (max=38) ×
    // 90 * 0.41 = 36.9 --- 37 violations (max=38) √
    // 90 * 0.42 = 37.8 --- 38 violations (max=38) ×
    // 90 * 0.43 = 38.7 --- 39 violations (max=38) ×
    // 90 * 1.00 = 90.0 --- 90 violations (max=38) ×
    // 90 * 0.24 = 21.6 --- 22 violations (max=38) √ not(t1.Abbr!=t2.Abbr) ×
    // 90 * 0.17 = 15.3 --- 16 violations (max=38) √ not(t1.Abbr=t2.Abbr) √ 因为不会同时生成 not(t1.Abbr!=t2.Abbr) 和 not(t1.Abbr=t2.Abbr)，所以选产生冲突少（覆盖证据集多）的作为规则而生成，即后者
    BasicDCGenerator generator = new BasicDCGenerator(dsPath, fullDCsPath, topKDCsPath, headerPath,
        new HashSet<>(), g1, Integer.MAX_VALUE);

    generator.setEvidencePath(evidencePath);
    Set<DenialConstraint> genDCs = generator.generateDCs();
    log.info("GenDCs size = {}", genDCs.size());

    // 检查待发现DC是否已经被发现
    for (DenialConstraint dc : gtDCs) {
      log.debug("{} is discovered: {}", DCFormatUtil.convertDC2String(dc), genDCs.contains(dc));
    }
  }

  /**
   * Test calculate g1 range of dc.
   */
  @Test
  public void testG1Range() {
//    String dsPath = "../data/ds_dirty.txt";
//    String headerPath = "../data/header.txt";
//    String gtDCsPath = "../data/dc_gt.txt";
    String dsPath = params.dirtyDataPath;
    String headerPath = params.headerPath;
    String gtDCsPath = params.groundTruthDCsPath;
    String g1RangeResPath = params.g1RangeResPath;
    List<DenialConstraint> gtDCs = DCLoader.load(headerPath, gtDCsPath);
    List<G1RangeResult> result = calculateG1Ranges(headerPath, dsPath, new HashSet<>(gtDCs));
    log.debug("Result size = {}", result.size());

    List<String> resultLines = new ArrayList<>();
    for (G1RangeResult rr : result) {
      resultLines.add(rr.toString());
      log.debug("{}", rr);
    }
    writeStringLinesToFile(resultLines, new File(g1RangeResPath));
  }

  /**
   * Test user guided detection 1 round.
   *
   * @throws InputGenerationException
   * @throws InputIterationException
   * @throws IOException
   */
  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException {
    maxDiscoveryRound = 1;
    repairErrors = false;
    UGuideDiscovery ud = new UGuideDiscovery(params.cleanDataPath, params.changesPath,
        params.dirtyDataPath, params.dirtyDataUnrepairedPath, params.excludedLinesPath,
        params.sampledDataPath, params.fullDCsPath, params.dcsPathForDCMiner, params.evidencesPath,
        params.topKDCsPath, params.groundTruthDCsPath, params.candidateDCsPath,
        params.candidateTrueDCsPath, params.excludedDCsPath, params.headerPath,
        params.csvResultPath, params.correlationByUserPath);
    ud.guidedDiscovery();
  }

  /**
   * Test user guided detection.
   */
  @Test
  public void testUGuide() {
    String[] args = "-i 3 -r 50 -u REPAIR -s EFFICIENT -a HYDRA -c VIO_AND_CONF -t VIOLATIONS_PRIOR -d SUC_COR_VIOS -g DYNAMIC".split(
        " ");
    int exitCode = new CommandLine(new UGDRunner()).execute(args);
    log.debug("ExitCode = {}", exitCode);
  }

  /**
   * Test baseline detection.
   */
  @Test
  public void testBaseLine() {
//    String out = baseDir + "/baseline_stock.csv";
//    String out = baseDir + "/baseline_hospital.csv";
    String out = baseDir + "/baseline_flights.csv";
    // 发现全部规则
    double g1 = 16E-3;
    int dcBudget = maxDCQuestionBudget;
    int round = maxDiscoveryRound;
    BasicDCGenerator generator = new BasicDCGenerator(params.dirtyDataPath, params.fullDCsPath,
        params.topKDCsPath, params.headerPath, new HashSet<>(), g1, Integer.MAX_VALUE);
    generator.generateDCs();
    // 按照user guided detection的规则budget设置，从少到多取出规则，并评价这些规则的准确率
    List<DenialConstraint> gtDCs = DCLoader.load(params.headerPath, params.groundTruthDCsPath);
    List<DenialConstraint> dcList = DCLoader.load(params.headerPath, params.fullDCsPath);
    List<String> resultLines = new ArrayList<>();

    int lastK = 0;
    for (int i = 0; i < round; i++) {
      int k = (i + 1) * dcBudget;
      List<DenialConstraint> kDCs = dcList.subList(0, Math.min(k, dcList.size()));
      int currK = kDCs.size();
      if (currK == lastK) {
        log.debug("No more dcs, just break!!!");
        break;
      }
      lastK = currK;
      // 评价k个规则的P R F1
      log.debug("Evaluate gtDCs = {}, kDCs = {}", gtDCs.size(), kDCs.size());
      Double[] result = eval(new HashSet<>(gtDCs), new HashSet<>(kDCs),
          params.dirtyDataUnrepairedPath);
      log.debug("Precision={}, Recall={}, F-measure={}", result[0], result[1], result[2]);
      String line = result[0] + "," + result[1] + "," + result[2];
      resultLines.add(line);
    }
    writeStringLinesToFile(resultLines, new File(out));
  }


  /**
   * Test that all errors (i.e. changes) can be recognized by dcs. 例如用于判断真实数据集的手工规则是否合理。
   */
  @Test
  public void testAllErrorsFound() {
    // 对于BART注入错误，元组id(TupleOID)从1开始
    // 对于Hydra检测冲突，行号line(LinePair)从0开始
    List<TChange> changes = loadChanges(params.changesPath);
    Set<TCell> cellsOfChanges = getCellsOfChanges(changes);
    log.debug("CellsOfChanges = {}, example = {}", cellsOfChanges.size(),
        cellsOfChanges.stream().findAny());

    // 检测冲突
    DCViolationSet vios = new HydraDetector(params.dirtyDataPath, params.groundTruthDCsPath,
        params.headerPath).detect();
    Input di = generateNewCopy(params.dirtyDataPath);

    // 转换成Cell
    Set<TCell> cellsOfVios = getCellsOfViolations(vios.getViosSet(), di);
    log.info("CellsOfVios = {}, example = {}", cellsOfVios.size(), cellsOfVios.stream().findAny());

    // 所有错误都能被发现
    assertTrue(cellsOfVios.containsAll(cellsOfChanges));
  }

}
