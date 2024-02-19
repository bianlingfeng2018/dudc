package edu.fudan.transformat;


import static edu.fudan.transformat.FormatUtil.extractColName;
import static edu.fudan.transformat.FormatUtil.extractExpression;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import edu.fudan.DCMinderToolsException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Lingfeng
 */
public class DCFileReader {
  private final String dataPath;
  private final String dcsPath;
  private final List<String> headers = Lists.newArrayList();
  private final Map<String, ParsedColumn<?>> headerMap = Maps.newHashMap();

  public DCFileReader(String dataPath, String dcsPath) {
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
        headers.add(colName);
        headerMap.put(colName, pc);
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
      DenialConstraint dc = convertLineToDC(line);
      dcs.add(dc);
    }
    br.close();
    return dcs;
  }

  private DenialConstraint convertLineToDC(String line) throws DCMinderToolsException {
    if (line == null) {
      throw new DCMinderToolsException("Illegal dc: null");
    }
    String exp = extractExpression(line);
    if (exp == null) {
      throw new DCMinderToolsException(String.format("Illegal dc: %s'", line));
    }
    String[] predicates = exp.split(Pattern.quote("^"));
    PredicateBitSet ps = new PredicateBitSet();
    // TODO: Now we only consider predicate format: t1.xxx is left and t2.xxx is right
    for (String predicate : predicates) {
      String[] split = splitPredicate(predicate);
      String lCol = split[0];
      String op = split[1];
      String rCol = split[2];
      if (!isLegalColumn(lCol) || !isLegalColumn(rCol) || !isLegalOperation(op)) {
        throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
      }
      ps.add(new Predicate(OperationStr.str2OperatorMap.get(op),
          new ColumnOperand<>(headerMap.get(lCol), 0),
          new ColumnOperand<>(headerMap.get(rCol), 1)));
    }
    return new DenialConstraint(ps);
  }

  private boolean isLegalColumn(String column) {
    return headers.contains(column);
  }

  private boolean isLegalOperation(String operation) {
    return Arrays.asList(OperationStr.legalOperations).contains(operation);
  }

  private String[] splitPredicate(String predicate) throws DCMinderToolsException {
    String[] result = new String[3];
    String operation = getOperation(predicate);
    String[] split = predicate.split(operation);
    if (split.length != 2 || !split[0].contains("t1.") || !split[1].contains("t2.")) {
      throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
    }
    result[0] = split[0].replace("t1.", "");
    result[1] = operation;
    result[2] = split[1].replace("t2.", "");
    return result;
  }

  private String getOperation(String predicate)
      throws DCMinderToolsException {
    String result = null;
    // 先判断= 再判断 != >= <=
    if (predicate.contains(OperationStr.unequal)) {
      result = OperationStr.unequal;
    } else if (predicate.contains(OperationStr.greaterEqual)) {
      result = OperationStr.greaterEqual;
    } else if (predicate.contains(OperationStr.lessEqual)) {
      result = OperationStr.lessEqual;
    } else if (predicate.contains(OperationStr.greater)) {
      result = OperationStr.greater;
    } else if (predicate.contains(OperationStr.less)) {
      result = OperationStr.less;
    } else if (predicate.contains(OperationStr.equal)) {
      result = OperationStr.equal;
    } else {
      throw new DCMinderToolsException("Unknown operation");
    }
    return result;
  }

}
