package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.addCounterExampleS;
import static edu.fudan.conf.DefaultConf.canBreakEarly;
import static edu.fudan.conf.DefaultConf.dcGeneratorConf;
import static edu.fudan.conf.DefaultConf.decreaseFactor;
import static edu.fudan.conf.DefaultConf.defCellQStrategy;
import static edu.fudan.conf.DefaultConf.defDCQStrategy;
import static edu.fudan.conf.DefaultConf.defTupleQStrategy;
import static edu.fudan.conf.DefaultConf.delta;
import static edu.fudan.conf.DefaultConf.dynamicG1;
import static edu.fudan.conf.DefaultConf.excludeLinePercent;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
import static edu.fudan.conf.DefaultConf.minLenOfDC;
import static edu.fudan.conf.DefaultConf.numInCluster;
import static edu.fudan.conf.DefaultConf.questionsConf;
import static edu.fudan.conf.DefaultConf.randomClusterS;
import static edu.fudan.conf.DefaultConf.repairErrors;
import static edu.fudan.conf.DefaultConf.succinctFactor;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.conf.DefaultConf.useSample;
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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
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
   * When discovered dcs size is 0.
   */
  private boolean noMoreDiscDCs = false;
  /**
   * The correlation score map.
   */
  private final Map<String, Double> columnsCorrScoreMap;

  public UGuideDiscovery(String cleanDataPath, String changesPath, String dirtyDataPath,
      String dirtyDataUnrepairedPath, String excludedLinesPath, String sampledDataPath,
      String fullDCsPath, String dcsPathForDCMiner, String evidencesPath, String topKDCsPath,
      String groundTruthDCsPath, String candidateDCsPath, String trueDCsPath, String visitedDCsPath,
      String headerPath, String csvResultPath, String correlationByUserPath) throws IOException {
    this.cleanDS = new CleanDS(cleanDataPath, headerPath, changesPath);
    this.dirtyDS = new DirtyDS(dirtyDataPath, dirtyDataUnrepairedPath, excludedLinesPath,
        headerPath);
    this.sampleDS = new SampleDS(sampledDataPath, headerPath);
    this.candidateDCs = new CandidateDCs(fullDCsPath, dcsPathForDCMiner, evidencesPath,
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
      long t1 = System.currentTimeMillis();
      if (repairErrors) {
        simRepairing();
      }
      // 采样
      long t2 = System.currentTimeMillis();
      long du1 = t2 - t1;
      log.debug("DU1: {}", du1);
      evaluation.setDu1(du1);
      if (useSample) {
        sample();
      } else {
        copyAsSample();
      }
      // 发现规则
      long t3 = System.currentTimeMillis();
      long du2 = t3 - t2;
      log.debug("DU2: {}", du2);
      evaluation.setDu2(du2);
      discoveryDCs();
      if (this.noMoreDiscDCs) {
        if (!dynamicG1) {
          log.debug("NoMoreDiscDCs and we use fixed g1, just break!!!");
          break;
        }
        if (evaluation.allTrueViolationsFound()) {
          if (evaluation.noFalseViolations()) {
            log.debug("NoMoreDiscDCs, allTrueViolationsFound and noFalseViolations, just break!!!");
            break;
          }
          log.debug("NoMoreDiscDCs and allTrueViolationsFound!!!");
        }
        // 没有更多DC了，在动态g1的设定下，且真规则还未找全，才考虑变化g1
        log.debug("NoMoreDiscDCs, decrease g1 and continue!!!");
//          evaluation.decreaseG1(decreaseFactor);  // 已经在evaluate中自动判断candiDC未变从而减小g1
        evaluate();
        persistResult();
        // 更新g1后继续循环
        this.noMoreDiscDCs = false;
        continue;
      }
      // 检测冲突
      long t4 = System.currentTimeMillis();
      long du3 = t4 - t3;
      log.debug("DU3: {}", du3);
      evaluation.setDu3(du3);
      detect();
      // 多轮提问
      long t5 = System.currentTimeMillis();
      long du4 = t5 - t4;
      log.debug("DU4: {}", du4);
      evaluation.setDu4(du4);
      if (questionsConf[0] == 1) {
        askCellQuestion();
      }
      long t6 = System.currentTimeMillis();
      long du5 = t6 - t5;
      log.debug("DU5: {}", du5);
      evaluation.setDu5(du5);
      if (questionsConf[1] == 1) {
        askTupleQuestion();
      }
      long t7 = System.currentTimeMillis();
      long du6 = t7 - t6;
      log.debug("DU6: {}", du6);
      evaluation.setDu6(du6);
      if (questionsConf[2] == 1) {
        askDCQuestion();
      }
      // 评价真冲突/假冲突
      long t8 = System.currentTimeMillis();
      long du7 = t8 - t7;
      log.debug("DU7: {}", du7);
      evaluation.setDu7(du7);
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
    Set<DenialConstraint> trueDCs = evaluation.getCandiTrueDCs();
    DCViolationSet violations = new HydraDetector(this.dirtyDS.getDataPath(), trueDCs).detect();
    log.debug("TrueDCs={}, violations={}", trueDCs.size(), violations.size());
  }

  private boolean canProcess(int round) {
    if (round >= maxDiscoveryRound) {
      log.info("Can no longer process, because it reach max round!!!");
      return false;
    }
    if (evaluation.allTrueViolationsFound()) {
      if (evaluation.noFalseViolations()) {
        // P=R=F1=1.0
        log.info("All TrueViolations found and no FalseViolations!!!");
        return false;
      }
      // 此时recall为1.0了，但是precision还能继续提高，例如DCQ可以继续排除假规则。
      log.info("All TrueViolations found!!!");
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
    persistDCs(evaluation.getCandiTrueDCs(), trueDCsPath);
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
        minLenOfDC, succinctFactor, defDCQStrategy, maxDCQuestionBudget);

    DCsQuestionResult result = selector.simulate();

    int budgetUsed = result.getBudgetUsed();
    log.info("DCQuestions/MaxDCQuestionBudget/CurrDCsSize={}/{}/{}", budgetUsed,
        maxDCQuestionBudget, evaluation.getCurrDCs().size());
    evaluation.addDCBudget(budgetUsed);
    Set<DenialConstraint> falseDCs = result.getFalseDCs();
    Set<DenialConstraint> trueDCs = result.getTrueDCs();
    HashSet<DenialConstraint> visitedDCs = new HashSet<>();
    visitedDCs.addAll(falseDCs);
    visitedDCs.addAll(trueDCs);
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
    evaluation.update(null, falseDCs, trueDCs, null, null, null, visitedDCs);
  }

  private void askTupleQuestion() {
    log.info("===== 5.2 Ask TUPLE question =====");
    // 在脏数据中，推荐一些元组让用户判断
    Set<Integer> errorLines = evaluation.getErrorLinesOfChanges();
    Set<DCViolation> currVios = evaluation.getCurrVios();
    TupleQuestion selector = new TupleQuestion(errorLines, currVios, defTupleQStrategy,
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
    evaluation.update(null, null, null, null, null, excludedLines, null);
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
        maxCellQuestionBudget, delta, canBreakEarly, defCellQStrategy, excludeLinePercent);

    CellQuestionResult result = selector.simulate();
    Set<DenialConstraint> falseDCs = result.getFalseDCs();
    Set<DenialConstraint> possibleTrueDCs = result.getPossibleTrueDCs();
    HashSet<DenialConstraint> visitedDCs = new HashSet<>();
    visitedDCs.addAll(falseDCs);
    // TODO: PossibleTrueDCs并不确定，如果作为visitedDC，可能会降低准确率。
//    visitedDCs.addAll(possibleTrueDCs);
    // Add counterexample from falseVios.
    Set<DCViolation> falseVios = result.getFalseVios();
    for (DCViolation vio : falseVios) {
      DenialConstraint dc = vio.getDenialConstraintsNoData().get(0);
      evaluation.addCounterExampleToMap(dc, vio);
    }
    int budgetUsed = result.getBudgetUsed();
    log.info("CellQuestions/MaxCellQuestionBudget/CurrViosSize={}/{}/{}", budgetUsed,
        maxCellQuestionBudget, currVios.size());
    evaluation.addCellBudget(budgetUsed);
    // TODO: 这里效率待优化
    // TODO: CellQ目前仅排除假规则
    evaluation.update(null, falseDCs, possibleTrueDCs, null, null, null, visitedDCs);
  }

  private void evaluate() {
    log.info("====== 6.Evaluate the true violations and false violations ======");
    EvalResult result = evaluation.evaluate();
    // 打印结果信息
    // 排除的元组信息
    // TODO: 当本轮发现的规则没有冲突时（可能是因为规则过拟合或者规则与注入错误无关），
    //  ExcludedLines(CellQ)为零，这样数据无法继续变得更干净，下一轮可能还是相同的结果，这样效率较低。
    log.info("ErrorLinesInSampleAndInExcluded/ErrorLinesInSample = {}/{}",
        result.getErrorLinesInSampleAndExcluded(), result.getErrorLinesInSample());
    log.info("ExcludedLinesOfCellQ/OfTupleQ/OfDCsQ/ExcludedLines = {}/{}/{}/{}",
        result.getExcludedLinesOfCellQ(), result.getExcludedLinesOfTupleQ(),
        result.getExcludedLinesOfDCsQ(), result.getExcludedLines());
    log.info("Precision/Recall/F-measure = {}/{}/{}", result.getPrecision(), result.getRecall(),
        result.getF1());
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
    // TODO: 这里设定冲突数量上线，以节省后续提问时间
    ArrayList<DCViolation> list = new ArrayList<>(violations);
    Collections.shuffle(list);
    List<DCViolation> subList = list.subList(0,
        Math.min(violations.size(), 10000));

    evaluation.update(null, null, null, new HashSet<>(subList), null, null, null);
  }

  private void discoveryDCs() {
    log.info("====== 3.Discovery DCs from sample ======");
    log.info("DCGeneratorConf: {}", dcGeneratorConf);
    DCGenerator generator = getGenerator(dcGeneratorConf, topK);
    Set<DenialConstraint> dcs = generator.generateDCs();

    if (dcs.size() == 0) {
      this.noMoreDiscDCs = true;
    }

    // 把新发现的规则作为candiDC
    evaluation.update(dcs, null, null, null, null, null, null);
  }

  private void copyAsSample() throws IOException {
    log.info("====== 2.Copy dirty data as sample ======");
    Files.copy(Paths.get(dirtyDS.getDataPath()), Paths.get(sampleDS.getDataPath()),
        StandardCopyOption.REPLACE_EXISTING);
  }

  private void sample() throws InputGenerationException, IOException, InputIterationException {
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    // 如果采用了修复策略，则采样前excludedLines已经被修复，从而为空
    if (excludedLines.size() != 0) {
      throw new RuntimeException("Illegal excludedLines size");
    }
    Map<DenialConstraint, Set<LinePair>> falseVioLinePairMap = evaluation.getFalseVioLinePairMap();
    log.info("====== 2.Sample from dirty data ======");

    SampleResult sampleResult = new TupleSampler().sample(new File(dirtyDS.getDataPath()),
        topKOfCluster, numInCluster, null, true, excludedLines, falseVioLinePairMap,
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
