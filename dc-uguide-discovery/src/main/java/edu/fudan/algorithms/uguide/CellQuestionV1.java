package edu.fudan.algorithms.uguide;

import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.utils.DataUtil.getDCsSetFromViolations;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.transformat.DCFormatUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class CellQuestionV1 {

  private final Evaluation evaluation;

  public CellQuestionV1(Evaluation evaluation) {
    this.evaluation = evaluation;
  }

  public CellQuestionResult simulate() {
    Set<DCViolation> selectedVios = evaluation.genCellQuestionsFromCurrState(maxCellQuestionBudget);
    Set<Integer> excludedLinesInCellQ = Sets.newHashSet();
    Set<DenialConstraint> dcsFromQuestions = getDCsSetFromViolations(selectedVios);
    Set<DenialConstraint> dcsUnchecked = Sets.newHashSet();
    Set<DenialConstraint> trueDCs = Sets.newHashSet();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    Set<DCViolation> trueVios = Sets.newHashSet();
    Set<DCViolation> falseVios = Sets.newHashSet();
    Map<DenialConstraint, Integer> dcTrustMap = Maps.newHashMap();
    for (DenialConstraint dc : dcsFromQuestions) {
      dcTrustMap.put(dc, 0);
    }
    for (DenialConstraint currDC : evaluation.getCurrDCs()) {
      if (!dcsFromQuestions.contains(currDC)) {
        dcsUnchecked.add(currDC);
      }
    }
    for (DCViolation vio : selectedVios) {
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      DenialConstraint dc = dcs.get(0);
      // Simulate check if it is a true violation!!!
      if (evaluation.isTrueViolation(dc, vio.getLinePair())) {
        trueVios.add(vio);
        // 若为真冲突，则增加DC的置信度
        dcTrustMap.put(dc, dcTrustMap.get(dc) + 1);
        // 若为真冲突，排除脏数据
        LinePair linePair = vio.getLinePair();
        int line1 = linePair.getLine1();
        int line2 = linePair.getLine2();
        excludedLinesInCellQ.add(line1);
        excludedLinesInCellQ.add(line2);
      } else {
        falseVios.add(vio);
        // 若为假冲突，排除假阳性DC
        falseDCs.add(dc);
      }
    }
    log.info("CheckedDCs(with trusts(TrueViolationSize)): {}", dcTrustMap.keySet().size());
    for (Entry<DenialConstraint, Integer> entry : dcTrustMap.entrySet()) {
      DenialConstraint dc = entry.getKey();
      Integer trust = entry.getValue();
      trueDCs.add(dc);
      log.debug("{}, {}", DCFormatUtil.convertDC2String(dc), trust);
    }
    // TODO: 1、置信度为真冲突个数，置信度低的可能是假阳性规则？2、冲突个数太多的（无论真假），可能是假阳性规则？
    log.info("FalseDCs: {}", falseDCs.size());
    for (DenialConstraint falseDC : falseDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(falseDC));
    }
    // TODO: 未检查的DC可能是因为其冲突没有取样，也可能是因为没有产生冲突，如果是这样：
    //  1、它是一个真规则，也是groundTruth，但是没有注入错误。（这种情况要避免？是groundTruth规则就要注入错误，否则该规则对减少冲突没贡献）
    //  2、它是一个真规则，不是groundTruth，没有注入错误。（此时该规则对检测冲突无贡献）
    //  3、可能是假规则吗？因为假规则一般都会产生一些冲突，但是是否绝对？
    //  1和2的情况目前是默认放到候选规则中，但是并不确定它是真规则，只有评估的时候可以确认，后面是否可以增加用户判断？
    log.info("UncheckedDCs: {}", dcsUnchecked.size());
    for (DenialConstraint dc : dcsUnchecked) {
      log.debug("{}", DCFormatUtil.convertDC2String(dc));
    }

    return new CellQuestionResult(falseDCs, falseVios, selectedVios.size());
  }

}
