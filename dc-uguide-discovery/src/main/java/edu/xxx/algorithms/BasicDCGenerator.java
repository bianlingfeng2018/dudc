package edu.xxx.algorithms;

import static edu.xxx.conf.DefaultConf.maxDCLen;
import static edu.xxx.utils.DCUtil.convertDCFinderDC2Str;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import edu.xxx.utils.DCUtil;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author XXX
 */
@Slf4j
public class BasicDCGenerator implements DCGenerator {

  private final String inputDataPath;
  private final String fullDCsPath;
  private final String topKDCsPath;
  private final String headerPath;
  private final Set<DenialConstraint> excludeDCs;
  private final double errorThreshold;
  private final int topK;
  @Setter
  private String evidencePath;

  public BasicDCGenerator(String inputDataPath, String fullDCsPath, String topKDCsPath,
      String headerPath, Set<DenialConstraint> excludeDCs, double errorThreshold, int topK) {
    this.inputDataPath = inputDataPath;
    this.fullDCsPath = fullDCsPath;
    this.topKDCsPath = topKDCsPath;
    this.headerPath = headerPath;
    this.excludeDCs = excludeDCs;
    this.errorThreshold = errorThreshold;
    this.topK = topK;
  }

//  @Override
//  public Set<DenialConstraint> generateDCs() {
//    try {
//      DenialConstraintSet dcs = DiscoveryEntry.discoveryDCsDCFinder(this.inputDataPath,
//          this.errorThreshold, this.evidencePath);
//      List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = getSortedDCs(
//          dcs);
//      log.info("Result size: " + dcList.size());
//
//      log.debug("Saving DCs into: " + this.fullDCsPath);
//      persistDCFinderDCs(dcList, this.fullDCsPath);
//    } catch (IOException e) {
//      throw new RuntimeException(e);
//    }
//    // 取前k个规则
//    // TODO: 删除特别长的规则，否则检测冲突的时间会很长
//    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(this.topK, this.fullDCsPath,
//        this.headerPath, this.excludeDCs, maxDCLen);
//    if (topKDCsPath != null) {
//      DCUtil.persistTopKDCs(topKDCs, this.topKDCsPath);
//    }
//    return new HashSet<>(topKDCs);
//  }

  @Override
  public Set<DenialConstraint> generateDCs() {
    // Call DCFinder to discover all dcs.
//    String[] args = new String[]{this.inputDataPath, "-t", String.valueOf(this.errorThreshold),
//        "-o", this.fullDCsPath};
//    int exitCode = new CommandLine(new DCFinderMocker()).execute(args);
//    log.debug("Discovery DCs (by DCFinder) exit code: {}", exitCode);

    // 创建 ProcessBuilder 实例并传入命令及参数
    String[] args = new String[]{this.inputDataPath, "-t", String.valueOf(this.errorThreshold),
        "-o", this.fullDCsPath};
    try {
      // 构造命令
      String command =
          "java -jar D:/MyFile/IdeaProjects/dc_miner_tools/libs/dc-discovery-1.0-SNAPSHOT-jar-with-dependencies.jar "
              + String.join(" ", args);
      log.debug("command: {}", command);

      // 使用 Runtime 执行命令
      Process process = Runtime.getRuntime().exec(command);

      // 获取进程的输入流（输出）
      InputStream inputStream = process.getInputStream();
      int exitCode = process.waitFor();
      System.out.println("命令执行完成，退出码: " + exitCode);

      // 打印命令输出
      int n;
      while ((n = inputStream.read()) != -1) {
        System.out.print((char) n);
      }

    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // 取前k个规则
    // TODO: 删除特别长的规则，否则检测冲突的时间会很长
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(this.topK, this.fullDCsPath,
        this.headerPath, this.excludeDCs, maxDCLen);
    if (topKDCsPath != null) {
      DCUtil.persistTopKDCs(topKDCs, this.topKDCsPath);
    }
    return new HashSet<>(topKDCs);
  }

  public static void persistDCFinderDCs(
      List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList,
      String dcsPathForFCDC) throws IOException {
    BufferedWriter writer = new BufferedWriter(new FileWriter(dcsPathForFCDC));
    for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcList) {
      // ----- 适配输出 -----
      String dcStr = convertDCFinderDC2Str(dc);
      writer.write(dcStr);
      writer.newLine();
    }
    writer.close();
  }

  public static List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> getSortedDCs(
      DenialConstraintSet dcs) {
    List<de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint> dcList = Lists.newArrayList();
    for (de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint dc : dcs) {
      dcList.add(dc);
    }
    dcList.sort(Comparator.comparingInt(
            de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraint::getPredicateCount)
        .thenComparing(System::identityHashCode));
    return dcList;
  }

}
