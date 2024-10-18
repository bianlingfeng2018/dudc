package edu.xxx;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.xxx.algorithms.DCLoader;
import edu.xxx.algorithms.DCViolation;
import edu.xxx.algorithms.DCViolationSet;
import edu.xxx.algorithms.HydraDetector;
import edu.xxx.transformat.DCFormatUtil;
import java.util.HashSet;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author XXX
 */
@Slf4j
public class HydraDetectionTest {

  String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";
  String dcsFile = baseDir + "\\result_rules\\dcs_hospital.out";
  String dataFile = baseDir + "\\preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
  String headerPath = baseDir + "\\preprocessed_data\\preprocessed_hospital_header.csv";

  @Test
  public void testLoadDC() {
    List<DenialConstraint> dcs = DCLoader.load(dataFile, dcsFile);
    for (DenialConstraint dc : dcs) {
      String dcStr = DCFormatUtil.convertDC2String(dc);
      log.debug("{}", dcStr);
    }
  }

  @Test
  public void testDetectDCViolationByDCPath() {
    DCViolationSet violationSet = new HydraDetector(dataFile, dcsFile, headerPath).detect();
    log.debug("ViolationSet = {}", violationSet.getViosSet().size());

    DCViolation example = violationSet.getViosSet().stream().findAny().orElse(null);
    log.debug("ViolationSet example = {}", example);
  }

  @Test
  public void testDetectDCViolationByDCSet() {
    List<DenialConstraint> dcs = DCLoader.load(dataFile, dcsFile);
    DCViolationSet violationSet = new HydraDetector(dataFile, new HashSet<>(dcs)).detect();
    log.debug("ViolationSet = {}", violationSet.getViosSet().size());

    DCViolation example = violationSet.getViosSet().stream().findAny().orElse(null);
    log.debug("ViolationSet example = {}", example);
  }

}
