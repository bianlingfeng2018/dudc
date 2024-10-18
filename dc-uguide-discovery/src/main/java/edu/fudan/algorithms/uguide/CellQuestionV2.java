package edu.fudan.algorithms.uguide;

import static edu.fudan.conf.DefaultConf.trueDCConfThreshold;
import static edu.fudan.conf.DefaultConf.userProb_cellq;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.fudan.algorithms.DCViolation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 */
@Slf4j
public class CellQuestionV2 {

  // 输入
  private final Input di;
  private final Set<TCell> cellsOfChanges;
  private final Set<DenialConstraint> dcs;
  private final Set<DCViolation> vios;
  // 默认设置
  private final int budget;
  private final double delta;
  private final boolean canBreakEarly;
  private final CellQStrategy strategy;
  private final double excludeLinePercent;

  public CellQuestionV2(Input di, Set<TCell> cellsOfChanges, Set<DenialConstraint> dcs,
      Set<DCViolation> vios, int budget, double delta, boolean canBreakEarly,
      CellQStrategy strategy, double excludeLinePercent) {
    this.di = di;
    this.cellsOfChanges = cellsOfChanges;
    this.dcs = dcs;
    this.vios = vios;
    this.budget = budget;
    this.delta = delta;
    this.canBreakEarly = canBreakEarly;
    this.strategy = strategy;
    this.excludeLinePercent = excludeLinePercent;
  }

