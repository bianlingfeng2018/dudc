package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.conf.DefaultConf.debugDCVioMap;
import static edu.fudan.conf.DefaultConf.defaultErrorThreshold;
import static edu.fudan.utils.DCUtil.getCellIdentifiersOfChanges;
import static edu.fudan.utils.DCUtil.getCellIdentyfiersFromVios;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Lists;
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
import java.io.BufferedWriter;
import java.io.FileWriter;
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
  @Getter
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
   * Path to output the final visited DCs
   */
  @Getter
  private final String visitedDCsPath;

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
  @Getter
  private Set<Integer> errorLinesOfChanges = Sets.newHashSet();
  private Set<Integer> errorLinesInSample = Sets.newHashSet();
  private Set<Integer> errorLinesInSampleAndExcluded = Sets.newHashSet();
  @Getter
  private Set<TCell> cellsOfChanges = Sets.newHashSet();
  private Set<TCell> cellsOfTrueVios = Sets.newHashSet();
  private Set<TCell> cellsOfTrueViosAndChanges = Sets.newHashSet();
  @Setter
  private Set<Integer> excludedLinesInCellQ = Sets.newHashSet();
  @Setter
  private Set<Integer> excludedLinesInTupleQ = Sets.newHashSet();
  @Setter
  private Set<Integer> excludedLinesInDCsQ = Sets.newHashSet();
  private SampleResult sampleResult = new SampleResult(new HashSet<>(), new ArrayList<>());
  @Getter
  @Setter
  private double errorThreshold = 0.0;
  @Getter
  private List<EvalResult> evalResults = Lists.newArrayList();
  @Getter
  private final String csvResultPath;

  public Evaluation(CleanData cleanData, DirtyData dirtyData, String groundTruthDCsPath,
      String candidateDCsPath, String trueDCsPath, String visitedDCsPath, String csvResultPath) {
    this.cleanData = cleanData;
    this.dirtyData = dirtyData;
    this.groundTruthDCsPath = groundTruthDCsPath;
    this.candidateDCsPath = candidateDCsPath;
    this.trueDCsPath = trueDCsPath;
    this.visitedDCsPath = visitedDCsPath;
    this.csvResultPath = csvResultPath;
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
    this.cellsOfChanges = getCellIdentifiersOfChanges(changes);
    this.errorLinesOfChanges = getErrorLinesContainingChanges(changes);
    // 设定errorThreshold
    this.errorThreshold = defaultErrorThreshold;
    // 确认visited为零
    clearVisitedDCs();
  }

  public void update(Set<DenialConstraint> candidateDCs,
      Set<DenialConstraint> falseDCs,
      Set<DCViolation> candidateViolations,
      Set<DCViolation> falseViolations,
      Set<Integer> excludedLines) {
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
        // 对于CellQ，是根据假冲突判断的falseDC，且在后面才删除，所以falseDC可以找到对应冲突并remove，
        // 对于DCsQ，是直接判断falseDC的，当这个规则是真规则但是不在groundTruth中时，可能没有冲突，所以这里要判空容错。
        if (vios == null) {
          continue;
        }
        for (DCViolation vio : vios) {
          // 删除和DC相关的冲突
          this.candidateViolations.remove(vio);
          // TODO: 增加需要排除的脏数据，FalseDC不一定对应的是脏数据，有可能是缺少反例，需要进一步判断
//          excludeLinesInVio(vio);
        }
      }
    }
    if (candidateViolations != null) {
      // 增加相关的冲突
      this.candidateViolations.addAll(candidateViolations);
      // 增加真冲突（增量增加，开销较小）
      for (DCViolation candiVio : candidateViolations) {
        for (DenialConstraint dc : candiVio.getDenialConstraintList()) {
          if (isTrueViolation(dc, candiVio.getLinePair())) {
            this.trueViolations.add(candiVio);
          }
        }
      }
    }
    if (falseViolations != null) {
      // 删除相关的冲突
      for (DCViolation vio : falseViolations) {
        this.candidateViolations.remove(vio);
      }
    }
    if (excludedLines != null) {
      // 增加需要排除的脏数据
      this.excludedLines.addAll(excludedLines);
    }
  }

  public void updateCurrState(Set<DCViolation> candidateViolations) {
    // 记录当前状态
    this.currVios.clear();
    this.currVios.addAll(candidateViolations);
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
    // 评价真规则和假规则个数
    long t1 = System.currentTimeMillis();
    for (DenialConstraint candiDC : this.candidateDCs) {
      if (isTrueDC(candiDC)) {
        this.trueDCs.add(candiDC);
      }
    }
    long t2 = System.currentTimeMillis();
    log.debug("Eval 1 time = {}s", (t2 - t1) / 1000);
    // 测试时，评价最终的候选规则及其关联的冲突个数
    Map<DenialConstraint, Integer> candiDCViosMap = Maps.newHashMap();
    if (debugDCVioMap) {
      for (DenialConstraint candiDC : this.candidateDCs) {
        candiDCViosMap.put(candiDC, 0);
      }
      for (DCViolation candiVio : this.candidateViolations) {
        List<DenialConstraint> dcs = candiVio.getDenialConstraintList();
        for (DenialConstraint dc : dcs) {
          Integer i = candiDCViosMap.get(dc);
          if (i == null) {
            throw new DCMinderToolsException("No candiDC found for a candiVio!!!");
          }
          candiDCViosMap.put(dc, i + 1);
        }
      }
    }
    long t3 = System.currentTimeMillis();
    log.debug("Eval 2 time = {}s", (t3 - t2) / 1000.0);
    // 评价error cells发现个数
    this.cellsOfTrueVios = getCellIdentyfiersFromVios(this.trueViolations,
        this.dirtyData.getInput());
    this.cellsOfTrueViosAndChanges = this.cellsOfTrueVios.stream()
        .filter(tc -> this.cellsOfChanges.contains(tc))
        .collect(Collectors.toSet());
    long t4 = System.currentTimeMillis();
    log.debug("Eval 3 time = {}s", (t4 - t3) / 1000.0);
    // 评价sample中已排除的错误元组数量
    this.errorLinesInSampleAndExcluded = this.errorLinesInSample.stream()
        .filter(i -> this.excludedLines.contains(i))
        .collect(Collectors.toSet());
    long t5 = System.currentTimeMillis();
    log.debug("Eval 4 time = {}s", (t5 - t4) / 1000.0);
    // 评价sample的error lines
    result.setExcludedLines(this.excludedLines.size());
    result.setExcludedLinesOfCellQ(this.excludedLinesInCellQ.size());
    result.setExcludedLinesOfTupleQ(this.excludedLinesInTupleQ.size());
    result.setExcludedLinesOfDCsQ(this.excludedLinesInDCsQ.size());
    result.setErrorLinesInSample(this.errorLinesInSample.size());
    result.setErrorLinesInSampleAndExcluded(this.errorLinesInSampleAndExcluded.size());
    result.setDCsTrue(this.trueDCs.size());
    result.setDCsCandidate(this.candidateDCs.size());
    result.setDCsGroundTruth(this.groundTruthDCs.size());
    result.setViolationsTrue(this.trueViolations.size());
    result.setViolationsCandidate(this.candidateViolations.size());
    result.setViolationsGroundTruth(this.groundTruthViolations.size());
    result.setQuestionsCell(this.cellBudgetUsed);
    result.setQuestionsTuple(this.tupleBudgetUsed);
    result.setQuestionsDC(this.dcBudgetUsed);
    result.setCellsOfChanges(this.cellsOfChanges.size());
    result.setCellsOfTrueVios(this.cellsOfTrueVios.size());
    result.setCellsOfTrueViosAndChanges(this.cellsOfTrueViosAndChanges.size());
    result.setCandiDCViosMap(candiDCViosMap);
    this.evalResults.add(result);
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
    boolean b = this.cellsOfTrueVios.containsAll(this.cellsOfChanges);
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

  private void clearVisitedDCs() throws IOException {
    // 创建一个 BufferedWriter 对象
    BufferedWriter writer = new BufferedWriter(new FileWriter(this.visitedDCsPath));
    // 写入空字符串以清空文件内容
    writer.write("");
    // 关闭 BufferedWriter
    writer.close();
    log.info("VisitedDCsPath cleared");
  }

  @Getter
  @Setter
  public class EvalResult {

    private int violationsTrue = 0;
    private int violationsCandidate = 0;
    private int violationsGroundTruth = 0;
    private int DCsTrue = 0;
    private int DCsCandidate = 0;
    private int DCsGroundTruth = 0;
    private int QuestionsCell = 0;
    private int QuestionsTuple = 0;
    private int QuestionsDC = 0;
    private int cellsOfChanges = 0;
    private int cellsOfTrueVios = 0;
    private int cellsOfTrueViosAndChanges = 0;
    private int excludedLines = 0;
    private int excludedLinesOfCellQ = 0;
    private int excludedLinesOfTupleQ = 0;
    private int excludedLinesOfDCsQ = 0;
    private int errorLinesInSample = 0;
    private int errorLinesInSampleAndExcluded = 0;
    private Map<DenialConstraint, Integer> candiDCViosMap = Maps.newHashMap();
  }
}
