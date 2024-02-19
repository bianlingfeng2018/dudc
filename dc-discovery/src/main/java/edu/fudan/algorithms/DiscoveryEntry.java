package edu.fudan.algorithms;

import br.edu.utfpr.pena.fdcd.mockers.FDCDMocker;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * @author Lingfeng
 */
@Slf4j
public class DiscoveryEntry {

  public static void doDiscovery(String dataPath, String dcsOutputPath) {
    String[] args = new String[]{dataPath, "-o", dcsOutputPath};
    int exitCode = new CommandLine(new FDCDMocker()).execute(args);
    log.info("Discovery DCs exit code: {}", exitCode);
  }

}
