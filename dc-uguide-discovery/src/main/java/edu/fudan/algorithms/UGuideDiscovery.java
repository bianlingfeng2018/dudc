package edu.fudan.algorithms;

import br.edu.utfpr.pena.fdcd.mockers.FDCDMocker;
import com.google.common.collect.Lists;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CleanData;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.SampledData;
import edu.fudan.exceptions.UGuideDiscoveryException;
import edu.fudan.utils.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

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

  private final int maxDiscoveryRound = 1;
  private final int maxQueryBudget = 100;
  private final int topKOfCluster = 2;
  private final int maxInCluster = 2;

  public UGuideDiscovery(String cleanData, String dirtyData, String sampledData, String dcsPath,
      String evidencesPath) {
    this.cleanData = new CleanData(cleanData);
    this.dirtyData = new DirtyData(dirtyData);
    this.sampledData = new SampledData(sampledData);
    this.candidateDCs = new CandidateDCs(dcsPath, evidencesPath);
  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException, UGuideDiscoveryException {
    // 模拟多轮采样+用户交互，以达到发现所有真冲突的目的
    log.info("Start user guided discovery");
    int round = 0;
    while (round < maxDiscoveryRound && !allTrueViolationsFound()) {
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

  private void answerCellQuestion() {
    log.info("Answer CELL question");

  }

  private void askCellQuestion() {
    log.info("Ask CELL question");

  }

  private boolean hasNextQuestion() {
    return false;
  }

  private void detect() {
    log.info("Detect violations");

  }

  private void discoveryDCs() {
    log.info("Discovery DCs from sample");
    // 从经过优化的采样数据中发现规则
    String inputData = sampledData.getDataPath();
    String dcsPath = candidateDCs.getDcsPath();
    String[] args = new String[]{inputData, "-o", dcsPath};
    int exitCode = new CommandLine(new FDCDMocker()).execute(args);
    log.info("Discovery DCs exit code: {}", exitCode);
    // 取前k个规则
    log.info("Rank and get top-k DCs");
    rankAndGetTopKDCs();
  }

  private void rankAndGetTopKDCs() {


  }

  private void sample()
      throws InputGenerationException, IOException, InputIterationException, UGuideDiscoveryException {
    log.info("Sample from dirty data");
    HashSet<Integer> skippedColumns = new HashSet<>();
    String inputPath = dirtyData.getDataPath();
    String outputPath = sampledData.getDataPath();
    File input = new File(inputPath);
    File output = new File(outputPath);
    List<List<String>> sampled = new TupleSampler()
        .sample(input, topKOfCluster, maxInCluster, skippedColumns, true);
    log.debug("Write to file: {}", outputPath);
    replaceFirstLineForFCDC(sampled);
    FileUtil.writeLinesToFile(sampled, output);
  }

  private void replaceFirstLineForFCDC(List<List<String>> lines) throws UGuideDiscoveryException {
    List<String> oldHeads = lines.get(0);
    List<String> newHeads = Lists.newArrayList();
    for (String head : oldHeads) {
      String newHead = convertToFCDCHead(head);
      newHeads.add(newHead);
    }
    lines.set(0, newHeads);
  }

  private String convertToFCDCHead(String head) throws UGuideDiscoveryException {
    if (head == null) {
      throw new UGuideDiscoveryException(String.format("Unknown head: %s", (Object) null));
    }
    String resultHead;
    if (head.contains("String")) {
      resultHead = head.replace("(String)", " str");
    } else if (head.contains("Integer")) {
      resultHead = head.replace("(Integer)", " int");
    } else if (head.contains("Double")) {
      resultHead = head.replace("(Double)", " float");
    } else {
      throw new UGuideDiscoveryException(String.format("Unknown head: %s", head));
    }
    return resultHead;
  }

  private boolean allTrueViolationsFound() {
    return false;
  }

}
