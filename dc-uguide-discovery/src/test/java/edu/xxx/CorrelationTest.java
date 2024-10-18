package edu.xxx;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.xxx.algorithms.DCLoader;
import edu.xxx.transformat.DCFormatUtil;
import edu.xxx.utils.UGDParams;
import edu.xxx.utils.UGDRunner;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static edu.xxx.utils.CorrelationUtil.getDCScoreUniformMap;
import static edu.xxx.utils.CorrelationUtil.readColumnCorrScoreMap;

/**
 * @author XXX
 */
@Slf4j
public class CorrelationTest {

  /**
   * Parameters used in User-guided-DC-Detection process.
   */
  UGDParams params;

  @Before
  public void setUp() throws Exception {
    int dsIndex = 0;
    params = UGDRunner.buildParams(dsIndex);
  }

  @Test
  public void testReadColumnCorrScore() throws IOException {
    // 读取属性两两之间的相关性打分矩阵
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(params.correlationByUserPath);

    log.debug("ColumnsCorrScoreMap: {}", columnsCorrScoreMap.size());
    for (String key : columnsCorrScoreMap.keySet()) {
      log.debug("{}:{}", key, columnsCorrScoreMap.get(key));
    }
  }

  @Test
  public void testCalculateCorrScore() throws IOException {
    // 读取规则
    List<DenialConstraint> testDCs = DCLoader.load(params.headerPath, params.fullDCsPath);
    // 读取打分矩阵
    Map<String, Double> columnsCorrScoreMap = readColumnCorrScoreMap(params.correlationByUserPath);
    // 计算综合分数
    Map<DenialConstraint, Double> dcScoreUniformMap = getDCScoreUniformMap(
        new HashSet<>(testDCs), columnsCorrScoreMap, 2, 0.5);
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
