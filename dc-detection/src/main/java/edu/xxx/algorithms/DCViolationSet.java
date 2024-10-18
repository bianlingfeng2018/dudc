package edu.xxx.algorithms;

import com.google.common.collect.Sets;
import java.util.Set;
import lombok.Getter;

/**
 * @author XXX
 */
@Getter
public class DCViolationSet {

  private final Set<DCViolation> viosSet = Sets.newHashSet();

  public void add(DCViolation violation) {
    viosSet.add(violation);
  }

  public void remove(DCViolation violation) {
    viosSet.remove(violation);
  }

  public int size() {
    return viosSet.size();
  }
}
