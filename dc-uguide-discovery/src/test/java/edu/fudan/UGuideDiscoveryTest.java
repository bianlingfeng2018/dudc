package edu.fudan;

import static edu.fudan.utils.DCUtil.getDCVioSizeMap;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.loadDirtyDataExcludedLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.Operator;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class UGuideDiscoveryTest {

  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data";
  private final String headerPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_header.csv";
  private final String cleanDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital.csv";
  private final String dirtyDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty.csv";
  private final String dirtyDataChangesPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_changes.csv";
  private final String dirtyDataExcludedPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty_excluded.csv";
  private final String sampledDataPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_dirty_sample.csv";
  private final String dcsPathForFCDC = baseDir + File.separator +
      "evidence_set\\dcs_fcdc_hospital.out";
  private final String evidencesPathForFCDC = baseDir + File.separator +
      "evidence_set\\evidence_set_fcdc_hospital.csv";
  private final String topKDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital.out";
  private final String groundTruthDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_ground.out";
  private final String candidateDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate.out";
  private final String trueDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate_true.out";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath,
        dirtyDataPath,
        dirtyDataExcludedPath,
        sampledDataPath,
        dcsPathForFCDC,
        evidencesPathForFCDC,
        topKDCsPath,
        groundTruthDCsPath,
        candidateDCsPath,
        trueDCsPath,
        headerPath);
    ud.guidedDiscovery();
  }

  @Test
  public void testGenGroundTruthDCs() {
    DiscoveryEntry.doDiscovery(cleanDataPath, dcsPathForFCDC);
  }

  @Test
  public void testGenTopKDCs() throws IOException {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(10, dcsPathForFCDC, headerPath, null);
    DCUtil.persistTopKDCs(topKDCs, topKDCsPath);
  }

  @Test
  public void testImplyMock() {
    // 长规则能被短规则推断出来
    ColumnOperand<?> o1 = mock(ColumnOperand.class);
    ColumnOperand<?> o2 = mock(ColumnOperand.class);

    DenialConstraint dc1 = new DenialConstraint(
        new Predicate(Operator.GREATER, o1, o2), new Predicate(Operator.LESS, o1, o2));
    DenialConstraint dc2 = new DenialConstraint(
        new Predicate(Operator.GREATER, o1, o2));
    NTreeSearch tree = new NTreeSearch();
    tree.add(PredicateSetFactory.create(dc2.getPredicateSet()).getBitset());

    boolean equals = dc1.isImpliedBy(tree);
    assertTrue(equals);
  }

  @Test
  public void testImply() {
    List<DenialConstraint> dcs1 = DCLoader.load(headerPath, topKDCsPath);
    List<DenialConstraint> gtDCs1 = DCLoader.load(headerPath, groundTruthDCsPath);

    NTreeSearch gtTree1 = new NTreeSearch();
    for (DenialConstraint dc : gtDCs1) {
      gtTree1.add(PredicateSetFactory.create(dc.getPredicateSet()).getBitset());
    }

    assert dcs1.get(0).isImpliedBy(gtTree1);
//    assert gtDCs1.contains(dcs1.get(0));

  }

  @Test
  public void testDCsViolationsSizeEquality()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    // TODO: Violation size: TureDcs <= gtDCs, TureDcs < candiDCs
    DCViolationSet vioSet1 = new HydraDetector(dirtyDataPath, candidateDCsPath).detect();
    DCViolationSet vioSet2 = new HydraDetector(dirtyDataPath, trueDCsPath).detect();
    DCViolationSet vioSet3 = new HydraDetector(dirtyDataPath, groundTruthDCsPath).detect();
    log.info("candi={}, candiTure={}, gt={}", vioSet1.size(), vioSet2.size(), vioSet3.size());
  }

  @Test
  public void testAllViolationHasOnlyOneDC()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    HydraDetector detector = new HydraDetector(dirtyDataPath, groundTruthDCsPath);
    DCViolationSet vioSet = detector.detect();
    log.info("VioSet = {}", vioSet.size());
    for (DCViolation vio : vioSet.getViosSet()) {
      List<DenialConstraint> dcs = vio.getDcs();
      assertEquals(1, dcs.size());
    }
  }

  @Test
  public void testPrintDCViosMap()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    HydraDetector detector = new HydraDetector(dirtyDataPath, candidateDCsPath);
    DCViolationSet vioSet = detector.detect();
    log.info("Vios size={}", vioSet.size());
    Map<DenialConstraint, Integer> dcViosSizeMap = getDCVioSizeMap(vioSet);
    log.info("DCStr~VioSize map:");
    for (Entry<DenialConstraint, Integer> entry : dcViosSizeMap.entrySet()) {
      log.info(DCFormatUtil.convertDC2String(entry.getKey()) + "~" + entry.getValue());
    }
  }

  @Test
  public void testDCsErrorsEquality()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    // TODO: Errors can all be found?
    // 对于BART注入错误，元组id(TupleOID)从1开始
    // 对于Hydra检测冲突，行号line(LinePair)从0开始
    List<TChange> changes = loadChanges(dirtyDataChangesPath);
    log.info("Changes size(total errors number)={}", changes.size());
    Set<String> cellIdentifiersOfChanges = Sets.newHashSet();
    for (TChange c : changes) {
      cellIdentifiersOfChanges.add(c.getLineIndex() + "_" + c.getAttribute());
    }
    log.info("cellIdentifiersOfChanges : {}, {}", cellIdentifiersOfChanges.size(),
        cellIdentifiersOfChanges.stream().findAny());
    // vios
