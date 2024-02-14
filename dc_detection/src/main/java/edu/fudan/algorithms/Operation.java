package edu.fudan.algorithms;

import de.metanome.algorithm_integration.Operator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Lingfeng
 */
public class Operation {

  public final static String EQUAL = "=";
  public final static String UNEQUAL = "!=";
  public final static String GREATER = ">";
  public final static String GREATER_EQUAL = ">=";
  public final static String LESS = "<";
  public final static String LESS_EQUAL = "<=";
  public final static String[] legalOperations =
      {UNEQUAL, GREATER_EQUAL, LESS_EQUAL, GREATER, LESS, EQUAL};

  public static Map<String, Operator> OPMap = new HashMap<String, Operator>() {{
    put(EQUAL, Operator.EQUAL);
    put(UNEQUAL, Operator.UNEQUAL);
    put(GREATER, Operator.GREATER);
    put(LESS, Operator.LESS);
    put(GREATER_EQUAL, Operator.GREATER_EQUAL);
    put(LESS_EQUAL, Operator.LESS_EQUAL);
  }};

}
