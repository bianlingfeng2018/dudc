package edu.fudan.utils;

import java.util.Arrays;
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

  @Override
  public String toString() {
    return constraint + "," + range[0] + "," + range[1];
  }
}
