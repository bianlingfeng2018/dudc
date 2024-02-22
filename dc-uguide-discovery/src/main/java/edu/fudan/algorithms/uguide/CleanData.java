package edu.fudan.algorithms.uguide;

import static edu.fudan.utils.DataUtil.generateNewCopy;

import de.hpi.naumann.dc.input.Input;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class CleanData {

  private final String dataPath;
  private final Input input;
  private final String headerPath;

  public CleanData(String dataPath, String headerPath) {
    this.dataPath = dataPath;
    this.input = generateNewCopy(dataPath);
    this.headerPath = headerPath;
  }
}
