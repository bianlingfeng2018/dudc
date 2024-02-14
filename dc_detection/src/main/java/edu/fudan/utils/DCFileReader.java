package edu.fudan.utils;

import static edu.fudan.algorithms.ExpressionToken.T1_POINT;
import static edu.fudan.algorithms.ExpressionToken.T2_POINT;
import static edu.fudan.algorithms.Operation.OPMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import edu.fudan.algorithms.Operation;
import edu.fudan.exceptions.DCDetectionException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Lingfeng
 */
public class DCFileReader {

  private final ParsedColumn<?>[] parsedColumns;
  private final List<String> headers = Lists.newArrayList();
  private final Map<String, ParsedColumn<?>> headerMap = Maps.newHashMap();

  public DCFileReader(ParsedColumn<?>[] parsedColumns) {
    this.parsedColumns = parsedColumns;
    initHeader();
  }

  private void initHeader() {
    for (ParsedColumn<?> pc : parsedColumns) {
      // eg. City(String) -> City
      String colName = pc.getName().replaceAll("\\(.*?\\)", "");
      headers.add(colName);
      headerMap.put(colName, pc);
    }
  }

  public DenialConstraintSet readDCsFromFile(File f) throws IOException, DCDetectionException {
    DenialConstraintSet dcs = new DenialConstraintSet();
    BufferedReader br = new BufferedReader(new FileReader(f));
    String line = null;
    while ((line = br.readLine()) != null) {
      DenialConstraint dc = convertLineToDC(line);
      dcs.add(dc);
    }
    br.close();
    return dcs;
  }

  private DenialConstraint convertLineToDC(String line) throws DCDetectionException {
    if (line == null) {
      throw new DCDetectionException("Illegal dc: null");
    }
    String exp = extractExpression(line);
    if (exp == null) {
      throw new DCDetectionException(String.format("Illegal dc: %s'", line));
    }
    String[] predicates = exp.split("\\^");
    PredicateBitSet ps = new PredicateBitSet();
    for (String predicate : predicates) {
      String[] split = splitPredicate(predicate);
      String lCol = split[0];
      String op = split[1];
      String rCol = split[2];
      if (!isLegalColumn(lCol) || !isLegalColumn(rCol) || !isLegalOperation(op)) {
        throw new DCDetectionException(String.format("Illegal predicate: %s", predicate));
      }
      ps.add(new Predicate(OPMap.get(op),
          new ColumnOperand<>(headerMap.get(lCol), 0),
          new ColumnOperand<>(headerMap.get(rCol), 1)));
    }
    return new DenialConstraint(ps);
  }

  private boolean isLegalColumn(String column) {
    return headers.contains(column);
  }

  private boolean isLegalOperation(String operation) {
    return Arrays.asList(Operation.legalOperations).contains(operation);
  }

  private String[] splitPredicate(String predicate) throws DCDetectionException {
    // TODO: 目前谓词表达式只考虑"t1.xxx operation t2.xxx"的情况，即t1在左边，t2在右边
    String[] result = new String[3];
    String operation = getOperation(predicate);
    String[] split = predicate.split(operation);
    if (split.length != 2 || !split[0].contains(T1_POINT) || !split[1].contains(T2_POINT)) {
      throw new DCDetectionException(String.format("Illegal predicate: %s", predicate));
    }
    result[0] = split[0].replace(T1_POINT, "");
    result[1] = operation;
    result[2] = split[1].replace(T2_POINT, "");
    return result;
  }

  private String getOperation(String predicate)
      throws DCDetectionException {
    String result = null;
    // 先判断= 再判断 != >= <=
    if (predicate.contains(Operation.UNEQUAL)) {
      result = Operation.UNEQUAL;
    } else if (predicate.contains(Operation.GREATER_EQUAL)) {
      result = Operation.GREATER_EQUAL;
    } else if (predicate.contains(Operation.LESS_EQUAL)) {
      result = Operation.LESS_EQUAL;
    } else if (predicate.contains(Operation.GREATER)) {
      result = Operation.GREATER;
    } else if (predicate.contains(Operation.LESS)) {
      result = Operation.LESS;
    } else if (predicate.contains(Operation.EQUAL)) {
      result = Operation.EQUAL;
    } else {
      throw new DCDetectionException("Unknown operation");
    }
    return result;
  }

  private String extractExpression(String input) {
    String patternString = "not\\((.*?)\\)";
    Pattern pattern = Pattern.compile(patternString);
    Matcher matcher = pattern.matcher(input);
    if (matcher.find()) {
      return matcher.group(1); // 返回匹配到的括号内的表达式
    } else {
      return null; // 没有匹配到
    }
  }

}
