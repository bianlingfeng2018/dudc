package edu.fudan;

import edu.fudan.utils.UGuideRunner;
import picocli.CommandLine;

/**
 * @author Lingfeng
 */
public class Main {

  public static void main(String[] args) {
    int exitCode = new CommandLine(new UGuideRunner()).execute(args);
    System.exit(exitCode);
  }
}
