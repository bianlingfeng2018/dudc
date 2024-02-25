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
  // Sample
  public static int topKOfCluster = 2;
  public static int maxInCluster = 2;
}
