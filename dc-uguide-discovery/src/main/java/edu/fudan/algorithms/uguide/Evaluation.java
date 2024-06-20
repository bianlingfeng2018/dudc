package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.conf.DefaultConf.defaultErrorThreshold;
import static edu.fudan.conf.DefaultConf.dynamicG1;
import static edu.fudan.utils.DCUtil.genLineChangesMap;
import static edu.fudan.utils.DCUtil.getCellsOfViolations;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.FileUtil.generateNewCopy;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.input.InputGenerationException;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.EvaluateUtil;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
  private final CleanDS cleanDS;

  /**
   * Dirty data
   */
  @Getter
  private final DirtyDS dirtyDS;

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
  private Set<DCViolation> groundTruthVios = Sets.newHashSet();

  /**
   * Ground truth DCs which hold on clean data. Violations wrt ground truth DCs are true
   * violations.
   */
  private final Set<DenialConstraint> groundTruthDCs = Sets.newHashSet();
  @Getter
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
  /**
   * 假规则及冲突元组对，冲突元组对作为这个假规则的候选反例，用于优化采样
   */
  @Getter
  private final Map<DenialConstraint, Set<LinePair>> falseVioLinePairMap = Maps.newHashMap();
  private int cellBudgetUsed = 0;
  private int dcBudgetUsed = 0;
  private int tupleBudgetUsed = 0;
  @Getter
  private Set<Integer> errorLinesOfChanges = Sets.newHashSet();
  private Set<Integer> errorLinesInSample = Sets.newHashSet();
  private Set<Integer> errorLinesInSampleAndExcluded = Sets.newHashSet();
  private Set<TCell> cellsOfChangesUnrepaired = Sets.newHashSet();
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
  private double errorThreshold = 0.0;
  @Getter
  private List<EvalResult> evalResults = Lists.newArrayList();
  @Getter
  private final String csvResultPath;
  /**
   * 记录上次candiDC个数
   */
  @Getter
  private int lastCandiDCsSize = 0;
  @Getter
  private Map<Integer, Map<Integer, String>> lineChangesMap;
  private List<TChange> changes;

  public Evaluation(CleanDS cleanDS, DirtyDS dirtyDS, String groundTruthDCsPath,
      String candidateDCsPath, String trueDCsPath, String visitedDCsPath, String csvResultPath) {
    this.cleanDS = cleanDS;
    this.dirtyDS = dirtyDS;
    this.groundTruthDCsPath = groundTruthDCsPath;
    this.candidateDCsPath = candidateDCsPath;
    this.trueDCsPath = trueDCsPath;
    this.visitedDCsPath = visitedDCsPath;
    this.csvResultPath = csvResultPath;
  }

  public void setUp() throws IOException, InputGenerationException {
    String cleanPath = this.cleanDS.getDataPath();
    String dirtyPath = this.dirtyDS.getDataPath();
    String headerPath = this.cleanDS.getHeaderPath();
    log.info("CleanPath={}", cleanPath);
    log.info("DirtyPath={}", dirtyPath);
    log.info("HeaderPath={}", headerPath);

    // 发现groundTruth规则
//    DiscoveryEntry.doDiscovery(dataPath, groundTruthDCsPath);
    // 读取groundTruth规则
    log.info("GroundTruthDCsPath={}", this.groundTruthDCsPath);
    List<DenialConstraint> dcList = DCLoader.load(headerPath, this.groundTruthDCsPath);
    // 检测冲突，结果应该为0
    log.debug("Confirm clean data HAS NO vios wrt ground truth DCs");
    DCViolationSet viosOnClean = new HydraDetector(cleanPath, this.groundTruthDCsPath,
        headerPath).detect();
    if (viosOnClean.size() != 0) {
      throw new RuntimeException("Found vios of gtDCs on clean data");
    }
    // 检测冲突，设定GroundTruth规则应当在脏数据集上发现的冲突数量
    log.debug("Confirm dirty data HAS vios wrt ground truth DCs");
    DCViolationSet viosOnDirty = new HydraDetector(dirtyPath, this.groundTruthDCsPath,
        headerPath).detect();
    if (viosOnDirty.size() == 0) {
      throw new RuntimeException("No vios of gtDCs on dirty data");
    }
    // 设定GroundTruth
    this.groundTruthVios = viosOnDirty.getViosSet();
    this.groundTruthDCs.addAll(dcList);
    log.info("GroundTruthDCsSize={}, ViosSize={}", this.groundTruthDCs.size(),
        this.groundTruthVios.size());
    for (DenialConstraint gtDC : this.groundTruthDCs) {
      this.gtTree.add(PredicateSetFactory.create(gtDC.getPredicateSet()).getBitset());
    }
    // 设定errors(changes)
    List<TChange> changes = loadChanges(this.cleanDS.getChangesPath());
    log.info("Changes: {}", changes.size());
    this.changes = changes;
    // 设定固定不变的、最初的cells of changes
    this.cellsOfChangesUnrepaired = DCUtil.getCellsOfChanges(this.changes);
    this.updateChanges();
    // 设定errorThreshold
    this.errorThreshold = defaultErrorThreshold;
    // 确认visited为零
    this.clearVisitedDCs();
  }

  public void update(Set<DenialConstraint> newCandiDCs, Set<DenialConstraint> falseDCs,
      Set<DCViolation> newCandiVios, Set<DCViolation> falseViolations, Set<Integer> excludedLines) {
    // 更新当前状态
    updateCurrState(newCandiDCs, newCandiVios);
    if (newCandiDCs != null) {
      // 增加候选规则
      this.candidateDCs.addAll(newCandiDCs);
      // 记录已经访问过的DC
      this.visitedDCs.addAll(newCandiDCs);
    }
    if (falseDCs != null) {
      // 减少假阳性规则
      for (DenialConstraint falseDC : falseDCs) {
        this.candidateDCs.remove(falseDC);
      }
    }
    if (excludedLines != null) {
      // 增加需要排除的脏数据
      this.excludedLines.addAll(excludedLines);
    }
  }

  /**
   * The linePair of falseVio that determines falseDC is used as a counterexample to optimize
   * sampling, that is, to increase the probability of sampling these tuples.
   *
   * @param dc  False DC
   * @param vio False violation
   */
  public void addCounterExampleToMap(DenialConstraint dc, DCViolation vio) {
    // TODO: FalseDC对应的冲突的元组都是这个falseDC的反例，但是其中可能有脏元组
    //  目前仅将不是脏元组的反例加入采样中，一方面让采样保持干净，一方面增加反例更快发现真规则
    //  目前判断脏元组只有TupleQ可以确定一部分
    //  有可能所有的反例都是脏元组，这会导致一部分规则会因为采样总是缺少反例而没办法发现（错误率较低，规则较短时会好一些）
    //  本质上是排除脏元组的同时会减少反例，这是互相矛盾的，我们目前无法兼顾。因此，修复脏元组可能比排除脏元组更好？
    //  然而CellQ可以通过确认元组对是干净来确认falseDC，这种情况下，元组一定是干净的反例。
    LinePair linePair = vio.getLinePair();
    if (falseVioLinePairMap.containsKey(dc)) {
      falseVioLinePairMap.get(dc).add(linePair);
    } else {
      falseVioLinePairMap.put(dc, Sets.newHashSet(linePair));
    }
  }

  public void updateCurrState(Set<DenialConstraint> dcs, Set<DCViolation> violations) {
    if (dcs != null) {
      this.currDCs.clear();
      this.currDCs.addAll(dcs);
    }
    if (violations != null) {
      this.currVios.clear();
      this.currVios.addAll(violations);
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

  public void evaluateCopyOfLast() {
    EvalResult res = evalResults.get(evalResults.size() - 1);
    evalResults.add(res);
  }

  public EvalResult evaluate() {
    EvalResult result = new EvalResult();
    // 当前g1
    result.setCurrG1(this.errorThreshold);
    if (dynamicG1 && this.lastCandiDCsSize == this.candidateDCs.size()) {
      double factor = 0.87;  // 0.87是0.001~0.0001分成50次降低得来的
      decreaseG1(factor);
    }
    // g1从大到小的范围内发现的candiDC都当作真DC，兼顾了反例多的(g1可以更松)和反例少(g1需要更严格)的规则
    // g1的本质是：不覆盖错误的元组对，同时也可能不覆盖一些反例
    // 1.当需要反例没覆盖时，发现的规则就是TooGeneral的假规则
    // 2.当不覆盖的错误不够时，发现的规则就是TooSpecific的假规则
    // 还有一个吹点，就是容错能力更强，比如一开始设定了一个不太好的g1（偏大的）？
    this.lastCandiDCsSize = this.candidateDCs.size();
    String unrepairedPath = dirtyDS.getDataUnrepairedPath();
    String dirtyPath = this.dirtyDS.getDataPath();
    // 真规则
    long t1 = System.currentTimeMillis();
    for (DenialConstraint candiDC : this.candidateDCs) {
      if (isTrueDC(candiDC)) {
        this.trueDCs.add(candiDC);
      }
    }
    long t2 = System.currentTimeMillis();
    log.debug("Eval 1 time = {}s", (t2 - t1) / 1000);
    // 计算已发现规则的冲突
    Set<DCViolation> vDisc = new HydraDetector(unrepairedPath, new HashSet<>(candidateDCs)).detect()
        .getViosSet();
    long t3 = System.currentTimeMillis();
    log.debug("Eval 2 time = {}s", (t3 - t2) / 1000.0);
    // 评价error cells发现个数
    this.cellsOfTrueVios = getCellsOfViolations(vDisc, generateNewCopy(dirtyPath));
    this.cellsOfTrueViosAndChanges = this.cellsOfTrueVios.stream()
        .filter(tc -> this.cellsOfChanges.contains(tc)).collect(Collectors.toSet());
    long t4 = System.currentTimeMillis();
    log.debug("Eval 3 time = {}s", (t4 - t3) / 1000.0);
    // 评价sample中已排除的错误元组数量
    this.errorLinesInSampleAndExcluded = this.errorLinesInSample.stream()
        .filter(lineIndex -> this.excludedLines.contains(lineIndex)).collect(Collectors.toSet());
    long t5 = System.currentTimeMillis();
    log.debug("Eval 4 time = {}s", (t5 - t4) / 1000.0);
    // 评价P R F1
    Double[] doubles = EvaluateUtil.eval(groundTruthDCs, candidateDCs, this.groundTruthVios, vDisc);
    long t6 = System.currentTimeMillis();
    log.debug("Eval 5 time = {}s", (t6 - t5) / 1000.0);
    result.setPrecision(doubles[0]);
    result.setRecall(doubles[1]);
    result.setF1(doubles[2]);
    result.setExcludedLines(this.excludedLines.size());
    result.setExcludedLinesOfCellQ(this.excludedLinesInCellQ.size());
    result.setExcludedLinesOfTupleQ(this.excludedLinesInTupleQ.size());
    result.setExcludedLinesOfDCsQ(this.excludedLinesInDCsQ.size());
    result.setErrorLinesInSample(this.errorLinesInSample.size());
    result.setErrorLinesInSampleAndExcluded(this.errorLinesInSampleAndExcluded.size());
    result.setDCsTrue(this.trueDCs.size());
    result.setDCsCandidate(this.candidateDCs.size());
    result.setDCsGroundTruth(this.groundTruthDCs.size());
    result.setQuestionsCell(this.cellBudgetUsed);
    result.setQuestionsTuple(this.tupleBudgetUsed);
    result.setQuestionsDC(this.dcBudgetUsed);
    result.setCellsOfChanges(this.cellsOfChanges.size());
    result.setCellsOfTrueVios(this.cellsOfTrueVios.size());
    result.setCellsOfTrueViosAndChanges(this.cellsOfTrueViosAndChanges.size());
    result.setCellsOfChangesUnrepaired(this.cellsOfChangesUnrepaired.size());
    this.evalResults.add(result);
    return result;
  }

  public void decreaseG1(double factor) {
    // TODO: 当candiDC数量没有增加时，即本轮top-k规则全部被判定为falseDC，此时g1需要更严格，即更小
    //  另外，增加对比实验证明，调整g1一定需要数据能变得更干净为前提，即可以排除脏元组，否则效果会打折扣
    //  调整g1后，之前candiDC有些就不成立，目前我们不对已经发现的candiDC做更改，因为新的g1发现的规则也不一定是对的
    //  调整g1后，如果仍然缺少反例，可能是反例没有被采样到，也可能是反例被排除脏元组时去掉了，因此目前我们不通过增大采样窗口来增加反例
    double newErrorThreshold = this.errorThreshold * factor;
    log.debug("ErrorThreshold change from {} to {}", this.errorThreshold, newErrorThreshold);
    this.errorThreshold = newErrorThreshold;
  }

  public void updateSampleResult(SampleResult sampleResult) {
    this.sampleResult = sampleResult;
    Set<Integer> allLinesInSample = this.sampleResult.getLineIndices();
    this.errorLinesInSample = allLinesInSample.stream()
        .filter(i -> this.errorLinesOfChanges.contains(i)).collect(Collectors.toSet());
  }

  public boolean isTrueDC(DenialConstraint dc) {
    // TODO: 包含情况怎么判断？
    return dc.isImpliedBy(this.gtTree);
  }

  public boolean isTrueViolation(DenialConstraint dc, LinePair linePair) {
    // TODO: 这里判断真冲突的方法是取巧的方法。
    //  正确判断真冲突的方法是：若冲突内包含脏数据，则为真冲突。
    //  真冲突不能说明其对应的规则为真规则。
    return this.isTrueDC(dc);
  }

  public boolean allTrueViolationsFound() {
    // 用Recall判断所有真冲突是否都找到，其他方式都不妥：
    // 1.用trueDCs数量判断。有可能trueDCs数量大于gtDCs，是因为impliedDC也算真规则。
    // 2.用trueViolations判断。有可能trueViolations数量大于groundTruthViolations，是因为多个前者可能映射到同一个后者。
    if (evalResults.isEmpty()) {
      return false;
    }
    EvalResult lastRes = this.evalResults.get(evalResults.size() - 1);
    double recall = lastRes.getRecall();
    return recall == 1.0;
  }

  public Set<DCViolation> genCellQuestionsFromCurrState(int maxQueryBudget) {
    List<DCViolation> chosenVios = getRandomElements(this.currVios, maxQueryBudget);
    return new HashSet<>(chosenVios);
  }

  public int getSampleResultSize() {
    Set<Integer> lineIndices = this.sampleResult.getLineIndices();
    return lineIndices.size();
  }

  public Set<DenialConstraint> genDCQuestionsFromCurrState(int maxQueryBudget) {
    List<DenialConstraint> chosenDCs = getRandomElements(this.currDCs, maxQueryBudget);
    return new HashSet<>(chosenDCs);
  }

  /**
   * 清理已经访问过的DC
   */
  private void clearVisitedDCs() {
    // 创建一个 BufferedWriter 对象
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(this.visitedDCsPath));
      // 写入空字符串以清空文件内容
      bw.write("");
      // 关闭 BufferedWriter
      bw.close();
      log.info("VisitedDCsPath cleared");
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (bw != null) {
          bw.close();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * 更新changes，如果excludedLines被修复，则对应的change也应当删除
   */
  public void updateChangesByExcludedLines() {
    Iterator<TChange> it = this.changes.iterator();
    while (it.hasNext()) {
      TChange next = it.next();
      if (this.excludedLines.contains(next.getLineIndex())) {
        it.remove();
      }
    }
    this.updateChanges();
    this.excludedLines.clear();
  }

  /**
   * 重新计算changes相关的数据
   */
  public void updateChanges() {
    this.lineChangesMap = genLineChangesMap(this.dirtyDS.getDataPath(), this.changes);
    this.cellsOfChanges = DCUtil.getCellsOfChanges(this.changes);
    this.errorLinesOfChanges = getErrorLinesContainingChanges(this.changes);
  }

  @Getter
  @Setter
  public class EvalResult {

    private int DCsTrue = 0;
    private int DCsCandidate = 0;
    private int DCsGroundTruth = 0;
    private int QuestionsCell = 0;
    private int QuestionsTuple = 0;
    private int QuestionsDC = 0;
    private int cellsOfChanges = 0;
    private int cellsOfChangesUnrepaired = 0;
    private int cellsOfTrueVios = 0;
    private int cellsOfTrueViosAndChanges = 0;
    private int excludedLines = 0;
    private int excludedLinesOfCellQ = 0;
    private int excludedLinesOfTupleQ = 0;
    private int excludedLinesOfDCsQ = 0;
    private int errorLinesInSample = 0;
    private int errorLinesInSampleAndExcluded = 0;
    private double precision = 0.0;
    private double recall = 0.0;
    private double f1 = 0.0;
    private double currG1 = 0.0;
  }
}
