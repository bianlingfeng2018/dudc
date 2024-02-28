package edu.fudan.utils;

import static edu.fudan.conf.DefaultConf.topK;

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
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class DCUtil {

  public static List<DenialConstraint> generateTopKDCs(int topK, String inputDCsPath,
      String headerPath, Set<DenialConstraint> excludedDCs) {
    List<DenialConstraint> dcList = DCLoader.load(headerPath, inputDCsPath, excludedDCs);
    int excludeSize = excludedDCs == null ? 0 : excludedDCs.size();
    log.debug("Read dcs size = {}, excluded(visited) dcs size = {}", dcList.size(), excludeSize);
    dcList.sort((o1, o2) -> {
      return Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
    });
    List<DenialConstraint> topKDCs = dcList.subList(0, Math.min(topK, dcList.size()));
    return topKDCs;
  }

  public static void persistTopKDCs(List<DenialConstraint> topKDCs, String topKDCsPath)
      throws IOException {
    List<String> result = new ArrayList<>();
    for (DenialConstraint dc : topKDCs) {
      String s = DCFormatUtil.convertDC2String(dc);
      result.add(s);
    }
    log.debug("Write top-{} DCs to file: {}", topK, topKDCsPath);
    FileUtil.writeStringLinesToFile(result, new File(topKDCsPath));
  }


  public static Map<DenialConstraint, Integer> getDCVioSizeMap(
      DCViolationSet vioSet) {
    Map<DenialConstraint, Integer> dcViosSizeMap = Maps.newHashMap();
    for (DCViolation vio : vioSet.getViosSet()) {
      List<DenialConstraint> dcs = vio.getDcs();
      for (DenialConstraint dc : dcs) {
        if (dcViosSizeMap.containsKey(dc)) {
          Integer count = dcViosSizeMap.get(dc);
          dcViosSizeMap.put(dc, count + 1);
        } else {
          dcViosSizeMap.put(dc, 1);
        }
      }
    }
    return dcViosSizeMap;
  }

  public static List<TChange> loadChanges(String dirtyDataChangesPath) throws IOException {
    List<TChange> changes = Lists.newArrayList();
    BufferedReader br = new BufferedReader(new FileReader(dirtyDataChangesPath));
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
    return changes;
  }

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

  public static Set<TCell> getCellIdentifiersOfChanges(List<TChange> changes) {
    Set<TCell> cellIdentifiersOfChanges = Sets.newHashSet();
    for (TChange c : changes) {
      cellIdentifiersOfChanges.add(
          new TCell(c.getLineIndex(), c.getAttribute(), c.getWrongValue()));
    }
    return cellIdentifiersOfChanges;
  }

  public static Set<TCell> getCellIdentyfiersFromVios(Set<DCViolation> vioSet, Input di) {
    Set<TCell> cellIdentifiers = Sets.newHashSet();
    for (DCViolation vio : vioSet) {
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
          int li1 = o1 == 0 ? line1 : line2;
          int li2 = o2 == 0 ? line1 : line2;
          Comparable<?> value1 = getCellValue(operand1, di, line1, line2);
          Comparable<?> value2 = getCellValue(operand2, di, line1, line2);
          cellIdentifiers.add(new TCell(li1, colName1.toLowerCase(), value1.toString()));
          cellIdentifiers.add(new TCell(li2, colName2.toLowerCase(), value2.toString()));
        }
      }
    }
    return cellIdentifiers;
  }


  public static Set<Integer> getErrorLinesContainingChanges(List<TChange> changes) {
    HashSet<Integer> results = Sets.newHashSet();
    for (TChange change : changes) {
      results.add(change.getLineIndex());
    }
    return results;
  }

  public static Comparable getCellValue(ColumnOperand<?> operand, Input di, int line1, int line2) {
    int index = operand.getIndex();
    String n = operand.getColumn().getName();
    String t = operand.getColumn().getType().getSimpleName();
    String nameWithBracket = n + "(" + t + ")";
    List<ParsedColumn<?>> filtered = Arrays.stream(di.getColumns())
        .filter(col -> col.getName().equals(nameWithBracket))
        .collect(Collectors.toList());
    ParsedColumn<?> column = filtered.get(0);
    Comparable value = column.getValue(index == 0 ? line1 : line2);
    return value;
  }
}
