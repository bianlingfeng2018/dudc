package edu.fudan.conf;

import edu.fudan.algorithms.uguide.CellQStrategy;
import edu.fudan.algorithms.uguide.DCQStrategy;
import edu.fudan.algorithms.uguide.TupleQStrategy;

/**
 * @author Lingfeng
 */
public class DefaultConf {

  /**
   * Top-k DCs discovered. Set k = Integer.MAX_VALUE if we want all discovered DCs.
   */
  public static int topK = Integer.MAX_VALUE;
  public static String defaultTable = "xxx";
  public static Boolean noCrossColumn = Boolean.TRUE;
  public static double minimumSharedValue = 0.30d;
  // Sample
  /**
   * Min top-k clusters. 2 ensure > and < exist.
   */
  public static int minTopKOfCluster = 2;
  /**
   * Min number in cluster. 2 ensure = exists.
   */
  public static int minNumInCluster = 2;
  /**
   * Top-k clusters. Default is 5.
   */
  public static int topKOfCluster = 5;
  /**
   * Number in cluster. Default is 3.
   */
  public static int numInCluster = 3;
  /**
   * If we use add-counter-example-sample, add at least 1 counter-example tuple pair (w.r.t the
   * false DC and not in the excluded tuples) to sampled result.
   */
  public static boolean addCounterExampleS = true;
  /**
   * If we use random-cluster-sample, random sample clusters.
   */
  public static boolean randomClusterS = false;
  // Sample
  public static boolean useSample = false;
  // ADC
  public static int maxDCLen = 4;
  public static double defaultErrorThreshold = 0.001;
  public static boolean dynamicG1 = true;
  public static boolean calG1Snapshot = false;
  // 0.87是0.001~0.0001分成50次降低得来的
  public static double decreaseFactor = 0.87;
  // Questions(CellQ TupleQ DCsQ)
  public static int maxDiscoveryRound = 50;
  public static Integer[] questionsConf = new Integer[]{1, 1, 1};
  public static boolean repairErrors = true;
  public static boolean debugDCVioMap = false;
  // CellQ
  public static int maxCellQuestionBudget = 100;
  public static CellQStrategy defCellQStrategy = CellQStrategy.VIO_AND_CONF;
  public static double delta = 0.1;
  public static boolean canBreakEarly = false;
  public static double excludeLinePercent = 0.1;
  public static double trueDCConfThreshold = 0.5;
  // TupleQ
  public static int maxTupleQuestionBudget = 100;
  public static TupleQStrategy defTupleQStrategy = TupleQStrategy.VIOLATIONS_PRIOR;
  // DCsQ
  public static int maxDCQuestionBudget = 5;
  public static int minLenOfDC = 2;
  public static double succinctFactor = 0.5;
  public static DCQStrategy defDCQStrategy = DCQStrategy.SUC_COR_VIOS;
  // DCMiner
  // TODO: 慎用static final关键字，因为修改后需要重新mvn clean一下才生效
  public static String dcGeneratorConf = "Basic";  // Basic DCMiner
  public static String condaScript = "C:\\Users\\66413\\anaconda3\\Scripts\\conda";
  public static String trainScript = "D:\\MyFile\\gitee\\dc_miner\\notebooks\\run_train.py";
  public static String predictScript = "D:\\MyFile\\gitee\\dc_miner\\notebooks\\run_play.py";
  public static String activeScript = "C:\\Users\\66413\\anaconda3\\Scripts\\activate.bat";
  public static String myScriptDir = "D:\\MyFile\\gitee\\dc_miner\\dc_miner";
  public static String env = "py39-pytorch2";
  public static String sharedArgs = "--train_version t_2024_3_4 --gpu_index 0 --k_i 0 --dataset_i 0 --use_sample 0 --BATCH_SIZE 32 --LR 0.0001 --num_episodes 2000 --buffer_size 10000 --factor_suc 0.5 --factor_div 0.5 --explore_eps_decay 0";
  public static String trainArgs = "--use_pretrain 1 --show_process 0";
  public static String predictArgs = "--ep_range 100_99";




  public static String getConfStr() {
    String s = String.format(
        "UseSample=%s,"
            + "\nMaxDiscoveryRound=%s, MaxCellQuestionBudget=%s, MaxTupleQuestionBudget=%s, MaxDCQuestionBudget=%s, QuestionsConf=[%s;%s;%s]"
            + "\nMinTopKOfCluster=%s, MinNumInCluster=%s, TopKOfCluster=%s, MaxInCluster=%s, AddCounterExampleS=%s, RandomClusterS=%s"
            + "\nDefaultErrorThreshold=%s, DynamicG1=%s, TopK=%s, "
            + "\nRepairExcluded=%s, TrueDCConfThreshold=%s, CellQStrategy=%s, TupleQStrategy=%s, DCsQStrategy=%s"
            + "\nDelta=%s, MinLenOfDC=%s, SuccinctFactor=%s\n",
        useSample,
        maxDiscoveryRound, maxCellQuestionBudget, maxTupleQuestionBudget, maxDCQuestionBudget,
        questionsConf[0], questionsConf[1], questionsConf[2],
        minTopKOfCluster, minNumInCluster, topKOfCluster, numInCluster, addCounterExampleS,
        randomClusterS,
        defaultErrorThreshold, dynamicG1, topK,
        repairErrors, trueDCConfThreshold, defCellQStrategy, defTupleQStrategy, defDCQStrategy,
        delta, minLenOfDC, succinctFactor);
    return s;
  }
}
