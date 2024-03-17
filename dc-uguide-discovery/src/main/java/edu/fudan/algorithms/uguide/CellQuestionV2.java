package edu.fudan.algorithms.uguide;

import static edu.fudan.algorithms.uguide.Strategy.getRandomElements;
import static edu.fudan.conf.DefaultConf.maxCellQuestionBudget;
import static edu.fudan.utils.DCUtil.getCellsOfViolation;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import de.hpi.naumann.dc.denialcontraints.DenialConstraint;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.paritions.LinePair;
import edu.fudan.algorithms.DCViolation;
import edu.fudan.transformat.DCFormatUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
 * <p>
 * 输出: falseDC, possible trueDCs, possible true vios(exclude tuple pairs?)
 */
@Slf4j
public class CellQuestionV2 implements CellQuestion {

  // 输入
  private final Input di;
  private final Set<TCell> cellsOfChanges;
  private final Set<DenialConstraint> dcs;
  private final Set<DCViolation> vios;
  // 默认设置
  private int budget = maxCellQuestionBudget;
  private double delta = 0.1;
  private boolean canBreakEarly = false;
  private boolean randomChoose = false;
  private double excludeLinePercent = 0.1;
  @Getter
  private CellQuestionResult result;

  public CellQuestionV2(Evaluation evaluation) {
    this.di = evaluation.getDirtyData().getInput();
    this.cellsOfChanges = evaluation.getCellsOfChanges();
    this.dcs = evaluation.getCurrDCs();
    this.vios = evaluation.getCurrVios();
  }

  public CellQuestionV2(Input di, Set<TCell> cellsOfChanges, Set<DenialConstraint> dcs,
      Set<DCViolation> vios) {
    this.di = di;
    this.cellsOfChanges = cellsOfChanges;
    this.dcs = dcs;
    this.vios = vios;
  }

  @Override
  public int getBudgetUsed() {
    return this.result.getSelectedCells().size();
  }

