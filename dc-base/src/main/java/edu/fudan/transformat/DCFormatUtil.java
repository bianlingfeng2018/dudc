package edu.fudan.transformat;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Lingfeng
 */
public class DCFormatUtil {

  public static String convertToDCStr(DenialConstraint dc) {
    List<String> predicates = Lists.newArrayList();
    PredicateBitSet predicateSet = dc.getPredicateSet();
    for (Predicate p : predicateSet) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      int i1 = operand1.getIndex() + 1;
      int i2 = operand2.getIndex() + 1;
      String col1Name = extractColName(operand1.getColumn().getName());
      String col2Name = extractColName(operand2.getColumn().getName());
      String op = OperationStr.operator2StrMap.get(p.getOperator());
      String predicate = "t" + i1 + "." + col1Name +
          op +
          "t" + i2 + "." + col2Name;
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
