package edu.fudan;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static edu.fudan.utils.CorrelationUtil.getDCScoreUniformMap;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;

/**
 * @author Lingfeng
 */
@Slf4j
public class CorrelationTest {

  private static String correlationByUserPath = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\correlation_matrix\\model_ltr_eval_hospital.csv";

  private static int dsIndex = 0;
  private static String headerPath;
  private static String cleanDataPath;
  private static String dirtyDataPath;
  private static String changesPath;
  private static String excludedLinesPath;
  private static String sampledDataPath;
  private static String fullDCsPath;
  private static String dcsPathForDCMiner;
  private static String evidencesPath;
  private static String topKDCsPath;
  private static String groundTruthDCsPath;
  private static String candidateDCsPath;
  private static String candidateTrueDCsPath;
  private static String excludedDCsPath;
  private static String csvResultPath;

  @Before
  public void setUp() throws Exception {
    UGDParams params = UGDRunner.buildParams(dsIndex);
    headerPath = params.headerPath;
    cleanDataPath = params.cleanDataPath;
    dirtyDataPath = params.dirtyDataPath;
    changesPath = params.changesPath;
    excludedLinesPath = params.excludedLinesPath;
    sampledDataPath = params.sampledDataPath;
    fullDCsPath = params.fullDCsPath;
    dcsPathForDCMiner = params.dcsPathForDCMiner;
    evidencesPath = params.evidencesPath;
    topKDCsPath = params.topKDCsPath;
    groundTruthDCsPath = params.groundTruthDCsPath;
    candidateDCsPath = params.candidateDCsPath;
    candidateTrueDCsPath = params.candidateTrueDCsPath;
    excludedDCsPath = params.excludedDCsPath;
    csvResultPath = params.csvResultPath;
  }

  @Test
  public void testReadColumnCorrScore() throws IOException {
    // 读取属性两两之间的相关性打分矩阵
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(correlationByUserPath);

    log.debug("ColumnsCorrScoreMap: {}", columnsCorrScoreMap.size());
    for (String key : columnsCorrScoreMap.keySet()) {
      log.debug("{}:{}", key, columnsCorrScoreMap.get(key));
    }
  }

  @Test
  public void testCalculateCorrScore() throws IOException {
    // 读取规则
    List<DenialConstraint> testDCs = DCLoader.load(headerPath, fullDCsPath);
    // 读取打分矩阵
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(correlationByUserPath);
    // 计算综合分数
    Map<DenialConstraint, Double> dcScoreUniformMap = getDCScoreUniformMap(
        testDCs, columnsCorrScoreMap, 2, 0.5);
    // 排序
    ArrayList<Entry<DenialConstraint, Double>> entries = new ArrayList<>(
        dcScoreUniformMap.entrySet());
    entries.sort(Comparator.comparingDouble((Entry<DenialConstraint, Double> entry) ->
        entry.getValue()).reversed());
    // 打印
    for (Entry<DenialConstraint, Double> entry : entries) {
      DenialConstraint dc = entry.getKey();
      log.debug("{}, {}", DCFormatUtil.convertDC2String(dc), entry.getValue());
    }
  }

}