  public CellQuestionResult simulate() {
    log.debug("Simulating cell question...");
    log.debug("Using strategy = {}, budget = {}", strategy, budget);
    log.debug("Building index start.");
    // All cells in all violations.
    Set<TCell> cells = Sets.newHashSet();
    Map<TCell, Set<DenialConstraint>> cellDCsMap = Maps.newHashMap();
    Map<TCell, Set<DCViolation>> cellViosMap = Maps.newHashMap();
    Map<DCViolation, Set<TCell>> vioCellsMap = Maps.newHashMap();
    Map<DenialConstraint, Set<DCViolation>> dcViosMap = Maps.newHashMap();
    for (DCViolation vio : this.vios) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcsNoData = vio.getDenialConstraintsNoData();
      if (dcsNoData.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcsNoData.get(0);
      addToDCViosMap(vio, dc, dcViosMap);
      Set<TCell> cellsOfViolation = getCellsOfViolation(this.di, dc, linePair);
      for (TCell tCell : cellsOfViolation) {
        cells.add(tCell);
        addToCellViosMap(vio, tCell, cellViosMap);
        addToVioCellsMap(vio, tCell, vioCellsMap);
        addToCellDCsMap(dc, tCell, cellDCsMap);
      }
    }
    log.debug("AllCellsInVios = {}", cells.size());
    log.debug("DCViosMap(Not all dcs have vios)={}, CellDCsMap={}, CellViosMap={}, VioCellsMap={}",
        dcViosMap.size(), cellDCsMap.size(), cellViosMap.size(), vioCellsMap.size());
    log.debug("Building index done.");

    // Print falseDCs (only for testing!!!).
    Set<DenialConstraint> falseDCsGround = printFalseDCs(vioCellsMap, cellsOfChanges);

    // The lower the average confidence(w) of the associated DC, the higher the priority.
    // w = 0.0 ~ +infinity
    Map<DenialConstraint, Double> dcWeightMap = Maps.newHashMap();
    double wInit = 0.0;
    for (DenialConstraint dc : dcs) {
      dcWeightMap.put(dc, wInit);
    }

    // Start asking questions.
    List<TCell> cellsList = new ArrayList<>(cells);
    Set<TCell> chosenFromCellList = Sets.newHashSet();
    Set<TCell> chosenFromPendingList = Sets.newHashSet();
    Set<TCell> selectedCells = Sets.newHashSet();
    Set<TCell> dirtyCells = Sets.newHashSet();
    Set<TCell> cleanCells = Sets.newHashSet();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    Set<DenialConstraint> possibleTrueDCs = Sets.newHashSet();
    Set<DCViolation> falseVios = Sets.newHashSet();
    Set<DCViolation> trueVios = Sets.newHashSet();
    // If a cell is dirty, the corresponding violation is TURE violation.
    // But if a cell is clean, the corresponding violation needs to be further confirmed by adding other cells in that violation.
    List<TCell> pendingCells = new ArrayList<>();
    log.debug("Iterating through cells...");
    for (int i = 0; i < budget; i++) {
      if ((i + 1) % 100 == 0) {
        log.debug("Budget used {}/{}", i + 1, budget);
      }
      // When all falseDCs are found, it can end early (only for testing!!!).
      if (canBreakEarly && falseDCs.size() == falseDCsGround.size()) {
        break;
      }
      if (cellsList.isEmpty()) {
        log.warn("Cells is empty");
        break;
      }
      // TODO: 作为对比算法，我们发现，由于dirtyCell太少了，所有即便每次选取关联的DC平均置信度最低的cell的，也大概率选的是cleanCell
      //  此时对置信度其实并无贡献，只有发现dirtyCell了，才对置信度有贡献。
      // 选择cell:
      // 1.随机选择cell
      // 2.根据置信度选择cell
      TCell selCell = null;
      switch (strategy) {
        case RANDOM_CELL:
          selCell = randomChooseCell(cellsList);
          break;
        case VIO_AND_CONF:
          selCell = chooseCell(cellsList, cellDCsMap, dcWeightMap, pendingCells, falseDCs,
              chosenFromCellList, chosenFromPendingList);
          break;
        default:
          log.error("Unknown strategy: {}", strategy);
          break;
      }
      selectedCells.add(selCell);
//      log.debug("SelectedCell = {}", selectedCell.toString());

      // 辅助结构
      Set<DenialConstraint> dcsOfSelectedCell = cellDCsMap.get(selCell);
      Set<DCViolation> viosOfSelectedCell = cellViosMap.get(selCell);
      Set<TCell> allCellsOfVios = getAllCellsOfVios(viosOfSelectedCell, vioCellsMap);
      // 判断选择的cell
      // Simulate checking if a cell is erroneous!!!
      if (isErrorCell(selCell)) {
        // 真错误
        dirtyCells.add(selCell);
        // 真冲突
        trueVios.addAll(viosOfSelectedCell);
        // 更新DC置信度w1
        for (DenialConstraint dc : dcsOfSelectedCell) {
          //
          if (falseDCs.contains(dc)) {
            continue;
          }
          double oldW = dcWeightMap.get(dc);
          dcWeightMap.put(dc, oldW + delta);
        }
        // 其余cell无需继续判定，因为它们属于同一个冲突，只要有一个cell已经被判定为脏cell了，那么不可能是全干净cell了
        if (!pendingCells.isEmpty()) {
          pendingCells.removeIf(allCellsOfVios::contains);
        }
      } else {
        // 假错误
        cleanCells.add(selCell);

        // 找到置信度最低的vio的cell放入pendingCells
        double minW = Double.MAX_VALUE;
        DCViolation minVio = null;
        for (DCViolation vio : viosOfSelectedCell) {
          List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
          if (dcs.size() != 1) {
            throw new RuntimeException("Illegal dcs size");
          }
          DenialConstraint dc = dcs.get(0);
          if (falseDCs.contains(dc)) {
            continue;
          }
          Double w = dcWeightMap.get(dc);
          if (w < minW) {
            minW = w;
            minVio = vio;
          }
        }
        if (minVio != null) {
          Set<TCell> minVioCells = vioCellsMap.get(minVio);
          // 已经被判定过为干净或者脏Cell的，或者已经在pendingCell里面的，需要过滤掉
          Set<TCell> cellsToBeAdd = minVioCells.stream().filter(
                  c -> !cleanCells.contains(c) && !dirtyCells.contains(c) && !pendingCells.contains(c))
              .collect(Collectors.toSet());
          pendingCells.addAll(cellsToBeAdd);
        }
//        log.debug("Add to pending cells: {}", filtered.size());

        // 判断是否有falseVio
        for (DCViolation vio : viosOfSelectedCell) {
          //
          if (trueVios.contains(vio) || falseVios.contains(vio)) {
            continue;
          }
          Set<TCell> cellsInVio = vioCellsMap.get(vio);
          int cellNum = cellsInVio.size();
          int cleanCellNum = 0;
          int dirtyCellNum = 0;
          for (TCell cell : cellsInVio) {
            if (cleanCells.contains(cell)) {
              cleanCellNum++;
            }
            if (dirtyCells.contains(cell)) {
              dirtyCellNum++;
            }
          }
          // 至少判断了一个cleanCell
          if (cleanCellNum == 0) {
            throw new RuntimeException("Illegal cleanCellNum: 0");
          }
          if (dirtyCellNum > 0) {
            // 脏cell理论上应该只会出现一次，且第一次出现是在上面的脏cell分支中
            throw new RuntimeException(String.format("Illegal dirtyCellNum: %s", dirtyCellNum));
          }
          // 统计干净cell的数量
          if (cleanCellNum == cellNum) {
            // 若全是干净cell，说明是假vio
            // 排除falseDC
            List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
            if (dcs.size() != 1) {
              throw new RuntimeException("Illegal dcs size");
            }
            // 直接删除falseDC，且计算权重时不再考虑falseDC
            DenialConstraint dc = dcs.get(0);
            falseDCs.add(dc);
            dcWeightMap.remove(dc);
            // 排除falseVio（待进一步完善）
            falseVios.add(vio);
          } else if (cleanCellNum < cellNum) {
            // 一部分是cleanCell，什么也不做，继续判断pendingCell
          } else {
            throw new RuntimeException(
                String.format("Illegal cell numbers: %s>%s", cleanCellNum, cellNum));
          }
        }
      }

    }

    log.debug(
        "SelectedCells={}, DirtyCells={}, CleanCells={}, FalseDCs={}, FalseVios={}, TrueVios={}, ChosenFromCellList={}, ChosenFromPendingList={}",
        selectedCells.size(), dirtyCells.size(), cleanCells.size(), falseDCs.size(),
        falseVios.size(), trueVios.size(), chosenFromCellList.size(), chosenFromPendingList.size());
    // 打印找到的falseDC和带置信度的DC
    log.debug("Found falseDCs:{}", falseDCs.size());
//    for (DenialConstraint falseDC : falseDCs) {
//      log.debug("{}", DCFormatUtil.convertDC2String(falseDC));
//    }
    for (Entry<DenialConstraint, Double> e : dcWeightMap.entrySet()) {
      DenialConstraint dc = e.getKey();
      Double conf = e.getValue();

      // 直接加入可能的真DC
//      log.debug("{} -> {}(Directly added to possible trueDCs)", DCFormatUtil.convertDC2String(dc), conf);
//      possibleTrueDCs.add(dc);
      // 置信度高的加入真DC
      if (conf >= trueDCConfThreshold) {
//        log.debug("{} -> {}(Conf > {}, added to possible trueDCs)", DCFormatUtil.convertDC2String(dc), conf,
//            trueDCConfThreshold);
        possibleTrueDCs.add(dc);
      }
      // 置信度为0，也加入falseDCs
//      else if (conf == 0){
//        falseDCs.add(dc);
//      }
    }
    log.debug("Found falseDCs (Confidence):{}", falseDCs.size());
    int falseDCCorrectionSize = 0;
    for (DenialConstraint dc : falseDCs) {
      if (falseDCsGround.contains(dc)) {
        falseDCCorrectionSize++;
      }
    }
    log.debug("Found falseDCs (Correction):{}", falseDCCorrectionSize);
    log.debug("Found possible trueDCs: {}", possibleTrueDCs.size());
    // TODO: TrueDC如果也去掉其冲突元组的10%，那就和DCsQ一模一样了。暂时只去掉直接判断过的trueVio
//    Set<Integer> excludedLines = Sets.newHashSet();
//    for (DCViolation vio : trueVios) {
//      LinePair linePair = vio.getLinePair();
//      excludedLines.add(linePair.getLine1());
//      excludedLines.add(linePair.getLine2());
//    }
//    int num = (int) Math.floor(excludeLinePercent * excludedLines.size());
//    List<Integer> randomExcludedLines = getRandomElements(excludedLines, num);
//    log.debug("RandomExcludedLines = {}, {} of {}", randomExcludedLines.size(), excludeLinePercent,
//        excludedLines.size());
    return new CellQuestionResult(falseDCs, possibleTrueDCs, falseVios, selectedCells.size());
  }

