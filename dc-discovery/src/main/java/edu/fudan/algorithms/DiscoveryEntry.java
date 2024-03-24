package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.minimumSharedValue;
import static edu.fudan.conf.DefaultConf.noCrossColumn;

import br.edu.utfpr.pena.fdcd.mockers.FDCDMocker;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.algorithms.dcfinder.DCFinder;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.predicates.PredicateBuilder;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
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
    log.debug("Discovery DCs exit code: {}", exitCode);
  }

  public static DenialConstraintSet discoveryDCsDCFinder(String inputDataPath,
      double errorThreshold) {
    return discoveryDCsDCFinder(inputDataPath, errorThreshold, null);
  }

  public static DenialConstraintSet discoveryDCsDCFinder(String inputDataPath,
      double errorThreshold, String evidenceFile) {
    RelationalInput ri = null;
    Input input;
    try {
      ri = new DefaultFileInputGenerator(new File(inputDataPath)).generateNewCopy();
      input = new Input(ri);
    } catch (InputGenerationException | FileNotFoundException | InputIterationException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (ri != null) {
          ri.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    PredicateBuilder predicatesSpace = new PredicateBuilder(input, noCrossColumn,
        minimumSharedValue);
    log.info("Size of the predicate space:" + predicatesSpace.getPredicates().size());

    DenialConstraintSet dcs = new DCFinder().run(input, predicatesSpace, errorThreshold,
        evidenceFile);
    return dcs;
  }

}
