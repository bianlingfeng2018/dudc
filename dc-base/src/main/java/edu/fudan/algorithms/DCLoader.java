package edu.fudan.algorithms;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.DCMinderToolsException;
import edu.fudan.transformat.DCFormatUtil;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * @author Lingfeng
 */
public class DCLoader {

  public static List<DenialConstraint> load(String headerFile, String dcsFile) {
    List<DenialConstraint> dcsWithoutData = Lists.newArrayList();
    // Read dcs from file
    BufferedReader br1 = null;
    BufferedReader br2 = null;
    try {
      br1 = new BufferedReader(new FileReader(headerFile));
      String header = br1.readLine();

      br2 = new BufferedReader(new FileReader(dcsFile));
      String line = null;
      while ((line = br2.readLine()) != null) {
        DenialConstraint dc = DCFormatUtil.convertString2DC(line, header);
        dcsWithoutData.add(dc);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        br1.close();
        br2.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return dcsWithoutData;
  }


}
