package edu.fudan;

import ch.javasoft.bitset.search.NTreeSearch;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.utils.DCUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
  private final String cleanDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital.csv";
  private final String dirtyDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty.csv";
  private final String dataDirtyLinesPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty_excluded.csv";
  private final String sampledDataPath = baseDir + File.separator +
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
  private final String trueDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate_true.out";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath,
        dirtyDataPath,
        dataDirtyLinesPath,
        sampledDataPath,
        dcsPathForFCDC,
        evidencesPathForFCDC,
        topKDCsPath,
        groundTruthDCsPath,
        candidateDCsPath,
        trueDCsPath,
        headerPath);
    ud.guidedDiscovery();
  }

  @Test
  public void testGenGroundTruthDCs() {
    DiscoveryEntry.doDiscovery(cleanDataPath, dcsPathForFCDC);
  }

  @Test
  public void testGenTopKDCs() throws IOException {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(10, dcsPathForFCDC, headerPath, null);
    DCUtil.persistTopKDCs(topKDCs, topKDCsPath);
  }

  @Test
  public void testImply() {
    List<DenialConstraint> dcs1 = DCLoader.load(headerPath, topKDCsPath);
    List<DenialConstraint> gtDCs1 = DCLoader.load(headerPath, groundTruthDCsPath);

    NTreeSearch gtTree1 = new NTreeSearch();
    for (DenialConstraint dc : gtDCs1) {
      gtTree1.add(PredicateSetFactory.create(dc.getPredicateSet()).getBitset());
    }

    assert dcs1.get(0).isImpliedBy(gtTree1);
//    assert gtDCs1.contains(dcs1.get(0));

  }

  @Test
  public void testDCsEquality() {
    // TODO: 测试candidateDCs是否等价gtDCs，即是否能找出所有真冲突，其数量是否相同？
    //  另外，验证其注入错误的Cell是都都能发现？

  }
}
