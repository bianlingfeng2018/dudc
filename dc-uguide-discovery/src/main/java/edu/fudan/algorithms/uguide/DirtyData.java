package edu.fudan.algorithms.uguide;

import static edu.fudan.utils.DataUtil.generateNewCopy;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.input.Input;
import java.util.Set;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DirtyData {

  /**
   * Line indices which are dirty. They should be excluded from dirty data to help discover ground
   * truth DCs.
   */
  private final Set<Integer> dirtyLines = Sets.newHashSet();

  private final String dataPath;
  private final Input input;
  private final String headerPath;

  public DirtyData(String dataPath, String headerPath) {
    this.dataPath = dataPath;
    this.input = generateNewCopy(dataPath);
    this.headerPath = headerPath;
  }
}
