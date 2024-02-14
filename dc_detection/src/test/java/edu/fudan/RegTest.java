package edu.fudan;


import java.util.Arrays;
import org.junit.Test;

/**
 * @author Lingfeng
 */
public class RegTest {

  @Test
  public void testRemoveBrackets() {
    String res1 = "City(String)".replaceAll("\\(.*?\\)", "");
    String res2 = "City()".replaceAll("\\(.*?\\)", "");
    String res3 = "City".replaceAll("\\(.*?\\)", "");
    System.out.println(res1);
    System.out.println(res2);
    System.out.println(res3);
  }

  @Test
  public void testSplit() {
    String[] split1 = "A^B".split("\\^");
    System.out.println(Arrays.toString(split1));

  }
}
