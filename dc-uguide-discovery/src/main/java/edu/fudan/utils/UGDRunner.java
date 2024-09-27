package edu.fudan.utils;

import static edu.fudan.conf.DefaultConf.defCellQStrategy;
import static edu.fudan.conf.DefaultConf.defDCQStrategy;
import static edu.fudan.conf.DefaultConf.defTupleQStrategy;
import static edu.fudan.conf.DefaultConf.dynamicG1;
import static edu.fudan.conf.DefaultConf.useSample;
import static edu.fudan.conf.DefaultConf.maxDiscoveryRound;
import static edu.fudan.conf.DefaultConf.randomClusterS;
import static edu.fudan.conf.DefaultConf.repairErrors;
import static edu.fudan.conf.GlobalConf.baseDir;
import static edu.fudan.conf.GlobalConf.dsNames;

import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.CellQStrategy;
import edu.fudan.algorithms.uguide.DCQStrategy;
import edu.fudan.algorithms.uguide.DiscoveryAlgo;
import edu.fudan.algorithms.uguide.G1Strategy;
import edu.fudan.algorithms.uguide.SampleStrategy;
import edu.fudan.algorithms.uguide.TupleQStrategy;
import edu.fudan.algorithms.uguide.UpdateStrategy;
import java.io.File;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.Spec;

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

  @Option(names = {"-r", "--rounds"}, description = "Max rounds.")
  int maxRound = 1;

  @Option(names = {"-u", "--update"}, description = "Update: EXCLUDE, REPAIR.")
  UpdateStrategy updateStrategy = UpdateStrategy.EXCLUDE;

  @Option(names = {"-s", "--sample"}, description = "Sample: Random, EFFICIENT.")
  SampleStrategy sampleStrategy = SampleStrategy.RANDOM;

  @Option(names = {"-a", "--discovery"}, description = "Approximate DC discovery method.")
  DiscoveryAlgo discoveryAlgo = DiscoveryAlgo.HYDRA;

  @Option(names = {"-c", "--cell-question"}, description = "Cell question.")
  CellQStrategy cellQStrategy = CellQStrategy.RANDOM_CELL;

  @Option(names = {"-t", "--tuple-question"}, description = "Tuple question.")
  TupleQStrategy tupleQStrategy = TupleQStrategy.RANDOM_TUPLE;

  @Option(names = {"-d", "--dc-question"}, description = "DC question.")
  DCQStrategy dcQStrategy = DCQStrategy.RANDOM_DC;

  @Option(names = {"-g", "--g1"}, description = "G1 used for approximate DC discovery.")
  G1Strategy g1Strategy = G1Strategy.FIXED;


  @Override
  public Integer call() throws Exception {
    ParseResult pr = spec.commandLine().getParseResult();

    maxDiscoveryRound = maxRound;
    repairErrors = updateStrategy == UpdateStrategy.REPAIR;
    randomClusterS = sampleStrategy == SampleStrategy.RANDOM;
    useSample = (sampleStrategy != SampleStrategy.NONE);
    defCellQStrategy = cellQStrategy;
    defTupleQStrategy = tupleQStrategy;
    defDCQStrategy = dcQStrategy;
    dynamicG1 = g1Strategy == G1Strategy.DYNAMIC;

    printArgs();

    UGDParams params = buildParams(dataset);

    log.info("The base dir is {}", new File(baseDir).getAbsolutePath());
    log.info("Executing algorithms...");
    UGuideDiscovery ud = new UGuideDiscovery(params.cleanDataPath, params.changesPath,
        params.dirtyDataPath, params.dirtyDataUnrepairedPath, params.excludedLinesPath,
        params.sampledDataPath, params.fullDCsPath, params.dcsPathForDCMiner, params.evidencesPath,
        params.topKDCsPath, params.groundTruthDCsPath, params.candidateDCsPath,
        params.candidateTrueDCsPath, params.excludedDCsPath, params.headerPath,
        params.csvResultPath, params.correlationByUserPath);
    ud.guidedDiscovery();

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
    params.dirtyDataUnrepairedPath = baseDir + "/preprocessed_" + dsName + "_dirty_unrepaired.csv";
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
    params.csvResultPath = baseDir + "/eval_error_detect_" + dsName + ".csv";
    params.correlationByUserPath = baseDir + "/model_ltr_eval_" + dsName + ".csv";
    params.g1RangeResPath = baseDir + "/g1_range_result_" + dsName + ".csv";
    return params;
  }

  private void printArgs() {
    StringBuilder sb = new StringBuilder();
    for (OptionSpec option : spec.options()) {
//      String name = option.longestName();
//      System.out.printf("%s was specified: %s%n", name, pr.hasMatchedOption(option));
//      System.out.printf("%s=%s (-1 means this option was not matched on command line)%n",
//          name, pr.matchedOptionValue(name, -1));
//      System.out.printf("%s=%s (arg value or default)%n", name, option.getValue());
//      System.out.println();
      Object value = option.getValue();
      sb.append(option.longestName()).append("=").append(value).append(",");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    String s = sb.toString();
    log.info("Args:{}", s);
  }
}
