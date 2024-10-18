package edu.xxx.conf;

public class GlobalConf {
  /**
   * All datasets used in this experiment.
   */
  public static String[] dsNames = {"hospital", "stock", "tax", "flights", "hospital2", "beers", "rayyan", "airport", "movie", "adult"};
  /**
   * The base directory.
   */
//  public static String baseDir = "/Users/benryoubon/IdeaProjects/dc_tools/data";
  // IDE测试用这个绝对路径
  public static String baseDir = "D:/paper/dc_user_guided_detection/experiment/data";
  // 打包后用这个相对路径
//  public static String baseDir = "./data";
//  public static String baseDir = "/Users/benryoubon/Desktop/paper/dc_user_guided_detection/experiment/data";
}
