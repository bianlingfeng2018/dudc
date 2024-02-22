package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.topK;
import static edu.fudan.utils.DataUtil.getDCsSetFromViolations;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CleanData;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.Evaluation;
import edu.fudan.algorithms.uguide.Evaluation.EvalResult;
import edu.fudan.algorithms.uguide.SampledData;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import java.awt.SystemTray;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

  private final int maxDiscoveryRound = 100;
  private final int maxQueryBudget = 10000;
  private final int topKOfCluster = 2;
  private final int maxInCluster = 2;

  public UGuideDiscovery(String cleanDataPath,
      String dirtyDataPath,
      String sampledDataPath,
      String dcsPathForFCDC,
      String evidencesPathForFCDC,
      String topKDCsPath,
      String groundTruthDCsPath,
      String candidateDCsPath,
      String headerPath) {
    this.cleanData = new CleanData(cleanDataPath, headerPath);
    this.dirtyData = new DirtyData(dirtyDataPath, headerPath);
    this.sampledData = new SampledData(sampledDataPath, headerPath);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, evidencesPathForFCDC, topKDCsPath);
    this.evaluation = new Evaluation(cleanData, dirtyData, groundTruthDCsPath, candidateDCsPath);
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
      log.info("Round {}", round);
      // 采样
      sample();
      // 发现规则
      discoveryDCs();
      // 检测冲突
      detect();
      // 多轮提问
      log.info("Ask questions to user");
      askCellQuestion();
      // 评价真冲突/假冲突
      evaluate();
      // 假等待
      try {
        Thread.sleep(3000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
    log.info("Finish user guided discovery");
  }

  private void askCellQuestion() {
    log.info("Ask CELL question");
    // 推荐一些让用户判断冲突
    Set<DCViolation> questions = evaluation.genCellQuestions(maxQueryBudget);
    Set<DenialConstraint> dcsFromQuestions = getDCsSetFromViolations(questions);
    Map<DenialConstraint, Integer> dcTrustMap = Maps.newHashMap();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    for (DenialConstraint dc : dcsFromQuestions) {
      dcTrustMap.put(dc, 0);
    }
    for (DCViolation vio : questions) {
      List<DenialConstraint> dcs = vio.getDcs();
      for (DenialConstraint dc : dcs) {
        if (evaluation.isTrueViolation(dc, vio.getLinePair())) {
          // 若为真冲突，则增加DC的置信度
          dcTrustMap.put(dc, dcTrustMap.get(dc) + 1);
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
    evaluation.update(null, falseDCs, null, null, null);
  }

  private void evaluate() {
    log.info("Evaluate the true violations and false violations");
    EvalResult result = evaluation.evaluate();
    log.info("TrueVios/TotalVios = {}/{}, {}", result.getTrueVios(), result.getTotalVios(),
        (double) result.getTrueVios() / result.getTotalVios());
    log.info("Final Candidate DCs and violations map:");
    Map<String, Integer> map = result.getDcStrVioSizeMap();
    for (String dcStr : map.keySet()) {
      log.info("{}: {}", dcStr, map.get(dcStr));
    }
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
    // 从经过优化的采样数据中发现规则
    String inputData = sampledData.getDataPath();
    String headerPath = sampledData.getHeaderPath();
    String dcsPathForFCDC = candidateDCs.getDcsPathForFCDC();
    DiscoveryEntry.doDiscovery(inputData, dcsPathForFCDC);
    // 取前k个规则
    log.info("Generate top-k DCs");
    String topKDCsPath = candidateDCs.getTopKDCsPath();
    // TODO: 排除前面已经确认过的DC
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(topK, dcsPathForFCDC, headerPath);
    DCUtil.persistTopKDCs(topKDCs, topKDCsPath);

    evaluation.update(new HashSet<>(topKDCs), null, null, null, null);
  }


  private void sample()
      throws InputGenerationException, IOException, InputIterationException {
    log.info("Sample from dirty data");
    HashSet<Integer> skippedColumns = new HashSet<>();
    List<List<String>> sampled = new TupleSampler()
        .sample(new File(dirtyData.getDataPath()), topKOfCluster, maxInCluster, skippedColumns,
            true);
    String out = sampledData.getDataPath();
    log.debug("Write to file: {}", out);
    FileUtil.writeListLinesToFile(sampled, new File(out));
  }

  private static Comparable getCellValue(ColumnOperand<?> operand, Input di, int line1, int line2) {
    int index = operand.getIndex();
    String n = operand.getColumn().getName();
    String t = operand.getColumn().getType().getSimpleName();
    String nameWithBracket = n + "(" + t + ")";
    List<ParsedColumn<?>> filtered = Arrays.stream(di.getColumns())
        .filter(col -> col.getName().equals(nameWithBracket))
        .collect(Collectors.toList());
    ParsedColumn<?> column = filtered.get(0);
    Comparable value = column.getValue(index == 0 ? line1 : line2);
    return value;
  }
}
