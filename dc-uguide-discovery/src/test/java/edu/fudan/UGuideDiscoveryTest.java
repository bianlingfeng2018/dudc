package edu.fudan;

import static edu.fudan.conf.DefaultConf.maxInCluster;
import static edu.fudan.conf.DefaultConf.topKOfCluster;
import static edu.fudan.utils.DCUtil.getCellIdentifiersOfChanges;
import static edu.fudan.utils.DCUtil.getCellIdentyfiersFromVios;
import static edu.fudan.utils.DCUtil.getDCVioSizeMap;
import static edu.fudan.utils.DCUtil.getErrorLinesContainingChanges;
import static edu.fudan.utils.DCUtil.loadChanges;
import static edu.fudan.utils.DCUtil.loadDirtyDataExcludedLines;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import ch.javasoft.bitset.search.NTreeSearch;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateSetFactory;
import de.metanome.algorithm_integration.Operator;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import edu.fudan.algorithms.BasicDCGenerator;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.DiscoveryEntry;
import edu.fudan.algorithms.HydraDetector;
import edu.fudan.algorithms.TupleSampler;
import edu.fudan.algorithms.TupleSampler.SampleResult;
import edu.fudan.algorithms.UGuideDiscovery;
import edu.fudan.algorithms.uguide.DirtyData;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.DCUtil;
import edu.fudan.utils.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
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
  private final String changesPath = baseDir + File.separator +
      "preprocessed_data\\preprocessed_hospital_changes.csv";
  private final String excludedLinesPath = baseDir + File.separator +
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
  private final String groundTruthDCsInjectErrorPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_ground_inject_error.out";
  private final String candidateDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate.out";
  private final String trueDCsPath = baseDir + File.separator +
      "result_rules\\dcs_hospital_candidate_true.out";

  @Test
  public void testOneRoundUGuide()
      throws InputGenerationException, InputIterationException, IOException, DCMinderToolsException {
    UGuideDiscovery ud = new UGuideDiscovery(cleanDataPath,
        changesPath,
        dirtyDataPath,
        excludedLinesPath,
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
  public void testGenGroundTruthDCsUsingDCFinder() {
    BasicDCGenerator generator = new BasicDCGenerator(sampledDataPath,
        dcsPathForFCDC, headerPath);
    generator.setExcludeDCs(new HashSet<>());
    generator.setErrorThreshold(0.001);
    Set<DenialConstraint> dcs = generator.generateDCsForUser();
    log.info("DCs size={}", dcs.size());
  }

  @Test
  public void testGenTopKDCs() throws IOException {
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(5, dcsPathForFCDC, headerPath, null);
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
  public void testDCsViolationsSizeCompare()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    // TODO: Violation size: tureDcs,candiDCs,gtDCs并无大小关系，因为冲突之间可能有元组对的重合，一对元组可能涉及多个冲突
    DCViolationSet vioSet1 = new HydraDetector(dirtyDataPath, candidateDCsPath).detect();
    DCViolationSet vioSet2 = new HydraDetector(dirtyDataPath, trueDCsPath).detect();
    DCViolationSet vioSet3 = new HydraDetector(dirtyDataPath, groundTruthDCsPath).detect();
    log.info("candi={}, candiTure={}, gt={}", vioSet1.size(), vioSet2.size(), vioSet3.size());
  }

  @Test
  public void testFalseDCsDetect()
      throws DCMinderToolsException, InputGenerationException, InputIterationException, IOException {
    // TODO: Violation size: tureDcs,candiDCs,gtDCs并无大小关系，因为冲突之间可能有元组对的重合，一对元组可能涉及多个冲突
//    String path = groundTruthDCsPath;
    String path = groundTruthDCsInjectErrorPath;
    DCViolationSet vios1 = new HydraDetector(cleanDataPath, path).detect();
    DCViolationSet vios2 = new HydraDetector(dirtyDataPath, path).detect();
    log.info("vios1={}, vios2={}", vios1.size(), vios2.size());
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
    String changesPath = this.changesPath;
    List<TChange> changes = loadChanges(changesPath);
    Set<TCell> cellsOfChanges = getCellIdentifiersOfChanges(changes);
    log.info("Cells of Changes: {}, {}", cellsOfChanges.size(), cellsOfChanges.stream().findAny());
    // Detect vios of DC
    DCViolationSet vioSetOfGroundTruthDCs = new HydraDetector(dirtyDataPath,
        groundTruthDCsPath).detect();
    DCViolationSet vioSetOfTrueDCs = new HydraDetector(dirtyDataPath, trueDCsPath).detect();
    Input di = new DirtyData(dirtyDataPath, excludedLinesPath, headerPath).getInput();
    Set<TCell> cellsFromGTVios = getCellIdentyfiersFromVios(vioSetOfGroundTruthDCs.getViosSet(),
        di);
    Set<TCell> cellsFromTrueVios = getCellIdentyfiersFromVios(vioSetOfTrueDCs.getViosSet(), di);
    log.info("Cells of GTDCs: {}, {}", cellsFromGTVios.size(),
        cellsFromGTVios.stream().findAny());
    log.info("Cells of TrueDCs: {}, {}", cellsFromTrueVios.size(),
        cellsFromTrueVios.stream().findAny());
    // All error cells are found in gtDCs
    assertTrue(cellsFromGTVios.containsAll(cellsOfChanges));
    // All error cells are found in trueDCs
    assertTrue(cellsFromTrueVios.containsAll(cellsOfChanges));

    for (TCell c : cellsFromGTVios) {
      if (cellsOfChanges.contains(c)) {
        log.info("Example true error cell in cellsFromGTVios: {}", c.toString());
        break;
      }
    }
    for (TCell c : cellsFromTrueVios) {
      if (cellsOfChanges.contains(c)) {
        log.info("Example true error cell in cellsFromTrueVios: {}", c.toString());
        break;
      }
    }
  }

  @Test
  public void testExcludedDirtyLinesAreTrueErrorLines() throws IOException {
    // TODO: All errorLines are in excluded dirtyLines, or vise versa?
    List<TChange> changes = loadChanges(changesPath);
    Set<Integer> changedLines = Sets.newHashSet();
    for (TChange change : changes) {
      changedLines.add(change.getLineIndex());
    }
    log.info("Changes={}", changedLines.size());

    Set<Integer> excludedLines = loadDirtyDataExcludedLines(excludedLinesPath);
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

  @Test
  public void testErrorLinesInSample()
      throws InputGenerationException, IOException, InputIterationException {
    //Changes
    List<TChange> changes = loadChanges(changesPath);
    Set<Integer> errorLinesContainingChanges = getErrorLinesContainingChanges(changes);
    log.info("Changes={}, errorLinesContainingChanges={}", changes.size(),
        errorLinesContainingChanges.size());
    // SampleResult
    log.info("Sampling...");
    SampleResult sampleResult = new TupleSampler()
        .sample(new File(dirtyDataPath), topKOfCluster, maxInCluster,
            null, true, null);
    List<List<String>> linesWithHeader = sampleResult.getLinesWithHeader();
    log.info("Write {} lines(with header line) to file: {}", linesWithHeader.size(),
        sampledDataPath);
    FileUtil.writeListLinesToFile(linesWithHeader, new File(sampledDataPath));
    Set<Integer> errorLinesInSample = sampleResult.getLineIndices().stream()
        .filter(i -> errorLinesContainingChanges.contains(i)).collect(
            Collectors.toSet());
    log.info("ErrorLinesInSample/SampledLineIndices: {}/{}",
        errorLinesInSample.size(), sampleResult.getLineIndices().size());
    log.info("ErrorLinesInSample: {}", errorLinesInSample);  // [2336, 2481, 2456, 2506]
  }

}
