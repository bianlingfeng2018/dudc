package edu.fudan.transformat;

import de.metanome.algorithm_integration.Operator;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Lingfeng
 */
public class OperationStr {

  public final static String equal = "=";
  public final static String unequal = "!=";
  public final static String greater = ">";
  public final static String greaterEqual = ">=";
  public final static String less = "<";
  public final static String lessEqual = "<=";
  public final static String[] legalOperations =
      {unequal, greaterEqual, lessEqual, greater, less, equal};

  public static Map<String, Operator> opString2ObjectMap = new HashMap<String, Operator>() {{
    put(equal, Operator.EQUAL);
    put(unequal, Operator.UNEQUAL);
    put(greater, Operator.GREATER);
    put(less, Operator.LESS);
    put(greaterEqual, Operator.GREATER_EQUAL);
    put(lessEqual, Operator.LESS_EQUAL);
  }};

  public static Map<Operator, String> opObject2StringMap = new HashMap<Operator, String>() {{
    put(Operator.EQUAL, equal);
    put(Operator.UNEQUAL, unequal);
    put(Operator.GREATER, greater);
    put(Operator.LESS, less);
    put(Operator.GREATER_EQUAL, greaterEqual);
    put(Operator.LESS_EQUAL, lessEqual);
  }};

}
