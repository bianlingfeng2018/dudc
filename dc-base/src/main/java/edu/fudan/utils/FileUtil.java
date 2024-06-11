package edu.fudan.utils;

import com.google.common.collect.Lists;
import de.hpi.naumann.dc.input.Input;
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
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lingfeng
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
        log.debug("File deleted failed");
      }
    }
    // 创建新文件
    try {
      if (out.createNewFile()) {
        log.debug("File created: {}", out);
      } else {
        log.debug("File created failed");
      }
    } catch (IOException e) {
      log.debug("File created error: {}", e.getMessage());
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
      List<String> line = ri.next();
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
      if (changMap.containsKey(columnIndex)) {
        String correctCellValue = changMap.get(columnIndex);
        newLine.add(correctCellValue);
      } else {
        newLine.add(cellValue);
      }
    }
    return newLine;
  }
}
