package edu.fudan.utils;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.transformat.DCFormatUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    log.debug("Read dcs size = {}", dcList.size());
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

}
