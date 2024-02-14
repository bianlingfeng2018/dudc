package edu.fudan;

import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class HydraInputTest {
  private final String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data";
  @Test
  public void testReadColumns()
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    String dataFile = baseDir + File.separator + "preprocessed_tax.csv";
    File dataF = new File(dataFile);
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(dataF);
    RelationalInput ri = actualGenerator.generateNewCopy();
    List<String> columnNames = ri.columnNames();
    log.debug("File column names = {}", columnNames.toString());

    Input input = new Input(ri);
    int lineCount = input.getLineCount();
    ParsedColumn<?>[] columns = input.getColumns();
    log.debug("Input columns(with type) = {}",
        Arrays.stream(columns).map(c -> "\n" + c.getName() + "(" + c.getType() + ")").collect(Collectors.joining()));
    log.debug("Line count = {}", lineCount);
  }


}
