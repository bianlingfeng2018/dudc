package edu.xxx.utils;

import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author XXX
 */
@AllArgsConstructor
@Getter
@Deprecated
public class AffTuplesResult {

  private Set<Integer> affTuples;

  private List<List<String>> affLinesWithHeader;

}
