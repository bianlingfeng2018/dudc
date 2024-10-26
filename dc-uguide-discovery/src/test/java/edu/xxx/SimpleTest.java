package edu.xxx;

import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTest {

  private static final Logger log = LoggerFactory.getLogger(SimpleTest.class);

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

  @Test
  public void testJoinWithBlank() {
    List<String> line = new ArrayList<>();
    line.add("a");
    line.add("");
    line.add("b");
    String join = String.join(",", line);
    log.debug("join: {}", join);
  }

  @Test
  public void testReadWithBlank()
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    String dsPath = "D:\\paper\\dc_user_guided_detection\\experiment\\data\\preprocessed_flights_dirty.csv";
    // 注意：读入csv文件后，ri用null表示空值，但是转换成Input后，空值变为""
    RelationalInput ri = new DefaultFileInputGenerator(new File(dsPath)).generateNewCopy();
    int i = 0;
    while (ri.hasNext() && i++ < 3) {
      List<String> next = ri.next();
      log.debug("next: {}", next);
    }
  }

  @Test
  public void testSplit2() {
//    String line = "a,,b";
    String line = "a,b,";
//    String[] split = line.split(",");
    String[] split = line.split(",", -1);
    log.debug("{}", split[2]);
    log.debug("split: {}", split.length);
  }

  public static String doubleToString(double number) {
    String str = Double.toString(number);
    if (str.endsWith(".0")) {
      str = str.substring(0, str.length() - 2); // 去掉末尾的".0"
    }
    return str;
  }

  @Test
  public void testToString() {
    Double a = new Double(-10);
    String b = doubleToString(a);
    log.debug("a = {}", b);

  }

  @Test
  public void testShuffle() {
    ArrayList<String> list = new ArrayList<>();
    list.add("a");
    list.add("b");
    list.add("c");
    list.add("d");
    list.add("e");

    log.debug("list: {}", list);
    Collections.shuffle(list);
    log.debug("list: {}", list);

  }

  @Test
  public void testRandom() {
    double r = Math.random();
    System.out.println(r);
  }
}
