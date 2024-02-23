package edu.fudan.algorithms.uguide;

import static edu.fudan.utils.DataUtil.generateNewCopy;

import de.hpi.naumann.dc.input.Input;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class DirtyData {

  private final String dataPath;
  private final String dataDirtyLinesPath;
  private final Input input;
  private final String headerPath;

  public DirtyData(String dataPath, String dataDirtyLinesPath, String headerPath) {
    this.dataPath = dataPath;
    this.dataDirtyLinesPath = dataDirtyLinesPath;
    this.headerPath = headerPath;
    this.input = generateNewCopy(dataPath);
  }
}