  private boolean isErrorCell(TCell selCell) {
//    return cellsOfChanges.contains(selCell);
    // TODO:用户判断正确率
    double r = Math.random();  // 生成0到1之间的随机数

    if (r < userProb_cellq) {
      // 60%的概率进入逻辑A
      return cellsOfChanges.contains(selCell);
    } else {
      // 40%的概率进入逻辑B
      return !cellsOfChanges.contains(selCell);
    }
  }

  private void addToDCViosMap(DCViolation vio, DenialConstraint dc,
      Map<DenialConstraint, Set<DCViolation>> dcViosMap) {
    if (dcViosMap.containsKey(dc)) {
      Set<DCViolation> viosOfDC = dcViosMap.get(dc);
      viosOfDC.add(vio);
    } else {
      dcViosMap.put(dc, Sets.newHashSet(vio));
    }
  }

  private void addToCellDCsMap(DenialConstraint dc, TCell tCell,
      Map<TCell, Set<DenialConstraint>> cellDCsMap) {
    if (cellDCsMap.containsKey(tCell)) {
      Set<DenialConstraint> set = cellDCsMap.get(tCell);
      set.add(dc);
    } else {
      cellDCsMap.put(tCell, Sets.newHashSet(dc));
    }
  }

  private void addToVioCellsMap(DCViolation vio, TCell tCell,
      Map<DCViolation, Set<TCell>> vioCellsMap) {
    if (vioCellsMap.containsKey(vio)) {
      Set<TCell> tCells = vioCellsMap.get(vio);
      tCells.add(tCell);
    } else {
      vioCellsMap.put(vio, Sets.newHashSet(tCell));
    }
  }

