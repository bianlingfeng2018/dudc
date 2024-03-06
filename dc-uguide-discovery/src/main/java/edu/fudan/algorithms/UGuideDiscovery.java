package edu.fudan.algorithms;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.conf.DefaultConf.dcGenerator;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
import static edu.fudan.conf.DefaultConf.questionsConf;
import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.utils.DataUtil.getDCsSetFromViolations;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CleanData;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.Evaluation;
import edu.fudan.algorithms.uguide.Evaluation.EvalResult;
import edu.fudan.algorithms.uguide.SampledData;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.CSVWriter;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscovery {

  // 干净数据
  private final CleanData cleanData;
  // 脏数据
  private final DirtyData dirtyData;
  // 采样数据
  private final SampledData sampledData;
  // 候选规则
  private final CandidateDCs candidateDCs;
  // 评价
  private final Evaluation evaluation;

  public UGuideDiscovery(String cleanDataPath,
      String changesPath,
      String dirtyDataPath,
      String excludedLinesPath,
      String sampledDataPath,
      String dcsPathForFCDC,
      String dcsPathForDCMiner,
      String evidencesPathForFCDC,
      String topKDCsPath,
      String groundTruthDCsPath,
      String candidateDCsPath,
      String trueDCsPath,
      String visitedDCsPath,
      String headerPath,
      String csvResultPath) {
    this.cleanData = new CleanData(cleanDataPath, headerPath, changesPath);
    this.dirtyData = new DirtyData(dirtyDataPath, excludedLinesPath, headerPath);
    this.sampledData = new SampledData(sampledDataPath, headerPath);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, dcsPathForDCMiner, evidencesPathForFCDC,
        topKDCsPath);
    this.evaluation = new Evaluation(cleanData, dirtyData, groundTruthDCsPath, candidateDCsPath,
        trueDCsPath, visitedDCsPath, csvResultPath);
  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException, DCMinderToolsException {
    // 设定groundTruth
    log.info("====== 1.Setup ======");
    evaluation.setUp();
    // 模拟多轮采样+用户交互，以达到发现所有真冲突的目的
    int round = 0;
    while (canProcess(round)) {
      round++;
      log.info("------ Round {} -------", round);
      // 采样
      sample();
      // 发现规则
      discoveryDCs();
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
    log.info("Finish");
  }

  private boolean canProcess(int round) {
    if (round >= maxDiscoveryRound) {
      log.info("Reach max round");
      return false;
    }
    if (evaluation.allTrueViolationsFound()) {
      log.info("All changes covered");
      return false;
    }
    return true;
  }

  private void persistResult() throws IOException {
    log.info("====== 7.Persist result ======");
    String trueDCsPath = evaluation.getTrueDCsPath();
    String candidateDCsPath = evaluation.getCandidateDCsPath();
    String visitedDCsPath = evaluation.getVisitedDCsPath();
    String excludedLinesPath = this.dirtyData.getExcludedLinesPath();
    log.info("TrueDCsPath={}", trueDCsPath);
    log.info("CandidateDCsPath={}", candidateDCsPath);
    log.info("VisitedDCsPath={}", visitedDCsPath);
    log.info("ExcludedLinesPath={}", excludedLinesPath);
    persistDCs(evaluation.getTrueDCs(), trueDCsPath);
    persistDCs(evaluation.getCandidateDCs(), candidateDCsPath);
    persistDCs(evaluation.getVisitedDCs(), visitedDCsPath);
    persistExcludedLines(this.dirtyData.getDataPath(), evaluation.getExcludedLines(),
        excludedLinesPath);
    // 保存结果
    CSVWriter.writeToFile(evaluation.getCsvResultPath(), evaluation.getEvalResults());
  }

  private void askDCQuestion()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    log.info("===== 5.3 Ask DC question ======");
    // TODO: 也可以确认假DC并排除
    // 确认真DC，并删除真DC发现的冲突元组
    Set<DenialConstraint> questions = evaluation.genDCQuestionsFromCurrState(maxDCQuestionBudget);
    int numb = questions.size();
    log.info("DCQuestions/MaxDCQuestionBudget/CurrDCsSize={}/{}/{}",
        numb,
        maxDCQuestionBudget,
        evaluation.getCurrDCs().size());
    evaluation.addDCBudget(numb);
    // 判断得到TrueDCs
    Set<DenialConstraint> trueDCs = questions.stream().filter(dc -> evaluation.isTrueDC(dc))
        .collect(Collectors.toSet());
    log.info("TureDCs(DCsQ): {}", trueDCs.size());
    for (DenialConstraint dc : trueDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    // 判断得到TrueDCs
    Set<DenialConstraint> falseDCs = questions.stream().filter(dc -> !evaluation.isTrueDC(dc))
        .collect(Collectors.toSet());
      log.info("FalseDCs(DCsQ): {}", falseDCs.size());
    for (DenialConstraint dc : falseDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    // 排除TrueDCs在脏数据上的冲突元组
    Set<Integer> excludedLinesInDCsQ = Sets.newHashSet();
    DCViolationSet vios = new HydraDetector(dirtyData.getDataPath(), trueDCs).detect();
    for (DCViolation vio : vios.getViosSet()) {
      LinePair linePair = vio.getLinePair();
      int line1 = linePair.getLine1();
      int line2 = linePair.getLine2();
      excludedLinesInDCsQ.add(line1);
      excludedLinesInDCsQ.add(line2);
    }
    // TODO: 优选排除的元组
    int sizeBefore = excludedLinesInDCsQ.size();
    int sizeAfter = (int) (sizeBefore * 0.1);
    Set<Integer> excludedLinesInDCsQRandom = new HashSet<>(getRandomElements(excludedLinesInDCsQ, sizeAfter));
    log.debug("ExcludedLinesInDCsQRandom before {}, after {}", sizeBefore, sizeAfter);
    evaluation.setExcludedLinesInDCsQ(excludedLinesInDCsQRandom);
    evaluation.update(null, falseDCs, null, null, excludedLinesInDCsQRandom);
  }

  private void askTupleQuestion() {
    log.info("===== 5.2 Ask TUPLE question =====");
    // 在采样数据中，推荐一些元组让用户判断
    Set<Integer> questions = evaluation.genTupleQuestionsFromCurrState(maxTupleQuestionBudget);
    int numb = questions.size();
    log.info("TupleQuestions/MaxTupleQuestionBudget/SampleSize={}/{}/{}",
        numb,
        maxTupleQuestionBudget,
        evaluation.getSampleResultSize());
    evaluation.addTupleBudget(numb);
    // 排除question(sample中推荐给用户判断的元组)中的所有错误行
    Set<Integer> excludedLinesInSample = questions.stream()
        .filter(i -> evaluation.getErrorLinesOfChanges().contains(i))
        .collect(Collectors.toSet());
    evaluation.setExcludedLinesInTupleQ(excludedLinesInSample);
    log.info("FalseTuples(excludedLinesInSample): {}", excludedLinesInSample.size());
    evaluation.update(null, null, null, null, excludedLinesInSample);
  }

  private void askCellQuestion() {
    log.info("====== 5.1 Ask CELL question ======");
    // 推荐一些让用户判断冲突
    Set<DCViolation> questions = evaluation.genCellQuestionsFromCurrState(maxCellQuestionBudget);
    int numb = questions.size();
    log.info("CellQuestions/MaxCellQuestionBudget/CurrViosSize={}/{}/{}",
        numb,
        maxCellQuestionBudget,
        evaluation.getCurrVios().size());
    evaluation.addCellBudget(numb);
    Set<Integer> excludedLinesInCellQ = Sets.newHashSet();
    Set<DenialConstraint> dcsFromQuestions = getDCsSetFromViolations(questions);
    Set<DenialConstraint> dcsUnchecked = Sets.newHashSet();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    Map<DenialConstraint, Integer> dcTrustMap = Maps.newHashMap();
    for (DenialConstraint dc : dcsFromQuestions) {
      dcTrustMap.put(dc, 0);
    }
    for (DenialConstraint currDC : evaluation.getCurrDCs()) {
      if (!dcsFromQuestions.contains(currDC)) {
        dcsUnchecked.add(currDC);
      }
    }
    for (DCViolation vio : questions) {
      // TODO: Input和LinePair结合找出相关Cells，给用户判断是否为真冲突
      List<DenialConstraint> dcs = vio.getDenialConstraintList();
      for (DenialConstraint dc : dcs) {
        if (evaluation.isTrueViolation(dc, vio.getLinePair())) {
          // 若为真冲突，则增加DC的置信度
          dcTrustMap.put(dc, dcTrustMap.get(dc) + 1);
          // 若为真冲突，排除脏数据
          LinePair linePair = vio.getLinePair();
          int line1 = linePair.getLine1();
          int line2 = linePair.getLine2();
          excludedLinesInCellQ.add(line1);
          excludedLinesInCellQ.add(line2);
        } else {
          // 若为假冲突，排除假阳性DC
          falseDCs.add(dc);
        }
      }
    }
    log.info("CheckedDCs(with trusts(TrueViolationSize)): {}", dcTrustMap.keySet().size());
    for (Entry<DenialConstraint, Integer> entry : dcTrustMap.entrySet()) {
      log.debug("{}, {}", DCFormatUtil.convertDC2String(entry.getKey()), entry.getValue());
    }
    // TODO: 1、置信度为真冲突个数，置信度低的可能是假阳性规则？2、冲突个数太多的（无论真假），可能是假阳性规则？
    log.info("FalseDCs: {}", falseDCs.size());
    for (DenialConstraint falseDC : falseDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(falseDC));
    }
    // TODO: 未检查的DC可能是因为其冲突没有取样，也可能是因为没有产生冲突，如果是这样：
    //  1、它是一个真规则，也是groundTruth，但是没有注入错误。（这种情况要避免？是groundTruth规则就要注入错误，否则该规则对减少冲突没贡献）
    //  2、它是一个真规则，不是groundTruth，没有注入错误。（此时该规则对检测冲突无贡献）
    //  3、可能是假规则吗？因为假规则一般都会产生一些冲突，但是是否绝对？
    //  1和2的情况目前是默认放到候选规则中，但是并不确定它是真规则，只有评估的时候可以确认，后面是否可以增加用户判断？
    log.info("UncheckedDCs: {}", dcsUnchecked.size());
    for (DenialConstraint dc : dcsUnchecked) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    evaluation.setExcludedLinesInCellQ(excludedLinesInCellQ);
    evaluation.update(null, falseDCs, null, null, excludedLinesInCellQ);
  }

  private void evaluate() throws DCMinderToolsException {
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
        result.getErrorLinesInSampleAndExcluded(),
        result.getErrorLinesInSample());
    log.info("ExcludedLinesOfCellQ/OfTupleQ/OfDCsQ/ExcludedLines = {}/{}/{}/{}",
        result.getExcludedLinesOfCellQ(),
        result.getExcludedLinesOfTupleQ(),
        result.getExcludedLinesOfDCsQ(),
        result.getExcludedLines());
    log.info("TrueVios/CandiVios/GTVios = {}/{}/{}",
        result.getViolationsTrue(),
        result.getViolationsCandidate(),
        result.getViolationsGroundTruth());
    log.info("TrueDCs/CandiDCs/GTDCs = {}/{}/{}",
        result.getDCsTrue(),
        result.getDCsCandidate(),
        result.getDCsGroundTruth());
    log.info("CellsOfTrueViosAndChanges/CellsOfTrueVios/CellsOfChanges = {}/{}/{}",
        result.getCellsOfTrueViosAndChanges(),
        result.getCellsOfTrueVios(),
        result.getCellsOfChanges());
    log.info("CellQuestion/TupleQuestion/DCQuestion = {}/{}/{}",
        result.getQuestionsCell(),
        result.getQuestionsTuple(),
        result.getQuestionsDC());
  }

  private void detect()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
    log.info("====== 4.Detect violations on dirty data ======");
//    String dataPath = sampledData.getDataPath();
    String dataPath = dirtyData.getDataPath();
    String topKDCsPath = candidateDCs.getTopKDCsPath();
    HydraDetector detector = new HydraDetector(dataPath, topKDCsPath);
    DCViolationSet vios = detector.detect();
    log.info("Vios = {}", vios.size());

    // update candidate DCs and violations
    Set<DCViolation> violations = vios.getViosSet();
    evaluation.update(null, null, violations, null, null);
    // update current violations, which will be used to choose questions
    evaluation.updateCurrState(violations);
  }

  private void discoveryDCs()
      throws IOException, DCMinderToolsException {
    log.info("====== 3.Discovery DCs from sample ======");
    // TODO:现在发现的规则没有加入g1，有的规则冲突太多，明显是假阳性，且影响后续的效率
    log.info("DCGenerator: {}", dcGenerator);
    DCGenerator generator = getGenerator(dcGenerator);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();

    if (dcs.size() != topK) {
      throw new DCMinderToolsException(String.format("Discovery DCs size is not %s: %s",
          topK, dcs.size()));
    }
    DCUtil.persistTopKDCs(new ArrayList<>(dcs), candidateDCs.getTopKDCsPath());

    evaluation.update(dcs, null, null, null, null);
  }

  private void sample()
      throws InputGenerationException, IOException, InputIterationException {
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    log.info("====== 2.Sample from dirty data ======");

    SampleResult sampleResult = new TupleSampler()
        .sample(new File(dirtyData.getDataPath()), topKOfCluster, maxInCluster,
            null, true, excludedLines);
    String out = sampledData.getDataPath();
    log.debug("Write to file: {}", out);
    FileUtil.writeListLinesToFile(sampleResult.getLinesWithHeader(), new File(out));

    evaluation.updateSampleResult(sampleResult);
  }

  private DCGenerator getGenerator(String dcGenerator) throws DCMinderToolsException {
    if (dcGenerator.equals("Basic")) {
      BasicDCGenerator generator = new BasicDCGenerator(sampledData.getDataPath(),
          candidateDCs.getDcsPathForFCDC(), sampledData.getHeaderPath());
      generator.setExcludeDCs(evaluation.getVisitedDCs());
      // 设定近似阈值
      generator.setErrorThreshold(evaluation.getErrorThreshold());
      return generator;
    } else if (dcGenerator.equals("DCMiner")) {
      RLDCGenerator generator = new RLDCGenerator(sampledData.getDataPath(),
          candidateDCs.getEvidencesPathForFCDC(),
          candidateDCs.getDcsPathForDCMiner(),
          sampledData.getHeaderPath());
      generator.setExcludeDCs(evaluation.getVisitedDCs());
      return generator;
    } else {
      throw new DCMinderToolsException(String.format("Unknown DCGenerator: %s", dcGenerator));
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
      BufferedWriter bw = new BufferedWriter(
          new FileWriter(excludedLinesPath));
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
