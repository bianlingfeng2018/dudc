package edu.fudan.utils;

import lombok.Getter;

/**
 * @author Lingfeng
 */
@Getter
public class G1RangeResult {

  private Double[] range;

  private String constraint;

  public G1RangeResult(Double[] range, String constraint) {
    this.range = range;
    this.constraint = constraint;
  }
}
