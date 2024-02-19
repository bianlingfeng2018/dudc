package edu.fudan;

import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.utils.CmdParamsReceiver;
import java.io.FileNotFoundException;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;

/**
 * @author Lingfeng
 */
@Slf4j
public class Main {

  public static void main(String[] args)
      throws InputGenerationException, FileNotFoundException, InputIterationException {
    CmdParamsReceiver params = new CmdParamsReceiver();
    new CommandLine(params).parseArgs(args);
    log.info("CmdParams : {}", params.toString());

    if (params.helpRequested) {
      System.out.println("DC Detection Tool usage: TBD...");
      return;
    }
    int dsId = params.dsId;
    String dcPath = params.dcPath;

    log.info("Detect violations, DCs={}, dataset={}", dcPath, dsId);
    HydraDetector detector = new HydraDetector(null);
    log.info("Test detector: {}", detector);
  }


}
