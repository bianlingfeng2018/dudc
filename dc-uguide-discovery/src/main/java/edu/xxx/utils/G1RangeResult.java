package edu.xxx.utils;

import lombok.Getter;

/**
 * @author XXX
 */
@Getter
public class G1RangeResult {

  private Double[] range;

  private String constraint;

  public G1RangeResult(Double[] range, String constraint) {
    this.range = range;
    this.constraint = constraint;
  }

  @Override
  public String toString() {
    return constraint + "," + range[0] + "," + range[1];
  }
}