//    DCViolationSet vioSet1 = new HydraDetector(dirtyDataPath, trueDCsPath).detect();
    DCViolationSet vioSet2 = new HydraDetector(dirtyDataPath, groundTruthDCsPath).detect();

    Set<String> cellIdentifiers = Sets.newHashSet();
    Set<DCViolation> viosSet = vioSet2.getViosSet();
    for (DCViolation vio : viosSet) {
      List<DenialConstraint> dcs = vio.getDcs();
      for (DenialConstraint dc : dcs) {
        LinePair linePair = vio.getLinePair();
        int line1 = linePair.getLine1();
        int line2 = linePair.getLine2();
        PredicateBitSet predicateSet = dc.getPredicateSet();
        for (Predicate predicate : predicateSet) {
          ColumnOperand<?> operand1 = predicate.getOperand1();
          ColumnOperand<?> operand2 = predicate.getOperand2();
          String colName1 = operand1.getColumn().getName();
          String colName2 = operand2.getColumn().getName();
          int o1 = operand1.getIndex();
          int o2 = operand2.getIndex();
          int i1 = o1 == 0 ? line1 : line2;
          int i2 = o2 == 0 ? line1 : line2;
          cellIdentifiers.add(i1 + "_" + colName1.toLowerCase());
          cellIdentifiers.add(i2 + "_" + colName2.toLowerCase());
        }
      }
    }
    log.info("cellIdentifiers : {}, {}", cellIdentifiers.size(),
        cellIdentifiers.stream().findAny());

    for (String c : cellIdentifiersOfChanges) {
      boolean contains = cellIdentifiers.contains(c);
      assertTrue(contains);
    }
  }

  @Test
  public void testExcludedDirtyLinesAreTrueErrorLines() throws IOException {
    // TODO: All errorLines are in excluded dirtyLines, or vise versa?
    List<TChange> changes = loadChanges(dirtyDataChangesPath);
    Set<Integer> changedLines = Sets.newHashSet();
    for (TChange change : changes) {
      changedLines.add(change.getLineIndex());
    }
    log.info("Changes={}", changedLines.size());

    Set<Integer> excludedLines = loadDirtyDataExcludedLines(dirtyDataExcludedPath);
    log.info("ExcludedLines={}", excludedLines.size());

    List<Integer> changesInExcludedLines = changedLines.stream()
        .filter(i -> excludedLines.contains(i))
        .collect(Collectors.toList());
    List<Integer> excludesInChangedLines = excludedLines.stream()
        .filter(i -> changedLines.contains(i))
        .collect(Collectors.toList());
    log.info("changesInExcludedLines={}, excludesInChangedLines={}", changesInExcludedLines.size(),
        excludesInChangedLines.size());
  }

}
