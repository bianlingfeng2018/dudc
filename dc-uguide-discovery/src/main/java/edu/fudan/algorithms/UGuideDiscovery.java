package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.topK;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CleanData;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.Evaluation;
import edu.fudan.algorithms.uguide.SampledData;
import edu.fudan.transformat.DCUnifyUtil;
import edu.fudan.transformat.DCFileReader;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.FileUtil;
import java.io.File;
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

  // 表头
  private final String headerPath;
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

  private final int maxDiscoveryRound = 1;
  private final int maxQueryBudget = 100;
  private final int topKOfCluster = 2;
  private final int maxInCluster = 2;

  public UGuideDiscovery(String cleanData,
      String dirtyData,
      String sampledData,
      String dcsPathForFCDC,
      String evidencesPathForFCDC,
      String topKDCsPath,
      String groundTruthDCsPath,
      String candidateDCsPath,
      String headerPath) {
    this.cleanData = new CleanData(cleanData);
    this.dirtyData = new DirtyData(dirtyData);
    this.sampledData = new SampledData(sampledData);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, evidencesPathForFCDC, topKDCsPath);
    this.headerPath = headerPath;
    this.evaluation = new Evaluation(groundTruthDCsPath, candidateDCsPath);
  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException, DCMinderToolsException {
    // 设定groundTruth
    setUp();
    // 模拟多轮采样+用户交互，以达到发现所有真冲突的目的
    log.info("Start user guided discovery");
    int round = 0;
    while (round < maxDiscoveryRound && !allTrueViolationsFound()) {
      round++;
      log.info("Round {}", round);
      // 采样
//      sample();
      // 发现规则
//      discoveryDCs();
      // 检测冲突
      detect();
      // 评价真冲突/假冲突
      evaluate();
      // 多轮提问
      log.info("Ask questions to user");
      int queryRound = 0;
      while (queryRound < maxQueryBudget && hasNextQuestion()) {
        queryRound++;
        log.info("Query round {}", queryRound);
        askCellQuestion();
        answerCellQuestion();
      }
    }
    log.info("Finish user guided discovery");
  }

  private void setUp()
      throws DCMinderToolsException, IOException, InputGenerationException, InputIterationException {
    log.info("Setup for ground truth DCs");
    String dataPath = cleanData.getDataPath();
    String groundTruthDCsPath = evaluation.getGroundTruthDCsPath();

    // 发现规则
    DiscoveryEntry.doDiscovery(dataPath, groundTruthDCsPath);
    // 读取规则
    DenialConstraintSet dcs = new DCFileReader(dataPath, groundTruthDCsPath).readDCsFromFile();
    // 检测冲突
    HydraDetector detector = new HydraDetector(dataPath);
    DCViolationSet vios = detector.detect(dcs);
    int size = vios.size();
    log.info("Violations size = {}", size);
    if (size != 0) {
      throw new RuntimeException("Error discovery of DCs on clean data");
    }

    // TODO: DC will be identified by a database name, eg. 'xxx_dirty.csv', which we do not need
    for (DenialConstraint dc : dcs) {
      evaluation.getGroundTruthDCs().add(DCUnifyUtil.getUnifiedCopyOf(dc));
    }
  }

  private void evaluate()
      throws InputGenerationException, IOException, InputIterationException, DCMinderToolsException {
    log.info("Evaluate the true violations and false violations");
    DCViolationSet vios = evaluation.getCandidateViolations();
    int totalVios = vios.size();
    int trueVios = 0;
    Map<DenialConstraint, Set<DCViolation>> viosMap = vios.getDcViosMap();
    Map<String, Integer> evalResult = Maps.newHashMap();
    for (Entry<DenialConstraint, Set<DCViolation>> entry : viosMap.entrySet()) {
      DenialConstraint dc = entry.getKey();
      Set<DCViolation> dcViolations = entry.getValue();
      // 模拟用户交互，判断规则是否是真规则，如果是，那么其对应的冲突就是真冲突
      if (evaluation.getGroundTruthDCs().contains(dc)) {
        int vioSize = dcViolations.size();
        evalResult.put(DCFormatUtil.convertToDCStr(dc), vioSize);
        trueVios += vioSize;
      }
    }
    log.info("True vios/totalVios = {}/{}", trueVios, totalVios);
    log.info("Evaluation results:");
    for (String dcStr : evalResult.keySet()) {
      log.info("{}: {}", dcStr, evalResult.get(dcStr));
    }
  }

  private void detect()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
    log.info("Detect violations");
//    String dataPath = sampledData.getDataPath();
    String dataPath = dirtyData.getDataPath();
    DenialConstraintSet dcs = new DCFileReader(dataPath, candidateDCs.getTopKDCsPath())
        .readDCsFromFile();
    DCViolationSet vios = new HydraDetector(dataPath).detect(dcs);
    log.info("Violations size = {}", vios.size());

    // update candidate DCs and violations
    updateCandidateDCsAndVios(dcs, vios);
  }

  private void updateCandidateDCsAndVios(DenialConstraintSet dcs, DCViolationSet vios) {
    for (DenialConstraint dc : dcs) {
      evaluation.getCandidateDCs().add(dc);
    }
    for (DCViolation vio : vios.getViosSet()) {
      evaluation.getCandidateViolations().add(vio);
    }
  }

  private void discoveryDCs()
      throws IOException, DCMinderToolsException {
    log.info("Discovery DCs from sample");
    // 从经过优化的采样数据中发现规则
    String inputData = sampledData.getDataPath();
    String dcsPath = candidateDCs.getDcsPathForFCDC();
    DiscoveryEntry.doDiscovery(inputData, dcsPath);
    // 取前k个规则
    log.info("Generate top-k DCs");
    generateTopKDCs(topK);
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


  private void generateTopKDCs(int topK)
      throws IOException, DCMinderToolsException {
    DenialConstraintSet dcs = new DCFileReader(sampledData.getDataPath(),
        candidateDCs.getDcsPathForFCDC()).readDCsFromFile();
    ArrayList<DenialConstraint> dcList = Lists.newArrayList(dcs);
    dcList.sort((o1, o2) -> {
      int cntComparison = Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
      return cntComparison;
    });
    log.debug("Read dcs size = {}", dcList.size());
    log.debug("Sorted dcs: {}", dcList);
    String topKDCsPath = candidateDCs.getTopKDCsPath();
    log.info("Write to file {}", topKDCsPath);
    List<String> dcStrList = dcList.stream()
        .map(DCFormatUtil::convertToDCStr)
        .collect(Collectors.toList());
    FileUtil.writeStringLinesToFile(dcStrList.subList(0, topK), new File(topKDCsPath));
  }

  private void answerCellQuestion() {
    log.info("Answer CELL question");

  }

  private void askCellQuestion() {
    log.info("Ask CELL question");

  }

  private boolean hasNextQuestion() {
    return false;
  }

  private boolean allTrueViolationsFound() {
    return false;
  }

}
