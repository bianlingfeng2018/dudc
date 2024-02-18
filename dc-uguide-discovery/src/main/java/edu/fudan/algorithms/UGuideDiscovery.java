package edu.fudan.algorithms;

import br.edu.utfpr.pena.fdcd.mockers.FDCDMocker;
import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.uguide.CandidateDCs;
import edu.fudan.algorithms.uguide.CleanData;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.SampledData;
import edu.fudan.transformat.DCFileReader;
import edu.fudan.transformat.FormatUtil;
import edu.fudan.utils.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
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

  public UGuideDiscovery(String cleanData,
      String dirtyData,
      String sampledData,
      String dcsPathForFCDC,
      String evidencesPathForFCDC) {
    this.cleanData = new CleanData(cleanData);
    this.dirtyData = new DirtyData(dirtyData);
    this.sampledData = new SampledData(sampledData);
    this.candidateDCs = new CandidateDCs(dcsPathForFCDC, evidencesPathForFCDC);
  }

  public void guidedDiscovery()
      throws InputGenerationException, IOException, InputIterationException, DCMinderToolsException {
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

  private void discoveryDCs()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    log.info("Discovery DCs from sample");
    // 从经过优化的采样数据中发现规则
    String inputData = sampledData.getDataPath();
    String dcsPath = candidateDCs.getDcsPathForFCDC();
    String[] args = new String[]{inputData, "-o", dcsPath};
    int exitCode = new CommandLine(new FDCDMocker()).execute(args);
    log.info("Discovery DCs exit code: {}", exitCode);
    // 取前k个规则
    log.info("Generate top-k DCs");
    generateTopKDCs();
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


  private void generateTopKDCs()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
    Input input = sampledData.generateNewCopy();
    String dcsPath = candidateDCs.getDcsPathForFCDC();
    DenialConstraintSet dcs = new DCFileReader(input.getColumns())
        .readDCsFromFile(new File(dcsPath));
    ArrayList<DenialConstraint> dcList = Lists.newArrayList(dcs);
    dcList.sort((o1, o2) -> {
      int cntComparison = Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
      return cntComparison;
    });
    log.debug("Read dcs size = {}", dcList.size());
    log.debug("Sorted dcs: {}", dcList);
    String sortedDCsPath = dcsPath.replace(".out", "_sorted.out");
    log.info("Write to file {}", sortedDCsPath);
    List<String> dcStrList = dcList.stream()
        .map(FormatUtil::convertToDCStr)
        .collect(Collectors.toList());
    FileUtil.writeStringLinesToFile(dcStrList, new File(sortedDCsPath));
  }


  private boolean allTrueViolationsFound() {
    return false;
  }

}