  private void addToCellViosMap(DCViolation vio, TCell tCell,
      Map<TCell, Set<DCViolation>> cellViosMap) {
    if (cellViosMap.containsKey(tCell)) {
      Set<DCViolation> vios = cellViosMap.get(tCell);
      vios.add(vio);
    } else {
      cellViosMap.put(tCell, Sets.newHashSet(vio));
    }
  }

  private Set<DenialConstraint> printFalseDCs(Map<DCViolation, Set<TCell>> vioCellsMap, Set<TCell> cellsOfChanges) {
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    for (Entry<DCViolation, Set<TCell>> entry : vioCellsMap.entrySet()) {
      DCViolation vio = entry.getKey();
      Set<TCell> cells = entry.getValue();
      List<DenialConstraint> dcs = vio.getDenialConstraintsNoData();
      if (dcs.size() != 1) {
        throw new RuntimeException("Illegal dcs size");
      }
      DenialConstraint dc = dcs.get(0);
      if (falseDCs.contains(dc)) {
        continue;
      }
      // 全是干净的cell，说明冲突是假冲突，DC为falseDC
      if (allClean(cells, cellsOfChanges)) {
        falseDCs.add(dc);
      }
    }
    log.debug("Printing falseDCs: {}", falseDCs.size());
//    for (DenialConstraint falseDC : falseDCs) {
//      log.debug(" {}", DCFormatUtil.convertDC2String(falseDC));
//    }
    return falseDCs;
  }

  private boolean allClean(Set<TCell> cells, Set<TCell> cellsOfChanges) {
    for (TCell cell : cells) {
      if (cellsOfChanges.contains(cell)) {
        return false;
      }
    }
    return true;
  }

  private Set<TCell> getAllCellsOfVios(Set<DCViolation> vios,
      Map<DCViolation, Set<TCell>> vioCellsMap) {
    Set<TCell> results = Sets.newHashSet();
    for (DCViolation vio : vios) {
      Set<TCell> cells = vioCellsMap.get(vio);
      results.addAll(cells);
    }
    return results;
  }

  private TCell randomChooseCell(List<TCell> cellsList) {
    Collections.shuffle(cellsList);
    return cellsList.remove(0);
  }

  private TCell chooseCell(List<TCell> cellsList, Map<TCell, Set<DenialConstraint>> cellDCsMap,
      Map<DenialConstraint, Double> dcWeightMap, List<TCell> pendingCells,
      Set<DenialConstraint> falseDCs, Set<TCell> chosenFromCellList,
      Set<TCell> chosenFromPendingList) {
    TCell selectedCell;
    if (!pendingCells.isEmpty()) {
      // 防止每次顺序不一样
      pendingCells.sort(Comparator.comparing((TCell c) -> c.toString()));
      selectedCell = pendingCells.remove(0);
      cellsList.remove(selectedCell);
      chosenFromPendingList.add(selectedCell);
    } else {
      sortCells(cellsList, cellDCsMap, dcWeightMap, falseDCs);
      selectedCell = cellsList.remove(0);
      chosenFromCellList.add(selectedCell);
    }
    return selectedCell;
  }

  private void sortCells(List<TCell> cells, Map<TCell, Set<DenialConstraint>> cellDCsMap,
      Map<DenialConstraint, Double> dcWeightMap, Set<DenialConstraint> falseDCs) {
    cells.sort(Comparator.comparingDouble((TCell cell) -> {
      Set<DenialConstraint> dcs = cellDCsMap.get(cell);
      double sum1 = 0.0;
      int cnt = 0;
      for (DenialConstraint dc : dcs) {
        if (falseDCs.contains(dc)) {
          continue;
        }
        Double w = dcWeightMap.get(dc);
        sum1 += w;
        cnt++;
      }
      double w = sum1 / cnt;
      return w;
    }).thenComparing((TCell c) -> c.toString()));  // 防止每次顺序不一样
  }

}
