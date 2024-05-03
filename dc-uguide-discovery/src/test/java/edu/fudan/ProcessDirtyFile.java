package edu.fudan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 用于给stock数据集注入的错误计算具体的值。数据集用BART注入错误后，先手动替换", "为"; "， 然后调用此main函数方法计算所有错误值，并写回脏数据文件
 */
public class ProcessDirtyFile {

  public static void main(String[] args) {
    String originFile = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\preprocessed_stock_dirty.csv";
    String tmpFile = "D:\\MyFile\\gitee\\dc_miner\\data\\preprocessed_data\\preprocessed_stock_tmp.csv";
    try {
      // 读取原文件
      File inputFile = new File(originFile);
      FileReader fileReader = new FileReader(inputFile);
      BufferedReader bufferedReader = new BufferedReader(fileReader);

      // 创建临时文件
      File tempFile = new File(tmpFile);
      FileWriter fileWriter = new FileWriter(tempFile);
      BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

      String line;
      while ((line = bufferedReader.readLine()) != null) {
        // 按条件处理每一行，这里只是简单示例
        String processedLine = processLine(line);

        // 将处理后的行写入临时文件
        bufferedWriter.write(processedLine);
        bufferedWriter.newLine();
      }

      // 关闭读写流
      bufferedReader.close();
      bufferedWriter.close();

      // 删除原文件
      if (inputFile.delete()) {
        // 将临时文件重命名为原文件
        if (!tempFile.renameTo(inputFile)) {
          System.out.println("无法重命名临时文件");
        }
      } else {
        System.out.println("无法删除原文件");
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static String processLine(String line) {
    // 在这里添加对每一行的处理逻辑，这里只是简单示例
    String[] split = line.split(",");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < split.length; i++) {
      String s = split[i];
      sb.append(extractWrongValue(s, i))
          .append(",");
    }
    if (sb.length() > 0) {
      sb.deleteCharAt(sb.length() - 1);
    }
    return sb.toString();
  }

  private static String extractWrongValue(String s, int index) {
    // 40194.low,"[56.76; INF]",37.77 注意前后双引号要转义
    // 53045.open,"[-INF; 36.55]",46.43
    if (s.contains("[-INF; ")) {
      // 需要减小数值
      String newStr = s.replace("\"[-INF; ", "")
          .replace("]\"", "");
      String v =
          index == 6 ? String.valueOf(Integer.parseInt(newStr) - 1) :
              String.valueOf(Double.parseDouble(newStr) - 1.0);
      return v;
    } else if (s.contains("; INF]")) {
      // 需要增大数值
      String newStr = s.replace("; INF]\"", "")
          .replace("\"[", "");
      String v =
          index == 6 ? String.valueOf(Integer.parseInt(newStr) + 1) :
              String.valueOf(Double.parseDouble(newStr) + 1.0);
      return v;
    } else {
      return s;
    }
  }
}
