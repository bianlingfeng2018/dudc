package edu.xxx.transformat;

import static edu.xxx.conf.DefaultConf.defaultTable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.metanome.algorithm_integration.Operator;
import edu.xxx.DCMinderToolsException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author XXX
 */
public class DCFormatUtil {

  /**
   * 将规则字符串转换成规则对象
   *
   * @param dcStr  规则字符串
   * @param header 表头
   * @return 规则对象
   */
  public static DenialConstraint convertString2DC(String dcStr, String header) {
    try {
      if (dcStr == null || header == null) {
        throw new DCMinderToolsException(
            String.format("Illegal dc or header: %s, %s", dcStr, header));
      }
      String exp = extractExpression(dcStr);
      if (exp == null) {
        throw new DCMinderToolsException(String.format("Illegal dc expression: %s", dcStr));
      }
      // 准备colTypeMap
      List<String> colNames = Lists.newArrayList();
      Map<String, Class> colTypeMap = Maps.newHashMap();
      String[] headerSplit = header.split(",");
      for (String s : headerSplit) {
        String[] ss = extractColumnNameType(s);
        String colName = ss[0];
        String colType = ss[1];
        colNames.add(colName);
        colTypeMap.put(colName, Class.forName("java.lang." + colType));
      }

      String[] predicates = exp.split(Pattern.quote("^"));
      PredicateBitSet ps = new PredicateBitSet();
      for (String predicate : predicates) {
        String[] split = splitPredicate(predicate);
        String lCol = split[0];
        int lOperandIndex = Integer.parseInt(split[1]) - 1;
        String op = split[2];
        String rCol = split[3];
        int rOperandIndex = Integer.parseInt(split[4]) - 1;
        if (!isLegalColumn(lCol, colNames) ||
            !isLegalColumn(rCol, colNames) ||
            !isLegalOperation(op) ||
            !isLegalIndex4DCObject(lOperandIndex) ||
            !isLegalIndex4DCObject(rOperandIndex)) {
          throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
        }
        ps.add(new Predicate(DCFormatUtil.convertString2Operator(op),
                new ColumnOperand(
                    ConsistParsedColumnProvider.getParsedColumnInstance(defaultTable, lCol,
                        colTypeMap.get(lCol), colNames.indexOf(lCol)),
                    lOperandIndex
                ),
                new ColumnOperand(
                    ConsistParsedColumnProvider.getParsedColumnInstance(defaultTable, rCol,
                        colTypeMap.get(rCol), colNames.indexOf(rCol)),
                    rOperandIndex
                )
            )
        );
      }
      return new DenialConstraint(ps);
    } catch (DCMinderToolsException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * 将规则对象转换成规则字符串，且谓词顺序永远保持一致
   *
   * @param dc 规则对象
   * @return 规则字符串
   */
  public static String convertDC2String(DenialConstraint dc) {
    try {
      List<String> predicates = Lists.newArrayList();
      PredicateBitSet predicateSet = dc.getPredicateSet();
      for (Predicate p : predicateSet) {
        ColumnOperand<?> operand1 = p.getOperand1();
        ColumnOperand<?> operand2 = p.getOperand2();
        int operand1Index = operand1.getIndex() + 1;
        int operand2Index = operand2.getIndex() + 1;
        String col1Name = operand1.getColumn().getName();
        String col2Name = operand2.getColumn().getName();
        String op = DCFormatUtil.convertOperator2String(p.getOperator());
        if (!isLegalIndex4DCString(operand1Index) ||
            !isLegalIndex4DCString(operand2Index)) {
          throw new DCMinderToolsException("Illegal column index for DC string");
        }
        String predicate = "t"
            + operand1Index
            + "."
            + col1Name
            + op
            + "t"
            + operand2Index
            + "."
            + col2Name;
        predicates.add(predicate);
      }
      return "not("
          + predicates.stream()
          .sorted()
          .collect(Collectors.joining("^"))
          + ")";
    } catch (DCMinderToolsException e) {
      throw new RuntimeException(e);
    }
  }

  public static Operator convertString2Operator(String op) {
    return OperatorConst.str2objMap.get(op);
  }

  public static String convertOperator2String(Operator op) {
    return OperatorConst.obj2strMap.get(op);
  }

  public static String[] extractColumnNameType(String columnWithBracket) {
    int leftBracket = columnWithBracket.indexOf("(");
    int rightBracket = columnWithBracket.indexOf(")");
    String colName = columnWithBracket.substring(0, leftBracket);
    String colType = columnWithBracket.substring(leftBracket + 1, rightBracket);
    return new String[]{colName, colType};
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

  public static String[] splitPredicate(String predicate) throws DCMinderToolsException {
    // split has 5 elements: eg. ['City', 'x', '=', 'City', 'x']
    String[] result = new String[5];
    String operation = getOperation(predicate);
    String[] split = predicate.split(operation);
    if (split.length != 2) {
      throw new DCMinderToolsException(String.format("Illegal predicate: %s", predicate));
    }
    // Get column name and index from split[0] and split[1], eg. tx.City tx.City
    String[] res1 = getColNameAndIndex(split[0]);
    result[0] = res1[0];
    result[1] = res1[1];
    result[2] = operation;
    String[] res2 = getColNameAndIndex(split[1]);
    result[3] = res2[0];
    result[4] = res2[1];
    return result;
  }

  private static String[] getColNameAndIndex(String s) throws DCMinderToolsException {
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
    if (predicate.contains(OperatorConst.unequal)) {
      result = OperatorConst.unequal;
    } else if (predicate.contains(OperatorConst.greaterEqual)) {
      result = OperatorConst.greaterEqual;
    } else if (predicate.contains(OperatorConst.lessEqual)) {
      result = OperatorConst.lessEqual;
    } else if (predicate.contains(OperatorConst.greater)) {
      result = OperatorConst.greater;
    } else if (predicate.contains(OperatorConst.less)) {
      result = OperatorConst.less;
    } else if (predicate.contains(OperatorConst.equal)) {
      result = OperatorConst.equal;
    } else {
      throw new DCMinderToolsException("Unknown operation");
    }
    return result;
  }

  public static boolean isLegalColumn(String column, List<String> columnNames) {
    return columnNames.contains(column);
  }

  public static boolean isLegalOperation(String operation) {
    return Arrays.asList(OperatorConst.legalOperations).contains(operation);
  }

  public static boolean isLegalIndex4DCString(int columnIndex) {
    return columnIndex >= 1 && columnIndex <= 2;
  }

  public static boolean isLegalIndex4DCObject(int columnIndex) {
    return columnIndex >= 0 && columnIndex <= 1;
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
