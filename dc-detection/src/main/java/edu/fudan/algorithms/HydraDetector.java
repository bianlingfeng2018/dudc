package edu.fudan.algorithms;

import static edu.fudan.utils.FileUtil.generateNewCopy;

import ch.javasoft.bitset.IBitSet;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.util.concurrent.AtomicLongMap;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.evidenceset.HashEvidenceSet;
import de.hpi.naumann.dc.evidenceset.IEvidenceSet;
import de.hpi.naumann.dc.evidenceset.build.PartitionEvidenceSetBuilder;
import de.hpi.naumann.dc.evidenceset.build.sampling.SystematicLinearEvidenceSetBuilder;
import de.hpi.naumann.dc.helpers.IndexProvider;
import de.hpi.naumann.dc.helpers.SuperSetWalker;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.ClusterPair;
import de.hpi.naumann.dc.paritions.IEJoin;
import de.hpi.naumann.dc.paritions.LinePair;
import de.hpi.naumann.dc.paritions.StrippedPartition;
import de.hpi.naumann.dc.predicates.PartitionRefiner;
import de.hpi.naumann.dc.predicates.Predicate;
import de.hpi.naumann.dc.predicates.PredicateBuilder;
import de.hpi.naumann.dc.predicates.PredicatePair;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HydraDetector {

  private final String dataPath;
  private final Set<DenialConstraint> dcsNoData;
  private final Boolean noCrossColumn = Boolean.TRUE;
  private final double minimumSharedValue = 0.30d;
  // 是否用第一列的ID属性替换linePair的行号 ID = 行号 + 1
  private boolean replaceLineWithID = false;

  /**
   * 采样次数
   * TODO: 当数据集很小时，如果报错，可以将 sampleRounds 设为 1
   */
  protected int sampleRounds = 20;

  public HydraDetector(String dataPath, String dcsPath, String headerPath) {
    this.dataPath = dataPath;
    this.dcsNoData = new HashSet<>(DCLoader.load(headerPath, dcsPath));
  }

  public HydraDetector(String dataPath, Set<DenialConstraint> dcsNoData) {
    this.dataPath = dataPath;
    this.dcsNoData = dcsNoData;
  }

  public DCViolationSet detect(boolean replaceLineWithID) {
    this.replaceLineWithID = replaceLineWithID;
    return detect();
  }

  public DCViolationSet detect() {
    Input input = generateNewCopy(this.dataPath);
    PredicateBuilder predicates = new PredicateBuilder(input, this.noCrossColumn, this.minimumSharedValue);
    DenialConstraintSet set = HydraDCAdaptor.buildHydraDCs(this.dcsNoData, input);

    IEvidenceSet sampleEvidence =
        new SystematicLinearEvidenceSetBuilder(predicates, sampleRounds).buildEvidenceSet(input);
//    log.debug("Checking {} DCs.", set.size());
//    log.debug("Input size {}", input.getLineCount());

//    log.debug("Building selectivity estimation");

    // frequency estimation predicate pairs
    Multiset<PredicatePair> paircountDC = frequencyEstimationForPredicatePairs(set);

    // selectivity estimation for predicates & predicate pairs
    AtomicLongMap<PartitionRefiner> selectivityCount = createSelectivityEstimation(sampleEvidence,
        paircountDC.elementSet());

    ArrayList<PredicatePair> sortedPredicatePairs = getSortedPredicatePairs(paircountDC,
        selectivityCount);

    IndexProvider<PartitionRefiner> indexProvider = new IndexProvider<>();

//    log.debug("Grouping DCs..");
    Map<IBitSet, List<DenialConstraint>> predicateDCMap = groupDCs(set, sortedPredicatePairs,
        indexProvider,
        selectivityCount);

    int[] refinerPriorities = getRefinerPriorities(selectivityCount, indexProvider, predicateDCMap);

    SuperSetWalker walker = new SuperSetWalker(predicateDCMap.keySet(), refinerPriorities);

//    log.debug("Calculating partitions..");

    HashEvidenceSet resultEv = new HashEvidenceSet();
    DCViolationSet violationSet = new DCViolationSet();

    ClusterPair startPartition = StrippedPartition.getFullParition(input.getLineCount());
    int[][] values = input.getInts();
    IEJoin iejoin = new IEJoin(values);
    PartitionEvidenceSetBuilder builder = new PartitionEvidenceSetBuilder(predicates, values);

    long startTime = System.nanoTime();
    walker.walk((inter) -> {
      if ((System.nanoTime() - startTime) > TimeUnit.MINUTES.toNanos(120)) {
        log.error("Walk Over time");
        return;
      }

      Consumer<ClusterPair> consumer = (clusterPair) -> {
        List<DenialConstraint> currentDCs = predicateDCMap.get(inter.currentBits);
        if (currentDCs != null) {
          if (currentDCs.size() != 1) {
            throw new RuntimeException("Illegal dcs size");
          }
          List<DenialConstraint> dcsNoData = HydraDCAdaptor.buildDCsNoData(currentDCs);

          // EtmPoint point = etmMonitor.createPoint("EVIDENCES");
          builder.addEvidences(clusterPair, resultEv);
          // point.collect();

          // 构建冲突集合
          for (Iterator<LinePair> it = clusterPair.getLinePairIterator(); it.hasNext(); ) {
            LinePair linePair = it.next();
            // 用id列编号-1来获取原来数据集中的行号（如果不增删和改变原来数据集的顺序，则直接用行号即可）
            if (linePair.getLine1() != linePair.getLine2()) {
              if (replaceLineWithID) {
                int id1 = Integer.parseInt((String) input.getColumns()[0].getValue(linePair.getLine1())) - 1;
                int id2 = Integer.parseInt((String) input.getColumns()[0].getValue(linePair.getLine2())) - 1;
                LinePair lp = new LinePair(id1, id2);
                DCViolation vio = new DCViolation(dcsNoData, currentDCs, lp);
                violationSet.add(vio);
              } else {
                DCViolation vio = new DCViolation(dcsNoData, currentDCs, linePair);
                violationSet.add(vio);
              }
            }
          }
        } else {
          inter.nextRefiner.accept(clusterPair);
        }
      };

      PartitionRefiner refiner = indexProvider.getObject(inter.newRefiner);
//			System.out.println(refiner);
      ClusterPair partition = inter.clusterPair != null ? inter.clusterPair : startPartition;
      partition.refine(refiner, iejoin, consumer);

    });

    return violationSet;
  }

  private static BiFunction<AtomicLongMap<PartitionRefiner>,
      Function<PartitionRefiner, Integer>, Comparator<PartitionRefiner>> resultSorter = (
      selectivityCount, counts) -> (r2, r1) -> {

    long s1 = selectivityCount.get(r1);
    long s2 = selectivityCount.get(r2);

    return Double.compare(1.0d * counts.apply(r1).intValue() / s1,
        1.0d * counts.apply(r2).intValue() / s2);

  };

  private static BiFunction<Multiset<PredicatePair>,
      AtomicLongMap<PartitionRefiner>, Function<PredicatePair, Double>> pairWeight = (
      paircountDC, selectivityCount) -> (pair) -> {
    return Double.valueOf(1.0d * selectivityCount.get(pair) / paircountDC.count(pair));
  };

  private int[] getRefinerPriorities(AtomicLongMap<PartitionRefiner> selectivityCount,
      IndexProvider<PartitionRefiner> indexProvider,
      Map<IBitSet, List<DenialConstraint>> predicateDCMap) {
    int[] counts2 = new int[indexProvider.size()];
    for (int i = 0; i < counts2.length; ++i) {
      counts2[i] = 1;
    }
    for (IBitSet bitset : predicateDCMap.keySet()) {
      for (int i = bitset.nextSetBit(0); i >= 0; i = bitset.nextSetBit(i + 1)) {
        counts2[i]++;
      }
    }

    ArrayList<PartitionRefiner> refiners = new ArrayList<PartitionRefiner>();

    int[] counts3 = new int[indexProvider.size()];

    for (int i = 0; i < counts3.length; ++i) {
      PartitionRefiner refiner = indexProvider.getObject(i);
      refiners.add(refiner);
    }
    refiners.sort(resultSorter.apply(selectivityCount,
        refiner -> Integer.valueOf(counts2[indexProvider.getIndex(refiner).intValue()])));

    int i = 0;
    for (PartitionRefiner refiner : refiners) {
      counts3[indexProvider.getIndex(refiner).intValue()] = i;
      ++i;
    }
    return counts3;
  }

  private Map<IBitSet, List<DenialConstraint>> groupDCs(DenialConstraintSet set,
      ArrayList<PredicatePair> sortedPredicatePairs, IndexProvider<PartitionRefiner> indexProvider,
      AtomicLongMap<PartitionRefiner> selectivityCount) {
    Map<IBitSet, List<DenialConstraint>> predicateDCMap = new HashMap<>();
    HashMap<PredicatePair, Integer> prios = new HashMap<>();
    for (int i = 0; i < sortedPredicatePairs.size(); ++i) {
      prios.put(sortedPredicatePairs.get(i), Integer.valueOf(i));
    }
    for (DenialConstraint dc : set) {
      Set<PartitionRefiner> refinerSet = getRefinerSet(prios, dc);

      predicateDCMap.computeIfAbsent(indexProvider.getBitSet(refinerSet),
          (Set) -> new ArrayList<>()).add(dc);
    }
    return predicateDCMap;
  }

  private Set<PartitionRefiner> getRefinerSet(HashMap<PredicatePair, Integer> prios,
      DenialConstraint dc) {
    Set<PartitionRefiner> refinerSet = new HashSet<>();

    Set<Predicate> pairSet = new HashSet<>();
    dc.getPredicateSet().forEach(p -> {
      if (StrippedPartition.isSingleSupported(p)) {
        refinerSet.add(p);
      } else {
        pairSet.add(p);
      }
    });
    while (pairSet.size() > 1) {
      PredicatePair bestP = getBest(prios, pairSet);
      refinerSet.add(bestP);
      pairSet.remove(bestP.getP1());
      pairSet.remove(bestP.getP2());
    }
    if (!pairSet.isEmpty()) {
      refinerSet.add(pairSet.iterator().next());
    }
    return refinerSet;
  }

  private PredicatePair getBest(HashMap<PredicatePair, Integer> prios, Set<Predicate> pairSet) {
    int best = -1;
    PredicatePair bestP = null;
    for (Predicate p1 : pairSet) {
      for (Predicate p2 : pairSet) {
        if (p1 != p2) {
          PredicatePair pair = new PredicatePair(p1, p2);
          int score = prios.get(pair).intValue();
          if (score > best) {
            best = score;
            bestP = pair;
          }
        }
      }
    }
    return bestP;
  }

  private ArrayList<PredicatePair> getSortedPredicatePairs(Multiset<PredicatePair> paircountDC,
      AtomicLongMap<PartitionRefiner> selectivityCount) {
    ArrayList<PredicatePair> sortedPredicatePairs = new ArrayList<>();
    sortedPredicatePairs.addAll(paircountDC.elementSet());
    Function<PredicatePair, Double> weightProv = pairWeight.apply(paircountDC, selectivityCount);
    sortedPredicatePairs.sort(new Comparator<PredicatePair>() {

      @Override
      public int compare(PredicatePair o1, PredicatePair o2) {
        return Double.compare(getPriority(o2), getPriority(o1));
      }

      private double getPriority(PredicatePair o1) {
        return weightProv.apply(o1).doubleValue();
      }
    });
    return sortedPredicatePairs;
  }

  private Multiset<PredicatePair> frequencyEstimationForPredicatePairs(DenialConstraintSet set) {
    Multiset<PredicatePair> paircountDC = HashMultiset.create();
    for (DenialConstraint dc : set) {
      dc.getPredicateSet().forEach(p1 -> {
        if (StrippedPartition.isPairSupported(p1)) {
          dc.getPredicateSet().forEach(p2 -> {
            if (!p1.equals(p2) && StrippedPartition.isPairSupported(p2)) {
              paircountDC.add(new PredicatePair(p1, p2));
            }
          });
        }
      });
    }
    return paircountDC;
  }

  private AtomicLongMap<PartitionRefiner> createSelectivityEstimation(IEvidenceSet sampleEvidence,
      Set<PredicatePair> predicatePairs) {
    AtomicLongMap<PartitionRefiner> selectivityCount = AtomicLongMap.create();
    for (PredicateBitSet ps : sampleEvidence) {
      int count = (int) sampleEvidence.getCount(ps);
      ps.forEach(p -> {
        selectivityCount.addAndGet(p, count);
      });
      for (PredicatePair pair : predicatePairs) {
        if (pair.bothContainedIn(ps)) {
          selectivityCount.addAndGet(pair, sampleEvidence.getCount(ps));
        }
      }
    }
    return selectivityCount;
  }

  private static Logger log = LoggerFactory.getLogger(HydraDetector.class);

}
