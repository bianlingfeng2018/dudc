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
  public static int maxDiscoveryRound = 50;
  public static int maxCellQuestionBudget = 100;
  public static int maxTupleQuestionBudget = 100;
  public static int maxDCQuestionBudget = 5;
  // Sample
  public static int topKOfCluster = 5;
  public static int maxInCluster = 3;
  // ADC
//  public static double defaultErrorThreshold = 0.001;
  public static double defaultErrorThreshold = 0.001;
  // CellQ TupleQ DCsQ
  public static Integer[] questionsConf = new Integer[]{1, 1, 1};
}
