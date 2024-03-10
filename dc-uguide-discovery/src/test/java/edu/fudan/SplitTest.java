package edu.fudan;

import java.util.regex.Pattern;
import org.junit.Test;

public class SplitTest {

  @Test
  public void testSplit() {
    String delimiter = "^"; // 假设这是你的分隔符变量

    String text = "hello^world^java";
    String[] parts = text.split(Pattern.quote(delimiter));
//    String[] parts = text.split(delimiter);

    for (String part : parts) {
      System.out.println(part);
    }
  }

  @Test
  public void testMath() {
    int i1 = (int) Math.floor(0.2);
    double i2 = Math.floor(0.2);
    System.out.println(i1);
    System.out.println(i2);
  }
}
