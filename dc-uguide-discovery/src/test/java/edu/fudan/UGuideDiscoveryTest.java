package edu.fudan;

import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.exceptions.UGuideDiscoveryException;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscoveryTest {

  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";
  private final String cleanData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital.csv";
  private final String dirtyData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty.csv";
  private final String sampledData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
  private final String dcsPath = baseDir + File.separator +
      "result_rules\\dcs_fcdc_hospital.out";
  private final String evidencesPath = baseDir + File.separator +
      "evidence_set\\evidence_set_fcdc_hospital.csv";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, UGuideDiscoveryException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanData, dirtyData, sampledData, dcsPath,
        evidencesPath);
    ud.guidedDiscovery();
  }
}
