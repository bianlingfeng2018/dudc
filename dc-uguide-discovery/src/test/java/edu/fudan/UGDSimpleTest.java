package edu.fudan;

import static edu.fudan.conf.DefaultConf.predictArgs;
import static edu.fudan.conf.DefaultConf.sharedArgs;
import static edu.fudan.conf.DefaultConf.trainArgs;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.loadDirtyDataExcludedLines;
import static edu.fudan.utils.DCUtil.printDCVioMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.PythonCaller;
import edu.fudan.algorithms.RLDCGenerator;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.UGDParams;
import edu.fudan.utils.UGDRunner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGDSimpleTest {

  /**
   * Parameters used in User-guided-DC-Detection process.
   */
  UGDParams params;

  @Before
  public void setUp() throws Exception {
    int dsIndex = 0;
    params = UGDRunner.buildParams(dsIndex);
  }

//  /**
//   * Discover dc using fdcd(2023)
//   */
//  @Test
//  public void testDiscoveryDCsUsingFDCD() {
//    DiscoveryEntry.doDiscovery(cleanDataPath, fullDCsPath);
//  }

  /**
   * Discover dc using dcFinder(2019)
   */
  @Test
  public void testDiscoveryDCsUsingDCFinderNoEvidence() {
    // 1.当evidenceFile为null，则生成规则集合
    // 2.当evidenceFile不为null，则生成证据集（例如：作为DCMiner训练模型的输入）
    DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(params.cleanDataPath, 0.001,
        params.evidencesPath);
    log.info("DCs: {}", dcs.size());
  }


  /**
   * Test generating top-k dcs.
   */
  @Test
  public void testGenTopKDCs() {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(5, params.fullDCsPath,
        params.headerPath, null, -1);
    DCUtil.persistTopKDCs(topKDCs, params.topKDCsPath);
  }

  /**
   * Test implication of dc string.
   */
  @Test
  public void testImpDCString() {
    // not(t1.City!=t2.City^t1.Year!=t2.Year) + not(t1.Abbr!=t2.Abbr^t1.City=t2.City)
    //  -> not(t1.Abbr!=t2.Abbr^t1.Year!=t2.Year)
    String dcStr1 = "not(t1.City!=t2.City^t1.Year!=t2.Year)";
    String dcStr2 = "not(t1.Abbr!=t2.Abbr^t1.City=t2.City)";
    String dcStr3 = "not(t1.Abbr=t2.Abbr^t1.City!=t2.City)";
    String dcStr4 = "not(t1.Abbr!=t2.Abbr^t1.Year!=t2.Year)";
    String dcStr5 = "not(t1.City!=t2.City)";
    String header = "City(String),Abbr(String),Year(String)";
    DenialConstraint dc1 = DCFormatUtil.convertString2DC(dcStr1, header);
    DenialConstraint dc2 = DCFormatUtil.convertString2DC(dcStr2, header);
    DenialConstraint dc3 = DCFormatUtil.convertString2DC(dcStr3, header);
    DenialConstraint dc4 = DCFormatUtil.convertString2DC(dcStr4, header);
    DenialConstraint dc5 = DCFormatUtil.convertString2DC(dcStr5, header);

    NTreeSearch gtTree = new NTreeSearch();
    gtTree.add(PredicateSetFactory.create(dc5.getPredicateSet()).getBitset());
//    gtTree.add(PredicateSetFactory.create(dc2.getPredicateSet()).getBitset());
//    gtTree.add(PredicateSetFactory.create(dc3.getPredicateSet()).getBitset());

    boolean implied = dc1.isImpliedBy(gtTree);
    log.debug("Implied = {}", implied);

    de.hpi.naumann.dc.denialcontraints.DenialConstraintSet set = new de.hpi.naumann.dc.denialcontraints.DenialConstraintSet();
    set.add(dc1);
    set.add(dc2);
    set.add(dc3);
    set.add(dc4);
    set.add(dc5);
    log.debug("DCs before minimize = {}", set.size());
    set.minimize();
    log.debug("DCs after minimize =  {}", set.size());
  }

  /**
   * Test dc violations size.
   */
  @Test
  public void testDCViolationsSize() {
    // CandiTureDC和GTDC的冲突数量并无大小关系，因为CandiTureDC之间的冲突映射到GTDC上时LinePair可能重复
    // CandiTureDC冲突数量小于等于CandiDC的冲突数量
    DCViolationSet vios1 = new HydraDetector(params.dirtyDataPath, params.candidateDCsPath,
        params.headerPath).detect();
    DCViolationSet vios2 = new HydraDetector(params.dirtyDataPath, params.candidateTrueDCsPath,
        params.headerPath).detect();
    DCViolationSet vios3 = new HydraDetector(params.dirtyDataPath, params.groundTruthDCsPath,
        params.headerPath).detect();
    log.info("CandiDCVios = {}, CandiTureDCVios = {}, GTDCVios = {}", vios1.size(), vios2.size(),
        vios3.size());
  }

  /**
   * Test one violation corresponds to one dc.
   */
  @Test
  public void testViolationDC() {
    // 目前hydra一个vio就只对应一个DC
    DCViolationSet vios = new HydraDetector(params.dirtyDataPath, params.groundTruthDCsPath,
        params.headerPath).detect();
    log.info("Vios = {}", vios.size());
    for (DCViolation vio : vios.getViosSet()) {
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      assertEquals(1, dcs.size());
    }
  }

  /**
   * Test changed lines and excluded lines.
   */
  @Test
  public void testChangedLinesExcludedLines() throws IOException {
    // 看看排除的行是否都有错误，反之，看看错误行是否都被排除了
    List<TChange> changes = loadChanges(params.changesPath);
    Set<Integer> changedLines = Sets.newHashSet();
    for (TChange change : changes) {
      changedLines.add(change.getLineIndex());
    }
    log.info("ChangedLines={}", changedLines.size());

    Set<Integer> excludedLines = loadDirtyDataExcludedLines(params.excludedLinesPath);
    log.info("ExcludedLines={}", excludedLines.size());

    List<Integer> changedInExcluded = changedLines.stream().filter(i -> excludedLines.contains(i))
        .collect(Collectors.toList());
    List<Integer> excludedInChanged = excludedLines.stream().filter(i -> changedLines.contains(i))
        .collect(Collectors.toList());
    log.info("ChangedInExcluded={}, ExcludedInChanged={}", changedInExcluded.size(),
        excludedInChanged.size());
  }

  /**
   * Test training DCMiner.
   */
  @Test
  public void testDCMinerTrain() throws IOException, InterruptedException {
    String[] args4Train = (sharedArgs + " " + trainArgs).split(" ");
    PythonCaller.trainModel(args4Train);
  }

  /**
   * Test DCMiner prediction.
   */
  @Test
  public void testDCMinerPredict() throws IOException, InterruptedException {
    String[] args4Predict = (sharedArgs + " " + predictArgs).split(" ");
    PythonCaller.predict(args4Predict);
  }

  /**
   * Test discovering dcs using RLDCGenerator.
   */
  @Test
  public void testDiscoverDCUsingRLDCGenerator() {
    // 排除之前发现过的规则
    List<DenialConstraint> excludeDCs = DCLoader.load(params.headerPath, params.excludedDCsPath,
        new HashSet<>());
    log.debug("Visited DCs size = {}", excludeDCs.size());
    RLDCGenerator generator = new RLDCGenerator(params.sampledDataPath, params.evidencesPath,
        params.dcsPathForDCMiner, params.headerPath);
    generator.setExcludeDCs(new HashSet<>(excludeDCs));
    // 用DCMiner生成规则
    Set<DenialConstraint> dcs = generator.generateDCs();
    log.info("DCMiner DCs size = {}", dcs.size());
    for (DenialConstraint dc : dcs) {
      log.debug(DCFormatUtil.convertDC2String(dc));
    }
  }

  /**
   * Test minimizing dcs set.
   */
  @Test
  public void testDCMinimize() {
    // 测试准备注入错误的20条DCs已经是最小化的、没有重复的。
    String dcsPath = "D:\\paper\\dc_user_guided_detection\\experiment\\data\\DS1_Hospital\\dcs_hospital 扩展性5~25条规则.txt";
    String headerPath = "D:\\paper\\dc_user_guided_detection\\experiment\\data\\DS1_Hospital\\preprocessed_hospital_header.csv";
    List<DenialConstraint> dcs = DCLoader.load(headerPath, dcsPath, new HashSet<>());
    de.hpi.naumann.dc.denialcontraints.DenialConstraintSet set = new de.hpi.naumann.dc.denialcontraints.DenialConstraintSet();
    for (DenialConstraint dc : dcs) {
      set.add(dc);
    }
    log.debug("DCs before minimize = {}", set.size());
    set.minimize();
    log.debug("DCs after minimize = {}", set.size());
  }

  /**
   * Test lingPair and associated dcs.
   */
  @Test
  public void testLinePairDCs() {
    // 测试一个LinePair是否关联多个DC？
    DCViolationSet vios = new HydraDetector(params.dirtyDataPath, params.groundTruthDCsPath,
        params.headerPath).detect();
    Map<LinePair, Set<DenialConstraint>> linePairDCsMap = Maps.newHashMap();
    for (DCViolation vio : vios.getViosSet()) {
      LinePair linePair = vio.getLinePair();
      DenialConstraint dc = vio.getDenialConstraintsNoData().get(0);
      if (linePairDCsMap.containsKey(linePair)) {
        Set<DenialConstraint> set = linePairDCsMap.get(linePair);
        set.add(dc);
      } else {
        linePairDCsMap.put(linePair, Sets.newHashSet(dc));
      }
    }
    // 冲突个数
    int size1 = vios.size();
    // 元组对个数
    int size2 = linePairDCsMap.size();
    log.debug("Violations = {}, LinePairs = {}", size1, size2);
    assertTrue(size1 >= size2);
    // 打印 LinePair -> DCs
    Set<DenialConstraint> next = linePairDCsMap.values().iterator().next();
    log.debug("Example lingPair associated dcs = {}", next.size());
  }

  @Test
  public void testCopyFile() throws IOException {
    String source = params.dirtyDataPath;
    String target = params.sampledDataPath;
    Files.copy(Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING);
    log.debug("File source = {}, target = {}", source, target);
  }

  @Test
  public void testSortComparator() {
    ArrayList<Integer> integers = new ArrayList<>();
    integers.add(3);
    integers.add(1);
    integers.add(4);
    integers.add(2);
    log.debug("integers = {}", integers);
    integers.sort(Comparator.naturalOrder());
    log.debug("integers = {}", integers);
  }
}
