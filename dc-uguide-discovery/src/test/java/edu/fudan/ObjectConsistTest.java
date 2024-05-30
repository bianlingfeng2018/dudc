package edu.fudan;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.algorithms.uguide.Strategy.getSortedLines;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.algorithms.DCLoader;
import edu.fudan.algorithms.uguide.TCell;
import edu.fudan.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author Lingfeng
 */
@Slf4j
public class ObjectConsistTest {

  /**
   * Test TCell contains
   */
  @Test
  public void testTCellContains() {
    TCell c1 = new TCell(0, "Name", "Alice");
    TCell c2 = new TCell(1, "Name", "Alice");
    TCell c3 = new TCell(0, "Name0", "Alice");
    TCell c4 = new TCell(0, "Name", "Alice0");
    TCell c5 = new TCell(0, "Name", "Alice");
    HashSet<TCell> set = new HashSet<>();
    set.add(c1);

    log.debug("{}", set.contains(c1));  // True
    log.debug("{}", set.contains(c2));  // False
    log.debug("{}", set.contains(c3));  // False
    log.debug("{}", set.contains(c4));  // False
    log.debug("{}", set.contains(c5));  // True
  }

  /**
   * Test TCell reference
   */
  @Test
  public void testTCellRef() {
    TCell c1 = new TCell(0, "Name", "Alice");
    HashSet<TCell> set = new HashSet<>();
    set.add(c1);

    ArrayList<TCell> list = new ArrayList<>(set);
    // 检查引用是否相同
    for (TCell element : list) {
      log.debug("{}", set.contains(element));  // True
    }
  }

  @Test
  public void testDCContains() {
    String headerPath = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\header_hospital.txt";
    String dcsPath1 = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\dc_hospital_1.txt";
    String dcsPath2 = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\dc_hospital_2.txt";
    List<DenialConstraint> allDCs = DCLoader.load(headerPath, dcsPath1);
    List<DenialConstraint> testDCs = DCLoader.load(headerPath, dcsPath2);
    DenialConstraint dc1 = testDCs.get(0);
    DenialConstraint dc2 = testDCs.get(1);
    log.debug("DC1 = {}, hashcode = {}", DCFormatUtil.convertDC2String(dc1), dc1.hashCode());
    log.debug("DC2 = {}, hashcode = {}", DCFormatUtil.convertDC2String(dc2), dc2.hashCode());
    log.debug("Contains DC1 = {}", allDCs.contains(dc1));
    log.debug("Contains DC2 = {}", allDCs.contains(dc2));
    log.debug("DC1 Equals DC2 = {}", dc1.equals(dc2));
  }

  /**
   * Test sorted map. Sort ENTRY in descending order of VALUE(a set) size.
   */
  @Test
  public void testSortedMap() {
    HashSet<String> set1 = new HashSet<>();
    set1.add("A");
    set1.add("B");
    HashSet<String> set2 = new HashSet<>();
    set2.add("D");
    HashSet<String> set3 = new HashSet<>();
    set3.add("C");
    HashSet<String> set4 = new HashSet<>();

    HashMap<Integer, Set<String>> map = new HashMap<>();
    map.put(0, set4);
    map.put(1, set3);
    map.put(2, set2);
    map.put(3, set1);
    ArrayList<Entry<Integer, Set<String>>> sortedLines = getSortedLines(map);
    for (Entry<Integer, Set<String>> entry : sortedLines) {
      log.debug("Entry.key = {}, Entry.value = {}", entry.getKey(), entry.getValue());
    }  // Entry.key = 3 1 2 0
  }

  /**
   * Test random strategy. Get random elements from a set.
   */
  @Test
  public void testRandomStrategy() {
    HashSet<String> set = new HashSet<>();
    set.add("A");
    set.add("B");
    set.add("C");
    set.add("D");
    set.add("E");
    List<String> randomElements = getRandomElements(set, 3);
    for (String element : randomElements) {
      log.debug(element);
    }
  }

}
