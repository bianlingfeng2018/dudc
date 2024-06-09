package edu.fudan.utils;

import static edu.fudan.transformat.DCFormatUtil.extractColumnNameType;
import static edu.fudan.transformat.DCFormatUtil.isLegalIndex4DCString;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class DCUtil {

  /**
   * Generate the top-k dcs after excluding some dcs
   *
   * @param topK
   * @param inputDCsPath
   * @param headerPath
   * @param excludedDCs
   * @return
   */
  public static List<DenialConstraint> generateTopKDCs(int topK, String inputDCsPath,
      String headerPath, Set<DenialConstraint> excludedDCs) {
    List<DenialConstraint> dcList = DCLoader.load(headerPath, inputDCsPath, excludedDCs);
    int excludeSize = excludedDCs == null ? 0 : excludedDCs.size();
    dcList.sort((o1, o2) -> {
      return Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
    });
    List<DenialConstraint> topKDCs = dcList.subList(0, Math.min(topK, dcList.size()));
    log.debug("Read dcs size = {}, excluded(visited) dcs size = {}, return topK dcs size = {}",
        dcList.size(), excludeSize, topKDCs.size());
    return topKDCs;
  }

  /**
   * Save top-k dcs to file
   *
   * @param topKDCs
   * @param topKDCsPath
   */
  public static void persistTopKDCs(List<DenialConstraint> topKDCs, String topKDCsPath) {
    List<String> result = new ArrayList<>();
    for (DenialConstraint dc : topKDCs) {
      String s = DCFormatUtil.convertDC2String(dc);
      result.add(s);
    }
    log.debug("Write top-{} DCs to file: {}", result.size(), topKDCsPath);
    FileUtil.writeStringLinesToFile(result, new File(topKDCsPath));
  }

  /**
   * Load changes from file
   *
   * @param dirtyDataChangesPath
   * @return
   */
  public static List<TChange> loadChanges(String dirtyDataChangesPath) {
    List<TChange> changes = Lists.newArrayList();
    BufferedReader br = null;
    try {
      br = new BufferedReader(new FileReader(dirtyDataChangesPath));
      // Skip header
      br.readLine();
      String line;
      while ((line = br.readLine()) != null) {
        String[] split = line.split(",");
        String cellIdentifier = split[0];
        String[] oidAttr = cellIdentifier.split("\\.");
        int oid = Integer.parseInt(oidAttr[0]);
        String attr = oidAttr[1];
        String wrongVal = split[1];
        String correctVal = split[2];
        changes.add(new TChange(oid, oid - 1, attr, wrongVal, correctVal));
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (br != null) {
          br.close();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return changes;
  }

  /**
   * Load excluded lines from file
   *
   * @param dirtyDataExcludedPath
   * @return
   * @throws IOException
   */
  public static Set<Integer> loadDirtyDataExcludedLines(String dirtyDataExcludedPath)
      throws IOException {
    Set<Integer> dirtyLines = Sets.newHashSet();
    BufferedReader br = new BufferedReader(new FileReader(dirtyDataExcludedPath));
    String line;
    while ((line = br.readLine()) != null) {
      String[] split = line.split("~");
      int lineIndex = Integer.parseInt(split[0]);
      dirtyLines.add(lineIndex);
    }
    return dirtyLines;
  }

  public static Set<TCell> getCellsOfChanges(List<TChange> changes) {
    Set<TCell> cellIdentifiersOfChanges = Sets.newHashSet();
    for (TChange c : changes) {
      cellIdentifiersOfChanges.add(
          new TCell(c.getLineIndex(), c.getAttribute(), c.getWrongValue()));
    }
    return cellIdentifiersOfChanges;
  }

  public static Set<TCell> getCellsOfViolations(Set<DCViolation> vioSet, Input di) {
    Set<TCell> cells = Sets.newHashSet();
    for (DCViolation vio : vioSet) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcNoData = vio.getDenialConstraintsNoData();
      if (dcNoData.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcNoData.get(0);
      Set<TCell> cellsOfViolation = getCellsOfViolation(di, dc, linePair);
      cells.addAll(cellsOfViolation);
    }
    return cells;
  }

  /**
   * 获取冲突的单元格，单元格值直接从hydraDC中读取
   *
   * @param hydraDC
   * @param linePair
   * @return
   */
  public static Set<TCell> getCellsOfViolation(DenialConstraint hydraDC, LinePair linePair) {
    Set<TCell> cells = new HashSet<>();
    int line1 = linePair.getLine1();
    int line2 = linePair.getLine2();
    PredicateBitSet predicateSet = hydraDC.getPredicateSet();
    for (Predicate predicate : predicateSet) {
      ColumnOperand<?> operand1 = predicate.getOperand1();
      ColumnOperand<?> operand2 = predicate.getOperand2();
      String colName1 = operand1.getColumn().getName();
      String colName2 = operand2.getColumn().getName();

      // 直接读取值v1 v2
      Comparable<?> v1 = operand1.getValue(line1, line2);
      Comparable<?> v2 = operand2.getValue(line1, line2);

      String v1String = v1.toString();
      String v2String = v2.toString();
      String col1Lowercase = extractColumnNameType(colName1)[0];
      String col2Lowercase = extractColumnNameType(colName2)[0];

      cells.add(new TCell(line1, col1Lowercase.toLowerCase(), v1String));
      cells.add(new TCell(line2, col2Lowercase.toLowerCase(), v2String));
    }
    return cells;
  }

  /**
   * 获取冲突的单元格，单元格值间接从input中读取
   *
   * @param di
   * @param dcNoData
   * @param linePair
   * @return
   */
  public static Set<TCell> getCellsOfViolation(Input di, DenialConstraint dcNoData,
      LinePair linePair) {
    Set<TCell> cellsOfViolation = Sets.newHashSet();
    int line1 = linePair.getLine1();
    int line2 = linePair.getLine2();
    PredicateBitSet predicateSet = dcNoData.getPredicateSet();
    for (Predicate predicate : predicateSet) {
      ColumnOperand<?> operand1 = predicate.getOperand1();
      ColumnOperand<?> operand2 = predicate.getOperand2();
      String colName1 = operand1.getColumn().getName();
      String colName2 = operand2.getColumn().getName();

      // 从Input间接读取值v1 v2
      int index1 = operand1.getIndex();
      int index2 = operand2.getIndex();
      String colType1 = operand1.getColumn().getType().getSimpleName();
      String colType2 = operand2.getColumn().getType().getSimpleName();
      if (index1 != 0 || index2 != 1) {
        throw new RuntimeException("Illegal operand index");
      }
      Comparable<?> v1 = getCellValue(colName1, colType1, line1, di);
      Comparable<?> v2 = getCellValue(colName2, colType2, line2, di);

      String v1String = v1.toString();
      String v2String = v2.toString();
      cellsOfViolation.add(new TCell(line1, colName1.toLowerCase(), v1String));
      cellsOfViolation.add(new TCell(line2, colName2.toLowerCase(), v2String));
    }
    return cellsOfViolation;
  }


  public static Set<Integer> getErrorLinesContainingChanges(List<TChange> changes) {
    HashSet<Integer> results = Sets.newHashSet();
    for (TChange change : changes) {
      results.add(change.getLineIndex());
    }
    return results;
  }

  public static Comparable getCellValue(String columnName, String columnType, int lineIndex,
      Input di) {
    String nameWithBracket = columnName + "(" + columnType + ")";
    ParsedColumn<?>[] columns = di.getColumns();
    ParsedColumn<?> column = null;
    for (ParsedColumn<?> col : columns) {
      if (Objects.equals(col.getName(), nameWithBracket)) {
        column = col;
      }
    }
    if (column == null) {
      throw new RuntimeException(String.format("Column not found: %s", columnName));
    }
    Comparable value = column.getValue(lineIndex);
    return value;
  }

  /**
   * 将DCFinder发现的规则转换为字符串形式
   *
   * @param dcFinderDC
   * @return
   */
  public static String convertDCFinderDC2Str(
      de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dcFinderDC) {
    List<String> predicates = Lists.newArrayList();
    PredicateSet predicateSet = dcFinderDC.getPredicateSet();
    for (de.metanome.algorithms.dcfinder.predicates.Predicate p : predicateSet) {
      de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand<?> operand1 = p.getOperand1();
      de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand<?> operand2 = p.getOperand2();
      int operand1Index = operand1.getIndex() + 1;
      int operand2Index = operand2.getIndex() + 1;
      String col1Name = extractColumnNameType(operand1.getColumn().getName())[0];
      String col2Name = extractColumnNameType(operand2.getColumn().getName())[0];
      String op = DCFormatUtil.convertOperator2String(p.getOperator());
      if (!isLegalIndex4DCString(operand1Index) ||
          !isLegalIndex4DCString(operand2Index)) {
        throw new RuntimeException("Illegal column index for DC string");
      }
      String predicate = "t"
          + operand1Index
          + "."
          + col1Name
          + op
          + "t"
          + operand2Index
          + "."
          + col2Name;
      predicates.add(predicate);
    }
    String dcStr = "not("
        + predicates.stream()
        .sorted()
        .collect(Collectors.joining("^"))
        + ")";
    return dcStr;
  }

  /**
   * 根据changes，按行和列存储正确值
   *
   * @param dirtyDataPath
   * @param changes
   * @return
   * @throws InputGenerationException
   * @throws FileNotFoundException
   */
  public static Map<Integer, Map<Integer, String>> genLineChangesMap(String dirtyDataPath,
      List<TChange> changes)
      throws InputGenerationException, FileNotFoundException {
    RelationalInput ri = new DefaultFileInputGenerator(
        new File(dirtyDataPath)).generateNewCopy();
    List<String> columnNames = ri.columnNames();
    try {
      ri.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    List<String> columnNamesSimple = columnNames.stream()
        .map(col -> extractColumnNameType(col)[0].toLowerCase())
        .collect(Collectors.toList());
    // 行-<列索引-列正确值>
    Map<Integer, Map<Integer, String>> lineChangesMap = Maps.newHashMap();
    for (TChange change : changes) {
      int lineIndex = change.getLineIndex();
      String attribute = change.getAttribute();
      String correctValue = change.getCorrectValue();
      int colIndex = columnNamesSimple.indexOf(attribute);
      if (colIndex < 0) {
        throw new RuntimeException("Illegal column");
      }
      if (lineChangesMap.containsKey(lineIndex)) {
        Map<Integer, String> lineChanges = lineChangesMap.get(lineIndex);
        if (lineChanges.containsKey(colIndex)) {
          throw new RuntimeException("Duplicated changes");
        }
        lineChanges.put(colIndex, correctValue);
      } else {
        Map<Integer, String> lineChanges = Maps.newHashMap();
        lineChanges.put(colIndex, correctValue);
        lineChangesMap.put(lineIndex, lineChanges);
      }
    }

    return lineChangesMap;
  }

  /**
   * 统计每个规则关联多少冲突
   *
   * @param vioSet 冲突集合
   */
  public static void printDCViolationsMap(DCViolationSet vioSet) {
    log.debug("Print DC-Violations map:");
    Map<String, Integer> dcViolationMap = Maps.newHashMap();
    if (vioSet.size() != 0) {
      Set<DCViolation> viosSet = vioSet.getViosSet();
      for (DCViolation vio : viosSet) {
        List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
        if (dcs.size() != 1) {
          throw new RuntimeException("Illegal dcs size");
        }
        DenialConstraint dc = dcs.get(0);
        // Key为dcString
        String dcString = DCFormatUtil.convertDC2String(dc);
        if (dcViolationMap.containsKey(dcString)) {
          Integer i = dcViolationMap.get(dcString);
          dcViolationMap.put(dcString, i + 1);
        } else {
          dcViolationMap.put(dcString, 1);
        }
      }
    }
    for (String dcString : dcViolationMap.keySet()) {
      log.debug("{}->{}", dcString, dcViolationMap.get(dcString));
    }
  }
}
