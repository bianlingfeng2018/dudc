package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDCQuestionBudget;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.maxTupleQuestionBudget;
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
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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
      String evidencesPathForFCDC,
      String topKDCsPath,
      String groundTruthDCsPath,
      String candidateDCsPath,
      String trueDCsPath,
      String headerPath) {
    this.cleanData = new CleanData(cleanDataPath, headerPath, changesPath);
    this.dirtyData = new DirtyData(dirtyDataPath, excludedLinesPath, headerPath);
    this.sampledData = new SampledData(sampledDataPath, headerPath);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, evidencesPathForFCDC, topKDCsPath);
    this.evaluation = new Evaluation(cleanData, dirtyData, groundTruthDCsPath, candidateDCsPath,
        trueDCsPath);
  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException, DCMinderToolsException {
    // 设定groundTruth
    log.info("Setup for ground truth DCs");
    evaluation.setUp();
    // 模拟多轮采样+用户交互，以达到发现所有真冲突的目的
    log.info("Start user guided discovery");
    int round = 0;
    while (round < maxDiscoveryRound && !evaluation.allTrueViolationsFound()) {
      round++;
      log.info("------ Round {} -------", round);
      // 采样
      sample();
      // 发现规则
      discoveryDCs();
      // 检测冲突
      detect();
      // 多轮提问
      askCellQuestion();
      askTupleQuestion();
      askDCQuestion();
      // 评价真冲突/假冲突
      evaluate();
      // 输出结果
      persistResult();
    }
    log.info("Finish user guided discovery");
  }

  private void persistResult() throws IOException {
    persistDCs(evaluation.getCandidateDCs(), evaluation.getCandidateDCsPath());
    persistDCs(evaluation.getTrueDCs(), evaluation.getTrueDCsPath());
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    try {
      BufferedReader br = new BufferedReader(new FileReader(this.dirtyData.getDataPath()));
      // 第一行header
      String header = br.readLine();
      log.info("Skip header: {}", header);
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
          new FileWriter(this.dirtyData.getExcludedLinesPath()));
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

  private void askDCQuestion()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    log.info("Ask DC question");
    // 确认真DC，并删除真DC发现的冲突元组
    Set<DenialConstraint> questions = evaluation.genDCQuestionsFromCurrState(maxDCQuestionBudget);
    int numb = questions.size();
    log.info("DCQuestions/MaxDCQuestionBudget={}/{}", numb, maxDCQuestionBudget);
    evaluation.addDCBudget(numb);
    Set<DenialConstraint> trueDCs = questions.stream().filter(dc -> evaluation.isTrueDC(dc))
        .collect(Collectors.toSet());
    Set<Integer> lines = Sets.newHashSet();
    // 检查TrueDCs在dirtyData上产生的所有冲突
    DCViolationSet vios = new HydraDetector(dirtyData.getDataPath(), trueDCs).detect();
    for (DCViolation vio : vios.getViosSet()) {
      LinePair linePair = vio.getLinePair();
      int line1 = linePair.getLine1();
      int line2 = linePair.getLine2();
      lines.add(line1);
      lines.add(line2);
    }
    evaluation.excludeLinesOfTrueDCs(lines);
  }

  private void askTupleQuestion() {
    log.info("Ask TUPLE question");
    // 在采样数据中，推荐一些元组让用户判断
    Set<Integer> questions = evaluation.genTupleQuestionsFromCurrState(maxTupleQuestionBudget);
    int numb = questions.size();
    log.info("TupleQuestions/MaxTupleQuestionBudget={}/{}", numb, maxTupleQuestionBudget);
    evaluation.addTupleBudget(numb);
    evaluation.excludeErrorLinesInSample(questions);
  }

  private void askCellQuestion() {
    log.info("Ask CELL question");
    // 推荐一些让用户判断冲突
    Set<DCViolation> questions = evaluation.genCellQuestionsFromCurrState(maxCellQuestionBudget);
    int numb = questions.size();
    log.info("CellQuestions/MaxCellQuestionBudget={}/{}", numb, maxCellQuestionBudget);
    evaluation.addCellBudget(numb);
    Set<DenialConstraint> dcsUnchecked = Sets.newHashSet();
    Set<DenialConstraint> dcsFromQuestions = getDCsSetFromViolations(questions);
    for (DenialConstraint currDC : evaluation.getCurrDCs()) {
      if (!dcsFromQuestions.contains(currDC)) {
        dcsUnchecked.add(currDC);
      }
    }
    Map<DenialConstraint, Integer> dcTrustMap = Maps.newHashMap();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    for (DenialConstraint dc : dcsFromQuestions) {
      dcTrustMap.put(dc, 0);
    }
    for (DCViolation vio : questions) {
      // TODO: Input和LinePair结合找出相关Cells，给用户判断是否为真冲突
      List<DenialConstraint> dcs = vio.getDcs();
      for (DenialConstraint dc : dcs) {
        if (evaluation.isTrueViolation(dc, vio.getLinePair())) {
          // 若为真冲突，则增加DC的置信度
          dcTrustMap.put(dc, dcTrustMap.get(dc) + 1);
          // 若为真冲突，排除脏数据
          evaluation.excludeLines(vio);
        } else {
          // 若为假冲突，排除假阳性DC
          falseDCs.add(dc);
        }
      }
//      LinePair linePair = vio.getLinePair();
//      int line1 = linePair.getLine1();
//      int line2 = linePair.getLine2();
//      for (DenialConstraint dc : vio.getDcs()) {
//        log.debug("Violation: dc={}, linePair={}", dc.toResult().toString(), linePair);
//        PredicateBitSet predicateSet = dc.getPredicateSet();
//        for (Predicate p : predicateSet) {
//          ColumnOperand<?> operand1 = p.getOperand1();
//          ColumnOperand<?> operand2 = p.getOperand2();
//          Operator op = p.getOperator();
//          Comparable v1 = getCellValue(operand1, di, line1, line2);
//          Comparable v2 = getCellValue(operand2, di, line1, line2);
//          log.debug("Predicate: {}", p.toString());
//          log.debug("v1={}, v2={}, op={}", v1, v2, op.getShortString());
//          boolean eval = op.eval(v1, v2);
//          log.debug("Satisfied={}", eval);
//        }
//      }
    }
    log.info("Cell question done");
    log.info("DC trusts:");
    for (Entry<DenialConstraint, Integer> entry : dcTrustMap.entrySet()) {
      log.info("{}, {}", DCFormatUtil.convertDC2String(entry.getKey()), entry.getValue());
    }
    // TODO: 1、置信度为真冲突个数，置信度低的可能是假阳性规则？2、冲突个数太多的（无论真假），可能是假阳性规则？
    log.info("FalseDCs:");
    for (DenialConstraint falseDC : falseDCs) {
      log.info("{}", DCFormatUtil.convertDC2String(falseDC));
    }
    // TODO: 未检查的DC可能是因为其冲突没有取样，也可能是因为没有产生冲突，如果是这样：
    //  1、它是一个真规则，也是groundTruth，但是没有注入错误。（这种情况要避免？是groundTruth规则就要注入错误，否则该规则对减少冲突没贡献）
    //  2、它是一个真规则，不是groundTruth，没有注入错误。（此时该规则对检测冲突无贡献）
    //  3、可能是假规则吗？因为假规则一般都会产生一些冲突，但是是否绝对？
    //  1和2的情况目前是默认放到候选规则中，但是并不确定它是真规则，只有评估的时候可以确认，后面是否可以增加用户判断？
    log.info("UncheckedDCs:");
    for (DenialConstraint dc : dcsUnchecked) {
      log.info("{}", DCFormatUtil.convertDC2String(dc));
    }
    evaluation.update(null, falseDCs, null, null, null);
  }

  private void evaluate() throws DCMinderToolsException {
    log.info("Evaluate the true violations and false violations");
    EvalResult result = evaluation.evaluate();
    log.info(result.toString());
    log.info("Final Candidate DCs and violations size map:");
    Map<DenialConstraint, Integer> map = result.getDcStrVioSizeMap();
    for (DenialConstraint dc : map.keySet()) {
      log.info("{}: {}", DCFormatUtil.convertDC2String(dc), map.get(dc));
    }
    // TODO: 当本轮发现的规则都是不产生冲突的规则时，不会确认真冲突，因此dirtyLines不变化
    Set<Integer> excludedLines = result.getExcludedLines();
    log.info("Excluded lines = {}", excludedLines.size());
    // TODO: 通过Tuple问题排除sample中的错误行
    Set<Integer> errorLinesInSample = result.getErrorLinesInSample();
    Set<Integer> errorLinesInSampleAndExcluded = errorLinesInSample.stream()
        .filter(i -> excludedLines.contains(i))
        .collect(Collectors.toSet());
    log.info("Excluded/ErrorLinesInSample = {}/{}", errorLinesInSampleAndExcluded.size(),
        errorLinesInSample.size());
    // 通过TrueDCs排除的元组
    Set<Integer> curExcludedLinesOfTrueDCs = result.getCurExcludedLinesOfTrueDCs();
    log.info("CurExcludedLinesOfTrueDCs = {}", curExcludedLinesOfTrueDCs.size());
  }

  private void detect()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
    log.info("Detect violations");
