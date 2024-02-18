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
}
