package edu.fudan;

import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.UGuideDiscovery;
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
  private final String headerPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_header.csv";
  private final String cleanData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital.csv";
  private final String dirtyData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty.csv";
  private final String sampledData = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
  private final String dcsPathForFCDC = baseDir + File.separator +
      "evidence_set\\dcs_fcdc_hospital.out";
  private final String evidencesPathForFCDC = baseDir + File.separator +
      "evidence_set\\evidence_set_fcdc_hospital.csv";
  private final String topKDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital.out";
  private final String groundTruthDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_ground.out";
  private final String candidateDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate.out";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanData,
        dirtyData,
        sampledData,
        dcsPathForFCDC,
        evidencesPathForFCDC,
        topKDCsPath,
        groundTruthDCsPath,
        candidateDCsPath,
        headerPath);
    ud.guidedDiscovery();
  }
}
