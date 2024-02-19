package edu.fudan.algorithms;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DCViolationSet {

  private final Map<DenialConstraint, Set<DCViolation>> dcViosMap = Maps.newHashMap();

  public Set<DCViolation> getViosSet() {
    Collection<Set<DCViolation>> values = dcViosMap.values();
    Set<DCViolation> mergedSet = mergeSets(values);
    return mergedSet;
  }

  public int size() {
    Collection<Set<DCViolation>> values = dcViosMap.values();
    Set<DCViolation> mergedSet = mergeSets(values);
    return mergedSet.size();
  }

  public void add(DCViolation violation) {
    List<DenialConstraint> dcs = violation.getDcs();
    for (DenialConstraint dc : dcs) {
      addOrCreate(dc, violation, dcViosMap);
    }

  }

  private void addOrCreate(DenialConstraint dc, DCViolation violation,
      Map<DenialConstraint, Set<DCViolation>> dcViosMap) {
    if (dcViosMap.containsKey(dc)) {
      dcViosMap.get(dc).add(violation);
    } else {
      dcViosMap.put(dc, Sets.newHashSet(violation));
    }
  }

  public static Set<DCViolation> mergeSets(Collection<Set<DCViolation>> collectionOfSets) {
    Iterable<DCViolation> concatenated = Iterables.concat(collectionOfSets);
    return Sets.newHashSet(concatenated);
  }
}