  public void simulate() {
    // 建立索引
    Set<TCell> cells = Sets.newHashSet();
    Map<TCell, Set<DenialConstraint>> cellDCsMap = Maps.newHashMap();
    Map<TCell, Set<DCViolation>> cellViosMap = Maps.newHashMap();
    Map<DCViolation, Set<TCell>> vioCellsMap = Maps.newHashMap();  // 判断Vio中cells是否都是干净的
    Map<DenialConstraint, Set<DCViolation>> dcViosMap = Maps.newHashMap();  // 排除falseDC时排除它关联的vios
    for (DCViolation vio : vios) {
      LinePair linePair = vio.getLinePair();
      List<DenialConstraint> dcList = vio.getDenialConstraintList();
      DenialConstraint dc = dcList.get(0);
      // dc -> vios
      addToDCViosMap(vio, dc, dcViosMap);
      Set<TCell> cellsOfViolation = getCellsOfViolation(di, dc, linePair);
      for (TCell tCell : cellsOfViolation) {
        // cells
        cells.add(tCell);
        // cell -> vios
        addToCellViosMap(vio, tCell, cellViosMap);
        // vio -> cells
        addToVioCellsMap(vio, tCell, vioCellsMap);
        // cell -> dcs
        addToCellDCsMap(dc, tCell, cellDCsMap);
      }
    }
    log.debug("Cells size = {}", cells.size());
    log.debug("DCViosMap size = {}(Not all DCs have vios)", dcViosMap.size());
    log.debug("CellDCsMap size = {}", cellDCsMap.size());
    log.debug("CellViosMap size = {}", cellViosMap.size());
    log.debug("VioCellsMap size = {}", vioCellsMap.size());
    ArrayList<TCell> cellsList = new ArrayList<>(cells);

    // 检查哪些是falseDCs
    int falseDCsSize = checkFalseDCs(vioCellsMap, cellsOfChanges);

    // w1 权重1 关联的DCs的平均置信度越低越优先 w1 = 0.0 ~ +
    // w2 权重2 关联的Vios的平均“已判断过的全为干净cell”的比例越高越优先 w2 = 1.0~0.0
    // w = w1+w2  0.1 + 2/3  0 + 3/3
    Map<DenialConstraint, Double> dcWeightMap = Maps.newHashMap();
    double w1Init = 0.0;
    for (DenialConstraint dc : dcs) {
      dcWeightMap.put(dc, w1Init);
    }

    // 模拟提问
    Set<TCell> chosenFromCellList = Sets.newHashSet();
    Set<TCell> chosenFromPendingList = Sets.newHashSet();
    Set<TCell> selectedCells = Sets.newHashSet();
    Set<TCell> dirtyCells = Sets.newHashSet();
    Set<TCell> cleanCells = Sets.newHashSet();
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    Set<DenialConstraint> trueDCs = Sets.newHashSet();
    Set<DCViolation> falseVios = Sets.newHashSet();
    Set<DCViolation> trueVios = Sets.newHashSet();
    // 如果有一个脏cell，就说明是真vio，直接改DC置信度，就不用pending了，否则需要进一步确认
    Set<TCell> pendingCells = Sets.newHashSet();
    log.debug("Start asking cell questions...");
    for (int i = 0; i < budget; i++) {
      // 测试：当所有的falseDC找到后，可以提前结束
      if (canBreakEarly && falseDCs.size() == falseDCsSize) {
        break;
      }
      if (cellsList.isEmpty()) {
        log.warn("Cells is empty");
        break;
      }
      if ((i + 1) % 100 == 0) {
        log.debug("Select {}/{}", i + 1, budget);
      }
      // TODO: 作为对比算法，我们发现，由于dirtyCell太少了，所有即便每次选取关联的DC平均置信度最低的cell的，也大概率选的是cleanCell
      //  此时对置信度其实并无贡献，只有发现dirtyCell了，才对置信度有贡献。
      // 选择cell
      TCell selectedCell = randomChoose ?
          randomChooseCell(cellsList) :  // 随机选择cell
          chooseCell(cellsList, cellDCsMap, dcWeightMap, pendingCells, falseDCs, chosenFromCellList,
              chosenFromPendingList);  // 根据置信度选择cell
      selectedCells.add(selectedCell);
//      log.debug("SelectedCell = {}", selectedCell.toString());

      // 辅助结构
      Set<DenialConstraint> dcsOfSelectedCell = cellDCsMap.get(selectedCell);
      Set<DCViolation> viosOfSelectedCell = cellViosMap.get(selectedCell);
      Set<TCell> allCellsOfVios = getAllCellsOfVios(viosOfSelectedCell, vioCellsMap);
      // 判断选择的cell
      if (cellsOfChanges.contains(selectedCell)) {
        // 真错误
        dirtyCells.add(selectedCell);
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
        // 相关vios不用继续判断了，因为它们不可能是全干净cell了
        if (!pendingCells.isEmpty()) {
          pendingCells.removeIf(allCellsOfVios::contains);
        }
      } else {
        // 假错误
        cleanCells.add(selectedCell);

        // 找到置信度最低的vio的cell放入pendingCells
        ArrayList<DCViolation> viosOfCell = new ArrayList<>(viosOfSelectedCell);
        double minW = Double.MAX_VALUE;
        DCViolation minVio = null;
        for (DCViolation vio : viosOfCell) {
          DenialConstraint dc = vio.getDenialConstraintList().get(0);
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
          Set<TCell> filtered = minVioCells.stream()
              .filter(c -> !cleanCells.contains(c) && !dirtyCells.contains(c) &&
                  !pendingCells.contains(c))
              .collect(Collectors.toSet());
          pendingCells.addAll(filtered);
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
            throw new RuntimeException(
                String.format("Illegal dirtyCellNum: %s", dirtyCellNum));
          }
          // 统计干净cell的数量
          if (cleanCellNum == cellNum) {
            // 若全是干净cell，说明是假vio
            // 排除falseDC
            List<DenialConstraint> candiDCs = vio.getDenialConstraintList();
            falseDCs.addAll(candiDCs);
            // 直接删除falseDC，且计算权重时不再考虑falseDC
            for (DenialConstraint candiDC : candiDCs) {
              dcWeightMap.remove(candiDC);
            }
            // 排除falseVio
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
        "SelectedCells={}, DirtyCells={}, cleanCells={}, falseDCs={}, falseVios={}, trueVios={}, "
            + "ChosenFromCellList={}, ChosenFromPendingList={}",
        selectedCells.size(), dirtyCells.size(), cleanCells.size(), falseDCs.size(),
        falseVios.size(), trueVios.size(), chosenFromCellList.size(), chosenFromPendingList.size());
    // 打印找到的falseDC和带置信度的DC
    log.debug("Found falseDCs:{}", falseDCs.size());
    for (DenialConstraint falseDC : falseDCs) {
      log.debug("{}", DCFormatUtil.convertDC2String(falseDC));
    }
    log.debug("Found possible trueDCs:");
    for (Entry<DenialConstraint, Double> e : dcWeightMap.entrySet()) {
      DenialConstraint dc = e.getKey();
      Double conf = e.getValue();
      if (conf > 0.0) {
        log.debug("{} -> {}(Added to possible trueDCs)", DCFormatUtil.convertDC2String(dc), conf);
        trueDCs.add(dc);
      } else {
        log.debug("{} -> {}(Added to falseDCs)", DCFormatUtil.convertDC2String(dc), conf);
        falseDCs.add(dc);
      }
    }
    // TODO: TrueDC如果也去掉其冲突元组的10%，那就和DCsQ一模一样了。暂时只去掉直接判断过的trueVio
    Set<Integer> excludedLines = Sets.newHashSet();
    for (DCViolation vio : trueVios) {
      LinePair linePair = vio.getLinePair();
      excludedLines.add(linePair.getLine1());
      excludedLines.add(linePair.getLine2());
    }
    int num = (int) Math.floor(excludeLinePercent * excludedLines.size());
    List<Integer> randomExcludedLines = getRandomElements(excludedLines, num);
    log.debug("RandomExcludedLines = {}, {} of {}", randomExcludedLines.size(), excludeLinePercent,
        excludedLines.size());
    this.result = new CellQuestionResult(selectedCells, new HashSet<>(), trueDCs, falseDCs,
        trueVios, falseVios, new HashSet<>(randomExcludedLines));
  }

  private static void addToDCViosMap(DCViolation vio, DenialConstraint dc,
      Map<DenialConstraint, Set<DCViolation>> dcViosMap) {
    if (dcViosMap.containsKey(dc)) {
      Set<DCViolation> viosOfDC = dcViosMap.get(dc);
      viosOfDC.add(vio);
    } else {
      dcViosMap.put(dc, Sets.newHashSet(vio));
    }
  }

  private static void addToCellDCsMap(DenialConstraint dc, TCell tCell,
      Map<TCell, Set<DenialConstraint>> cellDCsMap) {
    if (cellDCsMap.containsKey(tCell)) {
      Set<DenialConstraint> set = cellDCsMap.get(tCell);
      set.add(dc);
    } else {
      cellDCsMap.put(tCell, Sets.newHashSet(dc));
    }
  }

  private static void addToVioCellsMap(DCViolation vio, TCell tCell,
      Map<DCViolation, Set<TCell>> vioCellsMap) {
    if (vioCellsMap.containsKey(vio)) {
      Set<TCell> tCells = vioCellsMap.get(vio);
      tCells.add(tCell);
    } else {
      vioCellsMap.put(vio, Sets.newHashSet(tCell));
    }
  }

  private static void addToCellViosMap(DCViolation vio, TCell tCell,
      Map<TCell, Set<DCViolation>> cellViosMap) {
    if (cellViosMap.containsKey(tCell)) {
      Set<DCViolation> vios = cellViosMap.get(tCell);
      vios.add(vio);
    } else {
      cellViosMap.put(tCell, Sets.newHashSet(vio));
    }
  }

  private int checkFalseDCs(Map<DCViolation, Set<TCell>> vioCellsMap, Set<TCell> cellsOfChanges) {
    Set<DenialConstraint> falseDCs = Sets.newHashSet();
    for (Entry<DCViolation, Set<TCell>> entry : vioCellsMap.entrySet()) {
      DCViolation vio = entry.getKey();
      Set<TCell> cells = entry.getValue();
      List<DenialConstraint> dclist = vio.getDenialConstraintList();
      for (DenialConstraint dc : dclist) {
        if (falseDCs.contains(dc)) {
          continue;
        }
        // 全是干净的cell，说明冲突是假冲突，DC为falseDC
        if (allClean(cells, cellsOfChanges)) {
          falseDCs.add(dc);
        }
      }
    }
    log.debug("FalseDCs: {}", falseDCs.size());
    for (DenialConstraint falseDC : falseDCs) {
      log.debug("   {}", DCFormatUtil.convertDC2String(falseDC));
    }
    return falseDCs.size();
  }


  private boolean allClean(Set<TCell> cells, Set<TCell> cellsOfChanges) {
    for (TCell cell : cells) {
      if (cellsOfChanges.contains(cell)) {
        return false;
      }
    }
    return true;
  }

  private static Set<TCell> getAllCellsOfVios(Set<DCViolation> viosOfSelectedCell,
      Map<DCViolation, Set<TCell>> vioCellsMap) {
    Set<TCell> cls = Sets.newHashSet();
    for (DCViolation vio : viosOfSelectedCell) {
      Set<TCell> cs = vioCellsMap.get(vio);
      cls.addAll(cs);
    }
    return cls;
  }

  private TCell randomChooseCell(ArrayList<TCell> cellsList) {
    Collections.shuffle(cellsList);
    return cellsList.remove(0);
  }

  private static TCell chooseCell(ArrayList<TCell> cellsList,
      Map<TCell, Set<DenialConstraint>> cellDCsMap,
      Map<DenialConstraint, Double> dcWeightMap,
      Set<TCell> pendingCells, Set<DenialConstraint> falseDCs, Set<TCell> chosenFromCellList,
      Set<TCell> chosenFromPendingList) {
    TCell selectedCell;
    // 选cell
    if (!pendingCells.isEmpty()) {
      Iterator<TCell> it = pendingCells.iterator();
      selectedCell = it.next();
      it.remove();
      cellsList.remove(selectedCell);
      chosenFromPendingList.add(selectedCell);
    } else {
      sortCells(cellsList, cellDCsMap, dcWeightMap, falseDCs);
      selectedCell = cellsList.remove(0);
      chosenFromCellList.add(selectedCell);
    }
    return selectedCell;
  }


  private static void sortCells(
      List<TCell> cells,
      Map<TCell, Set<DenialConstraint>> cellDCsMap,
      Map<DenialConstraint, Double> dcWeightMap,
      Set<DenialConstraint> falseDCs) {
    cells.sort(Comparator.comparingDouble(cell -> {
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
    }));
  }

}
