package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;

import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.paritions.LinePair;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DataUtil;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Lingfeng
 */
public class Evaluation {

  /**
   * Clean data
   */
  private final CleanData cleanData;

  /**
   * Dirty data which is the data after injecting errors using BART
   */
  private final DirtyData dirtyData;

  /**
   * Path of ground truth DCs
   */
  private final String groundTruthDCsPath;
  /**
   * Ground truth DCs which hold on clean data. Violations wrt ground truth DCs are true
   * violations.
   */
  private final DenialConstraintSet groundTruthDCs = new DenialConstraintSet();

  /**
   * Path to output the final candidate DCs
   */
  private final String candidateDCsPath;

  /**
   * Candidate DCs which are finally used to detect violations.
   */
  private final DenialConstraintSet candidateDCs = new DenialConstraintSet();

  /**
   * Candidate Violations which are used to estimate the confidence of correspond DCs.
   */
  private final DCViolationSet candidateViolations = new DCViolationSet();

  public Evaluation(CleanData cleanData, DirtyData dirtyData, String groundTruthDCsPath,
      String candidateDCsPath) {
    this.cleanData = cleanData;
    this.dirtyData = dirtyData;
    this.groundTruthDCsPath = groundTruthDCsPath;
    this.candidateDCsPath = candidateDCsPath;
  }

  public void setUp()
      throws DCMinderToolsException, IOException, InputGenerationException, InputIterationException {
    String dataPath = cleanData.getDataPath();
    String headerPath = cleanData.getHeaderPath();

    // 发现groundTruth规则
//    DiscoveryEntry.doDiscovery(dataPath, groundTruthDCsPath);
    // 读取groundTruth规则
    List<DenialConstraint> dcList = DCLoader.load(headerPath, groundTruthDCsPath);
    // 检测冲突，结果应该为0
    DCViolationSet vios = new HydraDetector(dataPath, groundTruthDCsPath).detect();
    int size = vios.size();
    if (size != 0) {
      throw new RuntimeException("Error discovery of DCs on clean data");
    }

    // 设定GroundTruth规则
    for (DenialConstraint dc : dcList) {
      this.groundTruthDCs.add(dc);
    }
  }

  public void update(Set<DenialConstraint> candidateDCs,
      Set<DenialConstraint> falseDCs,
      Set<DCViolation> candidateViolations,
      Set<DCViolation> falseViolations,
      Set<Integer> dirtyLines) {
    if (candidateDCs != null) {
      // 增加候选规则
      for (DenialConstraint dc : candidateDCs) {
        this.candidateDCs.add(dc);
      }
    }
    if (falseDCs != null) {
      Map<DenialConstraint, Set<DCViolation>> map = DataUtil.getDCViosMapFromVios(
          this.candidateViolations.getViosSet());
      // 减少假阳性规则
      for (DenialConstraint dc : falseDCs) {
        this.candidateDCs.remove(dc);
        Set<DCViolation> dcVios = map.get(dc);
        for (DCViolation dcVio : dcVios) {
          // 删除和DC相关的冲突
          this.candidateViolations.getViosSet().remove(dcVio);
          // 增加需要排除的脏数据
          LinePair linePair = dcVio.getLinePair();
          int line1 = linePair.getLine1();
          int line2 = linePair.getLine2();
          this.dirtyData.getDirtyLines().add(line1);
          this.dirtyData.getDirtyLines().add(line2);
        }
      }
    }
    if (candidateViolations != null) {
      // 增加相关的冲突
      for (DCViolation vio : candidateViolations) {
        this.candidateViolations.add(vio);
      }
    }
    if (falseViolations != null) {
      // 删除相关的冲突
      for (DCViolation vio : falseViolations) {
        this.candidateViolations.remove(vio);
      }
    }
    if (dirtyLines != null) {
      // 增加需要排除的脏数据
      this.dirtyData.getDirtyLines().addAll(dirtyLines);
    }
  }

  public EvalResult evaluate() {
    // 评价真冲突和假冲突个数
    Set<DCViolation> vios = candidateViolations.getViosSet();
    int totalVios = vios.size();
    int trueVios = 0;
    Map<DenialConstraint, Set<DCViolation>> viosMap = DataUtil.getDCViosMapFromVios(vios);
    Map<String, Integer> dcStrVioSizeMap = Maps.newHashMap();
    for (Entry<DenialConstraint, Set<DCViolation>> entry : viosMap.entrySet()) {
      DenialConstraint dc = entry.getKey();
      Set<DCViolation> dcVios = entry.getValue();
      // 模拟用户交互，判断规则是否是真规则，如果是，那么其对应的冲突就是真冲突
      if (groundTruthDCs.contains(dc)) {
        int size = dcVios.size();
        dcStrVioSizeMap.put(DCFormatUtil.convertDC2String(dc), size);
        trueVios += size;
      }
    }

    // TODO: 真DC/所有DC的评估
    EvalResult result = new EvalResult();
    result.setTrueVios(trueVios);
    result.setTotalVios(totalVios);
    result.setDcStrVioSizeMap(dcStrVioSizeMap);
    return result;
  }

  public boolean isTrueViolation(DenialConstraint dc, LinePair linePair) {
    boolean contains = groundTruthDCs.contains(dc);
    return contains;
  }

  public boolean allTrueViolationsFound() {
    for (DenialConstraint gtDC : groundTruthDCs) {
      if (!candidateDCs.contains(gtDC)) {
        return false;
      }
    }
    return true;
  }

  public Set<DCViolation> genCellQuestions(int maxQueryBudget) {
    List<DCViolation> chosenVios = getRandomElements(candidateViolations.getViosSet(),
        maxQueryBudget);
    return new HashSet<>(chosenVios);
  }

  @Getter
  @Setter
  public class EvalResult {
    private int trueVios = 0;
    private int totalVios = 0;
    private Map<String, Integer> dcStrVioSizeMap = Maps.newHashMap();
  }
}
