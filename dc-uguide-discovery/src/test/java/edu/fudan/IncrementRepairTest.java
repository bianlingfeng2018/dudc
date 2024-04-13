package edu.fudan;

import static edu.fudan.utils.DCUtil.printDCViolationsMap;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.BasicDCGenerator;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class IncrementRepairTest {

  String[] dsArray = {"hospital_total", "SPStock", "Tax", "flights"};
  int dsIndex = 1;
  String baseDir = "D:\\paper\\denial_constraint_incremental_repair\\experiment\\2024\\software";
  String dataPath = baseDir + "\\source" + "\\" + dsArray[dsIndex] + ".csv";
  String headerPath = baseDir + "\\source" + "\\" + dsArray[dsIndex] + "_header.csv";
  String dcTransPath = baseDir + "\\dc" + "\\" + dsArray[dsIndex] + "_trans.txt";

  @Test
  public void testDiscoveryDCsUsingBasicGenerator() {
//    String dataPath = "D:\\MyFile\\IdeaProjects\\incr\\source\\preprocessed_stock_origin.csv";  // BART用的干净数据
//    String dataPath = "D:\\paper\\denial_constraint_incremental_repair\\experiment\\2024\\software\\source\\Tax_small.csv";  // Tax的干净数据
    BasicDCGenerator generator = new BasicDCGenerator(dataPath, dcTransPath, headerPath);
    generator.setExcludeDCs(new HashSet<>());
    generator.setErrorThreshold(0.0);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();
    log.info("DCs size={}", dcs.size());
  }

  @Test
  public void testDetectDCViolationUsingHydra() {
//    String dataPath = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\stock_target_dirty_for_holo.csv";  // 原始干净+数据注入了错误的数据 验证hydra和incr的冲突数量一样
    String dataPath = "D:\\MyFile\\IdeaProjects\\incr\\source\\preprocessed_stock_origin.csv";  // 原始+增量的干净数据
    String dcTransPath = "D:\\MyFile\\gitee\\dc_miner\\data\\result_rules\\dcs_stock.out";  // 选择的规则
    log.debug("datasetPath={}", dataPath);
    log.debug("headerPath={}", headerPath);
    log.debug("dcPath={}", dcTransPath);
    HydraDetector detector = new HydraDetector(dataPath, dcTransPath, headerPath);
    DCViolationSet violationSet = detector.detect();
    log.info("violationSet={}", violationSet.size());

    printDCViolationsMap(violationSet);
  }


}
