package edu.fudan.transformat;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.operands.ColumnOperand;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Lingfeng
 */
public class DCUnifyUtil {

  public static List<DenialConstraint> getUnifiedCopyOf(List<DenialConstraint> currentDCs) {
    ArrayList<DenialConstraint> dcList = Lists.newArrayList();
    for (DenialConstraint dc : currentDCs) {
      DenialConstraint copy = getUnifiedCopyOf(dc);
      dcList.add(copy);
    }
    return dcList;
  }

  public static DenialConstraint getUnifiedCopyOf(DenialConstraint dc) {
    PredicateBitSet newPs = new PredicateBitSet();
    for (Predicate p : dc.getPredicateSet()) {
      ColumnOperand<?> operand1 = p.getOperand1();
      ColumnOperand<?> operand2 = p.getOperand2();
      Predicate newP = new Predicate(p.getOperator(),
          new ColumnOperand<>(getUnifiedCopyOfParsedColumn(operand1),
              p.getOperand1().getIndex()),
          new ColumnOperand<>(getUnifiedCopyOfParsedColumn(operand2),
              p.getOperand2().getIndex()));
      newPs.add(newP);
    }
    DenialConstraint copy = new DenialConstraint(newPs);
    return copy;
  }

  private static ParsedColumn<?> getUnifiedCopyOfParsedColumn(ColumnOperand<?> operand) {
    ParsedColumn<?> ps = operand.getColumn();
    ParsedColumn<?> newPs = new ParsedColumn<>("xxx",
        ps.getName(),
        ps.getType(),
        ps.getIndex());
    return newPs;
  }


}
