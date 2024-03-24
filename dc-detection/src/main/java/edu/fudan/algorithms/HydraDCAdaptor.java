package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.defaultTable;
import static edu.fudan.transformat.DCFormatUtil.extractColumnNameType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Lingfeng
 */
class HydraDCAdaptor {

  /**
   * Convert hydra DCs to DCs no data
   *
   * @param dcsWithData
   * @return
   */
  public static List<DenialConstraint> buildDCsNoData(List<DenialConstraint> dcsWithData) {
    List<DenialConstraint> dcsNoData = Lists.newArrayList();
    for (DenialConstraint dc : dcsWithData) {
      DenialConstraint copy = buildDCNoData(dc);
      dcsNoData.add(copy);
    }
    return dcsNoData;
  }

  /**
   * Convert DCs with no data to hydra DCs with data
   *
   * @param input
   * @param dcsNoData
   * @return
   */
  public static DenialConstraintSet buildHydraDCs(Set<DenialConstraint> dcsNoData, Input input) {
    DenialConstraintSet dcs = new DenialConstraintSet();
    for (DenialConstraint dc : dcsNoData) {
      dcs.add(buildHydraDC(dc, input));
    }
    return dcs;
  }

  private static DenialConstraint buildHydraDC(DenialConstraint dcNoData, Input input) {
    if (dcNoData == null) {
      throw new RuntimeException("Illegal dcNoData: null");
    }
    // 构建parsedColMap
    Map<String, ParsedColumn<?>> parsedColMap = Maps.newHashMap();
    ParsedColumn<?>[] parsedColumns = input.getColumns();
    for (ParsedColumn<?> pc : parsedColumns) {
      // Key为属性名称 City(String) -> City
      String colName = extractColumnNameType(pc.getName())[0];
      parsedColMap.put(colName, pc);
    }
    // 构建hydraDC
    PredicateBitSet ps = new PredicateBitSet();
    for (Predicate p : dcNoData.getPredicateSet()) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      String colName1 = operand1.getColumn().getName();
      String colName2 = operand2.getColumn().getName();
      Predicate newP = new Predicate(p.getOperator(),
          new ColumnOperand<>(parsedColMap.get(colName1), operand1.getIndex()),
          new ColumnOperand<>(parsedColMap.get(colName2), operand2.getIndex()));
      ps.add(newP);
    }
    return new DenialConstraint(ps);
  }

  private static DenialConstraint buildDCNoData(DenialConstraint hydraDC) {
    PredicateBitSet newPs = new PredicateBitSet();
    for (Predicate p : hydraDC.getPredicateSet()) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      Predicate newP = new Predicate(
          p.getOperator(),
          new ColumnOperand<>(
              buildParsedColumnNoData(operand1.getColumn()),
              operand1.getIndex()
          ),
          new ColumnOperand<>(
              buildParsedColumnNoData(operand2.getColumn()),
              operand2.getIndex()
          )
      );
      newPs.add(newP);
    }
    DenialConstraint copy = new DenialConstraint(newPs);
    return copy;
  }

  private static ParsedColumn<?> buildParsedColumnNoData(ParsedColumn<?> parsedColumn) {
    // Hydra的列名默认是City(String)这种形式
    String columnName = extractColumnNameType(parsedColumn.getName())[0];
    ParsedColumn<?> newPs = new ParsedColumn<>(defaultTable,
        columnName,
        parsedColumn.getType(),
        parsedColumn.getIndex());
    return newPs;
  }

}
