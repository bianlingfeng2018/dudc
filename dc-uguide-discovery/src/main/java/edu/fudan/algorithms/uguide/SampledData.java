package edu.fudan.algorithms.uguide;

import de.hpi.naumann.dc.input.Input;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class SampledData {

  private final String dataPath;

  public SampledData(String dataPath) {
    this.dataPath = dataPath;
  }

  public Input generateNewCopy()
      throws InputGenerationException, FileNotFoundException, InputIterationException {
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(new File(dataPath));
    return new Input(actualGenerator.generateNewCopy());
  }
}
