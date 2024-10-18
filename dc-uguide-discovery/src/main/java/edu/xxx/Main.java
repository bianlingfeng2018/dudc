package edu.xxx;

import edu.xxx.utils.UGDRunner;
import picocli.CommandLine;

/**
 * @author XXX
 */
public class Main {

  public static void main(String[] args) {
    int exitCode = new CommandLine(new UGDRunner()).execute(args);
    System.exit(exitCode);
  }
}
