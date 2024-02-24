package edu.fudan.utils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.algorithms.DCViolationSet;
import edu.fudan.algorithms.uguide.TChange;
import edu.fudan.transformat.DCFormatUtil;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class DCUtil {

  public static List<DenialConstraint> generateTopKDCs(int topK, String dcsPath,
      String headerPath, Set<DenialConstraint> excludedDCs) {
    List<DenialConstraint> dcList = DCLoader.load(headerPath, dcsPath, excludedDCs);
    log.info("Read dcs size = {}, excluded dcs size = {}", dcList.size(), excludedDCs.size());
    dcList.sort((o1, o2) -> {
      return Integer.compare(o1.getPredicateCount(), o2.getPredicateCount());
    });
    log.debug("Sorted dcs: {}", dcList);
    List<DenialConstraint> topKDCs = dcList.subList(0, topK);
    return topKDCs;
  }

  public static void persistTopKDCs(List<DenialConstraint> topKDCs, String topKDCsPath)
      throws IOException {
    List<String> result = new ArrayList<>();
    for (DenialConstraint dc : topKDCs) {
      String s = DCFormatUtil.convertDC2String(dc);
      result.add(s);
    }
    log.info("Write top-k dcs to file {}", topKDCsPath);
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
}
