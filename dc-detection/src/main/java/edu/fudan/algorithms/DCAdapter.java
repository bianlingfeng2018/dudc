package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.defaultTable;
import static edu.fudan.transformat.DCFormatUtil.extractColNameAndType;
import static edu.fudan.transformat.DCFormatUtil.extractExpression;
import static edu.fudan.transformat.DCFormatUtil.isLegalColumn;
import static edu.fudan.transformat.DCFormatUtil.isLegalIndex4DCObject;
import static edu.fudan.transformat.DCFormatUtil.isLegalOperation;
import static edu.fudan.transformat.DCFormatUtil.splitPredicate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import edu.fudan.DCMinderToolsException;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.transformat.OperationStr;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Lingfeng
 */
class DCAdapter {

  /**
   * Convert hydra DCs to unified DCs
   *
   * @param dcsWithData
   * @return
   */
  public static List<DenialConstraint> getUnifiedDCs(List<DenialConstraint> dcsWithData) {
    // Strip the data from dcsWithData
    List<DenialConstraint> dcsWithoutData = Lists.newArrayList();
    for (DenialConstraint dc : dcsWithData) {
      DenialConstraint copy = getUnifiedCopyOfDC(dc);
      dcsWithoutData.add(copy);
    }
    return dcsWithoutData;
  }

  /**
   * Read and convert to hydra DCs
   *
   * @param input
   * @param dcsPath
   * @return
   * @throws IOException
   * @throws DCMinderToolsException
   */
  public static DenialConstraintSet getHydraDCs(Input input, String dcsPath)
      throws IOException, DCMinderToolsException {
    DenialConstraintSet dcs = new DenialConstraintSet();
    BufferedReader br = new BufferedReader(new FileReader(dcsPath));
    String line = null;
    while ((line = br.readLine()) != null) {
      DenialConstraint dc = getHydraDC(line, input);
      dcs.add(dc);
    }
    br.close();
    return dcs;
  }

  private static DenialConstraint getHydraDC(String dcStr, Input input)
      throws DCMinderToolsException {
    if (dcStr == null) {
      throw new DCMinderToolsException("Illegal dc: null");
    }
    String exp = extractExpression(dcStr);
    if (exp == null) {
      throw new DCMinderToolsException(String.format("Illegal dc: %s'", dcStr));
    }
    // Prepare
    List<String> colNames = Lists.newArrayList();
    Map<String, ParsedColumn<?>> parsedColMap = Maps.newHashMap();
    ParsedColumn<?>[] parsedColumns = input.getColumns();
    for (ParsedColumn<?> pc : parsedColumns) {
      // eg. City(String) -> City
      String colName = extractColNameAndType(pc.getName())[0];
      colNames.add(colName);
      parsedColMap.put(colName, pc);
    }
    // Convert
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
      ps.add(new Predicate(OperationStr.opString2ObjectMap.get(op),
          new ColumnOperand<>(parsedColMap.get(lCol), lOperandIndex),
          new ColumnOperand<>(parsedColMap.get(rCol), rOperandIndex)));
    }
    return new DenialConstraint(ps);
  }


  private static DenialConstraint getUnifiedCopyOfDC(DenialConstraint dc) {
    PredicateBitSet newPs = new PredicateBitSet();
    for (Predicate p : dc.getPredicateSet()) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      Predicate newP = new Predicate(p.getOperator(),
          new ColumnOperand<>(getUnifiedCopyOfParsedColumn(operand1.getColumn()),
              operand1.getIndex()),
          new ColumnOperand<>(getUnifiedCopyOfParsedColumn(operand2.getColumn()),
              operand2.getIndex()));
      newPs.add(newP);
    }
    DenialConstraint copy = new DenialConstraint(newPs);
    return copy;
  }

  private static ParsedColumn<?> getUnifiedCopyOfParsedColumn(ParsedColumn<?> parsedColumn) {
    ParsedColumn<?> newPs = new ParsedColumn<>(defaultTable,
        // hydra默认是City(String)，这里要去掉括号
        DCFormatUtil.extractColNameAndType(parsedColumn.getName())[0],
        parsedColumn.getType(),
        parsedColumn.getIndex());
    return newPs;
  }

}
