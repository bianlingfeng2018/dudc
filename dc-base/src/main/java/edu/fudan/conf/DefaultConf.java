package edu.fudan.conf;

/**
 * @author Lingfeng
 */
public class DefaultConf {

  public static Boolean noCrossColumn = Boolean.TRUE;
  public static double minimumSharedValue = 0.30d;
  public static int topK = 5;
  public static String defaultTable = "xxx";
  // Loop
  public static int maxDiscoveryRound = 20;
  public static int maxCellQuestionBudget = 100;
  public static int maxTupleQuestionBudget = 100;
  public static int maxDCQuestionBudget = 5;
  // Sample
  public static int topKOfCluster = 5;
  public static int maxInCluster = 3;
  // ADC
  public static double defaultErrorThreshold = 0.001;
//  public static double defaultErrorThreshold = 0;
  // CellQ TupleQ DCsQ
  public static Integer[] questionsConf = new Integer[]{1, 0, 0};
  public static boolean debugDCVioMap = false;
  // DCMiner
  // TODO: 慎用static final关键字，因为修改后需要重新mvn clean一下才生效
  public static String dcGenerator = "Basic";  // Basic DCMiner
  public static String condaScript = "C:\\Users\\66413\\anaconda3\\Scripts\\conda";
  public static String trainScript = "D:\\MyFile\\gitee\\dc_miner\\notebooks\\run_train.py";
  public static String predictScript = "D:\\MyFile\\gitee\\dc_miner\\notebooks\\run_play.py";
  public static String activeScript = "C:\\Users\\66413\\anaconda3\\Scripts\\activate.bat";
  public static String myScriptDir = "D:\\MyFile\\gitee\\dc_miner\\dc_miner";
  public static String env = "py39-pytorch2";
  public static String sharedArgs = "--train_version t_2024_3_4 --gpu_index 0 --k_i 0 --dataset_i 0 --use_sample 0 --BATCH_SIZE 32 --LR 0.0001 --num_episodes 2000 --buffer_size 10000 --factor_suc 0.5 --factor_div 0.5 --explore_eps_decay 0";
  public static String trainArgs = "--use_pretrain 1 --show_process 0";
  public static String predictArgs = "--ep_range 100_99";
}
