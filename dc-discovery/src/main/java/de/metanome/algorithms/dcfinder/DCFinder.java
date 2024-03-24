package de.metanome.algorithms.dcfinder;

import com.google.common.collect.Lists;
import de.metanome.algorithm_integration.Operator;
import de.metanome.algorithms.dcfinder.denialconstraints.DenialConstraintSet;
import de.metanome.algorithms.dcfinder.evidenceset.IEvidenceSet;
import de.metanome.algorithms.dcfinder.evidenceset.builders.SplitReconstructEvidenceSetBuilder;
import de.metanome.algorithms.dcfinder.input.Input;
import de.metanome.algorithms.dcfinder.predicates.Predicate;
import de.metanome.algorithms.dcfinder.predicates.PredicateBuilder;
import de.metanome.algorithms.dcfinder.predicates.operands.ColumnOperand;
import de.metanome.algorithms.dcfinder.predicates.sets.PredicateSet;
import de.metanome.algorithms.dcfinder.setcover.partial.MinimalCoverSearch;
import edu.fudan.transformat.DCFormatUtil;
import edu.fudan.utils.FileUtil;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DCFinder {

	protected long chunkLength = 10000 * 5000;
	protected int bufferLength = 5000;
	protected double errorThreshold = 0.01d;
	protected long violationsThreshold = 0L;
	protected long rsize = 0;

	public DenialConstraintSet run(Input input, PredicateBuilder predicates, double errorThreshold,
      String evidenceFile) {
		// 设定errorThreshold
		this.errorThreshold = errorThreshold;

		input.buildPLIs();
		rsize = input.getLineCount();
		
		setViolationsThreshold();

		SplitReconstructEvidenceSetBuilder evidenceSetBuilder = new SplitReconstructEvidenceSetBuilder(input,
				predicates, chunkLength, bufferLength);
		evidenceSetBuilder.buildEvidenceSet();

    IEvidenceSet fullEvidenceSet = evidenceSetBuilder.getFullEvidenceSet();
    if (evidenceFile != null) {
      // 将证据集存储下来
      List<String> eviList = Lists.newArrayList();
      for (PredicateSet predicateSet : fullEvidenceSet) {
        String evidenceWithCount = convertEvidenceStr(predicateSet)
            + ","
            + fullEvidenceSet.getCount(predicateSet);
        eviList.add(evidenceWithCount);
      }
      FileUtil.writeStringLinesToFile(eviList, new File(evidenceFile));
      return null;
    }

		DenialConstraintSet dcs = new MinimalCoverSearch(predicates.getPredicates(),
        violationsThreshold).getDenialConstraints(fullEvidenceSet);

		return dcs;
	}

	private void setViolationsThreshold() {
		long totaltps = rsize * (rsize - 1);
		violationsThreshold = (long) Math.ceil(((double) totaltps * errorThreshold));
		log.info("Error threshold: " + errorThreshold + ".");
		log.info("Discovering DCs with at most " + violationsThreshold + " violating tuple pairs.");
	}

	private static Logger log = LoggerFactory.getLogger(DCFinder.class);

  private String convertEvidenceStr(PredicateSet predicateSet) {
    List<String> ps = Lists.newArrayList();
    for (Predicate predicate : predicateSet) {
      Operator operator = predicate.getOperator();
      String op = DCFormatUtil.convertOperator2String(operator);
      ColumnOperand<?> operand1 = predicate.getOperand1();
      ColumnOperand<?> operand2 = predicate.getOperand2();
      int i1 = operand1.getIndex() + 1;  // t0 -> t1, t1 -> t2
      int i2 = operand2.getIndex() + 1;  // t0 -> t1, t1 -> t2
      String col1 = operand1.getColumn().getName();
      String col2 = operand2.getColumn().getName();
      String colName1 = DCFormatUtil.extractColumnNameType(col1)[0];
      String colName2 = DCFormatUtil.extractColumnNameType(col2)[0];
      String p = "t" + i1 + "." + colName1 + op +
          "t" + i2 + "." + colName2;
      ps.add(p);
    }
    String evidence = ps.stream().sorted().collect(Collectors.joining("^"));
    return evidence;
  }
}
