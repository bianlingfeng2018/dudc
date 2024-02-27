package edu.fudan.algorithms;

import static edu.fudan.conf.DefaultConf.topK;

import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import edu.fudan.utils.DCUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class BasicDCGenerator implements DCGenerator {

  private final String inputDataPath;
  private final String dcsPathForFCDC;
  private final String headerPath;
  @Setter
  private Set<DenialConstraint> excludeDCs = Sets.newHashSet();

  public BasicDCGenerator(String inputDataPath, String dcsPathForFCDC, String headerPath) {
    this.inputDataPath = inputDataPath;
    this.dcsPathForFCDC = dcsPathForFCDC;
    this.headerPath = headerPath;
  }

  @Override
  public Set<DenialConstraint> generateDCsForUser() {
    log.info("Generate top-{} DCs, exclude: {}", topK, this.excludeDCs.size());
    // 从经过优化的采样数据中发现规则
    DiscoveryEntry.doDiscovery(this.inputDataPath, this.dcsPathForFCDC);
    // 取前k个规则
    List<DenialConstraint> topKDCs = DCUtil.generateTopKDCs(topK, this.dcsPathForFCDC,
        this.headerPath, this.excludeDCs);
    return new HashSet<>(topKDCs);
  }

}
