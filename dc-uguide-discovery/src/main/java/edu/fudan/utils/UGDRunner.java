package edu.fudan.utils;

import edu.fudan.algorithms.UGuideDiscovery;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

import java.io.File;
import java.util.concurrent.Callable;

import static edu.fudan.utils.GlobalConf.baseDir;
import static edu.fudan.utils.GlobalConf.dsNames;

/**
 * @author Lingfeng
 */
@Slf4j
@Command(name = "UGD", mixinStandardHelpOptions = true, version = "UGD 1.0")
public class UGDRunner implements Callable<Integer> {

  @Spec
  CommandLine.Model.CommandSpec spec;

  @Option(names = {"-i", "--dataset"}, description = "The dataset index.")
  int dataset = 0;

  @Option(names = {"-u", "--update"}, description = "Update: EXCLUDE, REPAIR.")
  Update update = Update.EXCLUDE;

  @Option(names = {"-s", "--sample"}, description = "Sample: Random, Efficient.")
  Sample sample = Sample.RANDOM;

  @Option(names = {"-a", "--discovery"}, description = "Approximate DC discovery method.")
  Discovery discovery = Discovery.HYDRA;

  @Option(names = {"-c", "--cell-question"}, description = "Cell question.")
  CellQ cellQ = CellQ.RANDOM;

  @Option(names = {"-t", "--tuple-question"}, description = "Tuple question.")
  TupleQ tupleQ = TupleQ.RANDOM;

  @Option(names = {"-d", "--dc-question"}, description = "DC question.")
  DCQ dcQ = DCQ.RANDOM;

  @Option(names = {"-g", "--g1"}, description = "G1 used for approximate DC discovery.")
  G1 g1 = G1.FIXED;

  enum Update {
    EXCLUDE, REPAIR
  }

  enum Sample {
    RANDOM
  }

  enum Discovery {
    HYDRA
  }

  enum CellQ {
    RANDOM
  }

  enum TupleQ {
    RANDOM
  }

  enum DCQ {
    RANDOM
  }

  enum G1 {
    FIXED, DYNAMIC
  }


  @Override
  public Integer call() throws Exception {
    ParseResult pr = spec.commandLine().getParseResult();

    StringBuilder sb = new StringBuilder();
    for (OptionSpec option : spec.options()) {
//      String name = option.longestName();
//      System.out.printf("%s was specified: %s%n", name, pr.hasMatchedOption(option));
//      System.out.printf("%s=%s (-1 means this option was not matched on command line)%n",
//          name, pr.matchedOptionValue(name, -1));
//      System.out.printf("%s=%s (arg value or default)%n", name, option.getValue());
//      System.out.println();
      Object value = option.getValue();
      sb.append(option.longestName())
          .append("=")
          .append(value)
          .append(",");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    log.info("Args:{}", sb.toString());

    UGDParams params = buildParams(dataset);
    String cleanDataPath = "";

    log.info("The base dir is {}", new File(baseDir).getAbsolutePath());
    log.info("Executing algorithms...");
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath,
        params.changesPath,
        params.dirtyDataPath,
        params.excludedLinesPath,
        params.sampledDataPath,
        params.fullDCsPath,
        params.dcsPathForDCMiner,
        params.evidencesPath,
        params.topKDCsPath,
        params.groundTruthDCsPath,
        params.candidateDCsPath,
        params.candidateTrueDCsPath,
        params.excludedDCsPath,
        params.headerPath,
        params.csvResultPath,
        params.correlationByUserPath);
//    ud.guidedDiscovery();

    log.info("Finished algorithms.");
    return 0;
  }

  public static UGDParams buildParams(int dsIndex) {
    UGDParams params = new UGDParams();
    String dsName = dsNames[dsIndex];
    params.headerPath = baseDir + "/preprocessed_" + dsName + "_header.csv";
    params.changesPath = baseDir + "/preprocessed_" + dsName + "_changes.csv";
    params.cleanDataPath = baseDir + "/preprocessed_" + dsName + ".csv";
    params.dirtyDataPath = baseDir + "/preprocessed_" + dsName + "_dirty.csv";
    params.excludedLinesPath = baseDir + "/preprocessed_" + dsName + "_dirty_excluded.csv";
    params.sampledDataPath = baseDir + "/preprocessed_" + dsName + "_dirty_sample.csv";
    params.groundTruthDCsPath = baseDir + "/dcs_ground_" + dsName + ".txt";
    params.fullDCsPath = baseDir + "/dcs_full_" + dsName + ".txt";
    params.topKDCsPath = baseDir + "/dcs_top_k_" + dsName + ".txt";
    params.evidencesPath = baseDir + "/evidences_" + dsName + ".txt";
    params.dcsPathForDCMiner = baseDir + "/dcs_dc_miner_top_5_" + dsName + ".txt";
    params.candidateDCsPath = baseDir + "/dcs_candidate_" + dsName + ".txt";
    params.candidateTrueDCsPath = baseDir + "/dcs_candidate_true_" + dsName + ".txt";
    params.excludedDCsPath = baseDir + "/dcs_excluded_" + dsName + ".txt";
    params.csvResultPath = baseDir + "/eval_error_detect_" + dsName + ".txt";
    params.correlationByUserPath = baseDir + "/model_ltr_eval_" + dsName + ".csv";
    return params;
  }
}
