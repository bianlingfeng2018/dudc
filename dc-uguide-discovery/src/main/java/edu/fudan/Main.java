package edu.fudan;

import edu.fudan.utils.UGDRunner;
import picocli.CommandLine;

/**
 * @author Lingfeng
 */
public class Main {

  public static void main(String[] args) {
    int exitCode = new CommandLine(new UGDRunner()).execute(args);
    System.exit(exitCode);
  }
}
