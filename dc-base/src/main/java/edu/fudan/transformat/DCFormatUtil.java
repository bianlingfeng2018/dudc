package edu.fudan.transformat;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import edu.fudan.DCMinderToolsException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Lingfeng
 */
public class DCFormatUtil {

  public static DenialConstraint convertString2DC(String line,
      Map<String, ParsedColumn<?>> parsedColumnMap,
      List<String> columnNames) throws DCMinderToolsException {
    if (line == null) {
      throw new DCMinderToolsException("Illegal dc: null");
    }
    String exp = extractExpression(line);
    if (exp == null) {
      throw new DCMinderToolsException(String.format("Illegal dc: %s'", line));
    }
    String[] predicates = exp.split(Pattern.quote("^"));
    PredicateBitSet ps = new PredicateBitSet();
    for (String predicate : predicates) {
      // split has 5 elements: eg. ['City', '1', '=', 'City', '2']
      // 组装成DenialConstraint时，columnIndex要减1
      String[] split = splitPredicate(predicate);
      String lCol = split[0];
      int lColIndex = Integer.parseInt(split[1]) - 1;
      String op = split[2];
      String rCol = split[3];
      int rColIndex = Integer.parseInt(split[4]) - 1;
      if (!isLegalColumn(lCol, columnNames) ||
          !isLegalColumn(rCol, columnNames) ||
          !isLegalOperation(op) ||
          !isLegalIndex4DCObject(lColIndex) ||
          !isLegalIndex4DCObject(rColIndex)) {
        throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
      }
      ps.add(new Predicate(OperationStr.opString2ObjectMap.get(op),
          new ColumnOperand<>(parsedColumnMap.get(lCol), (lColIndex)),
          new ColumnOperand<>(parsedColumnMap.get(rCol), (rColIndex))));
    }
    return new DenialConstraint(ps);
  }

  public static String convertDC2String(DenialConstraint dc) throws DCMinderToolsException {
    List<String> predicates = Lists.newArrayList();
    PredicateBitSet predicateSet = dc.getPredicateSet();
    for (Predicate p : predicateSet) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      // 组装成字符串形式时，columnIndex要加1
      int col1Index = operand1.getIndex() + 1;
      int col2Index = operand2.getIndex() + 1;
      String col1Name = extractColName(operand1.getColumn().getName());
      String col2Name = extractColName(operand2.getColumn().getName());
      String op = OperationStr.opObject2StringMap.get(p.getOperator());
      if (!isLegalIndex4DCString(col1Index) || !isLegalIndex4DCString(col2Index)) {
        throw new DCMinderToolsException("Illegal column index for DC string");
      }
      String predicate = "t"
          + col1Index
          + "."
          + col1Name
          + op
          + "t"
          + col2Index
          + "."
          + col2Name;
      predicates.add(predicate);
    }
    return "not("
        + predicates.stream()
        .sorted()
        .collect(Collectors.joining("^"))
        + ")";
  }

  public static String extractColName(String columnWithBracket) {
    String colName = columnWithBracket.replaceAll("\\(.*?\\)", "");
    return colName;
  }

  public static String extractExpression(String input) {
    Pattern pattern = Pattern.compile("not\\((.*?)\\)");
    Matcher matcher = pattern.matcher(input);
    if (matcher.find()) {
      return matcher.group(1); // 返回匹配到的括号内的表达式
    } else {
      return null; // 没有匹配到
    }
  }

  private static String[] splitPredicate(String predicate) throws DCMinderToolsException {
    String[] result = new String[5];
    String operation = getOperation(predicate);
    String[] split = predicate.split(operation);
    if (split.length != 2) {
      throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
    }
    // Get column name and index from split[0] and split[1], eg. t1.City t2.City
    String[] res1 = getColumnNameAndIndex(split[0]);
    result[0] = res1[0];
    result[1] = res1[1];
    result[2] = operation;
    String[] res2 = getColumnNameAndIndex(split[1]);
    result[3] = res2[0];
    result[4] = res2[1];
    return result;
  }

  private static boolean isLegalColumn(String column, List<String> columnNames) {
    return columnNames.contains(column);
  }

  private static boolean isLegalOperation(String operation) {
    return Arrays.asList(OperationStr.legalOperations).contains(operation);
  }

  private static String[] getColumnNameAndIndex(String s) throws DCMinderToolsException {
    // result[0] is column name, result[1] is index
    if (s == null || (!s.contains("t1.") && !s.contains("t2."))) {
      throw new DCMinderToolsException(String.format("Illegal format: %s", s));
    }
    String[] result = new String[2];
    if (s.contains("t1.")) {
      result[0] = s.replace("t1.", "");
      result[1] = "1";
    } else if (s.contains("t2.")) {
      result[0] = s.replace("t2.", "");
      result[1] = "2";
    }
    return result;
  }

  private static String getOperation(String predicate)
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

  private static boolean isLegalIndex4DCObject(int columnIndex) {
    return columnIndex >= 0 && columnIndex <= 1;
  }

  private static boolean isLegalIndex4DCString(int columnIndex) {
    return columnIndex >= 1 && columnIndex <= 2;
  }

//  public static void replaceFirstLineForFCDC(List<List<String>> lines)
//      throws DCMinderToolsException {
//    List<String> oldHeads = lines.get(0);
//    List<String> newHeads = Lists.newArrayList();
//    for (String head : oldHeads) {
//      String newHead = convertToFCDCHead(head);
//      newHeads.add(newHead);
//    }
//    lines.set(0, newHeads);
//  }

//  private static String convertToFCDCHead(String head) throws DCMinderToolsException {
//    if (head == null) {
//      throw new DCMinderToolsException(String.format("Unknown head: %s", (Object) null));
//    }
//    String resultHead;
//    if (head.contains("String")) {
//      resultHead = head.replace("(String)", " str");
//    } else if (head.contains("Integer")) {
//      resultHead = head.replace("(Integer)", " int");
//    } else if (head.contains("Double")) {
//      resultHead = head.replace("(Double)", " float");
//    } else {
//      throw new DCMinderToolsException(String.format("Unknown head: %s", head));
//    }
//    return resultHead;
//  }


}
