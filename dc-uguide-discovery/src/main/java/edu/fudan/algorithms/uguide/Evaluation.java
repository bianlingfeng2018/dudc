package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.conf.DefaultConf.defaultErrorThreshold;
import static edu.fudan.utils.DCUtil.getCellIdentifiersOfChanges;
import static edu.fudan.utils.DCUtil.getCellIdentyfiersFromVios;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;

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
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.utils.DataUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
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
  @Getter
  private final Set<DCViolation> currVios = Sets.newHashSet();

  /**
   * Lines which should be excluded from dirty data.
   */
  @Getter
  private final Set<Integer> excludedLines = Sets.newHashSet();
  private int cellBudgetUsed = 0;
  private int dcBudgetUsed = 0;
  private int tupleBudgetUsed = 0;
  private Set<TCell> cellIdentifiersOfChanges = Sets.newHashSet();
  private Set<Integer> errorLinesOfChanges = Sets.newHashSet();
  private Set<Integer> errorLinesInSample = Sets.newHashSet();
  private Set<TCell> cellIdentifiersOfTrueVios = Sets.newHashSet();
  private Set<TCell> cellsOfTrueViosAndChanges = Sets.newHashSet();
  private SampleResult sampleResult = new SampleResult(new HashSet<>(), new ArrayList<>());
  private Set<Integer> curExcludedLinesOfTrueDCs = Sets.newHashSet();
  @Getter
  @Setter
  private double errorThreshold = 0.0;

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
    log.info("CleanDataPath={}", cleanDataPath);
    log.info("DirtyDataPath={}", dirtyDataPath);
    log.info("HeaderPath={}", headerPath);

    // 发现groundTruth规则
