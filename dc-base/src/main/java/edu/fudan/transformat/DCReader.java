package edu.fudan.transformat;


import static edu.fudan.transformat.DCFormatUtil.convertString2DC;
import static edu.fudan.transformat.DCFormatUtil.extractColName;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.DCMinderToolsException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author Lingfeng
 */
public class DCReader {

  private final String dataPath;
  private final String dcsPath;
  private final List<String> colNames = Lists.newArrayList();
  private final Map<String, ParsedColumn<?>> parsedColMap = Maps.newHashMap();

  public DCReader(String dataPath, String dcsPath) {
    this.dataPath = dataPath;
    this.dcsPath = dcsPath;
    initHeader();
  }

  private void initHeader() {
    DefaultFileInputGenerator actualGenerator = null;
    try {
      actualGenerator = new DefaultFileInputGenerator(new File(dataPath));
      Input input = new Input(actualGenerator.generateNewCopy());
      ParsedColumn<?>[] parsedColumns = input.getColumns();
      for (ParsedColumn<?> pc : parsedColumns) {
        // eg. City(String) -> City
        String colName = extractColName(pc.getName());
        colNames.add(colName);
        parsedColMap.put(colName, pc);
      }
    } catch (FileNotFoundException | InputGenerationException | InputIterationException e) {
      throw new RuntimeException(e);
    }
  }

  public DenialConstraintSet readDCsFromFile() throws IOException, DCMinderToolsException {
    DenialConstraintSet dcs = new DenialConstraintSet();
    BufferedReader br = new BufferedReader(new FileReader(dcsPath));
    String line = null;
    while ((line = br.readLine()) != null) {
      DenialConstraint dc = convertString2DC(line, parsedColMap, colNames);
      dcs.add(dc);
    }
    br.close();
    return dcs;
  }

}
