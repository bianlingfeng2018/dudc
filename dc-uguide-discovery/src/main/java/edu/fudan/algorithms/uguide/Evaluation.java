package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.DCMinderToolsException;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.utils.DataUtil;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
   * Dirty data
   */
  private final DirtyData dirtyData;

  /**
   * Path of ground truth DCs
   */
  private final String groundTruthDCsPath;

  /**
   * Path to output the final candidate DCs
   */
  @Getter
  private final String candidateDCsPath;

  /**
   * Path to output the final candidate DCs
   */
  @Getter
  private final String trueDCsPath;

  /**
   * Total violations wrt ground truth DCs.
   */
  private Set<DCViolation> groundTruthViolations = Sets.newHashSet();

  /**
   * True violations.
   */
  private final Set<DCViolation> trueViolations = Sets.newHashSet();

  /**
   * Candidate violations.
   */
  private final Set<DCViolation> candidateViolations = Sets.newHashSet();

  /**
   * Ground truth DCs which hold on clean data. Violations wrt ground truth DCs are true
   * violations.
   */
  private final Set<DenialConstraint> groundTruthDCs = Sets.newHashSet();
  private final NTreeSearch gtTree = new NTreeSearch();

  /**
   * Ture DCs(part of candidate DCs)
   */
  @Getter
  private final Set<DenialConstraint> trueDCs = Sets.newHashSet();

  /**
   * Candidate DCs. It can be updated individually or when candidate violations change.
   */
  @Getter
  private final Set<DenialConstraint> candidateDCs = Sets.newHashSet();

  /**
   * Visited DCs
   */
  @Getter
  private final Set<DenialConstraint> visitedDCs = Sets.newHashSet();

  // Current round state
  /**
   * Current round DCs. Choose DC questions from them.
   */
  @Getter
  private final Set<DenialConstraint> currDCs = Sets.newHashSet();
  /**
   * Current round violations. Choose violation question from them.
   */
  private final Set<DCViolation> currVios = Sets.newHashSet();

  /**
   * Line indices which are dirty. They should be excluded from dirty data to help discover ground
   * truth DCs.
   */
  @Getter
  private final Set<Integer> dirtyLines = Sets.newHashSet();

  public Evaluation(CleanData cleanData, DirtyData dirtyData, String groundTruthDCsPath,
      String candidateDCsPath, String trueDCsPath) {
    this.cleanData = cleanData;
    this.dirtyData = dirtyData;
    this.groundTruthDCsPath = groundTruthDCsPath;
    this.candidateDCsPath = candidateDCsPath;
    this.trueDCsPath = trueDCsPath;
  }

  public void setUp()
      throws DCMinderToolsException, IOException, InputGenerationException, InputIterationException {
    String cleanDataPath = this.cleanData.getDataPath();
    String dirtyDataPath = this.dirtyData.getDataPath();
    String headerPath = this.cleanData.getHeaderPath();

    // 发现groundTruth规则
//    DiscoveryEntry.doDiscovery(dataPath, groundTruthDCsPath);
    // 读取groundTruth规则
    List<DenialConstraint> dcList = DCLoader.load(headerPath, this.groundTruthDCsPath);
    // 检测冲突，结果应该为0
    DCViolationSet viosOnClean = new HydraDetector(cleanDataPath, this.groundTruthDCsPath).detect();
    int sizeClean = viosOnClean.size();
    if (sizeClean != 0) {
      throw new RuntimeException("Error discovery of DCs on clean data");
    }
    // 检测冲突，设定GroundTruth规则应当在脏数据集上发现的冲突数量
    DCViolationSet viosOnDirty = new HydraDetector(dirtyDataPath, this.groundTruthDCsPath).detect();
    int sizeDirty = viosOnDirty.size();
    if (sizeDirty == 0) {
      throw new RuntimeException("Error discovery of DCs on dirty data");
    }
    // 设定GroundTruth
    this.groundTruthViolations = viosOnDirty.getViosSet();
    this.groundTruthDCs.addAll(dcList);
    for (DenialConstraint gtDC : groundTruthDCs) {
      gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    }
  }

  public void update(Set<DenialConstraint> candidateDCs,
      Set<DenialConstraint> falseDCs,
      Set<DCViolation> candidateViolations,
      Set<DCViolation> falseViolations,
      Set<Integer> dirtyLines) {
    if (candidateDCs != null) {
      // 记录当前状态
      this.currDCs.clear();
      this.currDCs.addAll(candidateDCs);
      // 增加候选规则
      this.candidateDCs.addAll(candidateDCs);
      // 记录已经访问过的DC
      this.visitedDCs.addAll(candidateDCs);
    }
    if (falseDCs != null) {
      Map<DenialConstraint, Set<DCViolation>> map = DataUtil.getDCViosMapFromVios(
          this.candidateViolations);
      // 减少假阳性规则
      for (DenialConstraint falseDC : falseDCs) {
        this.candidateDCs.remove(falseDC);
        Set<DCViolation> vios = map.get(falseDC);
        for (DCViolation vio : vios) {
          // 删除和DC相关的冲突
          this.candidateViolations.remove(vio);
          // TODO: 增加需要排除的脏数据，FalseDC不一定对应的是脏数据，有可能是缺少反例，需要进一步判断
//          excludeDirtyLines(vio);
        }
      }
    }
    if (candidateViolations != null) {
      // 记录当前状态
      this.currVios.clear();
      this.currVios.addAll(candidateViolations);
      // 增加相关的冲突
      this.candidateViolations.addAll(candidateViolations);
    }
    if (falseViolations != null) {
      // 删除相关的冲突
      for (DCViolation vio : falseViolations) {
        this.candidateViolations.remove(vio);
      }
    }
    if (dirtyLines != null) {
      // 增加需要排除的脏数据
      this.dirtyLines.addAll(dirtyLines);
    }
  }

  public EvalResult evaluate() throws DCMinderToolsException {
    EvalResult result = new EvalResult();
    // 评价最终的候选规则及其关联的冲突个数
    Map<DenialConstraint, Integer> candiDCViosMap = Maps.newHashMap();
    // 评价真规则和假规则个数
    for (DenialConstraint candiDC : this.candidateDCs) {
      candiDCViosMap.put(candiDC, 0);
      if (isTrueDC(candiDC)) {
        this.trueDCs.add(candiDC);
      }
    }
    // 评价真冲突和假冲突个数
    for (DCViolation candiVio : this.candidateViolations) {
      List<DenialConstraint> dcs = candiVio.getDcs();
      for (DenialConstraint dc : dcs) {
        if (isTrueViolation(dc, candiVio.getLinePair())) {
          this.trueViolations.add(candiVio);
        }
        Integer i = candiDCViosMap.get(dc);
        if (i == null) {
          throw new DCMinderToolsException("No candiDC found for a candiVio!!!");
        }
        candiDCViosMap.put(dc, i + 1);
      }
    }
    result.setTrueDCs(this.trueDCs.size());
    result.setCandiDCs(this.candidateDCs.size());
    result.setGtDCs(this.groundTruthDCs.size());
    result.setTrueVios(this.trueViolations.size());
    result.setCandiVios(this.candidateViolations.size());
    result.setGtVios(this.groundTruthViolations.size());
    result.setDcStrVioSizeMap(candiDCViosMap);
    result.setDirtyLines(this.dirtyLines);
    return result;
  }

  public boolean isTrueDC(DenialConstraint dc) {
    // TODO: 包含情况怎么判断？
    return dc.isImpliedBy(this.gtTree);
  }

  public boolean isTrueViolation(DenialConstraint dc, LinePair linePair) {
    // TODO: 可以这么判断真冲突，但是不能就说它对应的规则就是真规则
    return this.isTrueDC(dc);
  }

  public boolean allTrueViolationsFound() {
    // TODO: 目前有可能trueDCs数量大于gtDCs，是因为impliedDC也算真规则
    boolean b = this.trueDCs.size() >= this.groundTruthDCs.size();
    return b;
  }

  public Set<DCViolation> genCellQuestionsFromCurrState(int maxQueryBudget) {
    List<DCViolation> chosenVios = getRandomElements(this.currVios, maxQueryBudget);
    return new HashSet<>(chosenVios);
  }

  public void excludeDirtyLines(DCViolation vio) {
    LinePair linePair = vio.getLinePair();
    int line1 = linePair.getLine1();
    int line2 = linePair.getLine2();
    this.dirtyLines.add(line1);
    this.dirtyLines.add(line2);
  }

  @Getter
  @Setter
  public class EvalResult {

    private int trueVios = 0;
    private int candiVios = 0;
    private int gtVios = 0;
    private int trueDCs = 0;
    private int candiDCs = 0;
    private int gtDCs = 0;
    private Map<DenialConstraint, Integer> dcStrVioSizeMap;
    private Set<Integer> dirtyLines;

    @Override
    public String toString() {
      String result = String.format(
          "Current round: %s/%s(trueVios/vios), %s(gtVios), %s/%s(trueCandiDCs/candiDCs), %s(gtDCs)",
          this.trueVios, this.candiVios, this.gtVios, this.trueDCs, this.candiDCs, this.gtDCs);
      return result;
    }
  }
}
