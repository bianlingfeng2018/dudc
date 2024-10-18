package edu.xxx;

import de.hpi.naumann.dc.algorithms.hybrid.Hydra;
import edu.xxx.algorithms.HydraGenerator;
import edu.xxx.algorithms.HydraGenerator.SampleResult;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.evidenceset.IEvidenceSet;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.PredicateBuilder;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

/**
 * @author XXX
 */
@Slf4j
public class HydraGeneratorTest {

  private Boolean noCrossColumn = Boolean.TRUE;
  private double minimumSharedValue = 0.30d;

  @Test
  public void testGenDCs()
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    File f = new File(
        "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\preprocessed_hospital_dirty_10.csv");
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(f);
    Input input = new Input(actualGenerator.generateNewCopy());
    PredicateBuilder predicates = new PredicateBuilder(input, noCrossColumn, minimumSharedValue);
    log.info("Predicate space size:" + predicates.getPredicates().size());

    DenialConstraintSet dcs = new Hydra().run(input, predicates);
    log.info("Result size: " + dcs.size());
//		for (DenialConstraint dc : dcs) {
//			log.info(dc.toResult().toString());
//		}
  }

  @Test
  public void testGenSample()
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    String baseDir = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\";
    String d = "preprocessed_hospital.csv";
    String out = baseDir + "hydra_sampled_evidence_set_hospital.csv";
    log.info("采样数据 {} ...", d);
    File f = new File(baseDir + d);
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(f);
    Input input = new Input(actualGenerator.generateNewCopy());
    PredicateBuilder predicates = new PredicateBuilder(input, noCrossColumn, minimumSharedValue);
//		PredicateBuilder predicates = new PredicateBuilder(input, Boolean.FALSE, minimumSharedValue);
    log.info("Predicate space size:" + predicates.getPredicates().size());
    writePSpace(predicates.getPredicates(), d);
    SampleResult sampleResult = new HydraGenerator().runSample(input, predicates);
    IEvidenceSet sample = sampleResult.evidenceSet;
    Map<PredicateBitSet, Integer> evidenceCountMap = sampleResult.evidenceCountMap;
    log.info("证据集数量:{}", sample.size());

    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(out));
      boolean flag = false;
      for (PredicateBitSet predicateBitSet : sample) {
        // 证据出现次数
        Integer count = evidenceCountMap.get(predicateBitSet);
        DenialConstraint evidence = new DenialConstraint(predicateBitSet);
        // 检查第一个证据的长度
        int size = evidence.toResult().getPredicateCount();
        if (!flag) {
          log.info("第一个证据长度:{}", size);
          flag = true;
        }
        // 一个证据
        String line = evidence.toResult().toString();
        int start = line.indexOf("(");
        int end = line.indexOf(")");
        line = line.substring(start + 1, end);
        line = line.replace("." + d, "")
            .replace(" String", "")
            .replace(" Integer", "")
            .replace(" Double", "")
            .replace("<>", "!=")
            .replace("==", "=")
            .replace("t1", "t2")
            .replace("t0", "t1")
        ;
        line = line + "," + count;
        bw.write(line);
        bw.newLine();
      }
      bw.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        bw.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private void writePSpace(Set<Predicate> predicates, String d) {
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(
          new FileWriter("p_space_" + d + ".txt"));
      for (Predicate predicate : predicates) {
        String s = predicate.toString();
        bw.write(s);
        bw.newLine();
        bw.flush();
      }
      bw.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (bw != null) {
          bw.close();
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

//  private static Logger log = LoggerFactory.getLogger(DenialConstraintSetTest.class);

}
