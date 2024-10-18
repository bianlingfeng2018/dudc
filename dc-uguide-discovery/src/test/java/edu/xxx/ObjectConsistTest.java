package edu.xxx;

import static edu.xxx.algorithms.uguide.Strategy.getRandomElements;
import static edu.xxx.algorithms.uguide.Strategy.getSortedLines;

import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.xxx.algorithms.DCLoader;
import edu.xxx.algorithms.DCViolation;
import edu.xxx.algorithms.uguide.TCell;
import edu.xxx.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author XXX
 */
@Slf4j
public class ObjectConsistTest {

  private String headerPath = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\header.txt";
  private String dcsPath1 = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\dc_1.txt";
  private String dcsPath2 = "D:\\MyFile\\IdeaProjects\\dc_miner_tools\\data\\dc_2.txt";

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

  /**
   * Test DC contains. What DCs are equivalent?
   */
  @Test
  public void testDCContains() {
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
   * Test TViolation contains. What DCViolations are equivalent? Note that a DCViolation only
   * contains 1 associated DC.
   */
  @Test
  public void testTViolationContains() {
    List<DenialConstraint> testDCs = DCLoader.load(headerPath, dcsPath2);
    LinePair lp1 = new LinePair(0, 1);
    LinePair lp2 = new LinePair(0, 1);
//    not(t2.C>t1.D)
//    not(t1.C>t2.D^t1.E<t2.F)
//    not(t2.C>t1.D^t2.E<t1.F)
//    not(t2.C>t1.D)
//    false, false

//    not(t1.C>t2.D^t1.E<t2.F)
//    not(t2.C>t1.D)
//    not(t2.C>t1.D^t2.E<t1.F)
//    not(t2.C>t1.D)
//    true, true
    List<DenialConstraint> dcs1 = testDCs.subList(0, 2);
    List<DenialConstraint> dcs2 = testDCs.subList(2, 4);
    for (DenialConstraint dc : dcs1) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    for (DenialConstraint dc : dcs2) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }
    DCViolation v1 = new DCViolation(dcs1, null, lp1);
    DCViolation v2 = new DCViolation(dcs2, null, lp2);
    log.debug("{}, {}", v1.hashCode() == v2.hashCode(), v1.equals(v2));
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
    set2.add("A");
    HashSet<String> set3 = new HashSet<>();
    set3.add("A");
    HashSet<String> set4 = new HashSet<>();
    set4.add("A");

    HashMap<Integer, Set<String>> map = new HashMap<>();
    map.put(1, set4);
    map.put(0, set2);
    map.put(2, set3);
    map.put(3, set1);
    HashMap<Integer, Set<String>> mapVios = new HashMap<>();
    mapVios.put(0, set2);
    mapVios.put(1, set1);
    mapVios.put(2, set2);
    mapVios.put(3, set2);
    List<Integer> sortedLines = getSortedLines(map, mapVios);
    // line = 3 1 2 0
    for (Integer line : sortedLines) {
      log.debug("line = {}", line);
    }
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
