package edu.fudan.algorithms;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author Lingfeng
 */
@AllArgsConstructor
@Getter
public class PredicateVO {

  private String operator;

  private ColumnOperandVO operand1;

  private ColumnOperandVO operand2;
}
