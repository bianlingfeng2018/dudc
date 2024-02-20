package edu.fudan;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.transformat.DCReader;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class HydraDetectionTest {

  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";

  @Test
  public void testReadDCsFromFile() throws DCMinderToolsException, IOException {
    String dcsFile = baseDir + "\\result_rules\\dcs_hospital.out";
    String dataFile = baseDir + "\\preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
    DenialConstraintSet dcs = new DCReader(dataFile, dcsFile).readDCsFromFile();
    for (DenialConstraint dc : dcs) {
      String dcStr = DCFormatUtil.convertDC2String(dc);
      log.debug(dcStr);
    }
  }

  @Test
  public void testDetectDCsViolations()
      throws IOException, InputGenerationException, InputIterationException, DCMinderToolsException {
    String dcsFile = baseDir + "\\result_rules\\dcs_hospital.out";
    String dataFile = baseDir + "\\preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
    DenialConstraintSet dcs = new DCReader(dataFile, dcsFile).readDCsFromFile();
    DCViolationSet vios = new HydraDetector(dataFile).detect(dcs);
    log.debug("DC violations size = {}", vios.getViosSet().size());
    log.debug("DC violation 0 = {}", vios.getViosSet().stream().findAny().orElse(null));
  }

}
