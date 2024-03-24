package edu.fudan;

import static edu.fudan.UGuideDiscoveryTest.universalDCsPath;
import static edu.fudan.UGuideDiscoveryTest.headerPath;
import static edu.fudan.utils.CorrelationUtil.getDCScoreUniformMap;
import static edu.fudan.utils.CorrelationUtil.readColumnCorrScoreMap;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.transformat.DCFormatUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class CorrelationTest {

  public static String correlationByUserPath = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\correlation_matrix\\model_ltr_eval_hospital.csv";

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
    List<DenialConstraint> testDCs = DCLoader.load(headerPath, universalDCsPath);
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