//    DiscoveryEntry.doDiscovery(dataPath, groundTruthDCsPath);
    // 读取groundTruth规则
    log.info("GroundTruthDCsPath={}", this.groundTruthDCsPath);
    List<DenialConstraint> dcList = DCLoader.load(headerPath, this.groundTruthDCsPath);
    // 检测冲突，结果应该为0
    log.debug("Confirm clean data has NO vios wrt ground truth DCs");
    DCViolationSet viosOnClean = new HydraDetector(cleanDataPath, this.groundTruthDCsPath).detect();
    int sizeClean = viosOnClean.size();
    if (sizeClean != 0) {
      throw new RuntimeException("Found vios of gtDCs on clean data");
    }
    // 检测冲突，设定GroundTruth规则应当在脏数据集上发现的冲突数量
    log.debug("Confirm dirty data HAS vios wrt ground truth DCs");
    DCViolationSet viosOnDirty = new HydraDetector(dirtyDataPath, this.groundTruthDCsPath).detect();
    int sizeDirty = viosOnDirty.size();
    if (sizeDirty == 0) {
      throw new RuntimeException("No vios of gtDCs on dirty data");
    }
    // 设定GroundTruth
    this.groundTruthViolations = viosOnDirty.getViosSet();
    this.groundTruthDCs.addAll(dcList);
    log.info("GroundTruthDCsSize={}, ViosSize={}",
        this.groundTruthDCs.size(), this.groundTruthViolations.size());
    for (DenialConstraint gtDC : this.groundTruthDCs) {
      this.gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    }
    // 设定errors(changes)
    List<TChange> changes = loadChanges(this.cleanData.getChangesPath());
    log.info("Changes: {}", changes.size());
    this.cellIdentifiersOfChanges = getCellIdentifiersOfChanges(changes);
    this.errorLinesOfChanges = getErrorLinesContainingChanges(changes);
    // 设定errorThreshold
    this.errorThreshold = defaultErrorThreshold;
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
      this.excludedLines.addAll(dirtyLines);
    }
  }

  public void addCellBudget(int numb) {
    this.cellBudgetUsed += numb;
  }

  public void addDCBudget(int numb) {
    this.dcBudgetUsed += numb;
  }

  public void addTupleBudget(int numb) {
    this.tupleBudgetUsed += numb;
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
    // 评价error cells发现个数
    this.cellIdentifiersOfTrueVios = getCellIdentyfiersFromVios(this.trueViolations,
        this.dirtyData.getInput());
    this.cellsOfTrueViosAndChanges = this.cellIdentifiersOfTrueVios.stream()
        .filter(tc -> this.cellIdentifiersOfChanges.contains(tc))
        .collect(Collectors.toSet());
    // 评价sample的error lines
    result.setCurExcludedLinesOfTrueDCs(this.curExcludedLinesOfTrueDCs);
    result.setErrorLinesInSample(this.errorLinesInSample);
    result.setTrueDCs(this.trueDCs.size());
    result.setCandiDCs(this.candidateDCs.size());
    result.setGtDCs(this.groundTruthDCs.size());
    result.setTrueVios(this.trueViolations.size());
    result.setCandiVios(this.candidateViolations.size());
    result.setGtVios(this.groundTruthViolations.size());
    result.setDcStrVioSizeMap(candiDCViosMap);
    result.setExcludedLines(this.excludedLines);
    result.setCellQuestions(this.cellBudgetUsed);
    result.setDcQuestions(this.dcBudgetUsed);
    result.setTupleQuestions(this.tupleBudgetUsed);
    result.setCellsOfChanges(this.cellIdentifiersOfChanges.size());
    result.setCellsOfTrueVios(this.cellIdentifiersOfTrueVios.size());
    result.setCellsOfTrueViosAndChanges(this.cellsOfTrueViosAndChanges.size());
    return result;
  }

  public void updateSampleResult(SampleResult sampleResult) {
    this.sampleResult = sampleResult;
    Set<Integer> allLinesInSample = this.sampleResult.getLineIndices();
    this.errorLinesInSample = allLinesInSample.stream()
        .filter(i -> this.errorLinesOfChanges.contains(i))
        .collect(Collectors.toSet());
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
//    boolean b = trueDCsMoreThanGroundTruthDCs();
//    boolean b = trueViosMoreThanGroundTruthVios();
    boolean b = this.cellIdentifiersOfTrueVios.containsAll(this.cellIdentifiersOfChanges);
    return b;
  }

  private boolean trueViosMoreThanGroundTruthVios() {
    // TODO: 目前有可能trueViolations数量大于groundTruthViolations，是因为同一个tuplePair可能违反多个规则，所以冲突之间可能有元组对的重合
    boolean b = this.trueViolations.size() == this.groundTruthViolations.size();
    return b;
  }

  private boolean trueDCsMoreThanGroundTruthDCs() {
    // TODO: 目前有可能trueDCs数量大于gtDCs，是因为impliedDC也算真规则
    boolean b = this.trueDCs.size() >= this.groundTruthDCs.size();
    return b;
  }

  public Set<DCViolation> genCellQuestionsFromCurrState(int maxQueryBudget) {
    List<DCViolation> chosenVios = getRandomElements(this.currVios, maxQueryBudget);
    return new HashSet<>(chosenVios);
  }

  public int getSampleResultSize() {
    Set<Integer> lineIndices = this.sampleResult.getLineIndices();
    return lineIndices.size();
  }

  public Set<Integer> genTupleQuestionsFromCurrState(int maxQueryBudget) {
    List<Integer> chosenLinesInSample = getRandomElements(this.sampleResult.getLineIndices(),
        maxQueryBudget);
    return new HashSet<>(chosenLinesInSample);
  }

  public Set<DenialConstraint> genDCQuestionsFromCurrState(int maxQueryBudget) {
    List<DenialConstraint> chosenDCs = getRandomElements(this.currDCs, maxQueryBudget);
    return new HashSet<>(chosenDCs);
  }

  public void excludeLines(DCViolation vio) {
    LinePair linePair = vio.getLinePair();
    int line1 = linePair.getLine1();
    int line2 = linePair.getLine2();
    this.excludedLines.add(line1);
    this.excludedLines.add(line2);
  }

  public void excludeErrorLinesInSample(Set<Integer> recommendedLines) {
    // 排除question(sample中推荐给用户判断的元组)中的所有错误行
    Set<Integer> errors = recommendedLines.stream()
        .filter(i -> this.errorLinesOfChanges.contains(i))
        .collect(Collectors.toSet());
    this.excludedLines.addAll(errors);
  }

  public void excludeLinesOfTrueDCs(Set<Integer> viosLinesOfTrueDCs) {
    this.excludedLines.addAll(viosLinesOfTrueDCs);
    // 记录当前轮排除的和TrueDCs相关的冲突元组
    this.curExcludedLinesOfTrueDCs = viosLinesOfTrueDCs;
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
    private int cellQuestions = 0;
    private int dcQuestions = 0;
    private int tupleQuestions = 0;
    private int cellsOfChanges = 0;
    private int cellsOfTrueVios = 0;
    private int cellsOfTrueViosAndChanges = 0;
    private Map<DenialConstraint, Integer> dcStrVioSizeMap = Maps.newHashMap();
    private Set<Integer> errorLinesInSample = Sets.newHashSet();
    private Set<Integer> excludedLines = Sets.newHashSet();
    private Set<Integer> curExcludedLinesOfTrueDCs = Sets.newHashSet();

    @Override
    public String toString() {
      String result = String.format("%s/%s/%s(trueVios/candiVios/gtVios), "
              + "%s/%s/%s(trueDCs/candiDCs/gtDCs), "
              + "%s/%s/%s(cellsOfTrueViosAndChanges/cellsOfTrueVios/cellsOfChanges), "
              + "%s/%s/%s(cellQ/dcQ/tupleQ)",
          this.trueVios, this.candiVios, this.gtVios,
          this.trueDCs, this.candiDCs, this.gtDCs,
          this.cellsOfTrueViosAndChanges, this.cellsOfTrueVios, this.cellsOfChanges,
          this.cellQuestions, this.dcQuestions, this.tupleQuestions
      );
      return result;
    }
  }
}
