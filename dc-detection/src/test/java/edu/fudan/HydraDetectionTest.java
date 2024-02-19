package edu.fudan;

import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.PredicateBuilder;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.transformat.DCFileReader;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class HydraDetectionTest {
  private Boolean noCrossColumn = Boolean.TRUE;
  private double minimumSharedValue = 0.30d;
  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";
  @Test
  public void testDetectDCsViolations()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
//    String dcsFile = baseDir + File.separator + "result_rules" + File.separator + "dcminer_5_hospital.csv";
    String dcsFile = baseDir + File.separator + "result_rules" + File.separator + "dcs_fastdc_5_hospital_1.out";
//    String dataFile = baseDir + File.separator + "preprocessed_data" + File.separator + "preprocessed_tax.csv";
    String dataFile = baseDir + File.separator + "preprocessed_data" + File.separator + "preprocessed_hospital_dirty_sample.csv";
    DenialConstraintSet dcs = new DCFileReader(dataFile, dcsFile).readDCsFromFile();
    DCViolationSet vios = new HydraDetector(dataFile).detect(dcs);
    log.debug("DC violations size = {}", vios.getViosSet().size());
    log.debug("DC violation 0 = {}", vios.getViosSet().stream().findAny().orElse(null));
  }

}
