package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.addCounterExampleS;
import static edu.fudan.conf.DefaultConf.canBreakEarly;
import static edu.fudan.conf.DefaultConf.cellQStrategy;
import static edu.fudan.conf.DefaultConf.dCsQStrategy;
import static edu.fudan.conf.DefaultConf.dcGeneratorConf;
import static edu.fudan.conf.DefaultConf.delta;
import static edu.fudan.conf.DefaultConf.excludeLinePercent;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
import static edu.fudan.conf.DefaultConf.minLenOfDC;
import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.questionsConf;
import static edu.fudan.conf.DefaultConf.randomClusterS;
import static edu.fudan.conf.DefaultConf.succinctFactor;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.conf.DefaultConf.tupleQStrategy;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;
import static edu.fudan.utils.FileUtil.generateNewCopy;
import static edu.fudan.utils.FileUtil.getRepairedLinesWithHeader;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CellQuestionResult;
import edu.fudan.algorithms.uguide.CellQuestionV2;
import edu.fudan.algorithms.uguide.CleanDS;
import edu.fudan.algorithms.uguide.DCsQuestion;
import edu.fudan.algorithms.uguide.DCsQuestionResult;
import edu.fudan.algorithms.uguide.DirtyDS;
import edu.fudan.algorithms.uguide.Evaluation;
import edu.fudan.algorithms.uguide.Evaluation.EvalResult;
import edu.fudan.algorithms.uguide.SampleDS;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TupleQuestion;
import edu.fudan.algorithms.uguide.TupleQuestionResult;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.CSVWriter;
import edu.fudan.utils.FileUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscovery {

  // 干净数据
  private final CleanDS cleanDS;
  // 脏数据
  private final DirtyDS dirtyDS;
  // 采样数据
  private final SampleDS sampleDS;
  // 候选规则
  private final CandidateDCs candidateDCs;
  // 评价
  private final Evaluation evaluation;
  /**
   * When dcs size is less than k, end loop.
   */
  private boolean dcsLessThanK = false;
  /**
   * The correlation score map.
   */
  private final Map<String, Double> columnsCorrScoreMap;

  public UGuideDiscovery(String cleanDataPath, String changesPath, String dirtyDataPath,
      String excludedLinesPath, String sampledDataPath, String dcsPathForFCDC,
      String dcsPathForDCMiner, String evidencesPathForFCDC, String topKDCsPath,
      String groundTruthDCsPath, String candidateDCsPath, String trueDCsPath, String visitedDCsPath,
      String headerPath, String csvResultPath, String correlationByUserPath) throws IOException {
    this.cleanDS = new CleanDS(cleanDataPath, headerPath, changesPath);
    this.dirtyDS = new DirtyDS(dirtyDataPath, excludedLinesPath, headerPath);
    this.sampleDS = new SampleDS(sampledDataPath, headerPath);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, dcsPathForDCMiner, evidencesPathForFCDC,
        topKDCsPath);
    this.evaluation = new Evaluation(cleanDS, dirtyDS, groundTruthDCsPath, candidateDCsPath,
        trueDCsPath, visitedDCsPath, csvResultPath);
    this.columnsCorrScoreMap = readColumnCorrScoreMap(correlationByUserPath);

  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException {
    // 设定groundTruth
    log.info("====== 1.Setup ======");
    evaluation.setUp();
    // 模拟多轮采样+用户交互，以达到发现所有真冲突的目的
    int round = 0;
    while (canProcess(round)) {
      round++;
      log.info("------ Round {} -------", round);
      // 修复
      simRepairing();
      // 采样
      sample();
      // 发现规则
      discoveryDCs();
      if (this.dcsLessThanK) {
        log.debug("Break early");
        break;
      }
      // 检测冲突
      detect();
      // 多轮提问
      if (questionsConf[0] == 1) {
        askCellQuestion();
      }
      if (questionsConf[1] == 1) {
        askTupleQuestion();
      }
      if (questionsConf[2] == 1) {
        askDCQuestion();
      }
      // 评价真冲突/假冲突
      evaluate();
      // 输出结果
      persistResult();
    }
    // 打印最终的DC和检测得到的Violation（仅仅用于测试）
    printFinalDCsAndViolations();
    log.info("Finish");
  }

  private void printFinalDCsAndViolations() {
    log.debug("PrintFinalDCsAndViolations:");
    Set<DenialConstraint> trueDCs = evaluation.getTrueDCs();
    DCViolationSet violations = new HydraDetector(this.dirtyDS.getDataPath(), trueDCs).detect();
    log.debug("TrueDCs={}, violations={}", trueDCs.size(), violations.size());
  }

  private boolean canProcess(int round) {
    if (round >= maxDiscoveryRound) {
      log.info("Reach max round");
      return false;
    }
    if (evaluation.allTrueViolationsFound()) {
      log.info("All changes covered!!!");
//      return false;
    }
    return true;
  }

  private void persistResult() throws IOException {
    log.info("====== 7.Persist result ======");
    String trueDCsPath = evaluation.getTrueDCsPath();
    String candidateDCsPath = evaluation.getCandidateDCsPath();
    String visitedDCsPath = evaluation.getVisitedDCsPath();
    String excludedLinesPath = dirtyDS.getExcludedLinesPath();
    log.info("TrueDCsPath={}", trueDCsPath);
    log.info("CandidateDCsPath={}", candidateDCsPath);
    log.info("VisitedDCsPath={}", visitedDCsPath);
    log.info("ExcludedLinesPath={}", excludedLinesPath);
    persistDCs(evaluation.getTrueDCs(), trueDCsPath);
    persistDCs(evaluation.getCandidateDCs(), candidateDCsPath);
    persistDCs(evaluation.getVisitedDCs(), visitedDCsPath);
    persistExcludedLines(dirtyDS.getDataPath(), evaluation.getExcludedLines(), excludedLinesPath);
    // 保存结果
    CSVWriter.writeToFile(evaluation.getCsvResultPath(), evaluation.getEvalResults());
  }

  private void askDCQuestion() {
    log.info("===== 5.3 Ask DC question ======");
    // 给用户推荐DC进行判断
    NTreeSearch gtTree = evaluation.getGtTree();
    Set<DenialConstraint> currDCs = evaluation.getCurrDCs();
    Set<DCViolation> currVios = evaluation.getCurrVios();

    DCsQuestion selector = new DCsQuestion(gtTree, currDCs, currVios, columnsCorrScoreMap,
        minLenOfDC, succinctFactor, dCsQStrategy, maxDCQuestionBudget);

    DCsQuestionResult result = selector.simulate();

    int budgetUsed = result.getBudgetUsed();
    log.info("DCQuestions/MaxDCQuestionBudget/CurrDCsSize={}/{}/{}", budgetUsed,
        maxDCQuestionBudget, evaluation.getCurrDCs().size());
    evaluation.addDCBudget(budgetUsed);
    Set<DenialConstraint> falseDCs = result.getFalseDCs();
//    // 排除真DC在脏数据上的冲突元组
//    Set<Integer> excludedLinesInDCsQ = Sets.newHashSet();
//    DCViolationSet vios = new HydraDetector(evaluation.getDirtyDS().getDataPath(), trueDCs).detect();
//    for (DCViolation vio : vios.getViosSet()) {
//      LinePair linePair = vio.getLinePair();
//      int line1 = linePair.getLine1();
//      int line2 = linePair.getLine2();
//      excludedLinesInDCsQ.add(line1);
//      excludedLinesInDCsQ.add(line2);
//    }
//    // TODO: 优选排除的元组，需要设定一定比例，否则可能排除数据集的大部分元组
//    int sizeBefore = excludedLinesInDCsQ.size();
//    int sizeAfter = (int) (sizeBefore * 0.1);
//    Set<Integer> excludedLinesInDCsQRandom = new HashSet<>(
//        getRandomElements(excludedLinesInDCsQ, sizeAfter));
//    log.debug("ExcludedLinesInDCsQRandom before {}, after {}", sizeBefore, sizeAfter);
//    evaluation.setExcludedLinesInDCsQ(excludedLinesInDCsQRandom);
    // TODO: DCsQ排除暂时仅排除假DC
    evaluation.update(null, falseDCs, null, null, null);
  }

  private void askTupleQuestion() {
    log.info("===== 5.2 Ask TUPLE question =====");
    // 在脏数据中，推荐一些元组让用户判断
    Set<Integer> errorLines = evaluation.getErrorLinesOfChanges();
    Set<DCViolation> currVios = evaluation.getCurrVios();
    TupleQuestion selector = new TupleQuestion(errorLines, currVios, tupleQStrategy,
        maxTupleQuestionBudget);
    TupleQuestionResult result = selector.simulate();

    // 排除错误行
    Set<Integer> excludedLines = new HashSet<>(result.getExcludedTuples());
    evaluation.setExcludedLinesInTupleQ(excludedLines);
    int budgetUsed = result.getBudgetUsed();
    log.info("TupleQuestions/MaxTupleQuestionBudget/SampleSize={}/{}/{}", budgetUsed,
        maxTupleQuestionBudget, evaluation.getSampleResultSize());
    evaluation.addTupleBudget(budgetUsed);
    log.info("FalseTuples(excludedLinesInDirty): {}", excludedLines.size());
    log.debug("TupleQuestion exclude : {}", excludedLines);
    evaluation.update(null, null, null, null, excludedLines);
  }

  private void askCellQuestion() {
    log.info("====== 5.1 Ask CELL question ======");
    // 选择Violation作为问题，判断是否是真冲突(已弃用)
//    CellQuestion selector = new CellQuestionV1(evaluation);
    // 选择Cell作为问题，判断是否是干净Cell
    Input input = generateNewCopy(evaluation.getDirtyDS().getDataPath());
    Set<TCell> cellsOfChanges = evaluation.getCellsOfChanges();
    Set<DenialConstraint> currDCs = evaluation.getCurrDCs();
    Set<DCViolation> currVios = evaluation.getCurrVios();
    CellQuestionV2 selector = new CellQuestionV2(input, cellsOfChanges, currDCs, currVios,
        maxCellQuestionBudget, delta, canBreakEarly, cellQStrategy, excludeLinePercent);

    CellQuestionResult result = selector.simulate();
    Set<DenialConstraint> falseDCs = result.getFalseDCs();
    int budgetUsed = result.getBudgetUsed();
    log.info("CellQuestions/MaxCellQuestionBudget/CurrViosSize={}/{}/{}", budgetUsed,
        maxCellQuestionBudget, currVios.size());
    evaluation.addCellBudget(budgetUsed);
    // TODO: 这里效率待优化
    // TODO: CellQ目前仅排除假规则
    evaluation.update(null, falseDCs, null, null, null);
  }

  private void evaluate() {
    log.info("====== 6.Evaluate the true violations and false violations ======");
    EvalResult result = evaluation.evaluate();
    // 打印结果信息
    // 最终的candiDCs及其对应的冲突个数信息
    Map<DenialConstraint, Integer> candiDCViosMap = result.getCandiDCViosMap();
    log.info("CandiDCViosMap: {}", candiDCViosMap.keySet().size());
    for (DenialConstraint dc : candiDCViosMap.keySet()) {
      log.debug("{}: {}", DCFormatUtil.convertDC2String(dc), candiDCViosMap.get(dc));
    }
    // 排除的元组信息
    // TODO: 当本轮发现的规则没有冲突时（可能是因为规则过拟合或者规则与注入错误无关），
    //  ExcludedLines(CellQ)为零，这样数据无法继续变得更干净，下一轮可能还是相同的结果，这样效率较低。
    log.info("ErrorLinesInSampleAndInExcluded/ErrorLinesInSample = {}/{}",
        result.getErrorLinesInSampleAndExcluded(), result.getErrorLinesInSample());
    log.info("ExcludedLinesOfCellQ/OfTupleQ/OfDCsQ/ExcludedLines = {}/{}/{}/{}",
        result.getExcludedLinesOfCellQ(), result.getExcludedLinesOfTupleQ(),
        result.getExcludedLinesOfDCsQ(), result.getExcludedLines());
    log.info("TrueVios/CandiVios/GTVios = {}/{}/{}", result.getViolationsTrue(),
        result.getViolationsCandidate(), result.getViolationsGroundTruth());
    log.info("TrueDCs/CandiDCs/GTDCs = {}/{}/{}", result.getDCsTrue(), result.getDCsCandidate(),
        result.getDCsGroundTruth());
    log.info("CellsOfTrueViosAndChanges/CellsOfTrueVios/CellsOfChanges = {}/{}/{}",
        result.getCellsOfTrueViosAndChanges(), result.getCellsOfTrueVios(),
        result.getCellsOfChanges());
    log.info("CellQuestion/TupleQuestion/DCQuestion = {}/{}/{}", result.getQuestionsCell(),
        result.getQuestionsTuple(), result.getQuestionsDC());
  }

  private void detect() {
    log.info("====== 4.Detect violations on dirty data ======");
    DCViolationSet vios = new HydraDetector(evaluation.getDirtyDS().getDataPath(),
        evaluation.getCurrDCs()).detect();
    Set<DCViolation> violations = vios.getViosSet();
    log.info("Violations = {}", violations.size());

    evaluation.update(null, null, violations, null, null);
  }

  private void discoveryDCs() {
    log.info("====== 3.Discovery DCs from sample ======");
    log.info("DCGeneratorConf: {}", dcGeneratorConf);
    DCGenerator generator = getGenerator(dcGeneratorConf, topK);
    Set<DenialConstraint> dcs = generator.generateDCs();

    if (dcs.size() != topK) {
//      throw new RuntimeException(String.format("Discovery DCs size is not %s: %s",
//          topK, dcs.size()));
      // 提前结束
      this.dcsLessThanK = true;
    }

    evaluation.update(dcs, null, null, null, null);
  }

  private void sample() throws InputGenerationException, IOException, InputIterationException {
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    // 如果采用了修复策略，则采样前excludedLines已经被修复，从而为空
    if (excludedLines.size() != 0) {
      throw new RuntimeException("Illegal excludedLines size");
    }
    Map<DenialConstraint, Set<LinePair>> falseDCLinePairMap = evaluation.getFalseDCLinePairMap();
    log.info("====== 2.Sample from dirty data ======");

    SampleResult sampleResult = new TupleSampler().sample(new File(dirtyDS.getDataPath()),
        topKOfCluster, numInCluster, null, true, excludedLines, falseDCLinePairMap,
        addCounterExampleS, randomClusterS);
    String samplePath = sampleDS.getDataPath();
    log.debug("Write to file: {}", samplePath);
    FileUtil.writeListLinesToFile(sampleResult.getLinesWithHeader(), new File(samplePath));

    evaluation.updateSampleResult(sampleResult);
  }

  private void simRepairing()
      throws InputGenerationException, InputIterationException, IOException {
    // 将需要排除的脏数据行进行修复
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    log.debug("====== 1.x SimRepairing excludedLines: {} ====== ", excludedLines.size());
    log.debug("REPAIR = {}", excludedLines);

    // 修复excludedLines
    String dirtyPath = dirtyDS.getDataPath();
    File dirtyFile = new File(dirtyPath);
    List<List<String>> repairedLinesWithHeader = getRepairedLinesWithHeader(excludedLines,
        evaluation.getLineChangesMap(), dirtyFile);
    log.debug("Write to file: {}", dirtyPath);
    FileUtil.writeListLinesToFile(repairedLinesWithHeader, dirtyFile);

    // 更新changes（excludedLines已经修复，因此需要从changes中删除）
    evaluation.updateChangesByExcludedLines();
  }

  private DCGenerator getGenerator(String dcGenerator, int topK) {
    if (dcGenerator.equals("Basic")) {
      BasicDCGenerator generator = new BasicDCGenerator(sampleDS.getDataPath(),
          candidateDCs.getFullDCsPath(), candidateDCs.getTopKDCsPath(), sampleDS.getHeaderPath(),
          evaluation.getVisitedDCs(), evaluation.getErrorThreshold(), topK);
      return generator;
    } else if (dcGenerator.equals("DCMiner")) {
      RLDCGenerator generator = new RLDCGenerator(sampleDS.getDataPath(),
          candidateDCs.getEvidencesPath(), candidateDCs.getDcsPathForDCMiner(),
          sampleDS.getHeaderPath());
      generator.setExcludeDCs(evaluation.getVisitedDCs());
      return generator;
    } else {
      throw new RuntimeException(String.format("Unknown DCGenerator: %s", dcGenerator));
    }
  }

  private void persistDCs(Set<DenialConstraint> candiDcs, String path) throws IOException {
    List<String> dcStrList = candiDcs.stream().map(DCFormatUtil::convertDC2String)
        .collect(Collectors.toList());
    FileUtil.writeStringLinesToFile(dcStrList, new File(path));
  }

  private void persistExcludedLines(String dirtyDataPath, Set<Integer> excludedLines,
      String excludedLinesPath) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(dirtyDataPath));
      // 第一行header
      String header = br.readLine();
//      log.debug("Skip header: {}", header);
      String line;
      // dirtyLines包含的是行号，行号从0开始
      int i = 0;
      List<String> result = Lists.newArrayList();
      while ((line = br.readLine()) != null) {
        if (excludedLines.contains(i)) {
          result.add(i + "~" + line);
        }
        i++;
      }
      BufferedWriter bw = new BufferedWriter(new FileWriter(excludedLinesPath));
      for (String s : result) {
        bw.write(s);
        bw.newLine();
      }
      bw.close();
      br.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
