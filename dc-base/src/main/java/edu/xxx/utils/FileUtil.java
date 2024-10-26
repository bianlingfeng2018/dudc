package edu.xxx.utils;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.input.Input;
import de.hpi.naumann.dc.input.ParsedColumn;
import de.metanome.algorithm_integration.input.InputGenerationException;
import de.metanome.algorithm_integration.input.InputIterationException;
import de.metanome.algorithm_integration.input.RelationalInput;
import de.metanome.backend.input.file.DefaultFileInputGenerator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jersey.repackaged.com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

/**
 * @author XXX
 */
@Slf4j
public class FileUtil {

  /**
   * 读取指定数据文件路径的列名称，并在结束时关闭资源
   *
   * @param path 数据路径
   * @return 列名称
   */
  public static List<String> readColumnNames(String path) {
    List<String> columnNames;
    RelationalInput ri = null;
    try {
      ri = new DefaultFileInputGenerator(new File(path)).generateNewCopy();
      columnNames = ri.columnNames();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (ri != null) {
          ri.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return columnNames;
  }

  /**
   * 读取数据，并在结束时关闭资源
   *
   * @param dataPath
   * @return
   */
  public static Input generateNewCopy(String dataPath) {
    Input input = null;
    RelationalInput ri = null;
    try {
      ri = new DefaultFileInputGenerator(new File(dataPath)).generateNewCopy();
      input = new Input(ri);
    } catch (FileNotFoundException | InputGenerationException | InputIterationException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        if (ri != null) {
          ri.close();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return input;
  }

  public static void writeListLinesToFile(List<List<String>> lines, File out) {
    // 删除文件（如果存在）
    if (out.exists()) {
      if (out.delete()) {
        log.debug("File deleted: {}", out);
      } else {
//        throw new RuntimeException("File deleted failed");
        log.error("Failed to delete file: {}", out);
      }
    }
    // 创建新文件
    try {
      if (out.createNewFile()) {
        log.debug("File created: {}", out);
      } else {
        throw new RuntimeException("File created failed");
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // 写入文件
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(out));
      for (List<String> line : lines) {
        String joined = String.join(",", line);
        bw.write(joined);
        bw.newLine();
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

  public static void writeStringLinesToFile(List<String> lines, File out) {
    BufferedWriter bw = null;
    try {
      bw = new BufferedWriter(new FileWriter(out));
      for (String line : lines) {
        bw.write(line);
        bw.newLine();
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

  public static List<List<String>> getLinesWithHeader(DefaultFileInputGenerator actualGenerator,
      Set<Integer> sampled, boolean requireHeader)
      throws InputGenerationException, InputIterationException {
    ArrayList<List<String>> lines = Lists.newArrayList();
    RelationalInput ri = actualGenerator.generateNewCopy();
    if (requireHeader) {
      List<String> columnNames = ri.columnNames();
      lines.add(columnNames);
    }
    int i = 0;
    while (ri.hasNext()) {
      List<String> next = ri.next();
      if (sampled.contains(i)) {
        lines.add(next);
      }
      i++;
    }
    try {
      ri.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  public static AffTuplesResult getAffectedTuples(Set<Integer> excludedLines,
      Map<Integer, Map<Integer, String>> lineChangesMap, File data)
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(data);
    List<List<String>> rows = Lists.newArrayList();
    Set<Integer> affTuples = Sets.newHashSet();
    RelationalInput ri0 = actualGenerator.generateNewCopy();
    RelationalInput ri1 = actualGenerator.generateNewCopy();
    Input input = new Input(ri0);
    int count = input.getLineCount();
    ParsedColumn<?>[] columns = input.getColumns();

    // 加表头，ID xxx xxx，增加ID是为了记住行号（约定行号从0开始，ID从1开始）
    List<String> headers = Lists.newArrayList("ID(String)");
    headers.addAll(ri0.columnNames());
    rows.add(headers);

    // col1:a b c col2:d e f
    List<Set<String>> affectedValues = Lists.newArrayList();
    int colSize = columns.length;
    for (int i = 0; i < colSize; i++) {
      affectedValues.add(Sets.newHashSet());
    }

    int lineIndex = 0;
    while (ri1.hasNext()) {
      List<String> lineRi = ri1.next();
      List<String> line = copyLine(lineRi);
      if (excludedLines.contains(lineIndex)) {
        // 修复行
        Map<Integer, String> changMap = lineChangesMap.get(lineIndex);
        for (int columnIndex = 0; columnIndex < line.size(); columnIndex++) {
          String cellValue = line.get(columnIndex);
          if (changMap.containsKey(columnIndex)) {
            // 修复单元格
            String correctCellValue = changMap.get(columnIndex);
            Set<String> affVals = affectedValues.get(columnIndex);
            // 新值、旧值 都加入被影响的值
            affVals.add(cellValue);
            affVals.add(correctCellValue);
          }
        }
      }
      lineIndex++;
    }

    for (int colIndex = 0; colIndex < colSize; colIndex++) {
      ParsedColumn<?> c = columns[colIndex];
      // 找出第i列所有受影响的元组
      Set<String> avs = affectedValues.get(colIndex);
      for (int tupleIndex = 0; tupleIndex < count; tupleIndex++) {
        Comparable<?> value = c.getValue(tupleIndex);
        if (avs.contains(value.toString())) {
          // 找到受影响的元组id
          affTuples.add(tupleIndex);
        }
      }
    }

    // 把所有受影响的行，第一列加上ID属性
    RelationalInput ri2 = actualGenerator.generateNewCopy();
    int i = 0;
    while (ri2.hasNext()) {
      List<String> lineRi = ri2.next();

      if (affTuples.contains(i)) {
        List<String> cpLine = copyLine(lineRi);
        // 第一列加上ID属性 ID = i(行号) + 1
        cpLine.add(0, String.valueOf(i + 1));
        // 加入结果行
        rows.add(cpLine);
      }

      i++;
    }

    try {
      ri0.close();
      ri1.close();
      ri2.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return new AffTuplesResult(affTuples, rows);
  }

  public static List<List<String>> getRepairedLinesWithHeader(Set<Integer> excludedLines,
      Map<Integer, Map<Integer, String>> lineChangesMap, File data)
      throws FileNotFoundException, InputGenerationException, InputIterationException {
    DefaultFileInputGenerator actualGenerator = new DefaultFileInputGenerator(data);
    ArrayList<List<String>> lines = Lists.newArrayList();
    RelationalInput ri = actualGenerator.generateNewCopy();
    // Add header
    List<String> columnNames = ri.columnNames();
    lines.add(columnNames);
    int lineIndex = 0;
    while (ri.hasNext()) {
      List<String> lineRi = ri.next();
      List<String> line = copyLine(lineRi);

      if (excludedLines.contains(lineIndex)) {
        // 修复行
        List<String> newLine = repairedLine(lineIndex, line, lineChangesMap);
        lines.add(newLine);
      } else {
        // 复制行
        lines.add(line);
      }
      lineIndex++;
    }
    try {
      ri.close();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return lines;
  }

  private static List<String> repairedLine(int lineIndex, List<String> line,
      Map<Integer, Map<Integer, String>> lineChangesMap) {
    List<String> newLine = Lists.newArrayList();
    Map<Integer, String> changMap = lineChangesMap.get(lineIndex);
    for (int columnIndex = 0; columnIndex < line.size(); columnIndex++) {
      String cellValue = line.get(columnIndex);
      if (changMap != null && changMap.containsKey(columnIndex)) {
        String correctCellValue = changMap.get(columnIndex);
        newLine.add(correctCellValue);
      } else {
        newLine.add(cellValue);
      }
    }
    return newLine;
  }

  private static List<String> copyLine(List<String> lineRi) {
    // TODO: 读入csv文件后，ri用null表示空值，这里需要替换为""
    List<String> line = Lists.newArrayList();
    for (String c : lineRi) {
      if (c== null || c.equals("null")) {
        line.add("");
      } else {
        line.add(c);
      }
    }
    return line;
  }

}
