package edu.fudan.utils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.algorithms.DCViolation;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Lingfeng
 */
public class DataUtil {

  public static Input generateNewCopy(String dataPath) {
    Input input = null;
    try {
      input = new Input(new DefaultFileInputGenerator(new File(dataPath)).generateNewCopy());
    } catch (FileNotFoundException | InputGenerationException | InputIterationException e) {
      throw new RuntimeException(e);
    }
    return input;
  }

  public static Set<DenialConstraint> getDCsSetFromViolations(Set<DCViolation> vioSet) {
    Set<DenialConstraint> dcs = Sets.newHashSet();
    for (DCViolation vio : vioSet) {
      dcs.addAll(vio.getDcs());
    }
    return dcs;
  }


  public static Map<DenialConstraint, Set<DCViolation>> getDCViosMapFromVios(
      Set<DCViolation> viosSet) {
    Map<DenialConstraint, Set<DCViolation>> dcViosMap = Maps.newHashMap();
    for (DCViolation vio : viosSet) {
      add2Map(vio, dcViosMap);
    }
    return dcViosMap;
  }

  private static void add2Map(DCViolation violation,
      Map<DenialConstraint, Set<DCViolation>> dcViosMap) {
    List<DenialConstraint> dcs = violation.getDcs();
    for (DenialConstraint dc : dcs) {
      addOrCreate(dc, violation, dcViosMap);
    }
  }

  private static void addOrCreate(DenialConstraint dc, DCViolation violation,
      Map<DenialConstraint, Set<DCViolation>> dcViosMap) {
    if (dcViosMap.containsKey(dc)) {
      dcViosMap.get(dc).add(violation);
    } else {
      dcViosMap.put(dc, Sets.newHashSet(violation));
    }
  }

}
