package edu.xxx.transformat;

import de.hpi.naumann.dc.input.ParsedColumn;
import java.util.HashMap;
import java.util.Map;

/**
 * @author XXX
 */
public class ConsistParsedColumnProvider {

  /**
   * 提供全局一致的ParsedColumn，保证当其名称相同时，其对象地址相同
   * 目前hydra比较DenialConstraint时，未重写ParsedColumn的hashcode和equals方法，所以这样适配
   */
  private static Map<String, ParsedColumn<?>> consistPSMap = new HashMap<>();

  public static ParsedColumn<?> getParsedColumnInstance(String tableName, String columnName,
      Class type, int index) {
    String key = tableName + "_" + columnName;
    ParsedColumn<?> ps = null;
    if (consistPSMap.containsKey(key)) {
      ps = consistPSMap.get(key);
    } else {
      ps = new ParsedColumn<>(tableName, columnName, type, index);
      consistPSMap.put(key, ps);
    }
    return ps;
  }

}
