package edu.fudan.algorithms;

import com.google.common.collect.Maps;
import de.hpi.naumann.dc.algorithms.hybrid.ResultCompletion;
import de.hpi.naumann.dc.cover.PrefixMinimalCoverSearch;
import de.hpi.naumann.dc.denialcontraints.DenialConstraintSet;
import de.hpi.naumann.dc.evidenceset.HashEvidenceSet;
import de.hpi.naumann.dc.evidenceset.IEvidenceSet;
import de.hpi.naumann.dc.evidenceset.build.sampling.ColumnAwareEvidenceSetBuilder;
import de.hpi.naumann.dc.evidenceset.build.sampling.SystematicLinearEvidenceSetBuilder;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.predicates.PredicateBuilder;
import de.hpi.naumann.dc.predicates.sets.PredicateBitSet;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HydraGenerator {

	protected int sampleRounds = 20;
	protected double efficiencyThreshold = 0.005d;


//	public IEvidenceSet runSample(Input input, PredicateBuilder predicates) {
	public SampleResult runSample(Input input, PredicateBuilder predicates) {

		log.info("Building approximate evidence set...");
		IEvidenceSet sampleEvidenceSet = new SystematicLinearEvidenceSetBuilder(predicates,
				sampleRounds).buildEvidenceSet(input);
		log.info("Estimation size systematic sampling:" + sampleEvidenceSet.size());

		HashEvidenceSet set = new HashEvidenceSet();
		Map<PredicateBitSet, Integer> countMap = Maps.newHashMap();
		sampleEvidenceSet.getSetOfPredicateSets().forEach(i -> {
			set.add(i);
		});
		IEvidenceSet fullEvidenceSet = new ColumnAwareEvidenceSetBuilder(predicates).buildEvidenceSet(set, input, efficiencyThreshold);
		log.info("Evidence set size deterministic sampler: " + fullEvidenceSet.size());

		DenialConstraintSet dcsApprox = new PrefixMinimalCoverSearch(predicates).getDenialConstraints(fullEvidenceSet);
		log.info("DC count approx:" + dcsApprox.size());
		dcsApprox.minimize();
		log.info("DC count approx after minimize:" + dcsApprox.size());


		IEvidenceSet result = new ResultCompletion(input, predicates).complete(dcsApprox, sampleEvidenceSet,
				fullEvidenceSet);

		for (PredicateBitSet s : result.getSetOfPredicateSets()) {
			if(!countMap.containsKey(s)) {
				// 已经存在
				countMap.put(s, 1);
			} else {
				countMap.put(s, countMap.get(s) + 1);
			}
		}

//		return result;
		return new SampleResult(result, countMap);
	}

	public static class SampleResult {
		public IEvidenceSet evidenceSet;
		public Map<PredicateBitSet, Integer> evidenceCountMap;

		public SampleResult(IEvidenceSet fullEvidenceSet, Map<PredicateBitSet, Integer> countMap) {
			this.evidenceSet = fullEvidenceSet;
			this.evidenceCountMap = countMap;
		}
	}
	private static Logger log = LoggerFactory.getLogger(HydraGenerator.class);

}