//    String dataPath = sampledData.getDataPath();
    String dataPath = dirtyData.getDataPath();
    String topKDCsPath = candidateDCs.getTopKDCsPath();
    HydraDetector detector = new HydraDetector(dataPath, topKDCsPath);
    DCViolationSet vios = detector.detect();
    log.info("Violations size = {}", vios.size());

    // update candidate DCs and violations
    evaluation.update(null, null, vios.getViosSet(), null, null);
  }

  private void discoveryDCs()
      throws IOException {
    log.info("Discovery DCs from sample");
    BasicDCGenerator generator = new BasicDCGenerator(sampledData.getDataPath(),
        // TODO:现在发现的规则没有加入g1，有的规则冲突太多，明显是假阳性，且影响后续的效率
        candidateDCs.getDcsPathForFCDC(), sampledData.getHeaderPath());
    generator.setExcludeDCs(evaluation.getVisitedDCs());
    Set<DenialConstraint> dcs = generator.generateDCsForUser();

    DCUtil.persistTopKDCs(new ArrayList<>(dcs), candidateDCs.getTopKDCsPath());

    evaluation.update(dcs, null, null, null, null);
  }


  private void sample()
      throws InputGenerationException, IOException, InputIterationException {
    Set<Integer> excludedLines = evaluation.getExcludedLines();
    log.info("Sample from dirty data");

    SampleResult sampleResult = new TupleSampler()
        .sample(new File(dirtyData.getDataPath()), topKOfCluster, maxInCluster,
            null, true, excludedLines);
    String out = sampledData.getDataPath();
    log.debug("Write to file: {}", out);
    FileUtil.writeListLinesToFile(sampleResult.getLinesWithHeader(), new File(out));

    evaluation.updateSampleResult(sampleResult);
  }

  private void persistDCs(Set<DenialConstraint> candiDcs, String path) throws IOException {
    List<String> dcStrList = candiDcs.stream().map(DCFormatUtil::convertDC2String)
        .collect(Collectors.toList());
    FileUtil.writeStringLinesToFile(dcStrList, new File(path));
  }

}
